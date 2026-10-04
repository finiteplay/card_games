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
 * Skip Animations removes the slide (`docs/games/freecell/UI_SPEC.md` "Motion"): a committed move
 * lands the very next frame rather than over 80-450 ms. Reuses `MoveFlightAnimationTest`'s own
 * fixture with the setting flipped on first.
 */
class SkipAnimationsFlightTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: FreeCellViewModel

    @Test
    fun aMoveLandsImmediatelyWithSkipAnimationsOn() {
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
        viewModel.setAnimationsEnabled(false)
        viewModel.loadFixtureForDebugging(state)
        composeRule.setContent { FinitePlayTheme { GameScreen(viewModel = viewModel) } }
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag("card_0_0").performClick()
        // A handful of frames, not one: the zero-duration flight still needs its LaunchedEffect
        // to actually launch and resume before `displayState` steps to the landed board.
        repeat(5) { composeRule.mainClock.advanceTimeByFrame() }

        composeRule.onNodeWithTag("card_1_1").assertIsDisplayed()
        composeRule.onNodeWithTag("card_0_0").assertDoesNotExist()
    }
}
