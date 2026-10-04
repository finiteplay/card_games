package org.finiteplay.freecell.ui.game

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.freecell.layout.FREE_CELLS
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.GameStatus
import org.finiteplay.freecell.layout.TABLEAU_COLUMNS
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The automatic finish's search runs off the main thread (`FreeCellViewModel.runAutoFinishIfAvailable`'s
 * own doc explains why), so unlike an ordinary move's flight this cannot be exercised by freezing
 * Compose's own clock — instead this proves the whole chain end to end: a King of clubs sits
 * buried under an inert Two of hearts in column 2 (diamonds, hearts, and spades are already fully
 * banked, so it is the last card the game needs), reachable only by parking the Two into a free
 * cell first — something the ordinary safe cascade never does, only the automatic finish's search
 * does. Tapping the unrelated Six of diamonds in column 0 (which only relocates onto column 1's
 * Seven of spades) triggers the finish check the same way any player move does. If
 * [FreeCellViewModel.pendingAutoFinish] were never consumed, or [FreeCellBoard] never called
 * [FreeCellViewModel.onSweepAnimationFinished] once its own animation of the sweep actually
 * finished playing, `isAutoFinishing` would stay true forever and the win dialog would never
 * appear — so seeing it appear here is proof the sweep's own animation actually completed, not
 * just that the search and its commit did.
 */
class AutoFinishFlightAnimationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: FreeCellViewModel

    @Test
    fun theBuriedFinishingCardBanksAfterTheOrdinaryMoveAndTheWinDialogWaitsForIt() {
        val state = FreeCellState(
            seed = 1L,
            versions = FREECELL_VERSIONS,
            tableau = List(TABLEAU_COLUMNS) { column ->
                when (column) {
                    0 -> listOf(Card(Suit.DIAMONDS, Rank.SIX))
                    1 -> listOf(Card(Suit.SPADES, Rank.SEVEN))
                    2 -> listOf(Card(Suit.HEARTS, Rank.TWO), Card(Suit.CLUBS, Rank.KING))
                    else -> emptyList()
                }
            },
            freeCells = List(FREE_CELLS) { null },
            foundations = mapOf(Suit.CLUBS to 12, Suit.DIAMONDS to 13, Suit.HEARTS to 13, Suit.SPADES to 13),
            status = GameStatus.IN_PROGRESS,
        )
        viewModel = FreeCellViewModel(initialSeed = 1L)
        viewModel.loadFixtureForDebugging(state)
        composeRule.setContent { FinitePlayTheme { GameScreen(viewModel = viewModel) } }

        composeRule.onNodeWithTag("card_0_0").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("win_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        assertEquals(GameStatus.WON, viewModel.session.state.status)
        assertEquals(52, viewModel.session.state.foundations.values.sum())
    }
}
