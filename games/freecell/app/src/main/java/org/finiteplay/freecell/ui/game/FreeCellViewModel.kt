package org.finiteplay.freecell.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.finiteplay.core.session.StatisticsPeriod
import org.finiteplay.core.session.shouldRunTimer
import org.finiteplay.core.ui.layout.DiscardingAction
import org.finiteplay.core.ui.layout.Handedness
import org.finiteplay.core.ui.layout.HintTimeout
import org.finiteplay.core.ui.layout.RestReminderInterval
import org.finiteplay.core.session.RestReminderWindow
import org.finiteplay.core.session.foregroundEntered
import org.finiteplay.core.session.foregroundExited
import org.finiteplay.core.session.foregroundMsAsOf
import org.finiteplay.core.session.isRestReminderDue
import org.finiteplay.core.ui.theme.ThemeMode
import org.finiteplay.freecell.deal.FreeCellCertifiedDealCatalog
import org.finiteplay.freecell.deal.FreeCellSolutionCatalog
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.dealGame
import org.finiteplay.freecell.layout.GameStatus
import org.finiteplay.freecell.layout.GameVersions
import org.finiteplay.freecell.rules.Move
import org.finiteplay.freecell.rules.applyMove
import org.finiteplay.freecell.rules.findAutoFinish
import org.finiteplay.freecell.rules.isLegal
import org.finiteplay.freecell.rules.legalMoves
import org.finiteplay.freecell.session.FreeCellLogEntry
import org.finiteplay.freecell.session.FreeCellSession
import org.finiteplay.freecell.session.commitAutoFinishWith
import org.finiteplay.freecell.session.commitMove
import org.finiteplay.freecell.session.undo
import org.finiteplay.freecell.session.withAutomaticMoves
import org.finiteplay.freecell.storage.FreeCellActiveGameLoadResult
import org.finiteplay.freecell.storage.FreeCellActiveGameStore
import org.finiteplay.freecell.storage.FreeCellHistoryRecord
import org.finiteplay.freecell.storage.FreeCellHistoryStore
import org.finiteplay.freecell.storage.FreeCellOutcome
import org.finiteplay.freecell.storage.FreeCellSettings
import org.finiteplay.freecell.storage.FreeCellSettingsStore
import org.finiteplay.freecell.storage.FreeCellTraversalStore
import org.finiteplay.freecell.storage.FreeCellStatistics
import org.finiteplay.core.storage.DealProgressStore
import org.finiteplay.core.storage.DealStatus
import org.finiteplay.freecell.storage.computeFreeCellStatistics
import org.finiteplay.freecell.solver.HINT_SOLVER_LIMITS
import org.finiteplay.freecell.solver.HintEngine
import org.finiteplay.freecell.solver.HintOutcome
import org.finiteplay.solitaire.catalog.catalog.DealTraversal

/**
 * Versions this pass deals against — frozen the same way Klondike's and Spider's are
 * (`docs/games/freecell/EXECUTION_PLAN.md` "RF — Rules Freeze", now closed).
 */
internal val FREECELL_VERSIONS = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1)

/** How long a No-solution or Inconclusive hint notice stays up before dismissing itself. */
internal val HINT_NOTICE_AUTO_DISMISS = 4.seconds

/**
 * How long a hint search runs before [FreeCellViewModel.hintShowsProgressDialog] switches on
 * (`docs/games/freecell/UI_SPEC.md` "Hint"). Below this, a search resolves silently — flashing a
 * modal for an instant would read as broken rather than helpful.
 */
internal val HINT_PROGRESS_DIALOG_DELAY = 1.seconds

/**
 * How often [FreeCellViewModel.checkRestReminder] re-evaluates the current window. Coarse on
 * purpose: the configured interval is tens of minutes at the shortest, so a check every fifteen
 * seconds costs nothing noticeable while keeping the reminder's own delay past the true boundary
 * small.
 */
/**
 * How often [FreeCellViewModel.refreshRestReminderElapsed] updates and
 * [FreeCellViewModel.checkRestReminder] re-evaluates the current window. A full second, matching
 * the elapsed-play ticker: Settings' read-only display is the reason this needs to be this
 * fine-grained at all — the reminder itself would be just as correct checked far less often,
 * since the configured interval is tens of minutes at the shortest.
 */
internal val REST_REMINDER_CHECK_INTERVAL = 1.seconds

/**
 * [HINT_SOLVER_LIMITS] at the player's own chosen wall-clock ceiling (`core:ui`'s [HintTimeout],
 * "Hint timeout" in Settings) rather than the solver module's fixed default.
 */
private fun hintSolverLimits(timeout: HintTimeout) = HINT_SOLVER_LIMITS.copy(maxDurationMs = timeout.seconds * 1_000L)

/**
 * What the Hint action currently shows (`docs/games/freecell/DESIGN.md` "Hint",
 * `docs/games/freecell/UI_SPEC.md` "Hint"). Simpler than Klondike's own: FreeCell has no strategy
 * ruleset to draw an Inconclusive suggestion from, so that case carries nothing to highlight
 * alongside its notice (`HintOutcome.Inconclusive`'s own doc explains why that is a deliberate
 * scope choice, not an oversight).
 */
sealed class HintUiState {
    data object Hidden : HintUiState()
    data object Loading : HintUiState()
    data class Guided(val move: Move) : HintUiState()
    data object NoSolution : HintUiState()
    data object Inconclusive : HintUiState()
}

/** The board immediately before the automatic finish began, plus the exact move sequence
 * [org.finiteplay.freecell.rules.findAutoFinish] proved wins from it, so [FreeCellBoard] can
 * animate the sweep leg by leg instead of jumping straight to the final board. */
data class PendingAutoFinish(val fromState: FreeCellState, val moves: List<Move>)

