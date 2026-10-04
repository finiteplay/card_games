package org.finiteplay.klondike.ui.game

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.finiteplay.klondike.deal.DealSeedSource
import org.finiteplay.klondike.deal.DifficultyTier
import org.finiteplay.klondike.deal.INTERIM_SEED_GRADES
import org.finiteplay.klondike.deal.INTERIM_SOLVABLE_SEEDS
import org.finiteplay.klondike.deal.seedsFor
import org.finiteplay.klondike.storage.DifficultyPreference
import org.finiteplay.klondike.debug.deadlockedGameState
import org.finiteplay.klondike.debug.nearWinGameState
import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.storage.ActiveGameStore
import org.finiteplay.klondike.storage.CatalogTraversalStore
import org.finiteplay.klondike.storage.FakeDataStores
import org.finiteplay.klondike.storage.HistoryRecord
import org.finiteplay.klondike.storage.HistoryStore
import org.finiteplay.klondike.storage.Outcome
import org.finiteplay.klondike.storage.SettingsStore
import org.finiteplay.klondike.storage.StatisticsPeriod
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * `EXECUTION_PLAN.md` A1 gate: "Invalid actions preserve state and score." Both moves
 * used here are illegal for any dealt board by construction (see comments), so the
 * assertions hold regardless of the placeholder seed drawn for [GameViewModel].
 *
 * Uses [FakeDataStores] (an in-memory `DataStore` substitute already built for S1's own
 * tests) rather than real file-backed stores: its flows never truly suspend across
 * threads, so under [UnconfinedTestDispatcher] the ViewModel's `init`-time restore and
 * every save complete inline before the next line of test code runs — no sleeps, no
 * explicit awaiting, and no risk of a background restore clobbering a test's own
 * mutations after the fact.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private lateinit var storageDir: File

    // Held onto directly (rather than reading it back off Dispatchers.Main, which wraps
    // it) so tests can advance virtual time for the hint notice's auto-dismiss delay.
    private val testScheduler = TestCoroutineScheduler()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        storageDir = Files.createTempDirectory("game-viewmodel-test").toFile()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        storageDir.deleteRecursively()
    }

    private fun newViewModel(): GameViewModel = GameViewModel(
        activeGameStore = ActiveGameStore(storageDir, dataStoreFactory = FakeDataStores::create),
        settingsStore = SettingsStore(storageDir, dataStoreFactory = FakeDataStores::create),
        historyStore = HistoryStore(storageDir, dataStoreFactory = FakeDataStores::create),
        // The real default (Dispatchers.Default) is a genuine background thread pool, so a
        // hint request would still be mid-flight when the test's next line runs. Dispatchers.Main
        // is the UnconfinedTestDispatcher installed in setUp(), which resolves it inline instead.
        hintDispatcher = Dispatchers.Main,
    )

    private fun history() = HistoryStore(storageDir, dataStoreFactory = FakeDataStores::create)

    @Test
    fun illegalTableauMoveIsRejectedAndSessionIsUnchanged() {
        val viewModel = newViewModel()
        val before = viewModel.session

        // A tableau column can never legally receive its own run: legalMoves() always
        // skips toColumn == fromColumn.
        val committed = viewModel.tryCommitMove(Move.TableauToTableau(fromColumn = 0, fromIndex = 0, toColumn = 0))

        assertFalse(committed)
        assertEquals(before, viewModel.session)
    }

    @Test
    fun illegalWasteMoveOnFreshDealIsRejectedAndSessionIsUnchanged() {
        val viewModel = newViewModel()
        val before = viewModel.session

        // The waste pile is always empty immediately after a deal.
        val committed = viewModel.tryCommitMove(Move.WasteToFoundation)

        assertFalse(committed)
        assertEquals(before, viewModel.session)
    }

    @Test
    fun undoOnFreshDealIsANoOp() {
        val viewModel = newViewModel()
        val before = viewModel.session

        viewModel.undo()

        assertEquals(before, viewModel.session)
    }

    @Test
    fun newGameOnAFreshDealDoesNotAskForConfirmation() {
        val viewModel = newViewModel()

        viewModel.requestNewGame()

        assertEquals(null, viewModel.pendingConfirmation)
    }

    @Test
    fun newGameAfterAPlayerActionAsksForConfirmationFirst() {
        val viewModel = newViewModel()
        // Draw is always legal on a freshly dealt board: the stock always has cards.
        viewModel.tryCommitMove(Move.Draw)
        val beforeConfirmation = viewModel.session

        viewModel.requestNewGame()

        assertEquals(PendingConfirmation.NEW_GAME, viewModel.pendingConfirmation)
        assertEquals(beforeConfirmation, viewModel.session)
    }

    @Test
    fun cancellingAPendingActionLeavesTheSessionUnchanged() {
        val viewModel = newViewModel()
        viewModel.tryCommitMove(Move.Draw)
        val before = viewModel.session
        viewModel.requestNewGame()

        viewModel.cancelPendingAction()

        assertEquals(null, viewModel.pendingConfirmation)
        assertEquals(before, viewModel.session)
    }

    @Test
    fun confirmingNewGameStartsADifferentSessionAndClearsPending() {
        val viewModel = newViewModel()
        viewModel.tryCommitMove(Move.Draw)
        val before = viewModel.session
        viewModel.requestNewGame()

        viewModel.confirmPendingAction()

        assertEquals(null, viewModel.pendingConfirmation)
        assertFalse(viewModel.session == before)
    }

    @Test
    fun replayReproducesTheOriginalDealAfterConfirmation() {
        val viewModel = newViewModel()
        val originalDeal = viewModel.session.state
        viewModel.tryCommitMove(Move.Draw)

        viewModel.requestReplay()
        viewModel.confirmPendingAction()

        assertEquals(originalDeal, viewModel.session.state)
        assertEquals(null, viewModel.pendingConfirmation)
    }

    @Test
    fun defaultDrawModeIsOneAndNewGameDealsUnderWhicheverDrawModeIsCurrentlyPersisted() {
        val viewModel = newViewModel()
        assertEquals(DrawMode.ONE, viewModel.persistedDrawMode)
        assertEquals(DrawMode.ONE, viewModel.session.state.drawMode)

        viewModel.setDrawMode(DrawMode.THREE)
        viewModel.requestNewGame() // a fresh, unplayed deal never needs confirmation

        assertEquals(DrawMode.THREE, viewModel.session.state.drawMode)
    }

    @Test
    fun replayPreservesTheGamesOwnDrawModeEvenAfterTheSettingChangesSinceItWasDealt() {
        val viewModel = newViewModel()
        check(viewModel.session.state.drawMode == DrawMode.ONE) // the deal this test replays

        viewModel.tryCommitMove(Move.Draw)
        viewModel.setDrawMode(DrawMode.THREE) // changes only what the *next* New Game will use
        viewModel.requestReplay()
        viewModel.confirmPendingAction()

        assertEquals(DrawMode.ONE, viewModel.session.state.drawMode)
        assertEquals(DrawMode.THREE, viewModel.persistedDrawMode)
    }

    @Test
    fun aDrawThreeDealUsesARandomSeedRatherThanTheDrawOneCertifiedInterimList() {
        val viewModel = newViewModel()
        viewModel.setDrawMode(DrawMode.THREE)

        viewModel.requestNewGame()

        // dealNumber looks the dealt seed up in INTERIM_SOLVABLE_SEEDS; a random 64-bit
        // seed lands there only astronomically rarely, so null is the expected, sound
        // signal that this deal is not from that (draw-one-only) certified list.
        assertEquals(null, viewModel.dealNumber)
    }

    /**
     * Solving a freshly dealt board from scratch is genuinely expensive — the same
     * reason the offline catalog pipeline budgets 15 s / 1.5M nodes per seed — so
     * these tests use the near-win debug fixture instead, where the search resolves
     * near-instantly and deterministically.
     */
    @Test
    fun hintIsHiddenUntilRequestedThenUnchangedByARepeatedRequest() {
        val viewModel = newViewModel()
        viewModel.loadFixtureForDebugging(nearWinGameState())
        assertEquals(null, viewModel.hint)

        viewModel.requestHint()
        val first = viewModel.hintState

        // The search returns the single next step of a proven line, not a list of
        // ranked candidates, so a repeated request while one is already showing has
        // nothing new to advance to.
        viewModel.requestHint()
        val second = viewModel.hintState

        assertEquals(true, first is HintUiState.Guided)
        assertEquals(first, second)
    }

    @Test
    fun `a restored game does not consume a seed from the sequence`() {
        // A fun-interface class, not a lambda, since the test needs to observe how
        // many times nextSeed() was actually called.
        class SequentialSeedSource(private val seeds: List<Long>) : DealSeedSource {
            var callCount = 0
                private set
            private var index = 0
            override fun nextSeed(): Long {
                callCount++
                return seeds[index++]
            }
        }
        val seedSource = SequentialSeedSource(listOf(11L, 22L, 33L))

        // Nothing saved yet: this construction deals fresh, consuming exactly one seed.
        val first = GameViewModel(
            activeGameStore = ActiveGameStore(storageDir, dataStoreFactory = FakeDataStores::create),
            settingsStore = SettingsStore(storageDir, dataStoreFactory = FakeDataStores::create),
            historyStore = HistoryStore(storageDir, dataStoreFactory = FakeDataStores::create),
            injectedSeedSource = seedSource,
            hintDispatcher = Dispatchers.Main,
        )
        assertEquals(11L, first.session.state.seed)
        assertEquals(1, seedSource.callCount)
        // A committed move on top of the already-persisted fresh deal (`startFreshDeal`
        // now saves immediately - see the cold-start persistence test below) - this
        // second save should not somehow cause a re-restored session to draw again.
        first.tryCommitMove(Move.Draw)

        // A second ViewModel over the same storage restores what `first` just saved —
        // it must not draw a placeholder seed at construction only to discard it.
        val second = GameViewModel(
            activeGameStore = ActiveGameStore(storageDir, dataStoreFactory = FakeDataStores::create),
            settingsStore = SettingsStore(storageDir, dataStoreFactory = FakeDataStores::create),
            historyStore = HistoryStore(storageDir, dataStoreFactory = FakeDataStores::create),
            injectedSeedSource = seedSource,
            hintDispatcher = Dispatchers.Main,
        )
        assertEquals(11L, second.session.state.seed)
        assertEquals(1, seedSource.callCount)
    }

    /**
     * A freshly dealt hand must survive a restart even before the player's first
     * action, not just once it's been played. Before this was fixed, `startFreshDeal`
     * left the deal unsaved until the player's first move while
     * `persistInterimSeedPositionIfNeeded` had already durably advanced the traversal
     * cursor the instant the seed was drawn - a kill-before-first-move restart would
     * silently draw the *next* hand instead of resuming the one actually on screen,
     * permanently skipping the hand the player never got to see.
     */
    @Test
    fun `a freshly dealt hand survives a restart even before the player's first action`() {
        val traversalDir = Files.createTempDirectory("catalog-traversal-cold-start-test").toFile()
        val traversalStore = CatalogTraversalStore(traversalDir, dataStoreFactory = FakeDataStores::create)
        val gameDir = Files.createTempDirectory("cold-start-game-test").toFile()

        val first = GameViewModel(
            activeGameStore = ActiveGameStore(gameDir, dataStoreFactory = FakeDataStores::create),
            settingsStore = SettingsStore(gameDir, dataStoreFactory = FakeDataStores::create),
            historyStore = HistoryStore(gameDir, dataStoreFactory = FakeDataStores::create),
            catalogTraversalStore = traversalStore,
            hintDispatcher = Dispatchers.Main,
        )
        val dealtSeed = first.session.state.seed
        // No player action taken - simulating the app being killed immediately after
        // the deal, before anything the player did would normally trigger a save.

        val second = GameViewModel(
            activeGameStore = ActiveGameStore(gameDir, dataStoreFactory = FakeDataStores::create),
            settingsStore = SettingsStore(gameDir, dataStoreFactory = FakeDataStores::create),
            historyStore = HistoryStore(gameDir, dataStoreFactory = FakeDataStores::create),
            catalogTraversalStore = traversalStore,
            hintDispatcher = Dispatchers.Main,
        )

        assertEquals(dealtSeed, second.session.state.seed)
    }

    /**
     * `docs/games/klondike/DESIGN.md` "Persistence": "Persist catalog version and traversal state so
     * new games do not repeat" — [InterimSolvableDealSeedSource]'s position must
     * survive a killed-and-restarted app, not just backgrounding, so New Game (from
     * either the action bar or the confirmation dialog — both funnel through
     * `performNewGame`) never redeals hand 1 on a device the player has already
     * played several hands on.
     */
    @Test
    fun `hand position survives a simulated restart instead of redealing hand 1`() {
        val traversalDir = Files.createTempDirectory("catalog-traversal-test").toFile()
        val traversalStore = CatalogTraversalStore(traversalDir, dataStoreFactory = FakeDataStores::create)
        // Pinned to one difficulty rather than the default Random: each level keeps its
        // own cursor, so under Random the hand number legitimately jumps between levels
        // and there is no single sequence to assert on.
        val settingsStore = SettingsStore(storageDir, dataStoreFactory = FakeDataStores::create)
        runBlocking { settingsStore.setDifficulty(DifficultyPreference.EASY) }

        val first = GameViewModel(
            activeGameStore = ActiveGameStore(storageDir, dataStoreFactory = FakeDataStores::create),
            settingsStore = settingsStore,
            historyStore = HistoryStore(storageDir, dataStoreFactory = FakeDataStores::create),
            catalogTraversalStore = traversalStore,
            hintDispatcher = Dispatchers.Main,
        )
        val easyCount = seedsFor(DifficultyTier.EASY).size
        val firstHand = requireNotNull(first.dealNumber)
        // Untouched fresh deal: no confirmation needed, so this reaches performNewGame directly.
        first.requestNewGame()
        val secondHand = requireNotNull(first.dealNumber)
        assertEquals((firstHand % easyCount) + 1, secondHand)

        // A brand-new GameViewModel over entirely different active-game/history storage
        // (simulating a killed-and-restarted app with no active save of its own) but the
        // same traversal and settings stores: it must continue the sequence, not restart
        // at hand 1, even though nothing about the active game itself was restored.
        val restartDir = Files.createTempDirectory("restart-test").toFile()
        val second = GameViewModel(
            activeGameStore = ActiveGameStore(restartDir, dataStoreFactory = FakeDataStores::create),
            settingsStore = settingsStore,
            historyStore = HistoryStore(restartDir, dataStoreFactory = FakeDataStores::create),
            catalogTraversalStore = traversalStore,
            hintDispatcher = Dispatchers.Main,
        )

        assertEquals((secondHand % easyCount) + 1, second.dealNumber)
    }

    @Test
    fun dismissHintHidesTheCurrentHintNotice() {
        val viewModel = newViewModel()
        viewModel.loadFixtureForDebugging(nearWinGameState())
        viewModel.requestHint()
        assertEquals(true, viewModel.hintState != HintUiState.Hidden)

        viewModel.dismissHint()

        assertEquals(HintUiState.Hidden, viewModel.hintState)
    }

    /**
     * With [GameViewModel.setHintShowsWinningMove] off, Hint highlights every legal move
     * instead of searching for a proven one — no [HintUiState] at all, and the toggle-on-
     * repeated-tap behavior Spider's own highlight hint already has (`docs/games/klondike/UI_SPEC.md`
     * "Hint").
     */
    @Test
    fun hintHighlightsEveryLegalMoveWhenSettingIsOff() {
        val viewModel = newViewModel()
        viewModel.loadFixtureForDebugging(nearWinGameState())
        viewModel.setHintShowsWinningMove(false)
        assertEquals(emptyList<Move>(), viewModel.legalMoveHighlights)

        viewModel.requestHint()
        val shown = viewModel.legalMoveHighlights
        assertEquals(true, shown.isNotEmpty())
        assertEquals(HintUiState.Hidden, viewModel.hintState)
        // Listing every legal move leads nowhere, so it is not a hint use.
        assertEquals(0, viewModel.hintsUsedThisGame)

        viewModel.requestHint()
        assertEquals(emptyList<Move>(), viewModel.legalMoveHighlights)
        assertEquals(0, viewModel.hintsUsedThisGame)
    }

    @Test
    fun committingAMoveHidesLegalMoveHighlightsToo() {
        val viewModel = newViewModel()
        viewModel.loadFixtureForDebugging(nearWinGameState())
        viewModel.setHintShowsWinningMove(false)
        viewModel.requestHint()
        assertEquals(true, viewModel.legalMoveHighlights.isNotEmpty())

        viewModel.tryCommitMove(Move.TableauToTableau(fromColumn = 0, fromIndex = 1, toColumn = 1))

        assertEquals(emptyList<Move>(), viewModel.legalMoveHighlights)
    }

    /**
     * `docs/games/klondike/UI_SPEC.md` "Hint": the No solution / Inconclusive notice is "a brief
     * dismissible notice" — brief meaning it disappears on its own, not only when the
     * player taps Dismiss. Uses [deadlockedGameState] for a fast, deterministic
     * [HintUiState.NoSolution]; the auto-dismiss logic in [GameViewModel.requestHint]
     * treats [HintUiState.Inconclusive] identically, so this one outcome exercises it.
     */
    @Test
    fun noSolutionHintNoticeAutoDismissesIfLeftAlone() {
        val viewModel = newViewModel()
        viewModel.loadFixtureForDebugging(deadlockedGameState())

        viewModel.requestHint()
        assertEquals(HintUiState.NoSolution, viewModel.hintState)

        testScheduler.advanceTimeBy(HINT_NOTICE_AUTO_DISMISS.inWholeMilliseconds / 2)
        assertEquals(HintUiState.NoSolution, viewModel.hintState)

        testScheduler.advanceTimeBy(HINT_NOTICE_AUTO_DISMISS.inWholeMilliseconds)
        testScheduler.runCurrent()
        assertEquals(HintUiState.Hidden, viewModel.hintState)
    }

    @Test
    fun committingAMoveHidesTheHintAgain() {
        val viewModel = newViewModel()
        viewModel.loadFixtureForDebugging(nearWinGameState())
        viewModel.requestHint()
        assertEquals(true, viewModel.hint != null)

        viewModel.tryCommitMove(Move.TableauToTableau(fromColumn = 0, fromIndex = 1, toColumn = 1))

        assertEquals(null, viewModel.hint)
    }

    @Test
    fun isNotLoadingOnceConstructionCompletes() {
        val viewModel = newViewModel()

        assertFalse(viewModel.isLoading)
    }

    @Test
    fun restoresAPreviouslySavedGameOnTheNextConstruction() {
        val first = newViewModel()
        first.tryCommitMove(Move.Draw)
        val savedState = first.session.state

        val second = newViewModel()

        assertEquals(savedState, second.session.state)
        assertFalse(second.recoveryNoticeVisible)
    }

    @Test
    fun aCorruptSaveIsRecoveredWithoutBlockingPlay() {
        FakeDataStores.corrupt(storageDir, "active_game")

        val viewModel = newViewModel()

        assertEquals(true, viewModel.recoveryNoticeVisible)
        // The game stays playable on a fresh deal (UI_SPEC.md "Recovery").
        assertEquals(true, viewModel.tryCommitMove(Move.Draw))
    }

    @Test
    fun winningRecordsExactlyOneHistoryEntry() {
        val viewModel = newViewModel()
        viewModel.loadFixtureForDebugging(nearWinGameState())

        // Moving the King off the Queen exposes the last face-down card; the automatic
        // finish takes it from there straight to a win.
        viewModel.tryCommitMove(Move.TableauToTableau(fromColumn = 0, fromIndex = 1, toColumn = 1))

        assertEquals(true, viewModel.session.state.isWon)
        val records = runBlocking { history().all() }
        assertEquals(1, records.size)
        assertEquals(Outcome.WIN, records.single().outcome)
    }

    @Test
    fun abandoningAnUnfinishedPlayedGameRecordsALoss() {
        val viewModel = newViewModel()
        viewModel.tryCommitMove(Move.Draw)

        viewModel.requestNewGame()
        viewModel.confirmPendingAction()

        val records = runBlocking { history().all() }
        assertEquals(1, records.size)
        assertEquals(Outcome.LOSS, records.single().outcome)
    }

    /**
     * Switching level forfeits the deal in play and records it, exactly as New Game does
     * (`DESIGN.md` "Statistics"). Deliberately *not* Replay's exemption: the picker would
     * otherwise be a way to walk away from a losing position for free.
     */
    @Test
    fun switchingLevelOnAPlayedGameRecordsALossAndDealsAgain() {
        val viewModel = newViewModel()
        viewModel.tryCommitMove(Move.Draw)
        val abandoned = viewModel.session.state.seed

        viewModel.setDifficulty(DifficultyPreference.HARD)

        val records = runBlocking { history().all() }
        assertEquals(1, records.size)
        assertEquals(Outcome.LOSS, records.single().outcome)
        assertNotEquals(abandoned, viewModel.session.state.seed)
        assertEquals(0, viewModel.session.state.moveCount)
    }

    /** A raw board has nothing to abandon, so the switch still redeals but records nothing. */
    @Test
    fun switchingLevelOnAnUntouchedDealRecordsNothing() {
        val viewModel = newViewModel()
        val before = viewModel.session.state.seed

        viewModel.setDifficulty(DifficultyPreference.HARD)

        assertEquals(emptyList<Outcome>(), runBlocking { history().all() }.map { it.outcome })
        assertNotEquals(before, viewModel.session.state.seed)
    }

    /** Re-picking the level already in force must not become a free reroll of the hand. */
    @Test
    fun selectingTheLevelAlreadyInForceChangesNothing() {
        val viewModel = newViewModel()
        viewModel.setDifficulty(DifficultyPreference.HARD)
        viewModel.tryCommitMove(Move.Draw)
        val seed = viewModel.session.state.seed
        val moves = viewModel.session.state.moveCount

        viewModel.setDifficulty(DifficultyPreference.HARD)

        assertEquals(seed, viewModel.session.state.seed)
        assertEquals(moves, viewModel.session.state.moveCount)
        assertEquals(emptyList<Outcome>(), runBlocking { history().all() }.map { it.outcome })
    }

    /** The replacement hand comes from the level just chosen, not the one being left. */
    @Test
    fun switchingLevelDealsFromTheNewLevel() {
        val viewModel = newViewModel()
        viewModel.setDifficulty(DifficultyPreference.HARD)

        assertEquals(DifficultyTier.HARD, viewModel.dealDifficulty)
        assertEquals(DifficultyPreference.HARD, viewModel.difficulty)
    }

    @Test
    fun replayingAnUnfinishedPlayedGameRecordsNoAbandonment() {
        val viewModel = newViewModel()
        viewModel.tryCommitMove(Move.Draw)

        viewModel.requestReplay()
        viewModel.confirmPendingAction()

        val records = runBlocking { history().all() }
        assertEquals(0, records.size)
    }

    @Test
    fun abandoningAReplayedGameRecordsNoLoss() {
        val viewModel = newViewModel()
        viewModel.tryCommitMove(Move.Draw)
        viewModel.requestReplay()
        viewModel.confirmPendingAction()

        // Neither the replayed game's own abandonment, nor anything before the
        // replay, should ever have been recorded (`DESIGN.md` "Statistics").
        viewModel.tryCommitMove(Move.Draw)
        viewModel.requestNewGame()
        viewModel.confirmPendingAction()

        val records = runBlocking { history().all() }
        assertEquals(0, records.size)
    }

    @Test
    fun startingAFreshGameWithoutHavingPlayedRecordsNoAbandonment() {
        val viewModel = newViewModel()

        viewModel.requestNewGame()

        val records = runBlocking { history().all() }
        assertEquals(0, records.size)
    }

    @Test
    fun automaticMovesSettingAppliesImmediatelyAndSurvivesRestart() {
        val first = newViewModel()

        first.setAutomaticMovesEnabled(false)

        assertEquals(false, first.session.automaticMovesEnabled)
        val second = newViewModel()
        assertEquals(false, second.session.automaticMovesEnabled)
    }

    @Test
    fun animationsEnabledSettingAppliesImmediatelyAndSurvivesRestart() {
        val first = newViewModel()
        assertTrue(first.persistedAnimationsEnabled)

        first.setAnimationsEnabled(false)

        assertEquals(false, first.persistedAnimationsEnabled)
        val second = newViewModel()
        assertEquals(false, second.persistedAnimationsEnabled)
    }

    @Test
    fun dealNumberIsWithinTheInterimSeedListOnAFreshDeal() {
        val viewModel = newViewModel()

        val dealNumber = viewModel.dealNumber

        assertEquals(true, dealNumber != null && dealNumber in 1..INTERIM_SOLVABLE_SEEDS.size)
    }

    @Test
    fun dealNumberIsNullForTheDebugFixtureWhichIsNotInTheInterimSeedList() {
        val viewModel = newViewModel()

        viewModel.loadFixtureForDebugging(nearWinGameState())

        assertEquals(null, viewModel.dealNumber)
    }

    @Test
    fun dealDifficultyMatchesTheCheckedInGradeForTheDealtSeed() {
        val viewModel = newViewModel()

        val expected = INTERIM_SEED_GRADES.getValue(viewModel.session.state.seed)

        assertEquals(expected, viewModel.dealDifficulty)
    }

    @Test
    fun dealDifficultyIsNullForTheDebugFixtureWhichIsNotInTheInterimSeedList() {
        val viewModel = newViewModel()

        viewModel.loadFixtureForDebugging(nearWinGameState())

        assertEquals(null, viewModel.dealDifficulty)
    }

    @Test
    fun dealDifficultyIsNullForADrawThreeDealWhichUsesARandomSeed() {
        val viewModel = newViewModel()
        viewModel.setDrawMode(DrawMode.THREE)

        viewModel.requestNewGame()

        assertEquals(null, viewModel.dealDifficulty)
    }

    @Test
    fun resetStatisticsClearsHistoryAndRefreshesTheExposedSnapshot() {
        val viewModel = newViewModel()
        runBlocking { history().upsert(HistoryRecord("g", "g:result", Outcome.WIN, 1000, 50, 1)) }
        viewModel.refreshStatistics()
        assertEquals(1, viewModel.statistics.wins)

        viewModel.resetStatistics()

        assertEquals(0, viewModel.statistics.wins)
        assertEquals(0, runBlocking { history().all() }.size)
    }

    @Test
    fun switchingStatisticsPeriodReFiltersAlreadyFetchedHistoryWithoutANewDiskRead() {
        val viewModel = newViewModel()
        // Timestamp 1 (near epoch) is always outside a WEEK/MONTH window from the real clock.
        runBlocking { history().upsert(HistoryRecord("g", "g:result", Outcome.WIN, 1000, 50, timestampMillis = 1L)) }
        viewModel.refreshStatistics()
        assertEquals(1, viewModel.statistics.wins)
        assertEquals(StatisticsPeriod.ALL_TIME, viewModel.currentStatisticsPeriod)

        viewModel.setStatisticsPeriod(StatisticsPeriod.WEEK)
        assertEquals(StatisticsPeriod.WEEK, viewModel.currentStatisticsPeriod)
        assertEquals(0, viewModel.statistics.wins)

        viewModel.setStatisticsPeriod(StatisticsPeriod.ALL_TIME)
        assertEquals(1, viewModel.statistics.wins)
    }

    @Test
    fun drawOneAndDrawThreeHistoryAreNeverBlendedInTheStatisticsView() {
        val viewModel = newViewModel()
        runBlocking {
            history().upsert(HistoryRecord("g1", "g1:result", Outcome.WIN, 1000, 50, 1, DrawMode.ONE))
            history().upsert(HistoryRecord("g2", "g2:result", Outcome.WIN, 1000, 50, 1, DrawMode.THREE))
            history().upsert(HistoryRecord("g3", "g3:result", Outcome.WIN, 1000, 50, 1, DrawMode.THREE))
        }
        viewModel.refreshStatistics()

        assertEquals(DrawMode.ONE, viewModel.currentStatisticsDrawMode)
        assertEquals(1, viewModel.statistics.wins)

        viewModel.setStatisticsDrawMode(DrawMode.THREE)

        assertEquals(2, viewModel.statistics.wins)
    }
}
