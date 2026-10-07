package org.finiteplay.spider.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.finiteplay.cards.Card
import org.finiteplay.core.session.Session
import org.finiteplay.core.session.StatisticsPeriod
import org.finiteplay.core.session.shouldRunTimer
import org.finiteplay.spider.deal.SpiderCertifiedDealCatalog
import org.finiteplay.spider.deal.SpiderSolutionCatalog
import org.finiteplay.spider.game.DealSequence
import org.finiteplay.spider.game.HintedCard
import org.finiteplay.spider.game.hintedCards
import org.finiteplay.spider.game.resolveTap
import org.finiteplay.spider.layout.GameStatus
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.rules.BankedRun
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.applyMoveDetailed
import org.finiteplay.spider.rules.canDealRow
import org.finiteplay.spider.rules.findAutoFinish
import org.finiteplay.spider.rules.isAutoFinishAvailable
import org.finiteplay.spider.rules.isLegal
import org.finiteplay.spider.solver.HINT_SOLVER_LIMITS
import org.finiteplay.spider.solver.HintEngine
import org.finiteplay.spider.solver.HintOutcome
import org.finiteplay.spider.solver.SpiderSolver
import org.finiteplay.spider.session.SpiderSession
import org.finiteplay.spider.session.commitMove
import org.finiteplay.spider.session.undo
import org.finiteplay.core.ui.layout.DiscardingAction
import org.finiteplay.core.ui.layout.Handedness
import org.finiteplay.core.ui.layout.HintTimeout
import org.finiteplay.core.ui.layout.RestReminderInterval
import org.finiteplay.core.session.RestReminderWindow
import org.finiteplay.core.session.afterIntervalChange
import org.finiteplay.core.session.foregroundEntered
import org.finiteplay.core.session.foregroundExited
import org.finiteplay.core.session.foregroundMsAsOf
import org.finiteplay.core.session.isRestReminderDue
import org.finiteplay.core.ui.theme.ThemeMode
import org.finiteplay.spider.storage.SpiderActiveGameLoadResult
import org.finiteplay.spider.storage.SpiderActiveGameStore
import org.finiteplay.spider.storage.SpiderSettings
import org.finiteplay.spider.storage.SpiderHistoryRecord
import org.finiteplay.spider.storage.SpiderHistoryStore
import org.finiteplay.spider.storage.SpiderOutcome
import org.finiteplay.spider.storage.SpiderSettingsStore
import org.finiteplay.spider.storage.SpiderTraversalStore
import org.finiteplay.spider.storage.SpiderStatistics
import org.finiteplay.core.storage.DealProgressStore
import org.finiteplay.core.storage.DealProgress
import org.finiteplay.core.storage.DealStatus
import org.finiteplay.core.storage.advancedBy
import org.finiteplay.spider.storage.computeSpiderStatistics
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Versions this pass deals against — frozen the same way Klondike's are, and stated here rather
 * than assumed since Spider's rules and shuffle are not yet declared final anywhere.
 */
internal val SPIDER_VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

/**
 * How long [SpiderViewModel.runAutoFinishIfAvailable] pauses after each committed move before
 * committing the next, standing in for that move's own flight — this class has no visibility into
 * `SpiderBoard.kt`'s actual screen geometry, so it cannot compute the real duration the way the
 * board itself does. Comfortably above the board's own per-move ceiling of 450 ms; the bank
 * flight's own is longer still — up to thirteen cards staggered a quarter of their own (also
 * 450 ms-capped) flight apart — so a move that also completes the last sequence gets the longer of
 * the two pauses.
 */
private const val AUTO_FINISH_MOVE_STEP_DELAY_MS = 500L

/** How many deals the picker lists at a suit count with no certified catalog. */
internal const val UNCERTIFIED_PICKER_DEALS = 500
private const val AUTO_FINISH_BANK_STEP_DELAY_MS = 2_000L

/** How long a No-solution or Inconclusive hint notice stays up before dismissing itself. */
internal val HINT_NOTICE_AUTO_DISMISS = 4.seconds

/**
 * How long a hint search runs before [SpiderViewModel.hintShowsProgressDialog] switches on
 * (`docs/games/spider/UI_SPEC.md` "Hint"). Below this, a search resolves silently — flashing a
 * modal for an instant would read as broken rather than helpful. Most hints resolve well inside it;
 * a hard two-suit board, which `PhaseSearch` keeps widening on until the timeout, is what it is for.
 */
internal val HINT_PROGRESS_DIALOG_DELAY = 1.seconds

/**
 * How often [SpiderViewModel.refreshRestReminderElapsed] updates and
 * [SpiderViewModel.checkRestReminder] re-evaluates the current window. A full second, matching
 * the elapsed-play ticker: Settings' read-only display is the reason this needs to be this
 * fine-grained at all — the reminder itself would be just as correct checked far less often,
 * since the configured interval is tens of minutes at the shortest.
 */
internal val REST_REMINDER_CHECK_INTERVAL = 1.seconds

/**
 * [HINT_SOLVER_LIMITS] at the player's own chosen wall-clock ceiling (`core:ui`'s `HintTimeout`,
 * "Hint timeout" in Settings) rather than the solver module's fixed default. [HintEngine] splits it
 * between its two searches, and the first of them widens until the clock runs out, so a longer
 * timeout wins more of the hard boards.
 */
private fun hintSolverLimits(timeout: HintTimeout) = HINT_SOLVER_LIMITS.copy(maxMillis = timeout.seconds * 1_000L)

