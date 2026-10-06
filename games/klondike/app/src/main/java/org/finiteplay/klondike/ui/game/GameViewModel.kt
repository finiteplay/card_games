package org.finiteplay.klondike.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.finiteplay.klondike.deal.DealSeedSource
import org.finiteplay.klondike.deal.CertifiedDealCatalog
import org.finiteplay.klondike.deal.DifficultyDealSeedSource
import org.finiteplay.klondike.deal.DifficultyTier
import org.finiteplay.klondike.deal.INTERIM_SEED_GRADES
import org.finiteplay.klondike.deal.RandomDealSeedSource
import org.finiteplay.klondike.deal.RandomDifficultyDealSeedSource
import org.finiteplay.klondike.deal.SolutionCatalog
import org.finiteplay.klondike.deal.seedsFor
import org.finiteplay.klondike.deal.toTier
import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.canAutoFinish
import org.finiteplay.klondike.rules.isLegal
import org.finiteplay.klondike.rules.legalMoves
import org.finiteplay.klondike.session.GameSession
import org.finiteplay.klondike.session.commitAutoFinish
import org.finiteplay.klondike.session.commitMove
import org.finiteplay.klondike.session.currentHint
import org.finiteplay.klondike.session.undo
import org.finiteplay.klondike.session.withAutomaticMoves
import org.finiteplay.klondike.solver.HintEngine
import org.finiteplay.klondike.solver.HintOutcome
import org.finiteplay.klondike.solver.search.SolverLimits
import org.finiteplay.klondike.storage.ActiveGameLoadResult
import org.finiteplay.klondike.storage.ActiveGameStore
import org.finiteplay.klondike.storage.CatalogTraversalStore
import org.finiteplay.klondike.storage.DifficultyPreference
import org.finiteplay.klondike.storage.GameStatistics
import org.finiteplay.klondike.storage.Handedness
import org.finiteplay.core.ui.layout.HintTimeout
import org.finiteplay.core.ui.layout.RestReminderInterval
import org.finiteplay.core.ui.theme.ThemeMode
import org.finiteplay.core.session.RestReminderWindow
import org.finiteplay.core.session.afterIntervalChange
import org.finiteplay.core.session.foregroundEntered
import org.finiteplay.core.session.foregroundExited
import org.finiteplay.core.session.foregroundMsAsOf
import org.finiteplay.core.session.isRestReminderDue
import org.finiteplay.klondike.storage.Settings
import org.finiteplay.klondike.storage.HistoryRecord
import org.finiteplay.klondike.storage.ArchivedGame
import org.finiteplay.klondike.storage.GameArchiveStore
import org.finiteplay.klondike.storage.HistoryStore
import org.finiteplay.klondike.storage.Outcome
import org.finiteplay.klondike.storage.SettingsStore
import org.finiteplay.klondike.storage.StatisticsPeriod
import org.finiteplay.klondike.storage.TraversalLoadResult
import org.finiteplay.klondike.storage.computeStatistics
import org.finiteplay.core.session.solutionRatioPercent
import org.finiteplay.core.storage.DealProgressStore
import org.finiteplay.core.storage.DealProgress
import org.finiteplay.core.storage.DealStatus
import org.finiteplay.core.storage.advancedBy
import org.finiteplay.klondike.storage.filterByDrawMode
import org.finiteplay.klondike.storage.filterByPeriod
import org.finiteplay.klondike.storage.shouldRunTimer
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/** D1a/S1 own real deal versioning; A2a fixes every version at 1 (`EXECUTION_PLAN.md` A1). */
private val PLACEHOLDER_VERSIONS = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1)

/** Never drawn from [DealSeedSource]; see the comment on [GameViewModel.seed]. */
private const val PLACEHOLDER_SEED = 0L

/**
 * Legacy fixture catalog version. Production uses the validated binary catalog's own version;
 * this value exists only when a unit fixture intentionally omits that catalog.
 *
 * 2: the levels ship in a shuffled order rather than most-forgiving-first, so every deal
 * number now names a different deal. The lists are the same length, which is why the record
 * count cannot notice on its own and this has to move.
 */
private const val INTERIM_SEED_CATALOG_VERSION = 2

/**
 * Interactive on-device search budget (`docs/games/klondike/DESIGN.md` "On-Device Hint Search") — far
 * below the offline catalog pipeline's [SolverLimits] defaults, since this runs on a
 * player's device while they wait for a response rather than in an unattended batch.
 * [HintEngine]'s dead-state and certificate caches mean only the first request against
 * a given board typically spends anywhere near this budget.
 *
 * `maxNodes` is A*'s existing 340,000-node allowance. [HintEngine] gives the faster
 * preceding DFS an additional fixed 300,000 nodes rather than debiting A*. Every deal
 * in the interim solvable catalog resolves to Guidance
 * from its raw fresh board (the worst case; `HintSearchBudgetTest` pins this). Peak
 * memory is governed not by this total but by the larger individual search
 * (attempts run one at a time and release their frontier in between),
 * which keeps the A* frontier comfortably under the `120 MB PSS` play-time budget
 * (`docs/games/klondike/DESIGN.md` "Performance") for a search that runs alongside an already-live
 * game. Wall-clock is the user-facing ceiling, and now the player's own choice
 * (`persistedHintTimeout`, `core:ui`'s `HintTimeout` — "Hint timeout" in Settings): the full
 * total typically costs 3–6 s of search on a phone-class core, comfortably under the
 * shortest option a player can pick.
 */
private fun hintSolverLimits(timeout: HintTimeout) = SolverLimits(maxNodes = 340_000L, maxDurationMs = timeout.seconds * 1_000L)

/**
 * How long the Hint action's No solution / Inconclusive notice (`docs/games/klondike/UI_SPEC.md`
 * "Hint": "a brief dismissible notice") stays up before dismissing itself, for a
 * player who reads it and moves on rather than tapping Dismiss. Not applied to
 * [HintUiState.Guided]: that outcome has no notice row at all (`Board` highlights
 * the card instead) and stays up until the player acts on or requests past it.
 */
internal val HINT_NOTICE_AUTO_DISMISS = 4.seconds

/**
 * How long a hint search runs before [GameViewModel.hintShowsProgressDialog] switches on
 * (`docs/games/klondike/UI_SPEC.md` "Hint"). Below this, a search resolves silently — flashing a
 * modal for an instant would read as broken rather than helpful.
 */
internal val HINT_PROGRESS_DIALOG_DELAY = 1.seconds

/**
 * How often [GameViewModel.refreshRestReminderElapsed] updates and [GameViewModel.checkRestReminder]
 * re-evaluates the current window. A full second, matching the elapsed-play ticker: Settings'
 * read-only display is the reason this needs to be this fine-grained at all — the reminder itself
 * would be just as correct checked far less often, since the configured interval is tens of
 * minutes at the shortest.
 */
internal val REST_REMINDER_CHECK_INTERVAL = 1.seconds