/**
 * Holds the game in progress and persists it: a deal on screen, tap and drag, undo, an
 * unconfirmed-when-untouched New Game and Replay, the automatic cascade and finish, settings,
 * statistics, the certified deal catalog, and Hint (`docs/games/freecell/EXECUTION_PLAN.md`
 * F3a/F3b/F5/F6).
 *
 * [initialSeed] is a throwaway placeholder deal, never shown: with a [catalogLoader], [isLoading]
 * gates the screen until the real catalog (or its failure) is known, the same way [store] already
 * gates it on restoration. It only matters to a test that supplies neither, letting it construct a
 * known board directly, mirroring Klondike's and Spider's own view models.
 *
 * [store], [settingsStore], [historyStore], [traversalStore], and [catalogLoader] are null in
 * tests that do not exercise persistence or the catalog, which then behave exactly as this class
 * did before it had either (mirroring `SpiderViewModel`'s own optional stores). With a
 * [catalogLoader] but no [traversalStore], a fresh deal always takes the catalog's first seed —
 * deterministic, not random, which is what a test that supplies a loader but no traversal store
 * wants.
 *
 * Unlike Klondike's or Spider's own certified catalogs, FreeCell's has no uncertified fallback
 * tier (`docs/games/freecell/DEALS.md`): [catalogLoader] failing leaves the game permanently
 * non-playable behind [unrecoverableCatalogReason] until [retryCatalogLoad] succeeds, rather than
 * dealing from an unverified formula.
 */