/**
 * What the solver-backed Hint mode currently shows (`docs/games/spider/DESIGN.md` "Hint",
 * `docs/games/spider/UI_SPEC.md` "Hint") — [SpiderViewModel.hintedCards]/[SpiderViewModel.hintedStock]
 * still carry what actually renders on the board in either hint mode, so [Guided] itself holds no
 * highlight state of its own; this only tracks the loading/notice lifecycle the highlight-only mode
 * never needed.
 */
sealed class HintUiState {
    data object Hidden : HintUiState()
    data object Loading : HintUiState()
    data class Guided(val move: Move) : HintUiState()
    data object NoSolution : HintUiState()
    data object Inconclusive : HintUiState()
}

/**
 * Holds the game in progress and persists it (`docs/games/spider/EXECUTION_PLAN.md` S3b). It turns
 * taps and drags into committed [SpiderSession] transitions, and saves the result.
 *
 * [initialSeed] defaults to a fresh random deal in production and lets a test construct a known
 * board directly — mirroring Klondike's own `GameViewModel` seed-injection constructor, and for
 * the same reason: a view model that can only ever deal randomly cannot be tested
 * deterministically at all.
 *
 * [store] is null in tests that do not exercise persistence, which then behave exactly as this
 * class did before it had any.
 */
class SpiderViewModel(
    private val initialSeed: Long = Random.nextLong(),
    private val store: SpiderActiveGameStore? = null,
    private val settingsStore: SpiderSettingsStore? = null,
    private val historyStore: SpiderHistoryStore? = null,
    private val traversalStore: SpiderTraversalStore? = null,
    private val certifiedCatalog: SpiderCertifiedDealCatalog? = null,
    private val solutionCatalog: SpiderSolutionCatalog? = null,
    /** Debug builds only; see [MoveRecorder]. */
    private val moveRecorder: MoveRecorder? = null,
    /** Which deals have been played and won; null in tests that do not exercise it (progress is then kept in memory only). */
    private val dealProgressStore: DealProgressStore? = null,
    /** Builds the hint engine for a timeout; a seam so a test can supply one that never finds a line. */
    private val hintEngineFactory: (HintTimeout) -> HintEngine = { HintEngine(SpiderSolver(hintSolverLimits(it))) },
) : ViewModel() {

    /** Whether the active deal came from the certified catalog rather than the uncertified formula. */
    val dealIsCertified: Boolean get() = certifiedCatalog?.seedsFor(session.state.suitCount) != null

    /**
     * Which deal this is at its suit count — "#7" is always the same board, and New Game walks the
     * numbers in order rather than jumping to a random seed, exactly as Klondike numbers within a
     * difficulty (`DealSequence`).
     */
    var dealNumber by mutableStateOf(DealSequence.FIRST)
        private set

    /** Finished games, for Statistics. Empty with no store. */
    var historyRecords by mutableStateOf<List<SpiderHistoryRecord>>(emptyList())
        private set

    /** Which suit count Statistics is showing; starts on the one being played. */
    var statisticsSuitCount by mutableStateOf(SpiderSettings.DEFAULT.nextSuitCount)
        private set

    var statisticsPeriod by mutableStateOf(StatisticsPeriod.ALL_TIME)
        private set

    val statistics: SpiderStatistics
        get() = computeSpiderStatistics(
            records = historyRecords,
            suitCount = statisticsSuitCount,
            period = statisticsPeriod,
            nowMillis = System.currentTimeMillis(),
        )

    fun showStatisticsFor(suitCount: SuitCount) { statisticsSuitCount = suitCount }
    fun showStatisticsFor(period: StatisticsPeriod) { statisticsPeriod = period }

    fun resetStatistics() {
        historyRecords = emptyList()
        dealProgress = emptyMap()
        viewModelScope.launch { dealProgressStore?.clear() }
        val store = historyStore ?: return
        viewModelScope.launch { store.clear() }
    }

    /**
     * Which deals have been played and won, by seed. Seeds are derived per suit count, so a seed
     * names a board at one suit count only and no suit count needs recording beside it.
     */
    var dealProgress by mutableStateOf<Map<Long, DealProgress>>(emptyMap())
        private set

    /**
     * Raises the active deal's progress: played once the player has moved, won once it is, and keeps
     * the moves with it — the fewest of any win, or those of the game last played.
     */
    private fun syncDealProgress() {
        val state = session.state
        val reached = when {
            state.status == GameStatus.WON -> DealStatus.WON
            state.moveCount > 0 -> DealStatus.PLAYED
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
     * How many deals the picker lists at the active suit count: the whole certified catalog. Only a
     * game built without one (tests) falls back to a fixed window of the formula's first
     * [UNCERTIFIED_PICKER_DEALS].
     */
    val dealPickerCount: Int
        get() = certifiedCatalog?.seedsFor(session.state.suitCount)?.size ?: UNCERTIFIED_PICKER_DEALS

    /** [dealProgress] for the active suit count, keyed by the deal numbers the picker lists. */
    fun dealPickerProgress(): Map<Int, DealProgress> {
        val suitCount = session.state.suitCount
        return buildMap {
            for (number in 1..dealPickerCount) dealProgress[seedFor(suitCount, number)]?.let { put(number, it) }
        }
    }

    private var pendingDealNumber: Int? = null

    /**
     * Starts deal [number] at the suit count in play, confirming first when the game on screen
     * has been played and is unfinished — picking a deal replaces it exactly as New Game does,
     * and records the same loss. Picking the deal already on screen does nothing.
     */
    fun requestSelectDeal(number: Int) {
        if (number !in 1..dealPickerCount || number == dealNumber) return
        if (needsConfirmation()) {
            pendingDealNumber = number
            pendingAction = DiscardingAction.NEW_GAME
        } else {
            selectDeal(number)
        }
    }

    private fun selectDeal(number: Int) {
        recordLossIfAbandoned()
        val suitCount = session.state.suitCount
        dealNumber = number
        session = newDeal(suitCount, seedFor(suitCount, number))
        lastBankedRuns = emptyList()
        lastMovedSequence = null
        lastDealtRow = null
        hintsUsedThisGame = 0
        hintEngine.reset()
        primeHintEngineWithStoredSolution(session.state)
        currentGameRecorded = false
        dealRowRefused = false
        elapsedSeconds = 0
        gameId = newGameId()
        afterCommit()
    }

    /**
     * True once this game's result has been written, so a win is recorded once however many times
     * the board is recomposed or the finish re-checked.
     */
    private var currentGameRecorded = false

    private fun recordWinIfFinished() {
        if (!session.state.isWon || currentGameRecorded) return
        currentGameRecorded = true
        statisticsSuitCount = session.state.suitCount
        record(SpiderOutcome.WIN)
    }

    /**
     * A played game abandoned for a new deal counts as a loss — otherwise a player could avoid
     * every loss by dealing again, and the win rate would describe nothing. A restart does not:
     * it is the same deal retried, not a game given up on (`DESIGN.md` "Scoring and statistics").
     */
    private fun recordLossIfAbandoned() {
        if (currentGameRecorded || session.state.moveCount == 0 || session.state.isWon) return
        currentGameRecorded = true
        record(SpiderOutcome.LOSS)
    }

    private fun record(outcome: SpiderOutcome) {
        val store = historyStore ?: return
        val record = SpiderHistoryRecord(
            gameId = gameId,
            resultId = "$gameId-${outcome.name}",
            outcome = outcome,
            suitCount = session.state.suitCount,
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

    /** Current settings; [SpiderSettings.DEFAULT] until the store has been read, and always that when there is none. */
    var settings by mutableStateOf(SpiderSettings.DEFAULT)
        private set
    var session by mutableStateOf(newDeal(SpiderSettings.DEFAULT.nextSuitCount, initialSeed))
        private set

    /**
     * True until the persisted game has been checked. The board stays non-interactive while it
     * holds, rather than flashing a fresh deal that restoration is about to replace.
     */
    var isLoading by mutableStateOf(store != null)
        private set

    /** Set when a save existed and could not be read; the player is owed that notice. */
    var recoveryNoticeVisible by mutableStateOf(false)
        private set

    private var gameId: String = newGameId()

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
        if (store != null) {
            viewModelScope.launch {
                // Settings are read before the board is decided: with no game to restore, the
                // opening deal has to use the count the player configured, not the built-in
                // default the field above was initialised with.
                val configured = settingsStore?.current()?.also { settings = it } ?: settings
                when (val loaded = store.load()) {
                    is SpiderActiveGameLoadResult.Restored -> {
                        session = loaded.session
                        lastBankedRuns = emptyList()
                        lastMovedSequence = null
                        lastDealtRow = null
                        elapsedSeconds = loaded.elapsed.inWholeSeconds.toInt()
                        gameId = loaded.gameId
                        dealNumber = loaded.dealNumber
                        primeHintEngineWithStoredSolution(session.state)
                        moveRecorder?.onCommitted(gameId, session, shownHint = null)
                    }
                    is SpiderActiveGameLoadResult.Recovered -> {
                        recoveryNoticeVisible = true
                        dealNext(configured.nextSuitCount)
                    }
                    is SpiderActiveGameLoadResult.Missing -> dealNext(configured.nextSuitCount)
                }
                isLoading = false
                syncTimer()
            }
        }
    }

    /**
     * Cards currently lit by Hint, empty when no hint is showing.
     *
     * Cleared by the next committed move: a hint describes the board it was asked about, and
     * leaving it lit over a board that has since changed points at the wrong cards.
     */
    var hintedCards by mutableStateOf<Set<HintedCard>>(emptySet())
        private set

    /**
     * Set when Hint was asked and found no tableau card with anywhere to go — the board's own cue
     * to deal a fresh row is the stock, so that is what lights instead. Cleared everywhere
     * [hintedCards] is, and by the same events.
     */
    var hintedStock by mutableStateOf(false)
        private set

    /**
     * Hints taken this game, for [record] to carry onto the history entry — reset whenever a
     * fresh board is dealt (`restart`, `newGame`, `loadFixtureForDebugging`), never by undo: a
     * hint once taken stays taken regardless of how the game plays out from there, the same way
     * Klondike's own `hintsUsedThisGame` does.
     */
    var hintsUsedThisGame by mutableIntStateOf(0)
        private set

    /**
     * What the solver-backed hint mode is doing right now; see [HintUiState]. Stays [HintUiState.Hidden]
     * for the whole life of a highlight-only hint (`showHint`'s other branch, below) — this only
     * exists for the loading/notice lifecycle a live search needs.
     */
    var hintState by mutableStateOf<HintUiState>(HintUiState.Hidden)
        private set

    /**
     * True once a running [HintUiState.Loading] search has taken long enough to show
     * [org.finiteplay.core.ui.layout.HintProgressDialog] — most hints resolve well under
     * [HINT_PROGRESS_DIALOG_DELAY], so this stays false for them.
     */
    var hintShowsProgressDialog by mutableStateOf(false)
        private set

    private var hintJob: Job? = null

    /** Owns the cached winning line successive hints resolve against (`HintEngine`'s own doc on why
     * this matters); reset alongside every fresh deal so it never carries a cached line from the
     * game before into a board it no longer describes. */
    private var hintEngine = hintEngineFactory(HintTimeout.DEFAULT)

    /** The [HintTimeout] [hintEngine] is currently built for; rebuilt in [requestGuidedHint] on change. */
    private var hintEngineTimeout = HintTimeout.DEFAULT

    /**
     * Lights every column's topmost movable card (`Hints.kt`), or the stock when none has anywhere
     * to go — used whenever [SpiderSettings.hintShowsWinningMove] is off, or the guided mode below
     * is not offered for the current deal. Never counted in [hintsUsedThisGame]: it lists what is
     * legal without pointing at a winning line, so it is not help the statistic should measure.
     * Tapping again while a hint is showing (cards or stock) dismisses it, so Hint doubles as its
     * own dismiss button.
     *
     * The guided mode is offered at [SuitCount.ONE] unconditionally — [HintEngine]'s own live
     * search solves it in milliseconds even uncatalogued — and at any other suit count only when
     * [dealIsCertified], so a live search is never the only path there: [requestGuidedHint]'s
     * [hintEngine] resolves a certified deal from [SpiderSolutionCatalog]'s primed cache instead of
     * searching (`docs/games/spider/DEALS.md` "Solutions"). A live four-suit search essentially never
     * succeeds (which is why FOUR is guided only from its certified catalog), and whether an uncertified two-suit deal should fall back on the live search — which
     * now wins most two-suit boards (`DESIGN.md` "Hint") — is still an open product decision, so
     * this never routes an uncertified deal at either count into that search. An uncertified
     * deal (one dealt without a catalog, as in tests) stays on the plain highlight, the only thing a live search there could
     * offer anyway.
     */
    fun showHint() {
        val guidedModeOffered = session.state.suitCount == SuitCount.ONE || dealIsCertified
        // A game with no move behind it follows the deal's certified path whatever the setting says.
        val followsCertifiedPath = session.state.moveCount == 0 && solutionMoveCount > 0
        if ((settings.hintShowsWinningMove || followsCertifiedPath) && guidedModeOffered) {
            // The fallback highlight below is on screen: this tap puts it away, as in the plain mode.
            // A search on the same board would only time out the same way; the next move's Hint searches.
            if (hintFallbackShowing) {
                dismissHint()
                return
            }
            requestGuidedHint()
            return
        }
        if (hintedCards.isNotEmpty() || hintedStock) {
            dismissHint()
            return
        }
        val next = hintedCards(session.state)
        hintedCards = next
        hintedStock = next.isEmpty()
    }

    /**
     * True while the plain highlight of every movable card is up because a guided search timed out.
     * The player's mode is untouched, so the next Hint after a move searches for a winning line again.
     */
    private var hintFallbackShowing = false

    /**
     * Runs [hintEngine] off the main thread and shows what it finds. A no-op while already loading
     * or showing a result, the same gate Klondike's and FreeCell's own `requestHint` use — repeating
     * the request has nothing new to show, since the engine resolves to the next step of a cached
     * winning line rather than a ranked list to page through.
     */
    private fun requestGuidedHint() {
        if (hintState != HintUiState.Hidden) return
        if (settings.hintTimeout != hintEngineTimeout) {
            hintEngineTimeout = settings.hintTimeout
            hintEngine = hintEngineFactory(hintEngineTimeout)
        }
        hintState = HintUiState.Loading
        val requestedState = session.state
        hintJob = viewModelScope.launch {
            launch {
                delay(HINT_PROGRESS_DIALOG_DELAY)
                if (hintState == HintUiState.Loading) hintShowsProgressDialog = true
            }
            val hintStarted = System.nanoTime()
            val outcome = withContext(Dispatchers.Default) { hintEngine.hint(requestedState) }
            if (session.state != requestedState || hintState != HintUiState.Loading) return@launch
            hintShowsProgressDialog = false
            moveRecorder?.onHint(gameId, session, outcome, (System.nanoTime() - hintStarted) / 1_000_000)
            val shown = when (outcome) {
                is HintOutcome.Guidance -> HintUiState.Guided(outcome.move)
                HintOutcome.NoSolution -> HintUiState.NoSolution
                HintOutcome.Inconclusive -> HintUiState.Inconclusive
            }
            hintState = shown
            if (shown is HintUiState.Guided) {
                hintsUsedThisGame++
                when (val move = shown.move) {
                    Move.DealRow -> {
                        hintedCards = emptySet()
                        hintedStock = true
                    }
                    is Move.TableauToTableau -> {
                        hintedCards = setOf(HintedCard(move.fromColumn, move.fromIndex))
                        hintedStock = false
                    }
                }
            }
            if (shown is HintUiState.Inconclusive) {
                // No winning line in the time allowed, but the player still gets what the plain mode
                // gives: every card that has somewhere to go, not counted as a hint taken.
                val movable = hintedCards(session.state)
                hintedCards = movable
                hintedStock = movable.isEmpty()
                hintFallbackShowing = true
            }
            if (shown is HintUiState.NoSolution || shown is HintUiState.Inconclusive) {
                launch {
                    delay(HINT_NOTICE_AUTO_DISMISS)
                    // Only if nothing else (Dismiss, a move, undo, a new game) already moved on.
                    if (hintState == shown) hintState = HintUiState.Hidden
                }
            }
        }
    }

    /** Dismisses the current hint (loading, guidance, a notice, or a plain highlight) without waiting for it. */
    fun dismissHint() {
        hintJob?.cancel()
        hintJob = null
        hintState = HintUiState.Hidden
        hintShowsProgressDialog = false
        hintedCards = emptySet()
        hintedStock = false
        hintFallbackShowing = false
    }

    fun dismissRecoveryNotice() {
        recoveryNoticeVisible = false
    }

    /**
     * Seconds of play, on `core:session`'s timer rule: it runs only once the player has moved,
     * and stops on a win, in the background, or behind a modal (`docs/PLATFORM.md` "Persistence"
     * — a running timer is never persisted, only this count).
     */
    var elapsedSeconds by mutableIntStateOf(0)
        private set

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
     * True for the whole automatic finish, not just between its own commits — the board
     * (`SpiderBoard.kt`) also holds input disabled on this rather than solely on whether a flight
     * is actively playing right now, so a fast tap landing in the brief gap between one commit's
     * flight finishing and the next one starting cannot slip a move of its own into the middle of
     * a sequence [findAutoFinish] computed against a board that move would have already changed.
     */
    var isAutoFinishing by mutableStateOf(false)
        private set

    /**
     * Set for one composition after a row deal is refused for having an empty column
     * (`DESIGN.md` "Interaction"), cleared by [dismissDealRowRefused].
     */
    var dealRowRefused by mutableStateOf(false)
        private set

    /**
     * The column a tap or drag just failed to find any legal destination for, briefly
     * highlighted and sounded by the board — Klondike's own `invalidFeedback`/`markInvalid`,
     * adapted to Spider's single move type. Cleared by [dismissInvalidFeedback].
     */
    var invalidFeedbackColumn by mutableStateOf<Int?>(null)
        private set

    fun dismissInvalidFeedback() {
        invalidFeedbackColumn = null
    }

    /** A drag that found no column to drop on at all (released off the tableau row entirely). */
    fun markDragInvalid(fromColumn: Int) {
        invalidFeedbackColumn = fromColumn
    }

    /**
     * What the move behind the current [session] value banked, if anything — read by the board's
     * own animation trigger (`SpiderBoard.kt`) each time [session] changes. Every place [session]
     * is reassigned sets this in the same statement, to empty unless it is [commit] recording a
     * real bank, specifically so a stale value from an earlier interactive move can never be
     * misread as belonging to an unrelated change — undo, restore, restart, a fresh deal — that
     * happens to leave the board at a state shaped like one.
     */
    var lastBankedRuns: List<BankedRun> = emptyList()
        private set

    /** One [Move.TableauToTableau]'s own cards, bottom of the run first — the board's flight animation. */
    data class MovedSequence(val fromColumn: Int, val toColumn: Int, val cards: List<Card>)

    /**
     * What the move behind the current [session] value relocated within the tableau, if anything —
     * read by the board's own animation trigger (`SpiderBoard.kt`) exactly like [lastBankedRuns],
     * and reset alongside it everywhere [session] is reassigned. Null whenever that move also
     * banked a run: the bank flight already shows those cards leaving, so this stays unset rather
     * than adding a second, redundant pre-step ahead of it (`SpiderBoard.kt`'s own scope note).
     */
    var lastMovedSequence: MovedSequence? = null
        private set

    /** One [Move.DealRow]'s own cards, indexed by the column each landed on — the board's flight animation. */
    data class DealtRow(val cards: List<Card>)

    /**
     * What the move behind the current [session] value dealt from the stock, if anything — read
     * and reset exactly like [lastMovedSequence]. Unlike a tableau move, a deal is never fully
     * suppressed for having banked something: banking is per column here, so [SpiderBoard.kt]
     * animates every column the deal reached except whichever one(s) [lastBankedRuns] names, the
     * same "let the bank flight cover it instead" rule applied per column rather than to the
     * whole move.
     */
    var lastDealtRow: DealtRow? = null
        private set

    /**
     * Whether the move behind the current [session] value was one of [runAutoFinishIfAvailable]'s
     * own commits rather than the player's — read by the screen's sound trigger (`GameScreen.kt`)
     * to select the quieter automatic-transfer cue.
     */
    var lastMoveWasAutomatic: Boolean = false
        private set

    /**
     * Tapping the exact card a shown guided hint currently highlights commits the hint's own
     * move instead of [resolveTap]'s standard pick, since the two can disagree — the same
     * override Klondike's and FreeCell's own boards give their tap handlers
     * (`docs/games/klondike/DESIGN.md` "Interaction").
     */
    fun tapCard(column: Int, index: Int) {
        val guidedHint = (hintState as? HintUiState.Guided)?.move as? Move.TableauToTableau
        val move = if (guidedHint != null && guidedHint.fromColumn == column && guidedHint.fromIndex == index) {
            guidedHint
        } else {
            resolveTap(session.state, column, index) ?: run {
                invalidFeedbackColumn = column
                return
            }
        }
        commit(move)
        afterCommit(autoFinish = true)
    }

    /** Applies [move] through the ordinary reducer, recording what it banked, moved, and dealt for the board to animate. */
    private fun commit(move: Move, automatic: Boolean = false) {
        val preMoveState = session.state
        val bankedRuns = applyMoveDetailed(preMoveState, move).bankedRuns
        val shownHint = (hintState as? HintUiState.Guided)?.move?.takeUnless { automatic }
        session = session.commitMove(move)
        moveRecorder?.onCommitted(gameId, session, shownHint)
        lastBankedRuns = bankedRuns
        lastMovedSequence = if (move is Move.TableauToTableau && bankedRuns.isEmpty()) {
            val column = preMoveState.tableau[move.fromColumn]
            MovedSequence(move.fromColumn, move.toColumn, column.subList(move.fromIndex, column.size).map { it.card })
        } else {
            null
        }
        lastDealtRow = if (move is Move.DealRow) DealtRow(preMoveState.stock.take(TABLEAU_COLUMNS)) else null
        lastMoveWasAutomatic = automatic
    }

    /**
     * Plays out the last sequence once nothing is left to decide (`RULES.md` "The automatic
     * finish").
     *
     * The search runs off the main thread. It is the only expensive computation this screen has,
     * it runs after every move once the endgame is close, and on a position it cannot solve it
     * grinds to its node ceiling — measured at 767 ms on a desktop JVM before the ceiling was
     * lowered, which is a multi-second freeze on a phone, arriving exactly when a player is about
     * to win. Doing it inline was the bug.
     *
     * The board is re-checked before the moves are applied: the player can act while the search
     * runs, and a finish computed for a board that has since changed is not a finish.
     *
     * Runs only after a player's own move, never after a restore — a restored game replays its
     * log, and finishing it here would append moves the save does not have.
     *
     * Each move commits through the same [commit] a player's own tap or drag does, so the board's
     * usual flight animation plays for every one of them exactly as if the player had played it —
     * with a pause standing in for the flight afterward, since this coroutine has no way to know
     * how long the board's own geometry will actually make that flight take. Skip Animations
     * turns that pause off, the same as it would for a played-out move, so the sweep still
     * resolves in one jump when the player has asked not to watch slides at all
     * (`docs/games/spider/UI_SPEC.md` "Motion"). [isAutoFinishing] holds input disabled through
     * every pause between commits, not just while a flight is actually in the air, so a fast tap
     * landing in one of those gaps can never insert a move of its own into a sequence
     * [findAutoFinish] computed against a board that move would have already changed.
     */
    private fun runAutoFinishIfAvailable() {
        if (!isAutoFinishAvailable(session.state)) return
        if (autoFinishJob?.isActive == true) return
        val snapshot = session.state
        autoFinishJob = viewModelScope.launch {
            val moves = withContext(Dispatchers.Default) { findAutoFinish(snapshot) } ?: return@launch
            if (session.state != snapshot) return@launch
            isAutoFinishing = true
            try {
                for (move in moves) {
                    commit(move, automatic = true)
                    if (settings.animationsEnabled) {
                        delay(if (lastBankedRuns.isNotEmpty()) AUTO_FINISH_BANK_STEP_DELAY_MS else AUTO_FINISH_MOVE_STEP_DELAY_MS)
                    }
                }
            } finally {
                isAutoFinishing = false
            }
            hintedCards = emptySet()
            hintedStock = false
            recordWinIfFinished()
            syncTimer()
            scheduleSave()
        }
    }

    fun dragMove(fromColumn: Int, fromIndex: Int, toColumn: Int): Boolean {
        val move = Move.TableauToTableau(fromColumn, fromIndex, toColumn)
        if (!isLegal(session.state, move)) {
            invalidFeedbackColumn = fromColumn
            return false
        }
        commit(move)
        afterCommit(autoFinish = true)
        return true
    }

    fun tapStock() {
        if (!canDealRow(session.state)) {
            dealRowRefused = true
            return
        }
        commit(Move.DealRow)
        afterCommit(autoFinish = true)
    }

    fun dismissDealRowRefused() {
        dealRowRefused = false
    }

    fun undo() {
        session = session.undo()
        lastBankedRuns = emptyList()
        lastMovedSequence = null
        lastDealtRow = null
        afterCommit()
    }

    /** The suit count the *next* New Game deals at; the current deal's own count never changes. */
    val nextSuitCount: SuitCount get() = settings.nextSuitCount

    private var pendingSuitCount: SuitCount? = null

    /**
     * Switching away from a game with no move behind it gives its deal back to its suit count's
     * sequence, so switching there and back finds the same hand rather than burning one each time.
     * Plain New Game does not: it is a request for a different deal.
     */
    private fun returnUnplayedDeal() {
        val traversal = traversalStore ?: return
        if (session.state.moveCount > 0) return
        val suitCount = session.state.suitCount
        val number = dealNumber
        val wrapAt = certifiedCatalog?.seedsFor(suitCount)?.size
        viewModelScope.launch { traversal.giveBack(suitCount, number, wrapAt) }
    }

    /**
     * Settings' suit-count choice: picking a different count than the game in play starts a new game
     * at it, asking first — as New Game does — when the game on screen has been played and is
     * unfinished, since leaving it records a loss. Picking the count already in play only records it
     * as the choice for later deals.
     */
    fun requestSuitCount(suitCount: SuitCount) {
        if (suitCount == session.state.suitCount) {
            pickNextSuitCount(suitCount)
        } else if (needsConfirmation()) {
            pendingSuitCount = suitCount
            pendingAction = DiscardingAction.NEW_GAME
        } else {
            setSuitCount(suitCount)
        }
    }

    fun pickNextSuitCount(suitCount: SuitCount) {
        if (settingsStore == null) {
            settings = settings.copy(nextSuitCount = suitCount)
            return
        }
        viewModelScope.launch { settingsStore.setNextSuitCount(suitCount) }
    }

    /**
     * Changes the suit count and deals a new game at it immediately — the status row's own
     * picker (`docs/games/spider/UI_SPEC.md` "Status Row"), reached specifically to switch what's
     * being played right now rather than to plan the next one. [pickNextSuitCount] persists the
     * choice the same way Settings' own selector does, so it also sticks for every deal after
     * this one; [newGame] does the actual redeal, recording a loss first exactly as it would for
     * any other forfeited deal ([recordLossIfAbandoned]'s own doc explains why).
     *
     * Selecting the suit count already in force is a no-op, the same guard Klondike's own
     * `setDifficulty` uses, so the picker cannot double as a way to skip a hand for free.
     */
    fun setSuitCount(suitCount: SuitCount) {
        if (suitCount == session.state.suitCount) return
        returnUnplayedDeal()
        pickNextSuitCount(suitCount)
        // Dealt at the count chosen, not at the persisted setting: the write has not landed yet, so
        // reading it back would deal at the old count and need the choice made twice.
        newGame(suitCount)
    }

    fun setAnimationsEnabled(value: Boolean) = updateSettings({ it.copy(animationsEnabled = value) }) { it.setAnimationsEnabled(value) }
    /** Applies immediately and persists; does not itself change what a hint already on screen shows. */
    fun setHintShowsWinningMove(value: Boolean) = updateSettings({ it.copy(hintShowsWinningMove = value) }) { it.setHintShowsWinningMove(value) }
    fun setHintTimeout(value: HintTimeout) = updateSettings({ it.copy(hintTimeout = value) }) { it.setHintTimeout(value) }

    /** Applies immediately and persists; restarts the current window so a new choice takes effect right away. */
    fun setRestReminderInterval(value: RestReminderInterval) {
        updateSettings({ it.copy(restReminderInterval = value) }) { it.setRestReminderInterval(value) }
        val now = System.currentTimeMillis()
        restReminderWindow = (restReminderWindow ?: RestReminderWindow.start(now))
            .afterIntervalChange(now, value.minutes?.let { it * 60_000L })
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
     * and the collector above brings the change back.
     */
    private fun updateSettings(
        inMemory: (SpiderSettings) -> SpiderSettings,
        persist: suspend (SpiderSettingsStore) -> Unit,
    ) {
        val store = settingsStore
        if (store == null) {
            settings = inMemory(settings)
            return
        }
        viewModelScope.launch { persist(store) }
    }

    /**
     * The action waiting on the player's confirmation, or null. Set only when confirming is
     * actually warranted — a game with no moves behind it, or one already won, has nothing to
     * lose and is replaced straight away (`docs/PLATFORM.md`).
     */
    var pendingAction by mutableStateOf<DiscardingAction?>(null)
        private set

    private fun needsConfirmation(): Boolean =
        session.state.moveCount > 0 && session.state.status != GameStatus.WON

    /** Whether [setSuitCount] would forfeit the game on screen, for the picker's own warning text. */
    val suitCountSwitchWouldForfeit: Boolean get() = needsConfirmation()

    /**
     * New Game deals straight away at the configured suit count rather than asking for one first:
     * the count is a setting, and being asked it before every single deal is a question with the
     * same answer nearly every time. Changing it is Settings' job (`EXECUTION_PLAN.md` S3c).
     */
    fun requestNewGame() {
        if (needsConfirmation()) pendingAction = DiscardingAction.NEW_GAME else newGame()
    }

    /** Restart: the same deal again from the start, at the count it was dealt at. */
    fun requestRestart() {
        if (needsConfirmation()) pendingAction = DiscardingAction.REPLAY else restart()
    }

    fun confirmPendingAction() {
        val action = pendingAction ?: return
        pendingAction = null
        val deal = pendingDealNumber
        pendingDealNumber = null
        val suits = pendingSuitCount
        pendingSuitCount = null
        when (action) {
            DiscardingAction.NEW_GAME -> when {
                deal != null -> selectDeal(deal)
                suits != null -> setSuitCount(suits)
                else -> newGame()
            }
            DiscardingAction.REPLAY -> restart()
        }
    }

    fun dismissPendingAction() {
        pendingAction = null
        pendingDealNumber = null
        pendingSuitCount = null
    }

    /**
     * Re-deals the game in progress from its own seed.
     *
     * Deliberately the *current deal's* suit count rather than [nextSuitCount]: a restart has to
     * reproduce the board exactly, even when the player has since changed the setting that governs
     * the next new game. Taking the setting here would silently hand back a different game under
     * the name "restart".
     */
    fun restart() {
        val state = session.state
        session = SpiderSession.start(seed = state.seed, versions = state.versions, suitCount = state.suitCount)
        lastBankedRuns = emptyList()
        lastMovedSequence = null
        lastDealtRow = null
        hintsUsedThisGame = 0
        hintEngine.reset()
        primeHintEngineWithStoredSolution(session.state)
        currentGameRecorded = false
        dealRowRefused = false
        elapsedSeconds = 0
        gameId = newGameId()
        afterCommit()
    }

    fun newGame(suitCount: SuitCount = nextSuitCount) {
        recordLossIfAbandoned()
        dealNext(suitCount)
        currentGameRecorded = false
        dealRowRefused = false
        elapsedSeconds = 0
        gameId = newGameId()
        afterCommit()
    }

    /**
     * The length of the certified line shipped for this game's seed, or 0 where none is (a game dealt
     * without a catalog). Recorded on the history record when the game ends, rather than
     * derived later, because the catalog can be regenerated under a player's feet.
     */
    var solutionMoveCount by mutableIntStateOf(0)
        internal set

    /**
     * Looks the shipped line's length up off the main thread — the first lookup parses the whole
     * solution index — against the raw deal rather than the board in front of the player, because
     * a stored line is written from the deal and a restored session is already past its start.
     */
    private fun lookUpSolutionLength(state: SpiderState) {
        solutionMoveCount = 0
        val catalog = solutionCatalog ?: return
        val seed = state.seed
        val suitCount = state.suitCount
        val versions = state.versions
        viewModelScope.launch {
            val moves = withContext(Dispatchers.Default) {
                catalog.solutionFor(SpiderSession.start(seed = seed, versions = versions, suitCount = suitCount).state)?.size ?: 0
            }
            // A New Game may have replaced the session while this ran.
            if (session.state.seed == seed && session.state.suitCount == suitCount) solutionMoveCount = moves
        }
    }

    /**
     * Hands [hintEngine] the solution shipped with this seed, so a player who takes each hint and
     * plays it walks a known winning line with no search at all. Deviating from it costs nothing:
     * the board simply stops matching the line and the search resumes.
     *
     * Only meaningful from the raw deal — a restored mid-game session is already past the line's
     * start, and the engine's own state matching handles rejoining it — and a no-op when no
     * catalog is wired up (tests) or the seed is uncatalogued.
     */
    private fun primeHintEngineWithStoredSolution(freshState: SpiderState) {
        lookUpSolutionLength(freshState)
        val catalog = solutionCatalog ?: return
        if (freshState.moveCount > 0) return
        val solution = catalog.solutionFor(freshState) ?: return
        hintEngine.primeWithKnownSolution(freshState, solution)
    }

    /**
     * Replaces the board with [state] as a fresh, undo-empty session, bypassing the normal seeded
     * deal — for a debug-only fixture button (`src/debug`) and for tests that need a specific
     * board (an empty column, a game one move from won) that neither a fresh deal nor a short
     * sequence of real gestures reaches reliably. Harmless in a release build since no release
     * code path calls it; mirrors Klondike's own `loadFixtureForDebugging`.
     */
    fun loadFixtureForDebugging(state: SpiderState) {
        session = SpiderSession(Session.start(state))
        lastBankedRuns = emptyList()
        lastMovedSequence = null
        lastDealtRow = null
        hintsUsedThisGame = 0
        hintEngine.reset()
        currentGameRecorded = false
        dealRowRefused = false
        elapsedSeconds = 0
        gameId = newGameId()
        afterCommit()
    }

    /**
     * Test-only: shows [move] as the current guided hint without running the search, for a test
     * that needs a specific hint/board disagreement neither a real search nor a short sequence of
     * real gestures reaches reliably (`BoardHintTapOverrideTest`) — the same seam
     * [loadFixtureForDebugging] gives a specific board. Call after [loadFixtureForDebugging], not
     * before: that call's own [afterCommit] clears any hint already showing.
     */
    fun showHintForDebugging(move: Move.TableauToTableau) {
        hintState = HintUiState.Guided(move)
        hintedCards = setOf(HintedCard(move.fromColumn, move.fromIndex))
        hintedStock = false
    }

    /**
     * Saves asynchronously after every committed transition, and never blocks the caller — the
     * board is already showing the move by the time this runs.
     */
    /**
     * [autoFinish] is false for anything that is not the player advancing the game. Undo above all:
     * undoing back into a finishable position would otherwise finish it again immediately, so the
     * player could never step back through the finish at all.
     */
    private fun afterCommit(autoFinish: Boolean = false) {
        // Records undo, Restart, New Game and fixtures; commit() already recorded a move, and the
        // recorder writes only entries it has not seen.
        moveRecorder?.onCommitted(gameId, session, shownHint = null)
        dismissHint()
        if (autoFinish) runAutoFinishIfAvailable()
        recordWinIfFinished()
        syncTimer()
        scheduleSave()
    }

    private fun scheduleSave() {
        syncDealProgress()
        val store = store ?: return
        if (isLoading) return
        val snapshot = session
        val elapsed = elapsedSeconds
        val id = gameId
        val number = dealNumber
        viewModelScope.launch {
            store.save(snapshot, elapsed.seconds, id, number)
        }
    }

    /**
     * The seed for deal [number] at [suitCount]: the certified catalog's [number]th seed when one
     * exists for this suit count, otherwise the uncertified formula. Which source applies is a
     * property of the suit count, not a per-call choice — every suit count takes
     * its catalog once one is bundled.
     */
    private fun seedFor(suitCount: SuitCount, number: Int): Long {
        val certified = certifiedCatalog?.seedsFor(suitCount)
        return if (certified != null) certified[(number - 1).mod(certified.size)] else DealSequence.seedFor(suitCount, number)
    }

    /**
     * Deals the next number in [suitCount]'s sequence, advancing the stored position.
     *
     * With no traversal store — the tests that do not exercise persistence — the position simply
     * does not advance, so every deal is number one and reproducible, which is what a test wants.
     *
     * Owns [hintEngine]'s reset-then-reprime pair for every fresh deal it makes, rather than
     * leaving it to callers: this function's own traversal-store branch deals asynchronously, so a
     * caller resetting the engine right after calling this would race a same-thread reset against
     * an as-yet-undealt board (or, worse, clobber a prime this function only gets to run later).
     */
    private fun dealNext(suitCount: SuitCount) {
        val traversal = traversalStore
        if (traversal == null) {
            dealNumber = DealSequence.FIRST
            session = newDeal(suitCount, seedFor(suitCount, dealNumber))
            lastBankedRuns = emptyList()
            lastMovedSequence = null
            lastDealtRow = null
            hintsUsedThisGame = 0
            hintEngine.reset()
            primeHintEngineWithStoredSolution(session.state)
            return
        }
        val wrapAt = certifiedCatalog?.seedsFor(suitCount)?.size
        viewModelScope.launch {
            val number = traversal.next(suitCount, wrapAt)
            dealNumber = number
            session = newDeal(suitCount, seedFor(suitCount, number))
            lastBankedRuns = emptyList()
            lastMovedSequence = null
            lastDealtRow = null
            hintsUsedThisGame = 0
            hintEngine.reset()
            primeHintEngineWithStoredSolution(session.state)
            moveRecorder?.onCommitted(gameId, session, shownHint = null)
            traversal.advancePast(suitCount, number, wrapAt)
            syncTimer()
            scheduleSave()
        }
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
            hasPlayerActed = session.state.moveCount > 0,
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

    private companion object {
        /** No catalog yet, so nothing dealt this way is certified (`TODO.md`). */
        fun newDeal(suitCount: SuitCount, seed: Long): SpiderSession =
            SpiderSession.start(seed = seed, versions = SPIDER_VERSIONS, suitCount = suitCount)

        /** Identifies one dealt game across saves, so a restore can tell it apart from a redeal. */
        fun newGameId(): String = java.util.UUID.randomUUID().toString()
    }
}
