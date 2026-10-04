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
 * A committed move now flies its card(s) from source to destination rather than jumping the board
 * straight to the post-move board — Spider's own `MoveFlightAnimationTest` proves the same thing
 * for its own tableau moves. Column 0 holds a lone Six of diamonds, column 1 a lone Seven of
 * spades — the only legal destination for the Six, and the nearest column to its right, so tapping
 * it resolves unambiguously (`resolveTableauTap`) with no automatic cascade to follow (every
 * foundation is empty, and nothing here is an Ace).
 */
class MoveFlightAnimationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: FreeCellViewModel

    @Test
    fun tappingACardFliesItToItsDestinationBeforeItLandsThere() {
        val state = FreeCellState(
            seed = 1L,
            versions = FREECELL_VERSIONS,
            tableau = List(TABLEAU_COLUMNS) { column ->
                when (column) {
                    0 -> listOf(Card(Suit.DIAMONDS, Rank.SIX))
                    1 -> listOf(Card(Suit.SPADES, Rank.SEVEN))
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

        // Committed already (the reducer is synchronous), but the Six has not visibly landed on
        // the Seven yet — the flight has barely started.
        composeRule.onNodeWithTag("card_1_1").assertDoesNotExist()

        // Comfortably past even a full-board-diagonal flight (bounded to 450 ms).
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("card_1_1").assertIsDisplayed()
        composeRule.onNodeWithTag("card_0_0").assertDoesNotExist()
    }
}
