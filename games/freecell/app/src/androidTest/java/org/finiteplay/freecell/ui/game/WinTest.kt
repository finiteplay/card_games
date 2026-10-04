package org.finiteplay.freecell.ui.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.freecell.layout.FREE_CELLS
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.GameStatus
import org.finiteplay.freecell.layout.TABLEAU_COLUMNS
import org.finiteplay.freecell.rules.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A hand-built near-win fixture rather than a played-out deal — the certified catalog only proves
 * a *fresh* deal is winnable from scratch, not any particular one move away from it, so reaching
 * a one-move-from-win board through real play would mean actually playing a whole game out first.
 * A constructed board isolates the win transition directly instead.
 *
 * Every suit but spades is already banked to King; spades sits at Queen with its own King alone
 * in a column. The commit goes through [FreeCellViewModel.dragMove] directly rather than a
 * simulated tap — with every other column empty, the documented tap priority
 * (`docs/games/freecell/DESIGN.md` "Interaction") sends a lone King to one of them *before* ever
 * trying the foundation, since an empty column is always a legal rightward destination; a real
 * drag chooses the foundation deliberately, which is exactly what this test needs to isolate the
 * win path (bank -> win detection -> dialog) from tap priority, already covered on its own by
 * `TapResolutionTest` in `:games:freecell:rules`.
 */
class WinTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: FreeCellViewModel

    @Test
    fun bankingTheLastCardWinsAndShowsTheDialog() {
        viewModel = FreeCellViewModel(initialSeed = 1L)
        composeRule.setContent {
            FinitePlayTheme {
                GameScreen(viewModel = viewModel)
            }
        }
        composeRule.waitForIdle()

        val fixture = FreeCellState(
            seed = 1L,
            versions = viewModel.session.state.versions,
            tableau = List(TABLEAU_COLUMNS) { index -> if (index == 0) listOf(Card(Suit.SPADES, Rank.KING)) else emptyList() },
            freeCells = List(FREE_CELLS) { null },
            foundations = mapOf(Suit.CLUBS to 13, Suit.DIAMONDS to 13, Suit.HEARTS to 13, Suit.SPADES to 12),
            status = GameStatus.IN_PROGRESS,
        )
        viewModel.loadFixtureForDebugging(fixture)
        composeRule.waitForIdle()

        assertTrue(viewModel.dragMove(Move.TableauToFoundation(0)))
        composeRule.waitForIdle()

        assertEquals(GameStatus.WON, viewModel.session.state.status)
        // The dialog waits for `!isAutoFinishing`, decided inside a coroutine that hops off the
        // main thread even for an instant win — `waitForIdle` alone raced ahead of it on a
        // slower device, so this polls the real condition instead of Compose's own idle signal.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("win_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("win_dialog").assertIsDisplayed()
    }
}
