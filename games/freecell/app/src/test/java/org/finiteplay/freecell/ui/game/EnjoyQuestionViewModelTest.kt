package org.finiteplay.freecell.ui.game

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
import org.finiteplay.freecell.layout.FREE_CELLS
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.GameStatus
import org.finiteplay.freecell.layout.GameVersions
import org.finiteplay.freecell.rules.Move
import org.finiteplay.freecell.layout.TABLEAU_COLUMNS
import org.finiteplay.freecell.storage.FreeCellHistoryStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * FreeCell's part of the "enjoying this game?" question (`docs/PLATFORM.md` "Feedback and rating"): a win is a good
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
        dir = Files.createTempDirectory("enjoy-freecell-test").toFile()
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

    private fun history() = FreeCellHistoryStore(dir, dataStoreFactory = FakeDataStores::create)

    private fun viewModel() = FreeCellViewModel(initialSeed = 1L, historyStore = history(), enjoy = enjoy)

    private val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    /** A board one move from won: every suit complete on its foundation but the king of spades, alone in a column. */
    private fun oneMoveFromWin() = FreeCellState(
        seed = 0L,
        versions = versions,
        tableau = List(TABLEAU_COLUMNS) { column -> if (column == 0) listOf(Card(Suit.SPADES, Rank.KING)) else emptyList() },
        freeCells = List(FREE_CELLS) { null },
        foundations = Suit.entries.associateWith { if (it == Suit.SPADES) 12 else 13 },
        status = GameStatus.IN_PROGRESS,
        moveCount = 99,
    )

    @Test
    fun `a win is a good result, and New Game from the won board opens the question`() {
        val vm = viewModel()
        vm.loadFixtureForDebugging(oneMoveFromWin())
        assertTrue(vm.dragMove(Move.TableauToFoundation(0)))

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