/** New Game, Replay and picking a deal ask for confirmation only for an unfinished played game (`DESIGN.md` "Game Lifecycle"). */
enum class PendingConfirmation { NEW_GAME, REPLAY, SELECT_DEAL, SET_DRAW_MODE, SET_DIFFICULTY }

/**
 * What the Hint action currently shows, per `docs/games/klondike/DESIGN.md` "On-Device Hint Search":
 * a move guaranteed to stay on a winning line, a proof that no such line exists from
 * the current board, or an inconclusive result when the search budget ran out before
 * either could be established.
 */
sealed class HintUiState {
    data object Hidden : HintUiState()
    data object Loading : HintUiState()
    data class Guided(val move: Move) : HintUiState()
    data object NoSolution : HintUiState()

    /**
     * The search ran out of budget without proving anything. [suggestion], when present,
     * is what the Expert strategy ruleset would play here — highlighted alongside the
     * notice so the player has something to act on, but deliberately *not* presented as
     * guidance, because nothing about this board was proven either way.
     */
    data class Inconclusive(val suggestion: Move? = null) : HintUiState()
}

/**
 * Owns the committed [GameSession] and the elapsed-time timer. This is the only state
 * that survives configuration change; transient gesture/animation/selection state lives
 * in the composables themselves so it is discarded (not restored) on rotation, per
 * `UI_SPEC.md` ("Cancel an active drag safely on rotation").
 *
 * [isLoading] is true from construction until the persisted active game (S1) has been
 * checked; the game screen shows the plain Loading surface until then rather than
 * flashing a fresh deal that restoration is about to replace (`UI_SPEC.md` "Screens and
 * States"). A corrupt or unsupported save surfaces as [recoveryNoticeVisible], per the
 * Recovery notice in the same spec — the game stays playable on a fresh deal.
 */