class FreeCellViewModel(
    private val initialSeed: Long = Random.nextLong(),
    private val store: FreeCellActiveGameStore? = null,
    private val settingsStore: FreeCellSettingsStore? = null,
    private val historyStore: FreeCellHistoryStore? = null,
    private val traversalStore: FreeCellTraversalStore? = null,
    private var certifiedCatalog: FreeCellCertifiedDealCatalog? = null,
    /** Reason the bundled catalog cannot be used; surfaces the non-playable state. */
    private var catalogLoadFailure: String? = null,
    /** Retries reading the bundled catalog after an unrecoverable asset error. */
    private val catalogLoader: (() -> FreeCellCertifiedDealCatalog.LoadResult)? = null,
    /** The shipped winning line per certified seed (`assets/solutions.bin`), so Hint can follow a
     * known solution on a fresh deal instead of searching from scratch. Null in tests that do not
     * exercise it, which then behave exactly as this class did before it existed. */
    private val solutionCatalog: FreeCellSolutionCatalog? = null,
    /** Debug builds only; see [MoveRecorder]. */
    private val moveRecorder: MoveRecorder? = null,
    /** Which deals have been played and won; null in tests that do not exercise it (progress is then kept in memory only). */
    private val dealProgressStore: DealProgressStore? = null,
) : ViewModel() {

    /** Current settings; [FreeCellSettings.DEFAULT] until the store has been read, and always that when there is none. */
    var settings by mutableStateOf(FreeCellSettings.DEFAULT)
        private set

    var session by mutableStateOf(newDeal(initialSeed, settings.automaticMovesEnabled))
        private set

    /** The automatic-moves setting *as it stood when [session]'s own deal was made* — what a
     * restore must replay from, since the log's own `SetAutomaticMoves` entries only record
     * later changes (`FreeCellActiveGameStore`'s own `FreeCellDealParameters`). */
    private var initialAutomaticMovesEnabledForCurrentDeal: Boolean = settings.automaticMovesEnabled

    var elapsedSeconds by mutableIntStateOf(0)
        private set

    /**
     * True until the persisted game and the certified catalog (when either exists) have both been
     * checked. The board stays non-interactive while it holds, rather than flashing a fresh deal
     * that restoration is about to replace, or one drawn before the catalog was known to be valid.
     * Stays true forever on a catalog failure — [unrecoverableCatalogReason] takes over the screen
     * instead of an indefinite spinner.
     */
    var isLoading by mutableStateOf(store != null || catalogLoader != null)
        private set

    /** Set when a save existed and could not be read; the player is owed that notice. */
    var recoveryNoticeVisible by mutableStateOf(false)
        private set

    /** Reason the bundled catalog cannot be used, or null while it is fine (or not yet checked). */
    val unrecoverableCatalogReason: String? get() = catalogLoadFailure

    /**
     * 1-based position of the active seed within the certified catalog's own committed seed
     * list — the same shape as Klondike's own `dealNumber` (position within its difficulty
     * tier's list): a stable identity of the seed itself, not a play-count. The catalog's own
     * seed order never changes once shipped, unlike [FreeCellTraversalStore]'s randomized,
     * non-repeating traversal, which only decides which seed New Game draws next and was never
     * meant to be read as a player-facing number itself (`docs/games/freecell/DEALS.md` "App
     * integration"). Null before the catalog has loaded, or for a fixture/test board dealt
     * without one at all.
     */
    val dealNumber: Int? get() = certifiedCatalog?.seeds?.indexOf(session.state.seed)?.takeIf { it >= 0 }?.plus(1)

    /** Identifies the current deal, changing on every fresh deal or debug fixture load — never on
     * a move, undo, or restore of the same game. [FreeCellBoard] keys its whole animation and
     * gesture state on this, so a new deal always starts clean rather than inheriting a stray
     * in-progress drag or a stale `displayState` from the game before it. */
    var gameId: String by mutableStateOf(newGameId())
        private set

    /** True once this game's result has been written, so a win is recorded once. */
    private var currentGameRecorded = false

    /** Finished games, for Statistics. Empty with no store. */
    var historyRecords by mutableStateOf<List<FreeCellHistoryRecord>>(emptyList())
        private set

    var statisticsPeriod by mutableStateOf(StatisticsPeriod.ALL_TIME)
        private set

    val statistics: FreeCellStatistics
        get() = computeFreeCellStatistics(
            records = historyRecords,
            period = statisticsPeriod,
            nowMillis = System.currentTimeMillis(),
            // The game in progress counts toward "games played" while unfinished, the same
            // reasoning Klondike's own recomputation follows — but only once it is actually
            // worth confirming a discard of; a fresh, untouched deal is not a played game.
            hasUnfinishedPlayedGame = needsConfirmation() && !currentGameRecorded,
        )

    fun showStatisticsFor(period: StatisticsPeriod) {
        statisticsPeriod = period
    }

    /**
     * All-time bests, independent of whichever period [statistics] is currently showing — the win
     * dialog states a personal best plainly, not "this week's" (`docs/games/freecell/UI_SPEC.md`
     * "Win Presentation").
     */
    val allTimeStatistics: FreeCellStatistics
        get() = computeFreeCellStatistics(
            records = historyRecords,
            period = StatisticsPeriod.ALL_TIME,
            nowMillis = System.currentTimeMillis(),
        )

    fun resetStatistics() {
        historyRecords = emptyList()
        dealProgress = emptyMap()
        viewModelScope.launch { dealProgressStore?.clear() }
        val store = historyStore ?: return
        viewModelScope.launch { store.clear() }
    }

    /** Which deals have been played and won, by seed. */
    var dealProgress by mutableStateOf<Map<Long, DealStatus>>(emptyMap())
        private set

    /** Raises the active deal's progress: played once the player has acted on it, won once it is. */
    private fun syncDealProgress() {
        val state = session.state
        val reached = when {
            state.status == GameStatus.WON -> DealStatus.WON
            session.hasPlayerActed -> DealStatus.PLAYED
            else -> return
        }
        val old = dealProgress[state.seed]
        if (old != null && old.ordinal >= reached.ordinal) return
        dealProgress = dealProgress + (state.seed to reached)
        val store = dealProgressStore ?: return
        viewModelScope.launch { store.mark(state.seed, reached) }
    }

    /** How many deals the picker lists: the whole certified catalog, or null before it has loaded (no picker then). */
    val dealPickerCount: Int? get() = certifiedCatalog?.seeds?.size

    /** [dealProgress] keyed by the deal numbers the picker lists. */
    fun dealPickerProgress(): Map<Int, DealStatus> {
        val seeds = certifiedCatalog?.seeds ?: return emptyMap()
        return buildMap {
            seeds.forEachIndexed { index, seed -> dealProgress[seed]?.let { put(index + 1, it) } }
        }
    }

    private var pendingDealNumber: Int? = null

    /**
     * Starts the [number]th certified deal, confirming first when the game on screen has been
     * played and is unfinished — picking a deal replaces it exactly as New Game does, and records
     * the same loss. Picking the deal already on screen does nothing.
     */
    fun requestSelectDeal(number: Int) {
        val seeds = certifiedCatalog?.seeds ?: return
        if (number !in 1..seeds.size || seeds[number - 1] == session.state.seed) return
        if (needsConfirmation()) {
            pendingDealNumber = number
            pendingAction = DiscardingAction.NEW_GAME
        } else {
            selectDeal(number)
        }
    }

    private fun selectDeal(number: Int) {
        val seed = certifiedCatalog?.seeds?.getOrNull(number - 1) ?: return
        recordLossIfAbandoned()
        dealFresh(settings.automaticMovesEnabled, seed = seed)
        afterFreshDeal()
    }

    var isForeground = true
        private set
    private var isModalOpen = false
    private var tickerJob: Job? = null
    private var autoFinishJob: Job? = null

    private var restReminderWindow: RestReminderWindow? = null
    private var restReminderTickerJob: Job? = null
    private var restBreakJob: Job? = null

    /** True while [org.finiteplay.core.ui.layout.RestReminderDialog] is offered. */
    var showRestReminderDialog by mutableStateOf(false)
        private set

    /** Seconds left in an accepted break, or null when no break is running. */
    var restBreakRemainingSeconds by mutableStateOf<Int?>(null)
        private set

    /** The current window's own running total, for Settings' read-only display — zero right after a reset. */
    var restReminderElapsedSeconds by mutableIntStateOf(0)
        private set

    /**
     * True for the whole automatic finish, not just between individual commits, so a fast tap
     * cannot land in the gap between two of its steps and insert a move of its own into a
     * sequence computed against a board that move would have already changed — the same reasoning
     * Spider's own `isAutoFinishing` states in full.
     */
    var isAutoFinishing by mutableStateOf(false)
        private set

    /** Set once the automatic finish's search and commit have both already happened; cleared once
     * [FreeCellBoard] has consumed it via [consumeAutoFinishAnimationSignal]. */
    var pendingAutoFinish by mutableStateOf<PendingAutoFinish?>(null)
        private set

    /** Consumed by [FreeCellBoard] once it has read [pendingAutoFinish] and queued the sweep's animation. */
    fun consumeAutoFinishAnimationSignal() {
        pendingAutoFinish = null
    }

    /** Released by [FreeCellBoard] once it has finished playing every leg of the sweep's
     * animation — not by [runAutoFinishIfAvailable] itself, so input (and the win dialog) stay
     * held through the whole visible sweep, not just its own search and commit. */
    fun onSweepAnimationFinished() {
        isAutoFinishing = false
    }

    /**
     * The board as it stood immediately before the last [undo], so [FreeCellBoard] can animate
     * the reversal by diffing it against the now-current state. Cleared once [FreeCellBoard] has
     * consumed it via [consumeUndoAnimationSignal].
     */
    var pendingUndoAnimation by mutableStateOf<FreeCellState?>(null)
        private set

    /** Consumed by [FreeCellBoard] once it has read [pendingUndoAnimation] and queued the reversal's animation. */
    fun consumeUndoAnimationSignal() {
        pendingUndoAnimation = null
    }

    /** The action waiting on the player's confirmation, or null. */
    var pendingAction by mutableStateOf<DiscardingAction?>(null)
        private set

    /**
     * Increments once per committed player move — never for undo, a fresh deal, or a restore —
     * so a `LaunchedEffect` keyed on it plays the move sound exactly where a move actually
     * happened, without FreeCell needing an animation system of its own to hang the cue on
     * (unlike Klondike's, which fires its sound from the card's own flight).
     */
    var moveSequence by mutableIntStateOf(0)
        private set

    /** What the Hint action currently shows; see [HintUiState]. */
    var hintState by mutableStateOf<HintUiState>(HintUiState.Hidden)
        private set

    /**
     * True once a running [HintUiState.Loading] search has taken long enough to show
     * [org.finiteplay.core.ui.layout.HintProgressDialog] — most hints resolve well under
     * [HINT_PROGRESS_DIALOG_DELAY], so this stays false for them.
     */
    var hintShowsProgressDialog by mutableStateOf(false)
        private set

    /** The move to pulse-highlight on the board, or null when there is nothing to show. */
    val hint: Move? get() = (hintState as? HintUiState.Guided)?.move

    /**
     * Every legal move's source, shown all at once instead of a single proven step, when
     * [FreeCellSettings.hintShowsWinningMove] is off (`docs/games/freecell/UI_SPEC.md` "Hint").
     * Computed directly from [legalMoves] — no search, so nothing can be "inconclusive" the way
     * the guided mode can be. Cleared alongside [hintState] in [dismissHint] everywhere a board
     * change makes a shown hint describe a board that no longer exists.
     */
    var legalMoveHighlights by mutableStateOf<List<Move>>(emptyList())
        private set

    private var hintJob: Job? = null

    /** Owns the certificate cache successive hints resolve against (`HintEngine`'s own doc on why
     * this matters); reset alongside every fresh deal in [afterFreshDeal] so it never carries a
     * cached line from the game before into a board it no longer describes. */
    private val hintEngine = HintEngine()

    init {
        if (historyStore != null) {
            viewModelScope.launch { historyRecords = historyStore.current() }
        }
        if (dealProgressStore != null) {
            viewModelScope.launch { dealProgress = dealProgressStore.current() + dealProgress }
        }
        if (settingsStore != null) {
            viewModelScope.launch {
                settingsStore.settings.collect { settings = it }
            }
        }
        if (catalogLoader != null) {
            viewModelScope.launch { resolveCatalogThenProceed(catalogLoader) }
        } else if (store != null) {
            viewModelScope.launch { restoreOrDealFresh() }
        }
    }

    /**
     * Loads the bundled catalog before anything else can happen: with no uncertified fallback
     * tier, a fresh deal is only ever decided once the catalog is known valid
     * (`docs/games/freecell/DEALS.md`). Success proceeds into the same restore-or-deal path
     * [store]-only wiring already uses; failure leaves [isLoading] true forever, so
     * [unrecoverableCatalogReason] takes over the screen instead of an indefinite spinner.
     */
    private suspend fun resolveCatalogThenProceed(loader: () -> FreeCellCertifiedDealCatalog.LoadResult) {
        when (val result = loader()) {
            is FreeCellCertifiedDealCatalog.LoadResult.Valid -> {
                certifiedCatalog = result.catalog
                catalogLoadFailure = null
                restoreOrDealFresh()
            }
            is FreeCellCertifiedDealCatalog.LoadResult.Invalid -> catalogLoadFailure = result.reason
        }
    }

    fun retryCatalogLoad() {
        val loader = catalogLoader ?: return
        isLoading = true
        viewModelScope.launch { resolveCatalogThenProceed(loader) }
    }

    /** Restores the active game if one exists, otherwise deals fresh — from the catalog when one loaded, the placeholder formula otherwise (tests only; see the class doc). */
    private suspend fun restoreOrDealFresh() {
        val activeGameStore = store
        if (activeGameStore == null) {
            dealFreshFromCatalogIfAny(settings.automaticMovesEnabled)
            primeHintEngineWithStoredSolution(session.state)
            isLoading = false
            syncTimer()
            return
        }
        // Settings are read before the board is decided: with no game to restore, the opening
        // deal has to use the automatic-moves setting the player configured, not the built-in
        // default the field above was initialised with.
        val configured = settingsStore?.current()?.also { settings = it } ?: settings
        when (val loaded = activeGameStore.load()) {
            is FreeCellActiveGameLoadResult.Restored -> {
                session = loaded.session
                elapsedSeconds = loaded.elapsed.inWholeSeconds.toInt()
                gameId = loaded.gameId
                initialAutomaticMovesEnabledForCurrentDeal = loaded.session.automaticMovesEnabled
            }
            is FreeCellActiveGameLoadResult.Recovered -> {
                recoveryNoticeVisible = true
                dealFreshFromCatalogIfAny(configured.automaticMovesEnabled)
            }
            is FreeCellActiveGameLoadResult.Missing -> dealFreshFromCatalogIfAny(configured.automaticMovesEnabled)
        }
        // A no-op unless this branch actually dealt fresh: primeHintEngineWithStoredSolution
        // itself checks moveCount == 0, which a `Restored` session past its first move never is.
        primeHintEngineWithStoredSolution(session.state)
        isLoading = false
        syncTimer()
    }

    fun dismissRecoveryNotice() {
        recoveryNoticeVisible = false
    }

    /**
     * Commits [move] if legal, returning whether it was. The sole commit entry point for both a
     * drag's own explicitly-chosen destination and a tap resolved by [FreeCellBoard] itself
     * (`resolveTableauTap`/`resolveFreeCellTap`, both already legal by construction) — [FreeCellBoard]
     * needs the resolved [Move] before committing either way, to compute its flight's endpoints.
     */
    fun dragMove(move: Move): Boolean {
        if (!isLegal(session.state, move)) return false
        commit(move)
        return true
    }

    private fun commit(move: Move) {
        val shownHint = hint
        session = session.commitMove(move)
        moveRecorder?.onCommitted(gameId, session, shownHint)
        moveSequence++
        dismissHint()
        afterCommit()
    }

    fun undo() {
        if (!session.canUndo) return
        val preUndoState = session.state
        session = session.undo()
        moveRecorder?.onCommitted(gameId, session, shownHint = null)
        dismissHint()
        afterCommit(autoFinish = false)
        pendingUndoAnimation = preUndoState
    }

    private fun afterCommit(autoFinish: Boolean = true) {
        if (autoFinish) runAutoFinishIfAvailable()
        recordWinIfFinished()
        syncTimer()
        scheduleSave()
    }

    /**
     * Plays out the rest of the game once no further decision is left (`RULES.md` "Automatic
     * Finish"). The search runs off the main thread — its node budget is not yet measured
     * against real endgame boards (`docs/games/freecell/EXECUTION_PLAN.md` F1's own note), and
     * Spider's own equivalent search once froze a real device for the better part of a second
     * before that mistake was found, so this never repeats it on an unmeasured one. That is also
     * why [FreeCellBoard] cannot compute this search itself the way it re-derives the ordinary
     * per-move cascade inline: by the time this function commits, the search and its result are
     * already known, and only the already-proven move sequence (`pendingAutoFinish`) is handed
     * across for the board to animate through.
     *
     * The board is re-checked after the search returns: the player can act while it runs, and a
     * finish computed for a board that has since changed is not a finish
     * (`FreeCellSession.commitAutoFinishWith`'s own contract depends on this).
     *
     * Runs only after a player's own move, never after undo — undoing back into a finishable
     * position would otherwise finish it again immediately, leaving no way to step back through
     * it (`RULES.md` "Automatic Finish"). [isAutoFinishing] holds input disabled through the
     * search and the commit; [FreeCellBoard] then holds it disabled the rest of the way by
     * calling [onSweepAnimationFinished] only once its own animation of the sweep finishes
     * playing, so a fast tap cannot land in the gap either during the search or while the board
     * is still visibly catching up to it.
     */
    private fun runAutoFinishIfAvailable() {
        if (autoFinishJob?.isActive == true) return
        val snapshot = session.state
        if (snapshot.isWon) return
        autoFinishJob = viewModelScope.launch {
            val moves = withContext(Dispatchers.Default) { findAutoFinish(snapshot) } ?: return@launch
            if (session.state != snapshot) return@launch
            isAutoFinishing = true
            var finished = snapshot
            for (move in moves) finished = applyMove(finished, move).copy(moveCount = finished.moveCount + 1)
            session = session.commitAutoFinishWith(finished)
            moveRecorder?.onCommitted(gameId, session, shownHint = null)
            pendingAutoFinish = PendingAutoFinish(snapshot, moves)
            recordWinIfFinished()
            syncTimer()
            scheduleSave()
        }
    }

    /** True once this game's result has been written, so a win is recorded once however many times the board is recomposed or the finish re-checked. */
    private fun recordWinIfFinished() {
        if (!session.state.isWon || currentGameRecorded) return
        currentGameRecorded = true
        record(FreeCellOutcome.WIN)
    }

    /**
     * A played game abandoned for a new deal counts as a loss — otherwise a player could avoid
     * every loss by dealing again, and the win rate would describe nothing. A replay does not: it
     * is the same deal retried, not a game given up on (`docs/games/freecell/DESIGN.md`
     * "Scoring and statistics").
     */
    private fun recordLossIfAbandoned() {
        if (currentGameRecorded || !session.hasPlayerActed || session.state.isWon) return
        currentGameRecorded = true
        record(FreeCellOutcome.LOSS)
    }

    private fun record(outcome: FreeCellOutcome) {
        val store = historyStore ?: return
        val record = FreeCellHistoryRecord(
            gameId = gameId,
            resultId = "$gameId-${outcome.name}",
            outcome = outcome,
            elapsedMillis = elapsedSeconds.seconds.inWholeMilliseconds,
            moveCount = session.state.moveCount,
            timestampMillis = System.currentTimeMillis(),
            hintsUsed = hintsUsedThisGame,
            solutionMoveCount = solutionMoveCount,
        )
        viewModelScope.launch {
            store.upsert(record)
            historyRecords = store.current()
        }
    }

    private fun needsConfirmation(): Boolean = session.hasPlayerActed && session.state.status != GameStatus.WON

    fun requestNewGame() {
        if (needsConfirmation()) pendingAction = DiscardingAction.NEW_GAME else newGame()
    }

    fun requestReplay() {
        if (needsConfirmation()) pendingAction = DiscardingAction.REPLAY else replay()
    }

    fun confirmPendingAction() {
        val action = pendingAction ?: return
        pendingAction = null
        val deal = pendingDealNumber
        pendingDealNumber = null
        when (action) {
            DiscardingAction.NEW_GAME -> if (deal != null) selectDeal(deal) else newGame()
            DiscardingAction.REPLAY -> replay()
        }
    }

    fun dismissPendingAction() {
        pendingAction = null
        pendingDealNumber = null
    }

    /** Re-deals the game in progress from its own seed, at its own original automatic-moves setting. */
    fun replay() {
        dealFresh(initialAutomaticMovesEnabledForCurrentDeal, seed = session.state.seed)
        afterFreshDeal()
    }

    /** The next deal from the certified catalog's own non-repeating traversal, or a fresh random
     * seed when no catalog is wired at all (tests only; see the class doc). */
    fun newGame() {
        recordLossIfAbandoned()
        val catalog = certifiedCatalog
        if (catalog == null) {
            dealFresh(settings.automaticMovesEnabled)
            afterFreshDeal()
            return
        }
        viewModelScope.launch {
            dealFreshFromCatalogIfAny(settings.automaticMovesEnabled)
            afterFreshDeal()
        }
    }

    private fun dealFresh(automaticMovesEnabled: Boolean, seed: Long = Random.nextLong()) {
        session = newDeal(seed, automaticMovesEnabled)
        initialAutomaticMovesEnabledForCurrentDeal = automaticMovesEnabled
    }

    /**
     * Hands the engine the solution shipped with this seed, so a player who takes each hint and
     * plays it walks a known winning line with no search at all. Deviating from it costs nothing:
     * the board simply stops matching the line and the search resumes.
     *
     * Only meaningful from the raw deal — a restored mid-game session is already past the line's
     * start, and the engine's own state matching handles rejoining it — and a no-op when no
     * catalog is wired up (tests) or the seed is uncatalogued. Called after [hintEngine] has
     * already been reset (or is still in its freshly-constructed state on cold start), never
     * before — priming and then resetting would just throw the primed line away.
     */
    private fun primeHintEngineWithStoredSolution(freshState: FreeCellState) {
        lookUpSolutionLength(freshState)
        val catalog = solutionCatalog ?: return
        if (freshState.moveCount > 0) return
        val solution = catalog.solutionFor(freshState) ?: return
        hintEngine.primeWithKnownSolution(freshState, solution)
    }

    /**
     * Hints taken in the current game, and the length of the certified line shipped for its seed
     * (0 where none is). Both are recorded on the history record when the game ends, rather than
     * derived later, because the catalog can be regenerated under a player's feet.
     *
     * Only a hint that points at a move on a winning line counts; listing every legal move
     * ([requestHint] with the winning-move setting off) leads nowhere and is not charged.
     */
    var hintsUsedThisGame by mutableIntStateOf(0)
        private set

    var solutionMoveCount by mutableIntStateOf(0)
        private set

    /**
     * Looks the shipped line's length up off the main thread — the first lookup parses the whole
     * solution index — against the raw deal rather than the board in front of the player, because
     * a stored line is written from the deal and a restored session is already past its start.
     */
    private fun lookUpSolutionLength(state: FreeCellState) {
        solutionMoveCount = 0
        val catalog = solutionCatalog ?: return
        val seed = state.seed
        val versions = state.versions
        viewModelScope.launch {
            val moves = withContext(Dispatchers.Default) { catalog.solutionFor(dealGame(seed, versions))?.size ?: 0 }
            // A New Game may have replaced the session while this ran.
            if (session.state.seed == seed) solutionMoveCount = moves
        }
    }

    /** [dealFresh] from the certified catalog's own traversal when [certifiedCatalog] is set,
     * otherwise a fresh random seed — the fallback exists only for a test that supplies neither a
     * catalog nor a loader; production always has one by the time this can be reached. */
    private suspend fun dealFreshFromCatalogIfAny(automaticMovesEnabled: Boolean) {
        val catalog = certifiedCatalog
        dealFresh(automaticMovesEnabled, seed = catalog?.let { nextCatalogSeed(it) } ?: Random.nextLong())
    }

    /**
     * The next seed from [catalog]'s own non-repeating, sequential traversal
     * (`org.finiteplay.solitaire.catalog.catalog.DealTraversal`), advancing and persisting the
     * position. With no [traversalStore] — a test that wires a catalog directly — always the
     * catalog's first seed, which is where a fresh sequential traversal starts anyway.
     */
    private suspend fun nextCatalogSeed(catalog: FreeCellCertifiedDealCatalog): Long {
        val traversal = traversalStore ?: return catalog.seeds[0]
        val recordCount = catalog.seeds.size
        val state = traversal.loadOrStartNew(catalog.catalogVersion, recordCount)
        val seed = catalog.seeds[DealTraversal.indexAt(state, recordCount)]
        traversal.save(DealTraversal.advance(state, recordCount), recordCount)
        return seed
    }

    private fun afterFreshDeal() {
        elapsedSeconds = 0
        gameId = newGameId()
        currentGameRecorded = false
        hintsUsedThisGame = 0
        dismissHint()
        hintEngine.reset()
        primeHintEngineWithStoredSolution(session.state)
        moveRecorder?.onCommitted(gameId, session, shownHint = null)
        syncTimer()
        scheduleSave()
    }

    /**
     * Runs the on-device solver from the current board and shows what it finds
     * (`docs/games/freecell/DESIGN.md` "Hint"). A no-op while already loading or showing a
     * result — repeating the request has nothing new to show, since the search returns the single
     * next step of a proven line rather than a ranked list to page through. Runs off the main
     * thread; if the board changes before the search returns, the result is discarded as stale
     * rather than shown against a board it no longer describes.
     *
     * Delegates to [hintEngine], whose own certificate cache is what makes successive hints lead
     * somewhere instead of each one re-searching from scratch: once a winning line is found, later
     * requests read the next step off that same line for as long as the board keeps matching it,
     * only falling back to a fresh search once the player deviates from it. That fresh search is
     * still passed the last committed player move — whether played by hand or by following a
     * previous hint — so it never ranks undoing it as ordinary progress (`HintEngine.hint`'s own
     * doc on `lastMove`). Null right after an undo, or before the first move of a game — the log's
     * own last entry decides which.
     */
    fun requestHint() {
        if (!settings.hintShowsWinningMove) {
            // No search, so no loading state and nothing that can be stale: toggles instantly.
            legalMoveHighlights = if (legalMoveHighlights.isEmpty()) legalMoves(session.state) else emptyList()
            return
        }
        if (hintState != HintUiState.Hidden) return
        hintState = HintUiState.Loading
        val requestedState = session.state
        val lastMove = (session.log.lastOrNull() as? FreeCellLogEntry.PlayerMove)?.move
        hintJob = viewModelScope.launch {
            launch {
                delay(HINT_PROGRESS_DIALOG_DELAY)
                if (hintState == HintUiState.Loading) hintShowsProgressDialog = true
            }
            val outcome = withContext(Dispatchers.Default) { hintEngine.hint(requestedState, hintSolverLimits(settings.hintTimeout), lastMove) }
            if (session.state != requestedState || hintState != HintUiState.Loading) return@launch
            hintShowsProgressDialog = false
            moveRecorder?.onHint(gameId, session, outcome)
            val shown = when (outcome) {
                is HintOutcome.Guidance -> HintUiState.Guided(outcome.move)
                is HintOutcome.NoSolution -> HintUiState.NoSolution
                is HintOutcome.Inconclusive -> HintUiState.Inconclusive
            }
            hintState = shown
            // Only guidance counts: a search that proved nothing gave the player nothing to take.
            if (shown is HintUiState.Guided) hintsUsedThisGame++
            if (shown is HintUiState.NoSolution || shown is HintUiState.Inconclusive) {
                launch {
                    delay(HINT_NOTICE_AUTO_DISMISS)
                    // Only if nothing else (Dismiss, a move, undo, a new game) already moved on.
                    if (hintState == shown) hintState = HintUiState.Hidden
                }
            }
        }
    }

    /** Dismisses the current hint (loading, guidance, or a notice) without waiting for it. */
    fun dismissHint() {
        hintJob?.cancel()
        hintJob = null
        hintState = HintUiState.Hidden
        hintShowsProgressDialog = false
        legalMoveHighlights = emptyList()
    }

    /**
     * Replaces the board with [state] as a fresh, undo-empty session, bypassing the normal
     * random deal — for a debug-only fixture button (`src/debug`) and for tests that need a
     * specific board neither a fresh deal nor a short sequence of real gestures reaches
     * reliably, mirroring Klondike's and Spider's own `loadFixtureForDebugging`.
     */
    fun loadFixtureForDebugging(state: FreeCellState) {
        session = FreeCellSession.of(state = state, automaticMovesEnabled = settings.automaticMovesEnabled)
        initialAutomaticMovesEnabledForCurrentDeal = settings.automaticMovesEnabled
        afterFreshDeal()
    }

    /**
     * Test-only: shows [move] as the current guided hint without running the search, for a test
     * that needs a specific hint/board disagreement neither a real search nor a short sequence of
     * real gestures reaches reliably (`BoardHintTapOverrideTest`) — the same seam
     * [loadFixtureForDebugging] gives a specific board.
     */
    fun showHintForDebugging(move: Move) {
        hintState = HintUiState.Guided(move)
    }

    fun setAutomaticMovesEnabled(value: Boolean) {
        session = session.withAutomaticMoves(value)
        moveRecorder?.onCommitted(gameId, session, shownHint = null)
        updateSettings({ it.copy(automaticMovesEnabled = value) }) { it.setAutomaticMovesEnabled(value) }
    }
    fun setAnimationsEnabled(value: Boolean) = updateSettings({ it.copy(animationsEnabled = value) }) { it.setAnimationsEnabled(value) }
    /** Applies immediately and persists; does not itself change what a hint already on screen shows. */
    fun setHintShowsWinningMove(value: Boolean) = updateSettings({ it.copy(hintShowsWinningMove = value) }) { it.setHintShowsWinningMove(value) }
    fun setHintTimeout(value: HintTimeout) = updateSettings({ it.copy(hintTimeout = value) }) { it.setHintTimeout(value) }

    /** Applies immediately and persists; restarts the current window so a new choice takes effect right away. */
    fun setRestReminderInterval(value: RestReminderInterval) {
        updateSettings({ it.copy(restReminderInterval = value) }) { it.setRestReminderInterval(value) }
        restReminderWindow = RestReminderWindow.start(System.currentTimeMillis())
        refreshRestReminderElapsed()
        syncRestReminderTicker()
    }
    fun setHandedness(value: Handedness) = updateSettings({ it.copy(handedness = value) }) { it.setHandedness(value) }
    fun setSoundEnabled(value: Boolean) = updateSettings({ it.copy(soundEnabled = value) }) { it.setSoundEnabled(value) }
    fun setLanguageTag(value: String) = updateSettings({ it.copy(languageTag = value) }) { it.setLanguageTag(value) }
    fun setThemeMode(value: ThemeMode) = updateSettings({ it.copy(themeMode = value) }) { it.setThemeMode(value) }

    /**
     * Applies a setting. With no store — the tests that do not exercise persistence — the change
     * lands in memory so the interface still behaves; with one, the store is the source of truth
     * and the collector in [init] brings the change back.
     */
    private fun updateSettings(
        inMemory: (FreeCellSettings) -> FreeCellSettings,
        persist: suspend (FreeCellSettingsStore) -> Unit,
    ) {
        val store = settingsStore
        if (store == null) {
            settings = inMemory(settings)
            return
        }
        viewModelScope.launch { persist(store) }
    }

    fun setForeground(foreground: Boolean) {
        isForeground = foreground
        syncTimer()
        syncRestReminderForeground(foreground)
        // Seconds tick between moves, and only a move would otherwise write them: without this,
        // a game left open for ten minutes and then killed comes back at the elapsed time of its
        // last move (`docs/PLATFORM.md` "Persistence" — the count is persisted, the timer is not).
        if (!foreground) scheduleSave()
    }

    fun setModalOpen(open: Boolean) {
        isModalOpen = open
        syncTimer()
    }

    private fun syncTimer() {
        val shouldRun = shouldRunTimer(
            hasPlayerActed = session.hasPlayerActed,
            isWon = session.state.status == GameStatus.WON,
            isAppForeground = isForeground,
            isModalOpen = isModalOpen,
        )
        if (shouldRun) {
            if (tickerJob?.isActive == true) return
            tickerJob = viewModelScope.launch {
                while (isActive) {
                    delay(1_000)
                    elapsedSeconds++
                }
            }
        } else {
            tickerJob?.cancel()
            tickerJob = null
        }
    }

    /** Accepts the reminder's offer: starts a countdown for half the configured interval, during which no usage is tracked at all. */
    fun startRestBreak() {
        showRestReminderDialog = false
        val minutes = settings.restReminderInterval.minutes ?: return
        restBreakRemainingSeconds = (minutes * 60) / 2
        syncRestReminderTicker()
        restBreakJob = viewModelScope.launch {
            while ((restBreakRemainingSeconds ?: 0) > 0) {
                delay(1_000)
                restBreakRemainingSeconds = (restBreakRemainingSeconds ?: 1) - 1
            }
            endRestBreak()
        }
    }

    /** Declines the reminder's offer; the next one is a full interval away, since [checkRestReminder] already rolled the window over. */
    fun dismissRestReminder() {
        showRestReminderDialog = false
    }

    /** Ends an accepted break early. */
    fun cancelRestBreak() {
        restBreakJob?.cancel()
        endRestBreak()
    }

    private fun endRestBreak() {
        restBreakJob = null
        restBreakRemainingSeconds = null
        // The break itself is excluded from usage entirely (DESIGN.md "Rest Reminders"), not just
        // paused: starting a fresh window now, rather than resuming the one open before the break,
        // is what keeps the break's own duration out of both the numerator and the denominator.
        restReminderWindow = RestReminderWindow.start(System.currentTimeMillis())
        refreshRestReminderElapsed()
        syncRestReminderTicker()
    }

    /** Opens or closes the current foreground segment of [restReminderWindow]; call from [setForeground]. */
    private fun syncRestReminderForeground(foreground: Boolean) {
        val now = System.currentTimeMillis()
        restReminderWindow = if (foreground) {
            (restReminderWindow ?: RestReminderWindow.start(now)).foregroundEntered(now)
        } else {
            restReminderWindow?.foregroundExited(now)
        }
        refreshRestReminderElapsed()
        syncRestReminderTicker()
    }

    /**
     * Runs once a second while there is anything to track: foreground and no break already
     * showing — not gated on the interval being set, since [restReminderElapsedSeconds] is a
     * plain running total Settings can show regardless of whether reminders are turned on.
     */
    private fun syncRestReminderTicker() {
        val shouldRun = isForeground && restBreakRemainingSeconds == null
        if (shouldRun) {
            if (restReminderTickerJob?.isActive == true) return
            restReminderTickerJob = viewModelScope.launch {
                while (isActive) {
                    delay(REST_REMINDER_CHECK_INTERVAL)
                    refreshRestReminderElapsed()
                    checkRestReminder()
                }
            }
        } else {
            restReminderTickerJob?.cancel()
            restReminderTickerJob = null
        }
    }

    private fun refreshRestReminderElapsed() {
        val window = restReminderWindow
        restReminderElapsedSeconds = if (window == null) 0 else (window.foregroundMsAsOf(System.currentTimeMillis()) / 1_000L).toInt()
    }

    /**
     * Shows the reminder the moment accumulated foreground time reaches the configured interval
     * (`DESIGN.md` "Rest Reminders") and starts a fresh window right away — matching
     * [RestReminderWindow]'s own background-gap reset, this is the only place the count itself
     * returns to zero while actively playing. A no-op while the feature is off
     * ([RestReminderInterval.NEVER]): the window above still tracks for the display, but nothing
     * here ever reads it.
     */
    private fun checkRestReminder() {
        val minutes = settings.restReminderInterval.minutes ?: return
        val window = restReminderWindow ?: return
        val now = System.currentTimeMillis()
        if (!isRestReminderDue(window, now, minutes * 60_000L)) return
        showRestReminderDialog = true
        restReminderWindow = RestReminderWindow.start(now)
        refreshRestReminderElapsed()
    }

    /**
     * Saves asynchronously and never blocks the caller — the board is already showing the move
     * by the time this runs. A no-op with no store, and while the initial restore is still in
     * flight: a save written before that check completes could race it and overwrite a real save
     * with the throwaway deal the constructor started on.
     */
    private fun scheduleSave() {
        syncDealProgress()
        val store = store ?: return
        if (isLoading) return
        val snapshot = session
        val initialAutomaticMoves = initialAutomaticMovesEnabledForCurrentDeal
        val elapsed = elapsedSeconds
        val id = gameId
        viewModelScope.launch {
            store.save(snapshot, initialAutomaticMoves, elapsed.seconds, id)
        }
    }

    private companion object {
        fun newDeal(seed: Long, automaticMovesEnabled: Boolean): FreeCellSession =
            FreeCellSession.start(seed = seed, versions = FREECELL_VERSIONS, automaticMovesEnabled = automaticMovesEnabled)

        /** Identifies one dealt game across saves, so a restore can tell it apart from a redeal. */
        fun newGameId(): String = java.util.UUID.randomUUID().toString()
    }
}
