package org.finiteplay.klondike.ui.game

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.finiteplay.core.session.LevelUpOffer
import org.finiteplay.klondike.debug.nearWinGameState
import org.finiteplay.klondike.deal.DealSeedSource
import org.finiteplay.klondike.deal.DifficultyTier
import org.finiteplay.klondike.deal.seedsFor
import org.finiteplay.klondike.storage.ActiveGameStore
import org.finiteplay.klondike.storage.DifficultyPreference
import org.finiteplay.klondike.storage.FakeDataStores
import org.finiteplay.klondike.storage.HistoryRecord
import org.finiteplay.klondike.storage.HistoryStore
import org.finiteplay.klondike.storage.Outcome
import org.finiteplay.klondike.storage.SettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * After the tenth win at the level being played, New Game asks whether to move up
 * (`docs/PLATFORM.md` "Levels"): switching starts a new game at the next level, staying starts one
 * at the same.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LevelUpViewModelTest {
    private lateinit var dir: File
    private val tier = DifficultyTier.EASY
    private val seeds = seedsFor(tier)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dir = Files.createTempDirectory("level-up-test").toFile()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        dir.deleteRecursively()
    }

    private fun history() = HistoryStore(dir, dataStoreFactory = FakeDataStores::create)

    private fun priorWins(count: Int, at: DifficultyTier = tier) = runBlocking {
        repeat(count) {
            history().upsert(
                HistoryRecord(
                    gameId = "earlier-$it",
                    resultId = "earlier-$it:result",
                    outcome = Outcome.WIN,
                    elapsedMillis = 1_000,
                    moveCount = 100,
                    timestampMillis = it.toLong(),
                    difficulty = at,
                ),
            )
        }
    }

    private fun viewModel() = GameViewModel(
        activeGameStore = ActiveGameStore(dir, dataStoreFactory = FakeDataStores::create),
        settingsStore = SettingsStore(dir, dataStoreFactory = FakeDataStores::create),
        historyStore = history(),
        injectedSeedSource = DealSeedSource { seeds[0] },
        hintDispatcher = Dispatchers.Main,
    )

    /** Plays the level's own game to a win: a near-won board dealt from one of the level's seeds. */
    private fun winOneGame(vm: GameViewModel) {
        vm.loadFixtureForDebugging(nearWinGameState().copy(seed = seeds[0]))
        var guard = 0
        while (!vm.session.state.isWon && guard++ < 20) {
            vm.requestHint()
            val guided = vm.hintState as HintUiState.Guided
            vm.tryCommitMove(guided.move)
            vm.dismissHint()
        }
        assertEquals(true, vm.session.state.isWon)
    }

    @Test
    fun `the tenth win at the level in play offers the next level instead of dealing`() {
        priorWins(9)
        val vm = viewModel()
        vm.setDifficulty(DifficultyPreference.EASY)
        winOneGame(vm)

        vm.requestNewGame()

        assertEquals(LevelUpOffer(DifficultyTier.EASY, DifficultyTier.MEDIUM, 10), vm.levelUpOffer)
        assertEquals("nothing is dealt until the player answers", true, vm.session.state.isWon)
    }

    @Test
    fun `accepting moves up a level and starts a new game there`() {
        priorWins(9)
        val vm = viewModel()
        vm.setDifficulty(DifficultyPreference.EASY)
        winOneGame(vm)
        vm.requestNewGame()

        vm.acceptLevelUp()

        assertNull(vm.levelUpOffer)
        assertEquals(DifficultyPreference.MEDIUM, vm.difficulty)
        assertFalse("a new game, not the won one", vm.session.state.isWon)
    }

    @Test
    fun `declining stays on the level and still starts a new game`() {
        priorWins(9)
        val vm = viewModel()
        vm.setDifficulty(DifficultyPreference.EASY)
        winOneGame(vm)
        vm.requestNewGame()

        vm.declineLevelUp()

        assertNull(vm.levelUpOffer)
        assertEquals(DifficultyPreference.EASY, vm.difficulty)
        assertFalse(vm.session.state.isWon)
    }

    @Test
    fun `the ninth win does not ask`() {
        priorWins(8)
        val vm = viewModel()
        vm.setDifficulty(DifficultyPreference.EASY)
        winOneGame(vm)

        vm.requestNewGame()

        assertNull(vm.levelUpOffer)
        assertFalse("New Game just deals", vm.session.state.isWon)
    }

    @Test
    fun `wins at another level are not counted`() {
        priorWins(9, at = DifficultyTier.TRIVIAL)
        val vm = viewModel()
        vm.setDifficulty(DifficultyPreference.EASY)
        winOneGame(vm)

        vm.requestNewGame()

        assertNull(vm.levelUpOffer)
    }

    @Test
    fun `a player no longer on the level is not asked`() {
        priorWins(9)
        val vm = viewModel()
        vm.setDifficulty(DifficultyPreference.MEDIUM)
        winOneGame(vm)

        vm.requestNewGame()

        assertNull(vm.levelUpOffer)
    }
}