class GameViewModel(
    private val activeGameStore: ActiveGameStore,
    private val settingsStore: SettingsStore,
    private val historyStore: HistoryStore,
    /**
     * Archives the last 100 finished games, each with its full move log, for offline
     * analysis. Null in tests that do not exercise it, which then behave as before the
     * archive existed.
     */
    private val gameArchiveStore: GameArchiveStore? = null,
    /**
     * Overrides difficulty-based seed selection entirely — for tests that need a
     * specific deal. Null in real use, where the seed comes from the selected
     * difficulty's list instead (`docs/games/klondike/DEALS.md`).
     */
    private val injectedSeedSource: DealSeedSource? = null,
    /**
     * Persists each difficulty's position so a killed-and-restarted app resumes every
     * level's sequence instead of redealing hand 1. Null (the default used by every
     * existing test that injects its own seed source) skips loading and saving entirely.
     */
    private val catalogTraversalStore: CatalogTraversalStore? = null,
    /** Where [requestHint] runs the search; overridable so tests can make it resolve inline. */
    private val hintDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Shipped per-seed solutions used to answer hints without searching; null in tests, which then behave as before solutions shipped. */
    private val solutionCatalog: SolutionCatalog? = null,
    /** Validated D1b draw-one catalog in production; null only in legacy unit fixtures. */
    private var certifiedCatalog: CertifiedDealCatalog? = null,
    /** Reason the bundled D1b catalog cannot be used; surfaces the non-playable state. */
    private var catalogLoadFailure: String? = null,
    /** Retries reading the bundled catalog after an unrecoverable asset error. */
    private val catalogLoader: (() -> CertifiedDealCatalog.LoadResult)? = null,
    /** Which deals have been played and won; null in tests that do not exercise it (progress is then kept in memory only). */
    private val dealProgressStore: DealProgressStore? = null,
) : ViewModel() {

    // Not drawn from seedSource: drawing here, unconditionally, would advance the
    // interim seed sequence's cursor even when restoreOrStartFresh() is about to
    // discard this placeholder in favor of a restored save — silently burning a
    // hand out of the rotation on every restart where a save exists (the common
    // case after first launch). The real seed for a fresh deal is drawn lazily by
    // startFreshDeal, only once it is known a fresh deal is actually needed. This
    // placeholder session is never shown: isLoading gates the screen until
    // restoreOrStartFresh() replaces or confirms it.
    private var seed: Long = PLACEHOLDER_SEED
    private var initialAutomaticMovesEnabledForCurrentDeal: Boolean = true

    /**
     * Stable per-deal identity (`EXECUTION_PLAN.md` A2b): a fresh deal or replay gets a
     * new ID even when it reuses the same seed, since replaying one seed repeatedly is
     * meant to be recordable as distinct games (`DESIGN.md` "Statistics"); restoration
     * keeps whatever ID the saved game already had. History upserts are keyed off this,
     * so re-recording the same game's result (e.g. after rotation) never duplicates it.
     * Public and observable so [Board] can key its own remembered state on it: a new
     * value means the session was replaced wholesale (new game, replay, restore, debug
     * fixture) rather than advanced by a move Board itself is animating through.
     */
    var gameId: String by mutableStateOf(UUID.randomUUID().toString())
        private set

    var session by mutableStateOf(startSession(seed, initialAutomaticMovesEnabledForCurrentDeal))
        private set

    var elapsedSeconds by mutableIntStateOf(0)
        private set

    var isLoading by mutableStateOf(true)
        private set

    var recoveryNoticeVisible by mutableStateOf(false)
        private set

    val unrecoverableCatalogReason: String? get() = catalogLoadFailure

    /** What the Hint action currently shows; see [HintUiState]. */
    var hintState by mutableStateOf<HintUiState>(HintUiState.Hidden)
        private set

    /**
     * True once a running [HintUiState.Loading] search has taken long enough to show
     * [org.finiteplay.core.ui.layout.HintProgressDialog] — most hints resolve well under
     * [HINT_PROGRESS_DIALOG_DELAY], so this stays false for them and the search never
     * visibly interrupts play at all.
     */
    var hintShowsProgressDialog by mutableStateOf(false)
        private set

    /** The in-flight hint search, if any — cancelled by [dismissHint] so a stale result never lands. */
    private var hintJob: Job? = null

    /**
     * Every legal move's source, shown all at once instead of a single proven step, when
     * [persistedHintShowsWinningMove] is off (`docs/games/klondike/UI_SPEC.md` "Hint"). Computed
     * directly from [legalMoves] — no search, so nothing can be "inconclusive" the way the guided
     * mode can be. Cleared alongside [hintState] everywhere a board change makes a shown hint
     * describe a board that no longer exists.
     */
    var legalMoveHighlights by mutableStateOf<List<Move>>(emptyList())
        private set

    /**
     * Per-game solver session backing [HintUiState.Guided]/[HintUiState.NoSolution]
     * (`docs/games/klondike/DESIGN.md` "On-Device Hint Search"). Its dead-state and certificate
     * caches persist across hint requests within one game — [reset] on New Game and
     * Replay only, since undo does not change which states are or are not solvable.
     */
    private val hintEngine = HintEngine()

    var pendingConfirmation by mutableStateOf<PendingConfirmation?>(null)
        private set

    /** The deal waiting on the player's confirmation when [pendingConfirmation] is [PendingConfirmation.SELECT_DEAL]. */
    private var pendingDealNumber: Int? = null

    /**
     * Which draw-one deals have been played and won, by seed. A seed is exact — no two levels
     * share one — so no level needs recording beside it. Draw-three's random shuffles are not a
     * list a player can choose from and are not tracked.
     */
    var dealProgress by mutableStateOf<Map<Long, DealProgress>>(emptyMap())
        private set

    /**
     * Raises the active deal's progress: played once the player has acted on it, won once it is, and
     * keeps the moves with it — the fewest of any win, or those of the game last played.
     */
    private fun syncDealProgress() {
        val state = session.state
        if (state.drawMode != DrawMode.ONE) return
        val reached = when {
            state.isWon -> DealStatus.WON
            session.hasPlayerActed -> DealStatus.PLAYED
            else -> return
        }
        val old = dealProgress[state.seed]
        val updated = old.advancedBy(reached, state.moveCount)
        if (updated == old) return
        dealProgress = dealProgress + (state.seed to updated)
        val store = dealProgressStore ?: return
        viewModelScope.launch { store.mark(state.seed, reached, state.moveCount) }
    }

    /**
     * The list the deal picker shows: the active deal's own level, which is the list its number
     * is a position in. Null where there is none — a draw-three game — and the status row then
     * offers no picker.
     */
    val dealPickerCount: Int? get() = dealDifficulty?.let { activeSeedsFor(it).size }

    /** [dealProgress] for the picker's level, keyed by the deal numbers the picker lists. */
    fun dealPickerProgress(): Map<Int, DealProgress> {
        val tier = dealDifficulty ?: return emptyMap()
        return buildMap {
            activeSeedsFor(tier).forEachIndexed { index, deal -> dealProgress[deal]?.let { put(index + 1, it) } }
        }
    }

    /**
     * Starts the [number]th deal of the active level, confirming first when the game on screen
     * has been played and is unfinished — picking a deal replaces it exactly as New Game does,
     * and records the same loss. Picking the deal already on screen does nothing.
     */
    fun requestSelectDeal(number: Int) {
        val tier = dealDifficulty ?: return
        val seeds = activeSeedsFor(tier)
        if (number !in 1..seeds.size || seeds[number - 1] == session.state.seed) return
        if (needsConfirmation()) {
            pendingDealNumber = number
            pendingConfirmation = PendingConfirmation.SELECT_DEAL
        } else {
            performSelectDeal(number)
        }
    }

    /**
     * Deals a chosen deal. One already won counts for nothing, as a Replay does: a deal the player
     * has solved is known solvable, so replaying it by choice would inflate the win rate the
     * certified catalog is meant to keep honest. One merely played still counts.
     */
    private fun performSelectDeal(number: Int) {
        val tier = dealDifficulty ?: return
        val chosen = activeSeedsFor(tier).getOrNull(number - 1) ?: return
        recordAbandonmentIfNeeded()
        seed = chosen
        gameId = UUID.randomUUID().toString()
        currentGameCountsForStatistics = dealProgress[chosen]?.status != DealStatus.WON
        resetTo(startSession(chosen, initialAutomaticMovesEnabledForCurrentDeal, DrawMode.ONE))
    }

    /** The persisted enable-animations setting (`EXECUTION_PLAN.md` S2); A3 adds the toggle UI. */
    var persistedAnimationsEnabled by mutableStateOf(true)
        private set

    /** True shows Hint's proven winning move (the original behavior); false highlights every
     * legal move instead, with no search at all. */
    var persistedHintShowsWinningMove by mutableStateOf(true)
        private set

    /** How long [requestHint]'s search is allowed to run before giving up (`core:ui`'s [HintTimeout]). */
    var persistedHintTimeout by mutableStateOf(HintTimeout.DEFAULT)
        private set

    /** How often [checkRestReminder] offers a break after continuous play (`core:ui`'s [RestReminderInterval]). */
    var persistedRestReminderInterval by mutableStateOf(RestReminderInterval.DEFAULT)
        private set

    /** True while [RestReminderDialog][org.finiteplay.core.ui.layout.RestReminderDialog] is offered. */
    var showRestReminderDialog by mutableStateOf(false)
        private set

    /** Seconds left in an accepted break, or null when no break is running. */
    var restBreakRemainingSeconds by mutableStateOf<Int?>(null)
        private set

    /** The current window's own running total, for Settings' read-only display — zero right after a reset. */
    var restReminderElapsedSeconds by mutableIntStateOf(0)
        private set

    /** The persisted handedness setting (`docs/games/klondike/UI_SPEC.md` "Left-Handed Layout"). */
    var persistedHandedness by mutableStateOf(Handedness.RIGHT)
        private set

    /** The persisted sound setting (`docs/games/klondike/DESIGN.md` "Sound"). */
    var persistedSoundEnabled by mutableStateOf(true)
        private set

    /**
     * The persisted draw-mode setting (`docs/games/klondike/DESIGN.md` "Draw-Three Mode"). Unlike
     * the settings above, this never applies to the game in progress — draw mode is
     * fixed once a game is dealt, so this only decides what the *next* New Game
     * deals; the current game's actual mode is always [GameSession.state]'s own
     * [org.finiteplay.klondike.board.GameState.drawMode].
     */
    var persistedDrawMode by mutableStateOf(DrawMode.ONE)
        private set

    /** The persisted difficulty selection, applied to the *next* new game (`docs/games/klondike/UI_SPEC.md`). */
    var persistedDifficulty by mutableStateOf(Settings.DEFAULT.difficulty)
        private set

    /** The persisted language override, or [SYSTEM_LANGUAGE] to follow the device (`storage/AppLocale.kt`). */
    var persistedLanguageTag by mutableStateOf(Settings.DEFAULT.languageTag)
        private set

    /** How the board's theme is chosen (`docs/PLATFORM.md` "Themes"). */
    var persistedThemeMode by mutableStateOf(Settings.DEFAULT.themeMode)
        private set

    /** One long-lived cursor per difficulty, so each level resumes independently of the others. */
    private val difficultySources = mutableMapOf<DifficultyTier, DifficultyDealSeedSource>()

    /** Refreshed by [refreshStatistics]/[setStatisticsPeriod] (A3; `docs/games/klondike/TODO.md` "Statistics Screen Visual Redesign"). */
    var statistics by mutableStateOf(GameStatistics.EMPTY)
        private set

    /**
     * Which trailing window [statistics] currently reflects; the Statistics screen's
     * tabs switch this via [setStatisticsPeriod]. Session-only, not persisted — every
     * screen open starts back at [StatisticsPeriod.ALL_TIME].
     */
    var currentStatisticsPeriod by mutableStateOf(StatisticsPeriod.ALL_TIME)
        private set

    /**
     * Which draw mode [statistics] currently reflects; the Statistics screen's mode
     * selector switches this via [setStatisticsDrawMode]. Session-only, not
     * persisted — every screen open starts back at [DrawMode.ONE]. Independent of
     * [persistedDrawMode], which decides the *next new game's* mode, not which
     * history this screen is looking at.
     */
    var currentStatisticsDrawMode by mutableStateOf(DrawMode.ONE)
        private set

    /** All history, fetched by [refreshStatistics] and re-filtered by [setStatisticsPeriod]/[setStatisticsDrawMode] without a fresh disk read. */
    private var historyRecords: List<HistoryRecord> = emptyList()

    /**
     * The board state as it stood immediately before the last [undo], so [Board] can
     * animate the reversal by diffing it against the now-current state. Cleared once
     * [Board] has consumed it via [consumeUndoAnimationSignal].
     */
    var pendingUndoAnimation by mutableStateOf<GameState?>(null)
        private set

    /**
     * Whether the active session should ever contribute to recorded statistics/history.
     * A Replay explicitly should not — it exists to retry the same deal, not to farm or
     * distort a solvable catalog's win rate (`docs/games/klondike/DESIGN.md` "Statistics") — so this is
     * cleared for the duration of a replayed game and restored for anything else (a
     * fresh New Game, a restored game, the debug fixture). Not persisted: if the app is
     * backgrounded and the process dies mid-replay, the restored game reverts to
     * counting, the same as any other restored game.
     */
    private var currentGameCountsForStatistics = true

    /**
     * Hints taken in the current game, and the length of the certified line shipped for its
     * seed (0 where none is: draw-three, or an Insane deal, which ships uncertified).
     *
     * Both are recorded on the history record when the game ends, rather than derived later,
     * because the catalog can be regenerated under a player's feet — a stored ratio has to
     * keep meaning what it meant when it was written.
     */
    var hintsUsedThisGame by mutableIntStateOf(0)
        private set

    var solutionMoveCount by mutableIntStateOf(0)
        internal set

    /** This game's moves as a percentage of the certified line's, or null without one. */
    val solutionRatioPercent: Long?
        get() = if (solutionMoveCount > 0) solutionRatioPercent(session.state.moveCount, solutionMoveCount) else null

    /**
     * 1-based position of the active seed **within its own difficulty's list**, so
     * "Hand 7" means the seventh Hard deal rather than the seventh of four thousand.
     * Null once the interim source is gone, or for a seed that has no grade at all
     * (draw-three's random shuffles).
     */
    val dealNumber: Int? get() {
        val difficulty = dealDifficulty ?: return null
        return activeSeedsFor(difficulty).indexOf(session.state.seed).let { if (it >= 0) it + 1 else null }
    }

    /** The active seed's difficulty tier, or null for a seed the catalog has no entry for (draw-three's random seeds). */
    val dealDifficulty: DifficultyTier? get() = certifiedCatalog?.difficultyOf(session.state.seed) ?: INTERIM_SEED_GRADES[session.state.seed]

    /** The level currently in force — what the picker shows selected. */
    val difficulty: DifficultyPreference get() = persistedDifficulty

    /**
     * Whether switching level right now would give up a game that counts: true exactly when
     * [setDifficulty] would record a loss, so the picker can warn before it happens rather
     * than after.
     */
    val levelSwitchWouldForfeit: Boolean get() = needsConfirmation()

    private var isForeground = false
    private var isModalOpen = false
    private var tickerJob: Job? = null

    private var restReminderWindow: RestReminderWindow? = null
    private var restReminderTickerJob: Job? = null
    private var restBreakJob: Job? = null

    init {
        viewModelScope.launch {
            dealProgressStore?.let { dealProgress = it.current() }
            restoreOrStartFresh()
        }
    }

    private fun startSession(seed: Long, automaticMovesEnabled: Boolean, drawMode: DrawMode = DrawMode.ONE) =
        GameSession.start(seed, PLACEHOLDER_VERSIONS, automaticMovesEnabled, drawMode)

    /**
     * [DrawMode.THREE]'s deals are not solver-certified (`docs/games/klondike/DESIGN.md`
     * "Draw-Three Mode": no catalog exists for this mode yet, unlike the draw-one
     * interim list) — a plain random seed, not [seedSource], every time.
     */
    private fun seedSourceFor(drawMode: DrawMode): DealSeedSource = when (drawMode) {
        // A caller-injected seedSource (every test that supplies one) always wins:
        // difficulty selection is a property of the shipped catalog, not something a
        // test asking for a specific seed should have silently overridden.
        DrawMode.ONE -> injectedSeedSource ?: difficultySeedSource()
        DrawMode.THREE -> RandomDealSeedSource
    }

    /**
     * The source for the currently selected difficulty. Each level keeps its own
     * long-lived cursor in [difficultySources], so switching between levels resumes each
     * one where it was rather than restarting it; "Random" picks a level per deal and
     * draws from that level's cursor, leaving the others untouched.
     */
    private fun difficultySeedSource(): DealSeedSource =
        when (val tier = persistedDifficulty.toTier()) {
            null -> RandomDifficultyDealSeedSource(::sourceForDifficulty)
            else -> sourceForDifficulty(tier)
        }

    private fun sourceForDifficulty(tier: DifficultyTier): DifficultyDealSeedSource =
        difficultySources.getOrPut(tier) { DifficultyDealSeedSource(tier, activeSeedsFor(tier)) }

    /** Loads the persisted active game if one exists; otherwise starts a fresh deal. */
    private suspend fun restoreOrStartFresh() {
        if (catalogLoadFailure != null) {
            isLoading = false
            return
        }
        restoreInterimSeedPositionIfNeeded()
        val settings = settingsStore.current()
        persistedAnimationsEnabled = settings.animationsEnabled
        persistedHintShowsWinningMove = settings.hintShowsWinningMove
        persistedHintTimeout = settings.hintTimeout
        persistedRestReminderInterval = settings.restReminderInterval
        persistedHandedness = settings.handedness
        persistedSoundEnabled = settings.soundEnabled
        persistedDrawMode = settings.drawMode
        persistedDifficulty = settings.difficulty
        persistedLanguageTag = settings.languageTag
        persistedThemeMode = settings.themeMode
        when (val result = activeGameStore.load()) {
            is ActiveGameLoadResult.Restored -> {
                session = result.session
                elapsedSeconds = result.elapsed.inWholeSeconds.toInt()
                initialAutomaticMovesEnabledForCurrentDeal = result.initialAutomaticMovesEnabled
                gameId = result.gameId
            }
            ActiveGameLoadResult.Missing -> startFreshDeal(settings.automaticMovesEnabled, settings.drawMode)
            ActiveGameLoadResult.Recovered -> {
                recoveryNoticeVisible = true
                startFreshDeal(settings.automaticMovesEnabled, settings.drawMode)
            }
        }
        isLoading = false
        syncTimer()
    }

    fun dismissRecoveryNotice() {
        recoveryNoticeVisible = false
    }

    fun retryCatalogLoad() {
        val loader = catalogLoader ?: return
        when (val result = loader()) {
            is CertifiedDealCatalog.LoadResult.Valid -> {
                certifiedCatalog = result.catalog
                catalogLoadFailure = null
                isLoading = true
                viewModelScope.launch { restoreOrStartFresh() }
            }
            is CertifiedDealCatalog.LoadResult.Invalid -> catalogLoadFailure = result.reason
        }
    }

    /**
     * The move to highlight on the board, or `null` when there is nothing to show.
     *
     * Covers both a proven guidance move and the Expert-strategy suggestion offered
     * alongside an inconclusive result — the board highlights them identically because
     * both answer "what should I do next"; only the notice text distinguishes a proof
     * from a suggestion.
     */
    val hint: Move? get() = when (val state = hintState) {
        is HintUiState.Guided -> state.move
        is HintUiState.Inconclusive -> state.suggestion
        else -> null
    }

    /** Whether any hint is available right now, independent of whether one is currently shown. */
    val hasHint: Boolean get() = session.currentHint() != null

    /**
     * New Game, confirming first when the active game has been played and is not yet
     * won (`DESIGN.md`: "Confirm replay or new game when an unfinished game has at
     * least one successful player action... No confirmation is needed after a win.").
     */
    fun requestNewGame() {
        if (needsConfirmation()) pendingConfirmation = PendingConfirmation.NEW_GAME else performNewGame()
    }

    /** Replay: the same seed and versions, same confirmation rule as [requestNewGame]. */
    fun requestReplay() {
        if (needsConfirmation()) pendingConfirmation = PendingConfirmation.REPLAY else performReplay()
    }

    fun confirmPendingAction() {
        when (pendingConfirmation) {
            PendingConfirmation.NEW_GAME -> performNewGame()
            PendingConfirmation.REPLAY -> performReplay()
            PendingConfirmation.SELECT_DEAL -> pendingDealNumber?.let(::performSelectDeal)
            PendingConfirmation.SET_DRAW_MODE -> pendingDrawMode?.let(::setDrawModeAndDeal)
            PendingConfirmation.SET_DIFFICULTY -> pendingDifficulty?.let(::setDifficulty)
            null -> Unit
        }
        pendingConfirmation = null
        pendingDealNumber = null
        pendingDrawMode = null
        pendingDifficulty = null
    }

    fun cancelPendingAction() {
        pendingDrawMode = null
        pendingDifficulty = null
        pendingConfirmation = null
        pendingDealNumber = null
    }

    private fun needsConfirmation() = session.hasPlayerActed && !session.state.isWon

    private fun startFreshDeal(automaticMovesEnabled: Boolean, drawMode: DrawMode) {
        seed = seedSourceFor(drawMode).nextSeed()
        persistInterimSeedPositionIfNeeded()
        initialAutomaticMovesEnabledForCurrentDeal = automaticMovesEnabled
        currentGameCountsForStatistics = true
        session = startSession(seed, automaticMovesEnabled, drawMode)
        // Saved immediately, not left to the player's first action: the traversal
        // position above already advanced past this hand the instant it was drawn, so
        // a kill-before-first-move would otherwise lose this exact deal - the next
        // restart would silently draw a *different* hand the player never saw, rather
        // than resuming the one actually on screen. persistActiveGame() bypasses
        // scheduleSave()'s isLoading guard deliberately: by this point session/gameId/
        // initialAutomaticMovesEnabledForCurrentDeal are already final for this deal,
        // so there is no correctness reason to defer it until restoreOrStartFresh()
        // finishes moments later.
        persistActiveGame()
    }

    /** New Game deals under whatever [persistedDrawMode] currently is (`docs/games/klondike/DESIGN.md` "Draw-Three Mode": "Add draw-mode selection for new games"). */
    private fun performNewGame() {
        recordAbandonmentIfNeeded()
        seed = seedSourceFor(persistedDrawMode).nextSeed()
        persistInterimSeedPositionIfNeeded()
        gameId = UUID.randomUUID().toString()
        currentGameCountsForStatistics = true
        resetTo(startSession(seed, initialAutomaticMovesEnabledForCurrentDeal, persistedDrawMode))
    }

    /**
     * Resumes [seedSource]'s position from [catalogTraversalStore] before the first
     * deal of this instance's lifetime, so a killed-and-restarted app continues the
     * hand sequence instead of redealing hand 1 (`docs/games/klondike/DESIGN.md` "Persistence": "Persist
     * catalog version and traversal state so new games do not repeat"). No-op when
     * persistence isn't wired up (every existing test that injects its own [seedSource])
     * or when nothing has been persisted yet (first-ever launch).
     */
    private suspend fun restoreInterimSeedPositionIfNeeded() {
        val store = catalogTraversalStore ?: return
        val stored = store.loadDifficultyPositions(activeCatalogVersion(), difficultyRecordCounts())
        for ((name, index) in stored) {
            val tier = DifficultyTier.entries.find { it.name == name } ?: continue
            difficultySources[tier] = DifficultyDealSeedSource(tier, activeSeedsFor(tier), startIndex = index)
        }
    }

    /**
     * Persists every difficulty's position after a draw, not just the one just used:
     * "Random" advances whichever level it picked, and writing all four in the single
     * edit this already costs keeps them consistent without tracking which one moved.
     * A no-op for draw-three (uncatalogued random shuffles) or when
     * [catalogTraversalStore] is null.
     */
    private fun persistInterimSeedPositionIfNeeded() {
        val store = catalogTraversalStore ?: return
        if (difficultySources.isEmpty()) return
        val positions = difficultySources.entries.associate { (tier, source) -> tier.name to source.currentIndex }
        viewModelScope.launch { store.saveDifficultyPositions(activeCatalogVersion(), positions, difficultyRecordCounts()) }
    }

    private fun difficultyRecordCounts(): Map<String, Int> =
        certifiedCatalog?.recordCounts() ?: DifficultyTier.entries.associate { it.name to activeSeedsFor(it).size }

    private fun activeSeedsFor(tier: DifficultyTier): List<Long> = certifiedCatalog?.seedsFor(tier) ?: seedsFor(tier)

    private fun activeCatalogVersion(): Int = certifiedCatalog?.catalogVersion ?: INTERIM_SEED_CATALOG_VERSION

    /**
     * Replay never touches statistics: replacing the current game this way records no
     * abandonment loss for it (unlike [performNewGame]), and the replayed game itself
     * records no win or loss however it ends (`DESIGN.md` "Statistics") — a half
     * exemption (no loss on replace, but still recording an eventual win) would let a
     * bad position be retried risk-free until it wins, inflating the rate for a deal
     * that a draw-one player expects to be solvable by construction (draw-three has
     * no such certification yet, but the same "don't let free retries inflate the
     * rate" principle still applies).
     *
     * Preserves the current game's own draw mode ([GameSession.state]'s
     * [org.finiteplay.klondike.board.GameState.drawMode]), not [persistedDrawMode]
     * — replaying a deal must reproduce it exactly, even if the player has since
     * changed the Settings toggle for the *next* new game.
     */
    private fun performReplay() {
        gameId = UUID.randomUUID().toString()
        currentGameCountsForStatistics = false
        resetTo(startSession(seed, initialAutomaticMovesEnabledForCurrentDeal, session.state.drawMode))
    }

    private fun resetTo(newSession: GameSession) {
        session = newSession
        elapsedSeconds = 0
        hintJob?.cancel()
        hintJob = null
        hintState = HintUiState.Hidden
        hintShowsProgressDialog = false
        legalMoveHighlights = emptyList()
        hintEngine.reset()
        hintsUsedThisGame = 0
        // Off the main thread, and never awaited: the first lookup parses the whole solution
        // index, and doing that inline cost seconds of blank screen against a 1.5 s cold-start
        // budget (`DESIGN.md` "Performance"). Nothing needs the number until the game is won.
        //
        // Against the raw deal, not the board in front of the player: a stored line is written
        // from the deal, and a restored session is already past its start.
        solutionMoveCount = 0
        val solutionSeed = newSession.state.seed
        val deal = { dealGame(newSession.state.seed, newSession.state.versions, newSession.state.drawMode) }
        val catalog = solutionCatalog
        if (catalog != null) {
            viewModelScope.launch {
                val moves = withContext(hintDispatcher) { catalog.solutionFor(deal())?.size ?: 0 }
                // A New Game may have replaced the session while this ran; the count belongs
                // to the deal it was looked up for, not to whatever is on screen now.
                if (session.state.seed == solutionSeed) solutionMoveCount = moves
            }
        }
        primeHintEngineWithStoredSolution(newSession)
        syncTimer()
        scheduleSave()
    }

    /**
     * Hands the engine the solution shipped with this seed, so a player who takes each
     * hint and plays it walks a known winning line with no search at all. Deviating from
     * it costs nothing: the board simply stops matching the line and the search resumes.
     *
     * Only meaningful from the raw deal — a restored mid-game session is already past
     * the line's start, and the engine's own state matching handles rejoining it — and a
     * no-op when no catalog is wired up (tests) or the seed is uncatalogued (draw-three).
     */
    private fun primeHintEngineWithStoredSolution(newSession: GameSession) {
        val catalog = solutionCatalog ?: return
        if (newSession.state.moveCount > 0) return
        val solution = catalog.solutionFor(newSession.state) ?: return
        hintEngine.primeWithKnownSolution(newSession.state, solution)
    }

    /**
     * Replacing an unfinished played game counts as a loss (`DESIGN.md` "Statistics").
     * A no-op exactly when [needsConfirmation] would also have been false, so this is
     * safe to call unconditionally right before switching away from [session].
     */
    private fun recordAbandonmentIfNeeded() {
        if (!needsConfirmation()) return
        recordResult(Outcome.LOSS)
    }

    fun undo() {
        if (!session.canUndo) return
        val preUndoState = session.state
        session = session.undo()
        hintJob?.cancel()
        hintJob = null
        hintState = HintUiState.Hidden
        hintShowsProgressDialog = false
        legalMoveHighlights = emptyList()
        syncTimer()
        scheduleSave()
        pendingUndoAnimation = preUndoState
    }

    /** Consumed by [Board] once it has read [pendingUndoAnimation] and queued the reversal's animation. */
    fun consumeUndoAnimationSignal() {
        pendingUndoAnimation = null
    }

    /** Commits [move] if legal; returns whether it committed, so callers can drive invalid-action feedback. */
    fun tryCommitMove(move: Move): Boolean {
        if (!isLegal(session.state, move)) return false
        session = session.commitMove(move)
        hintJob?.cancel()
        hintJob = null
        hintState = HintUiState.Hidden
        hintShowsProgressDialog = false
        legalMoveHighlights = emptyList()
        finishAutomaticallyIfReady()
        if (session.state.isWon) recordResult(Outcome.WIN)
        syncTimer()
        scheduleSave()
        return true
    }

    /**
     * Runs the on-device solver from the current board and shows what it finds
     * (`docs/games/klondike/DESIGN.md` "On-Device Hint Search"). A no-op while already loading or
     * showing a result — repeating the request has nothing new to show, since the
     * search returns the single next step of a proven line rather than a list of
     * ranked candidates to page through. Runs off the main thread; if the board
     * changes before the search returns (the player moved while it was thinking),
     * the result is discarded as stale rather than shown against a board it no
     * longer describes.
     */
    /**
     * A game that has no player move behind it follows the deal's certified path whatever the
     * Intelligent Hint setting says (`docs/games/klondike/DESIGN.md` "On-Device Hint Search"): the
     * line is shipped, so showing it costs no search and leaves nothing to guess. Once the player
     * has moved, the setting decides. Without a shipped line (Insane, draw-three) it never applies.
     */
    private fun followsCertifiedPath(): Boolean = !session.hasPlayerActed && solutionMoveCount > 0

    fun requestHint() {
        if (!persistedHintShowsWinningMove && !followsCertifiedPath()) {
            // No search, so no loading state and nothing that can be stale: toggles instantly.
            legalMoveHighlights = if (legalMoveHighlights.isEmpty()) legalMoves(session.state) else emptyList()
            return
        }
        if (hintState != HintUiState.Hidden) return
        hintState = HintUiState.Loading
        val requestedState = session.state
        hintJob = viewModelScope.launch {
            launch {
                delay(HINT_PROGRESS_DIALOG_DELAY)
                if (hintState == HintUiState.Loading) hintShowsProgressDialog = true
            }
            val outcome = withContext(hintDispatcher) { hintEngine.hint(requestedState, hintSolverLimits(persistedHintTimeout)) }
            // Discard a stale result: the board moved on, or the player already
            // dismissed the loading notice, while the search was still running.
            if (session.state != requestedState || hintState != HintUiState.Loading) return@launch
            hintShowsProgressDialog = false
            val shown = when (outcome) {
                is HintOutcome.Guidance -> HintUiState.Guided(outcome.move)
                is HintOutcome.NoSolution -> HintUiState.NoSolution
                is HintOutcome.Inconclusive -> HintUiState.Inconclusive(outcome.suggestion)
            }
            hintState = shown
            // Only guidance counts: a search that proved nothing gave the player nothing to
            // take, and charging them for it would make the statistic a measure of the
            // solver's luck rather than of how much help they asked for.
            if (shown is HintUiState.Guided) hintsUsedThisGame++
            if (shown == HintUiState.NoSolution || shown is HintUiState.Inconclusive) {
                launch {
                    delay(HINT_NOTICE_AUTO_DISMISS)
                    // Only if nothing else (Dismiss, a move, undo, a new game) already moved on.
                    if (hintState == shown) hintState = HintUiState.Hidden
                }
            }
        }
    }

    /**
     * Dismisses the current hint notice (loading, no-solution, or inconclusive) without waiting
     * for it. Cancelling [hintJob] stops the player from waiting on a Loading search, not the
     * search itself — the solver's own node loop only checks a wall-clock deadline, not this Job
     * (`org.finiteplay.core.ui.layout.HintProgressDialog`'s own doc comment records the same limit)
     * — but the `hintState != HintUiState.Loading` guard above discards the result regardless once
     * the search does return.
     */
    fun dismissHint() {
        hintJob?.cancel()
        hintJob = null
        hintState = HintUiState.Hidden
        hintShowsProgressDialog = false
        legalMoveHighlights = emptyList()
    }

    /**
     * Replaces the board with [state] as a fresh, undo-empty session, bypassing the
     * normal seeded deal. Used only by the debug-only near-win fixture (`src/debug`,
     * `EXECUTION_PLAN.md` A2a); harmless but otherwise unused in a release build since
     * no release code path calls it.
     */
    fun loadFixtureForDebugging(state: GameState) {
        gameId = UUID.randomUUID().toString()
        currentGameCountsForStatistics = true
        resetTo(GameSession.of(state))
    }

    /**
     * The finish is a transition the engine offers; only the game screen invokes it
     * (`DESIGN.md` "Automatic Finish"). Checked after every committed transaction.
     */
    private fun finishAutomaticallyIfReady() {
        if (canAutoFinish(session.state)) {
            session = session.commitAutoFinish()
        }
    }

    /** Drives the DESIGN.md timer predicate's foreground half; call from the game screen's lifecycle. */
    fun setForeground(foreground: Boolean) {
        isForeground = foreground
        syncTimer()
        syncRestReminderForeground(foreground)
        if (!foreground) scheduleSave()
    }

    /** Modal screens (a pending New/Replay confirmation) pause the timer (`UI_SPEC.md`). */
    fun setModalOpen(modalOpen: Boolean) {
        isModalOpen = modalOpen
        syncTimer()
    }

    /**
     * Applies immediately to the active session and persists (`DESIGN.md` "Dialog
     * Behavior": "Settings apply immediately"). A3 calls this from the Settings screen.
     */
    fun setAutomaticMovesEnabled(enabled: Boolean) {
        session = session.withAutomaticMoves(enabled)
        scheduleSave()
        viewModelScope.launch { settingsStore.setAutomaticMovesEnabled(enabled) }
    }

    /** Applies immediately (merged with the system setting in [rememberReducedMotion]) and persists. */
    fun setAnimationsEnabled(enabled: Boolean) {
        persistedAnimationsEnabled = enabled
        viewModelScope.launch { settingsStore.setAnimationsEnabled(enabled) }
    }

    /** Applies immediately and persists; does not itself change what a hint already on screen shows. */
    fun setHintShowsWinningMove(enabled: Boolean) {
        persistedHintShowsWinningMove = enabled
        viewModelScope.launch { settingsStore.setHintShowsWinningMove(enabled) }
    }

    fun setHintTimeout(hintTimeout: HintTimeout) {
        persistedHintTimeout = hintTimeout
        viewModelScope.launch { settingsStore.setHintTimeout(hintTimeout) }
    }

    /** Applies immediately and persists; restarts the current window so a new choice takes effect right away. */
    fun setRestReminderInterval(restReminderInterval: RestReminderInterval) {
        persistedRestReminderInterval = restReminderInterval
        val now = System.currentTimeMillis()
        restReminderWindow = (restReminderWindow ?: RestReminderWindow.start(now))
            .afterIntervalChange(now, restReminderInterval.minutes?.let { it * 60_000L })
        refreshRestReminderElapsed()
        syncRestReminderTicker()
        viewModelScope.launch { settingsStore.setRestReminderInterval(restReminderInterval) }
    }

    /** Accepts the reminder's offer: starts a countdown for half the configured interval, during which no usage is tracked at all. */
    fun startRestBreak() {
        showRestReminderDialog = false
        val minutes = persistedRestReminderInterval.minutes ?: return
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
        val minutes = persistedRestReminderInterval.minutes ?: return
        val window = restReminderWindow ?: return
        val now = System.currentTimeMillis()
        if (!isRestReminderDue(window, now, minutes * 60_000L)) return
        showRestReminderDialog = true
        restReminderWindow = RestReminderWindow.start(now)
        refreshRestReminderElapsed()
    }

    /** Applies immediately and persists (`docs/games/klondike/UI_SPEC.md` "Left-Handed Layout"). */
    fun setHandedness(handedness: Handedness) {
        persistedHandedness = handedness
        viewModelScope.launch { settingsStore.setHandedness(handedness) }
    }

    /** Applies immediately and persists (`docs/games/klondike/DESIGN.md` "Sound"). */
    fun setSoundEnabled(enabled: Boolean) {
        persistedSoundEnabled = enabled
        viewModelScope.launch { settingsStore.setSoundEnabled(enabled) }
    }

    /** Persists the draw mode for new games; never touches the game in progress. See [persistedDrawMode]. */
    fun setDrawMode(drawMode: DrawMode) {
        persistedDrawMode = drawMode
        viewModelScope.launch { settingsStore.setDrawMode(drawMode) }
    }

    private var pendingDrawMode: DrawMode? = null
    private var pendingDifficulty: DifficultyPreference? = null

    /**
     * Settings' draw-mode choice: choosing a different mode than the game in play starts a new game
     * in it, asking first — as New Game does — when the game on screen has been played and is
     * unfinished, since leaving it records a loss. Choosing the mode already in play only records it
     * for later deals.
     */
    fun requestDrawMode(drawMode: DrawMode) {
        if (drawMode == session.state.drawMode) {
            setDrawMode(drawMode)
        } else if (needsConfirmation()) {
            pendingDrawMode = drawMode
            pendingConfirmation = PendingConfirmation.SET_DRAW_MODE
        } else {
            setDrawModeAndDeal(drawMode)
        }
    }

    private fun setDrawModeAndDeal(drawMode: DrawMode) {
        setDrawMode(drawMode)
        returnUnplayedDeal()
        performNewGame()
    }

    /**
     * Switching away from a game with no move behind it — to another level or draw mode — gives its
     * deal back to its level's sequence, so switching there and back finds the same hand rather
     * than burning one each time. Plain New Game does not: it is a request for a different deal.
     */
    private fun returnUnplayedDeal() {
        if (session.hasPlayerActed || session.state.drawMode != DrawMode.ONE) return
        val tier = dealDifficulty ?: return
        sourceForDifficulty(tier).giveBack(session.state.seed)
        persistInterimSeedPositionIfNeeded()
    }

    /** Settings' level choice: [setDifficulty] after the same confirmation [requestDrawMode] asks. */
    fun requestDifficulty(difficulty: DifficultyPreference) {
        if (difficulty != persistedDifficulty && needsConfirmation()) {
            pendingDifficulty = difficulty
            pendingConfirmation = PendingConfirmation.SET_DIFFICULTY
        } else {
            setDifficulty(difficulty)
        }
    }

    /**
     * Applies **immediately**, ending the game in play and dealing from the new level.
     *
     * Unlike draw mode, which only takes effect on the next New Game, choosing a level is a
     * request to play a different hand: leaving the old one on screen answers the opposite of
     * what was asked. So the current deal is forfeited and replaced, and — exactly as New Game
     * does — an unfinished game the player has acted on records a **loss**
     * (`DESIGN.md` "Statistics"). A deal still sitting on its raw board records nothing;
     * there is nothing to abandon.
     *
     * Selecting the level already in force is a no-op rather than a free reroll, since
     * otherwise the picker would double as a way to skip a hand without forfeiting it.
     */
    fun setDifficulty(difficulty: DifficultyPreference) {
        if (difficulty == persistedDifficulty) return
        returnUnplayedDeal()
        persistedDifficulty = difficulty
        viewModelScope.launch { settingsStore.setDifficulty(difficulty) }
        performNewGame()
    }

    /**
     * Records the language choice. The caller also writes it where `attachBaseContext`
     * can read it synchronously and recreates the Activity, which is what actually
     * changes the displayed language (`storage/AppLocale.kt`).
     */
    fun setLanguageTag(languageTag: String) {
        persistedLanguageTag = languageTag
        viewModelScope.launch { settingsStore.setLanguageTag(languageTag) }
    }

    /** Takes effect on the next frame: the theme is read from composition, not from a stored Activity. */
    fun setThemeMode(themeMode: ThemeMode) {
        persistedThemeMode = themeMode
        viewModelScope.launch { settingsStore.setThemeMode(themeMode) }
    }

    private fun syncTimer() {
        val shouldRun = shouldRunTimer(session, isForeground, isModalOpen)
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

    /** Saves asynchronously (`DESIGN.md` "Persistence and Privacy"); never blocks the caller. */
    private fun scheduleSave() {
        if (isLoading) return
        syncDealProgress()
        persistActiveGame()
    }

    /**
     * The actual save, without [scheduleSave]'s `isLoading` guard — [startFreshDeal]
     * calls this directly since, unlike every other caller, it runs *during*
     * [restoreOrStartFresh] itself (before `isLoading` flips false) and still needs
     * this exact deal saved immediately, not deferred to the player's first action.
     */
    private fun persistActiveGame() {
        val snapshot = session
        val elapsed = elapsedSeconds
        val initialAutomation = initialAutomaticMovesEnabledForCurrentDeal
        val id = gameId
        viewModelScope.launch {
            activeGameStore.save(snapshot, initialAutomation, elapsed.seconds, id)
        }
    }

    /**
     * Idempotent per game: the result ID is derived from [gameId] alone, so recording
     * the same game's outcome more than once (e.g. a win observed again after rotation
     * or restoration) upserts the same record rather than duplicating it
     * (`EXECUTION_PLAN.md` A2b). A no-op when [currentGameCountsForStatistics] is false
     * (`DESIGN.md` "Statistics") — covers both the win path (`tryCommitMove`) and the
     * abandonment path ([recordAbandonmentIfNeeded]).
     */
    private fun recordResult(outcome: Outcome) {
        // Archived before the statistics guard, not after: a Replay is excluded from
        // statistics by design, but its moves are still a real sequence of player
        // decisions, and this archive exists to analyse decisions rather than results.
        archiveFinishedGame(outcome)
        if (!currentGameCountsForStatistics) return
        val record = HistoryRecord(
            gameId = gameId,
            resultId = "$gameId:result",
            outcome = outcome,
            elapsedMillis = elapsedSeconds.seconds.inWholeMilliseconds,
            moveCount = session.state.moveCount,
            timestampMillis = System.currentTimeMillis(),
            drawMode = session.state.drawMode,
            hintsUsed = hintsUsedThisGame,
            solutionMoveCount = solutionMoveCount,
            difficulty = dealDifficulty,
        )
        viewModelScope.launch { historyStore.upsert(record) }
    }

    /**
     * Archives the finished game with its full move log (`GameArchiveStore`). Snapshots
     * every input before launching, for the same reason [persistActiveGame] does: the
     * coroutine runs after this returns, by which point a New Game may already have
     * replaced [session].
     */
    private fun archiveFinishedGame(outcome: Outcome) {
        val store = gameArchiveStore ?: return
        val archived = ArchivedGame(
            gameId = gameId,
            seed = session.state.seed,
            versions = session.state.versions,
            drawMode = session.state.drawMode,
            initialAutomaticMovesEnabled = initialAutomaticMovesEnabledForCurrentDeal,
            outcome = outcome,
            elapsedMillis = elapsedSeconds.seconds.inWholeMilliseconds,
            moveCount = session.state.moveCount,
            timestampMillis = System.currentTimeMillis(),
            countedForStatistics = currentGameCountsForStatistics,
            moves = session.log,
        )
        viewModelScope.launch { store.record(archived) }
    }

    /** Re-fetches history from disk and recomputes [statistics] for the current period/draw-mode selection (A3). */
    fun refreshStatistics() {
        viewModelScope.launch {
            historyRecords = historyStore.all()
            recomputeStatistics()
        }
    }

    /** Switches which trailing window [statistics] reflects, re-filtering already-fetched history — no disk read. */
    fun setStatisticsPeriod(period: StatisticsPeriod) {
        currentStatisticsPeriod = period
        recomputeStatistics()
    }

    /** Switches which draw mode [statistics] reflects, re-filtering already-fetched history — no disk read. */
    fun setStatisticsDrawMode(drawMode: DrawMode) {
        currentStatisticsDrawMode = drawMode
        recomputeStatistics()
    }

    private fun recomputeStatistics() {
        // The active game only counts toward *this* view's "games played" while
        // unfinished if it actually belongs to the mode currently being viewed —
        // an unfinished draw-three game must never inflate a draw-one "games played"
        // count, or vice versa.
        val hasUnfinishedPlayedGame = needsConfirmation() && currentGameCountsForStatistics &&
            session.state.drawMode == currentStatisticsDrawMode
        val windowed = filterByPeriod(historyRecords, currentStatisticsPeriod, nowMillis = System.currentTimeMillis())
        val byMode = filterByDrawMode(windowed, currentStatisticsDrawMode)
        statistics = computeStatistics(byMode, hasUnfinishedPlayedGame)
    }

    /**
     * Clears all recorded history, behind confirmation (`DESIGN.md` "Statistics"). Clears
     * the move archive in the same pass: a reset that visibly wipes results while quietly
     * keeping every move the player made would not be the reset they asked for.
     */
    fun resetStatistics() {
        dealProgress = emptyMap()
        viewModelScope.launch {
            dealProgressStore?.clear()
            gameArchiveStore?.clear()
            historyStore.clear()
            historyRecords = emptyList()
            recomputeStatistics()
        }
    }
}
