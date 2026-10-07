package org.finiteplay.spider.ui.game

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.core.session.LevelUpOffer
import org.finiteplay.core.storage.FakeDataStores
import org.finiteplay.spider.layout.GameStatus
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.layout.TableauCard
import org.finiteplay.spider.storage.SpiderHistoryRecord
import org.finiteplay.spider.storage.SpiderHistoryStore
import org.finiteplay.spider.storage.SpiderOutcome
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * After the tenth win at the suit count being played, New Game asks whether to move up
 * (`docs/PLATFORM.md` "Levels"): switching starts a new game at the next suit count, staying starts
 * one at the same.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpiderLevelUpTest {
    private lateinit var dir: File
    private val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dir = Files.createTempDirectory("spider-level-up").toFile()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        dir.deleteRecursively()
    }

    private fun history() = SpiderHistoryStore(dir, dataStoreFactory = FakeDataStores::create)

    private fun priorWins(count: Int, at: SuitCount = SuitCount.ONE) = runBlocking {
        repeat(count) {
            history().upsert(
                SpiderHistoryRecord(
                    gameId = "earlier-$it",
                    resultId = "earlier-$it-WIN",
                    outcome = SpiderOutcome.WIN,
                    suitCount = at,
                    elapsedMillis = 1_000,
                    moveCount = 100,
                    timestampMillis = it.toLong(),
                ),
            )
        }
    }

    /** A one-suit board that is already won, as a player's last move leaves it. */
    private fun wonBoard(suits: SuitCount = SuitCount.ONE) = SpiderState(
        tableau = (0 until TABLEAU_COLUMNS).map { listOf(TableauCard(Card(Suit.SPADES, Rank.KING), faceUp = true)) },
        stock = emptyList(),
        banked = Suit.entries.associateWith { if (it == Suit.SPADES) 8 else 0 },
        suitCount = suits,
        seed = 0L,
        versions = versions,
        moveCount = 100,
        status = GameStatus.WON,
    )

    private fun viewModel() = SpiderViewModel(initialSeed = 1L, historyStore = history())

    @Test
    fun `the tenth win at the suit count in play offers the next one instead of dealing`() {
        priorWins(9)
        val vm = viewModel()
        vm.loadFixtureForDebugging(wonBoard())

        vm.requestNewGame()

        assertEquals(LevelUpOffer(SuitCount.ONE, SuitCount.TWO, 10), vm.levelUpOffer)
        assertEquals("nothing is dealt until the player answers", GameStatus.WON, vm.session.state.status)
    }

    @Test
    fun `accepting moves up a suit count and starts a new game there`() {
        priorWins(9)
        val vm = viewModel()
        vm.loadFixtureForDebugging(wonBoard())
        vm.requestNewGame()

        vm.acceptLevelUp()

        assertNull(vm.levelUpOffer)
        assertEquals(SuitCount.TWO, vm.nextSuitCount)
        assertEquals(SuitCount.TWO, vm.session.state.suitCount)
        assertFalse("a new game, not the won one", vm.session.state.isWon)
    }

    @Test
    fun `declining stays on the suit count and still starts a new game`() {
        priorWins(9)
        val vm = viewModel()
        vm.loadFixtureForDebugging(wonBoard())
        vm.requestNewGame()

        vm.declineLevelUp()

        assertNull(vm.levelUpOffer)
        assertEquals(SuitCount.ONE, vm.nextSuitCount)
        assertEquals(SuitCount.ONE, vm.session.state.suitCount)
        assertFalse(vm.session.state.isWon)
    }

    @Test
    fun `the ninth win does not ask`() {
        priorWins(8)
        val vm = viewModel()
        vm.loadFixtureForDebugging(wonBoard())

        vm.requestNewGame()

        assertNull(vm.levelUpOffer)
        assertFalse("New Game just deals", vm.session.state.isWon)
    }

    @Test
    fun `wins at another suit count are not counted`() {
        priorWins(9, at = SuitCount.TWO)
        val vm = viewModel()
        vm.loadFixtureForDebugging(wonBoard())

        vm.requestNewGame()

        assertNull(vm.levelUpOffer)
    }

    @Test
    fun `four suits has nothing above it, so the tenth win there does not ask`() {
        priorWins(9, at = SuitCount.FOUR)
        val vm = viewModel()
        vm.pickNextSuitCount(SuitCount.FOUR)
        vm.loadFixtureForDebugging(wonBoard(SuitCount.FOUR))

        vm.requestNewGame()

        assertNull(vm.levelUpOffer)
    }
}
