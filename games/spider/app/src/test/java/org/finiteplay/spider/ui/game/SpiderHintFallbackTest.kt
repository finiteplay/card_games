package org.finiteplay.spider.ui.game

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.spider.layout.GameStatus
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.layout.TableauCard
import org.finiteplay.spider.solver.HintEngine
import org.finiteplay.spider.solver.SolverLimits
import org.finiteplay.spider.solver.SpiderSolver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * When the guided hint cannot find a winning line, Hint falls back to lighting every legal move, as
 * the plain mode does — without switching the player's mode, so the next Hint after a move searches
 * again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpiderHintFallbackTest {
    private val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Column 0 a lone Nine, column 1 a lone Ten: the Nine is liftable onto the Ten, and no line wins. */
    private fun board(): SpiderState = SpiderState(
        tableau = (0 until TABLEAU_COLUMNS).map { i ->
            when (i) {
                0 -> listOf(TableauCard(Card(Suit.SPADES, Rank.NINE), faceUp = true))
                1 -> listOf(TableauCard(Card(Suit.SPADES, Rank.TEN), faceUp = true))
                else -> emptyList()
            }
        },
        stock = emptyList(),
        banked = Suit.entries.associateWith { 0 },
        suitCount = SuitCount.ONE,
        seed = 0L,
        versions = versions,
        moveCount = 0,
        status = GameStatus.IN_PROGRESS,
    )

    /** A search budget too small to prove anything either way, so every guided hint is Inconclusive. */
    private fun viewModel() = SpiderViewModel(
        initialSeed = 1L,
        hintEngineFactory = { HintEngine(SpiderSolver(SolverLimits(maxNodes = 1, maxMillis = 1, playouts = 0))) },
    ).also { it.loadFixtureForDebugging(board()) }

    /** The search runs on a real background thread; wait for it to settle rather than guess a delay. */
    private fun SpiderViewModel.awaitHint(): HintUiState {
        val deadline = System.currentTimeMillis() + 10_000
        while (hintState == HintUiState.Loading && System.currentTimeMillis() < deadline) Thread.sleep(5)
        return hintState
    }

    @Test
    fun `an inconclusive guided hint lights every movable card, without counting as a hint or changing the mode`() {
        val viewModel = viewModel()
        assertTrue(viewModel.settings.hintShowsWinningMove)

        viewModel.showHint()

        assertEquals(HintUiState.Inconclusive, viewModel.awaitHint())
        assertFalse("the board should light the movable cards", viewModel.hintedCards.isEmpty())
        assertEquals(0, viewModel.hintsUsedThisGame)
        assertTrue("the setting must not be switched", viewModel.settings.hintShowsWinningMove)
    }

    @Test
    fun `tapping Hint again clears the fallback highlight instead of searching again on the same board`() {
        val viewModel = viewModel()
        viewModel.showHint()
        viewModel.awaitHint()

        viewModel.showHint()

        assertEquals(HintUiState.Hidden, viewModel.hintState)
        assertTrue(viewModel.hintedCards.isEmpty())
    }

    @Test
    fun `after a move the next Hint searches for a winning line again`() {
        val viewModel = viewModel()
        viewModel.showHint()
        viewModel.awaitHint()

        viewModel.dragMove(fromColumn = 0, fromIndex = 0, toColumn = 1)
        assertEquals(HintUiState.Hidden, viewModel.hintState)
        assertTrue(viewModel.hintedCards.isEmpty())

        viewModel.showHint()

        // Guided search ran (and was inconclusive again) rather than the plain highlight appearing
        // with no search at all: the notice is what only a guided request produces.
        assertEquals(HintUiState.Inconclusive, viewModel.awaitHint())
        assertEquals(0, viewModel.hintsUsedThisGame)
    }
}
