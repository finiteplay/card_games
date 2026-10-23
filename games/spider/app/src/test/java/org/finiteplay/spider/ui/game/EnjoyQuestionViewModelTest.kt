package org.finiteplay.spider.ui.game

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
import org.finiteplay.core.storage.FakeDataStores
import org.finiteplay.core.storage.seedVeteran
import org.finiteplay.core.ui.feedback.EnjoyController
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.spider.layout.GameStatus
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.layout.TableauCard
import org.finiteplay.spider.storage.SpiderHistoryStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Spider's part of the "enjoying this game?" question (`docs/PLATFORM.md` "Feedback and rating"): a win is a good
 * result, and choosing New Game from the won board opens the question when the win made it due.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EnjoyQuestionViewModelTest {
    private lateinit var dir: File
    private lateinit var store: EngagementStore
    private lateinit var enjoy: EnjoyController
    private val day = TimeUnit.DAYS.toMillis(1)
    private var now = 1_000_000_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dir = Files.createTempDirectory("enjoy-spider-test").toFile()
        store = EngagementStore(dir, dataStoreFactory = FakeDataStores::create)
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

    private fun history() = SpiderHistoryStore(dir, dataStoreFactory = FakeDataStores::create)

    private fun viewModel() = SpiderViewModel(initialSeed = 1L, historyStore = history(), enjoy = enjoy)

    private val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    /** A one-suit board that is already won, as a player's last move leaves it. */
    private fun wonBoard() = SpiderState(
        tableau = (0 until TABLEAU_COLUMNS).map { listOf(TableauCard(Card(Suit.SPADES, Rank.KING), faceUp = true)) },
        stock = emptyList(),
        banked = Suit.entries.associateWith { if (it == Suit.SPADES) 8 else 0 },
        suitCount = SuitCount.ONE,
        seed = 0L,
        versions = versions,
        moveCount = 100,
        status = GameStatus.WON,
    )

    @Test
    fun `a win is a good result, and New Game from the won board opens the question`() {
        val vm = viewModel()
        vm.loadFixtureForDebugging(wonBoard())

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
