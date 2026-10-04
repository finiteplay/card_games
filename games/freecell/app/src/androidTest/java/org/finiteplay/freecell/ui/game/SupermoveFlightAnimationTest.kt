package org.finiteplay.freecell.ui.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.freecell.layout.FREE_CELLS
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.GameStatus
import org.finiteplay.freecell.layout.TABLEAU_COLUMNS
import org.junit.Rule
import org.junit.Test

/**
 * A supermove's cards fly together as one rigid stack along the same path
 * (`docs/games/freecell/UI_SPEC.md` "Motion"), the way Spider's own tableau-to-tableau moves
 * already do. Column 0 holds a built two-card run (Eight of diamonds under Seven of clubs);
 * tapping its bottom card lifts the whole run onto column 1's Nine of spades, the only legal
 * destination and the nearest to its right, so tapping it resolves unambiguously.
 */
class SupermoveFlightAnimationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: FreeCellViewModel

    @Test
    fun tappingARunFliesBothCardsTogetherToItsDestination() {
        val state = FreeCellState(
            seed = 1L,
            versions = FREECELL_VERSIONS,
            tableau = List(TABLEAU_COLUMNS) { column ->
                when (column) {
                    0 -> listOf(Card(Suit.DIAMONDS, Rank.EIGHT), Card(Suit.CLUBS, Rank.SEVEN))
                    1 -> listOf(Card(Suit.SPADES, Rank.NINE))
                    else -> emptyList()
                }
            },
            freeCells = List(FREE_CELLS) { null },
            foundations = Suit.entries.associateWith { 0 },
            status = GameStatus.IN_PROGRESS,
        )
        viewModel = FreeCellViewModel(initialSeed = 1L)
        viewModel.loadFixtureForDebugging(state)
        composeRule.setContent { FinitePlayTheme { GameScreen(viewModel = viewModel) } }
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag("card_0_0").performClick()
        composeRule.mainClock.advanceTimeByFrame()

        // Committed already (the reducer is synchronous), but neither card has visibly landed yet.
        composeRule.onNodeWithTag("card_1_1").assertDoesNotExist()
        composeRule.onNodeWithTag("card_1_2").assertDoesNotExist()

        // Comfortably past even a full-board-diagonal flight (bounded to 450 ms).
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("card_1_1").assertIsDisplayed()
        composeRule.onNodeWithTag("card_1_2").assertIsDisplayed()
        composeRule.onNodeWithTag("card_0_0").assertDoesNotExist()
    }
}
