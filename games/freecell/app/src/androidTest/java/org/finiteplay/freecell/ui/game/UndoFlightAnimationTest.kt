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
 * Undo now flies its reversed card(s) back to where they came from rather than jumping the board
 * straight to the prior board — [FreeCellBoard]'s own `LaunchedEffect(pendingUndoAnimation)`,
 * which diffs the board before and after the reversal by card identity (mirroring Klondike's own
 * undo-flight diff) rather than replaying a move list, since undo restores a whole prior board in
 * one step. Reuses `MoveFlightAnimationTest`'s own fixture: column 0's Six of diamonds relocates
 * onto column 1's Seven of spades, then Undo reverses exactly that.
 */
class UndoFlightAnimationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: FreeCellViewModel

    @Test
    fun undoingAMoveFliesItBackBeforeItLandsThere() {
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

        composeRule.onNodeWithTag("card_0_0").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("card_1_1").assertIsDisplayed()

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("action_undo").performClick()
        composeRule.mainClock.advanceTimeByFrame()

        // The reversal has committed (the reducer is synchronous), but the Six has not visibly
        // landed back in column 0 yet — the flight has barely started.
        composeRule.onNodeWithTag("card_0_0").assertDoesNotExist()

        // Comfortably past even a full-board-diagonal flight (bounded to 450 ms).
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("card_0_0").assertIsDisplayed()
        composeRule.onNodeWithTag("card_1_1").assertDoesNotExist()
    }
}
