package org.finiteplay.klondike.ui.game

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.finiteplay.core.storage.DealProgressStore
import org.finiteplay.core.storage.DealProgress
import org.finiteplay.core.storage.DealStatus
import org.finiteplay.klondike.debug.nearWinGameState
import org.finiteplay.klondike.deal.DealSeedSource
import org.finiteplay.klondike.deal.DifficultyTier
import org.finiteplay.klondike.deal.seedsFor
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.storage.ActiveGameStore
import org.finiteplay.klondike.storage.FakeDataStores
import org.finiteplay.klondike.storage.HistoryStore
import org.finiteplay.klondike.storage.SettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class DealPickerViewModelTest {
    private lateinit var dir: File
    private val tier = DifficultyTier.EASY
    private val seeds = seedsFor(tier)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dir = Files.createTempDirectory("deal-picker-test").toFile()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        dir.deleteRecursively()
    }

    private fun progressStore() = DealProgressStore(dir, dataStoreFactory = FakeDataStores::create)

    private fun viewModel(startAt: Int = 0) = GameViewModel(
        activeGameStore = ActiveGameStore(dir, dataStoreFactory = FakeDataStores::create),
        settingsStore = SettingsStore(dir, dataStoreFactory = FakeDataStores::create),
        historyStore = HistoryStore(dir, dataStoreFactory = FakeDataStores::create),
        injectedSeedSource = DealSeedSource { seeds[startAt] },
        hintDispatcher = Dispatchers.Main,
        dealProgressStore = progressStore(),
    )

    @Test
    fun `a deal nobody has touched has no progress`() {
        val vm = viewModel()
        assertNull(vm.dealProgress[seeds[0]])
        assertEquals(emptyMap<Int, DealProgress>(), vm.dealPickerProgress())
    }

    @Test
    fun `the first move marks the deal played, and it is remembered across a restart`() {
        val vm = viewModel()
        vm.tryCommitMove(Move.Draw)

        assertEquals(DealStatus.PLAYED, vm.dealProgress[seeds[0]]?.status)
        // The game last played keeps its moves beside "played".
        assertEquals(vm.session.state.moveCount, vm.dealProgress.getValue(seeds[0]).moves)
        assertEquals(mapOf(1 to vm.dealProgress.getValue(seeds[0])), vm.dealPickerProgress())
        assertEquals(DealStatus.PLAYED, runBlocking { progressStore().current() }[seeds[0]]?.status)
        assertEquals(vm.dealProgress.getValue(seeds[0]), viewModel().dealProgress[seeds[0]])
    }

    @Test
    fun `winning marks the deal won`() {
        val vm = viewModel()
        vm.loadFixtureForDebugging(nearWinGameState())
        val seed = vm.session.state.seed
        var guard = 0
        while (!vm.session.state.isWon && guard++ < 20) {
            vm.requestHint()
            val guided = vm.hintState as HintUiState.Guided
            vm.tryCommitMove(guided.move)
            vm.dismissHint()
        }

        assertEquals(true, vm.session.state.isWon)
        assertEquals(DealStatus.WON, vm.dealProgress[seed]?.status)
        assertEquals(vm.session.state.moveCount, vm.dealProgress.getValue(seed).moves)
    }

    @Test
    fun `picking a deal on an untouched board starts it without asking`() {
        val vm = viewModel(startAt = 0)
        vm.requestSelectDeal(3)

        assertEquals(seeds[2], vm.session.state.seed)
        assertEquals(3, vm.dealNumber)
        assertNull(vm.pendingConfirmation)
    }

    @Test
    fun `picking a deal while a played game is unfinished asks first, then replaces it`() {
        val vm = viewModel(startAt = 0)
        vm.tryCommitMove(Move.Draw)
        vm.requestSelectDeal(3)

        assertEquals(PendingConfirmation.SELECT_DEAL, vm.pendingConfirmation)
        assertEquals(seeds[0], vm.session.state.seed)

        vm.confirmPendingAction()

        assertEquals(seeds[2], vm.session.state.seed)
        assertNull(vm.pendingConfirmation)
    }

    @Test
    fun `cancelling the confirmation keeps the game and forgets the pick`() {
        val vm = viewModel(startAt = 0)
        vm.tryCommitMove(Move.Draw)
        vm.requestSelectDeal(3)
        vm.cancelPendingAction()
        vm.confirmPendingAction()

        assertEquals(seeds[0], vm.session.state.seed)
    }

    @Test
    fun `picking the deal already on screen does nothing`() {
        val vm = viewModel(startAt = 0)
        vm.tryCommitMove(Move.Draw)
        vm.requestSelectDeal(1)

        assertNull(vm.pendingConfirmation)
        assertEquals(seeds[0], vm.session.state.seed)
    }

    @Test
    fun `resetting statistics clears deal progress too`() {
        val vm = viewModel()
        vm.tryCommitMove(Move.Draw)
        vm.resetStatistics()

        assertEquals(emptyMap<Long, DealProgress>(), vm.dealProgress)
        assertEquals(emptyMap<Long, DealProgress>(), runBlocking { progressStore().current() })
    }

    @Test
    fun `switching level and back finds the same hand, and marks nothing played`() {
        val vm = GameViewModel(
            activeGameStore = ActiveGameStore(dir, dataStoreFactory = FakeDataStores::create),
            settingsStore = SettingsStore(dir, dataStoreFactory = FakeDataStores::create),
            historyStore = HistoryStore(dir, dataStoreFactory = FakeDataStores::create),
            hintDispatcher = Dispatchers.Main,
            dealProgressStore = progressStore(),
        )
        val level = vm.difficulty
        val other = org.finiteplay.klondike.storage.DifficultyPreference.entries.first { it != level && it != org.finiteplay.klondike.storage.DifficultyPreference.RANDOM }
        val deal = vm.dealNumber
        val seed = vm.session.state.seed

        repeat(3) {
            vm.setDifficulty(other)
            vm.setDifficulty(level)
        }

        assertEquals(deal, vm.dealNumber)
        assertEquals(seed, vm.session.state.seed)
        assertEquals(emptyMap<Long, Any>(), vm.dealProgress)
    }
}
