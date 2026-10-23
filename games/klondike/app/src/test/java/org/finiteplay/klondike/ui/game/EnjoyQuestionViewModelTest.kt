package org.finiteplay.klondike.ui.game

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.finiteplay.core.session.ENJOY_MIN_GOOD_RESULTS
import org.finiteplay.core.storage.EngagementStore
import org.finiteplay.core.storage.seedVeteran
import org.finiteplay.core.ui.feedback.EnjoyController
import org.finiteplay.klondike.debug.nearWinGameState
import org.finiteplay.klondike.deal.DealSeedSource
import org.finiteplay.klondike.deal.DifficultyTier
import org.finiteplay.klondike.deal.seedsFor
import org.finiteplay.klondike.storage.ActiveGameStore
import org.finiteplay.klondike.storage.FakeDataStores
import org.finiteplay.klondike.storage.HistoryStore
import org.finiteplay.klondike.storage.SettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Klondike's part of the "enjoying this game?" question (`docs/PLATFORM.md` "Feedback and rating"): a win is a good
 * result, and choosing New Game from the won board opens the question when the win made it due.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EnjoyQuestionViewModelTest {
    private lateinit var dir: File
    private lateinit var store: EngagementStore
    private lateinit var enjoy: EnjoyController
    private val seeds = seedsFor(DifficultyTier.EASY)
    private val day = TimeUnit.DAYS.toMillis(1)
    private var now = 1_000_000_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dir = Files.createTempDirectory("enjoy-klondike-test").toFile()
        store = EngagementStore(dir, dataStoreFactory = org.finiteplay.core.storage.FakeDataStores::create)
        // A player one good result short of the question: ten sessions over several days, two good results so far.
        runBlocking {
            now = store.seedVeteran(now, ENJOY_MIN_GOOD_RESULTS - 1)
        }
        enjoy = EnjoyController(store, CoroutineScope(UnconfinedTestDispatcher()), clock = { now })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        dir.deleteRecursively()
    }

    private fun viewModel() = GameViewModel(
        activeGameStore = ActiveGameStore(dir, dataStoreFactory = FakeDataStores::create),
        settingsStore = SettingsStore(dir, dataStoreFactory = FakeDataStores::create),
        historyStore = HistoryStore(dir, dataStoreFactory = FakeDataStores::create),
        injectedSeedSource = DealSeedSource { seeds[0] },
        hintDispatcher = Dispatchers.Main,
        enjoy = enjoy,
    )

    /** Plays a near-won board to the win. */
    private fun win(vm: GameViewModel) {
        vm.loadFixtureForDebugging(nearWinGameState().copy(seed = seeds[0]))
        var guard = 0
        while (!vm.session.state.isWon && guard++ < 20) {
            vm.requestHint()
            val guided = vm.hintState as HintUiState.Guided
            vm.tryCommitMove(guided.move)
            vm.dismissHint()
        }
        assertTrue(vm.session.state.isWon)
    }

    @Test
    fun `a win is a good result, and New Game from the won board opens the question`() {
        val vm = viewModel()
        win(vm)

        assertEquals(ENJOY_MIN_GOOD_RESULTS, runBlocking { store.current().goodResults })
        assertFalse("not over the win dialog", enjoy.visible)

        vm.requestNewGame()

        assertTrue(enjoy.visible)
    }

    @Test
    fun `New Game on a fresh board asks nothing`() {
        val vm = viewModel()

        vm.requestNewGame()

        assertFalse(enjoy.visible)
        assertEquals(ENJOY_MIN_GOOD_RESULTS - 1, runBlocking { store.current().goodResults })
    }
}
