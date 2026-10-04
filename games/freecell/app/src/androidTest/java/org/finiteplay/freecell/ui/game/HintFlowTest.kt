package org.finiteplay.freecell.ui.game

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.freecell.rules.Move
import org.finiteplay.freecell.rules.isLegal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `docs/games/freecell/EXECUTION_PLAN.md` F6: a live end-to-end exercise of the Hint action
 * through the real ViewModel and board, on a real seed — the search itself is measured and
 * gated separately, at full-catalog scale, by `:games:freecell:solver`'s own
 * `HintSearchBudgetTest`; this proves the *wiring* around it.
 */
class HintFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun requestingAHintShowsALegalGuidedMoveAndAMoveClearsIt() {
        val viewModel = FreeCellViewModel(initialSeed = 1L)
        composeRule.setContent {
            FinitePlayTheme { GameScreen(viewModel = viewModel) }
        }

        composeRule.onNodeWithTag("action_hint").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { viewModel.hintState !is HintUiState.Loading }

        val shown = viewModel.hintState
        check(shown is HintUiState.Guided) { "expected Guided, got $shown" }
        assertTrue("the guided move must be legal against the board it was computed from", isLegal(viewModel.session.state, shown.move))
        assertEquals(shown.move, viewModel.hint)

        // Any real move — not necessarily the hinted one — clears a shown hint: it describes a
        // board that no longer exists once the player has acted.
        val column = viewModel.session.state.tableau.indexOfFirst { it.isNotEmpty() }
        viewModel.dragMove(Move.TableauToFreeCell(column, 0))
        composeRule.waitForIdle()

        assertEquals(HintUiState.Hidden, viewModel.hintState)
        assertNull(viewModel.hint)
    }

    @Test
    fun requestingAgainWhileOneIsAlreadyShowingIsANoOp() {
        val viewModel = FreeCellViewModel(initialSeed = 1L)
        composeRule.setContent {
            FinitePlayTheme { GameScreen(viewModel = viewModel) }
        }

        composeRule.onNodeWithTag("action_hint").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { viewModel.hintState !is HintUiState.Loading }
        val first = viewModel.hintState

        composeRule.onNodeWithTag("action_hint").performClick()
        composeRule.waitForIdle()

        assertEquals("a second request while one is showing must change nothing", first, viewModel.hintState)
    }

    /**
     * With [FreeCellViewModel.setHintShowsWinningMove] off, Hint highlights every legal move
     * instead of searching for a proven one -- no [HintUiState] at all, and the toggle-on-
     * repeated-tap behavior Spider's own highlight hint already has
     * (`docs/games/freecell/UI_SPEC.md` "Hint").
     */
    @Test
    fun hintHighlightsEveryLegalMoveWhenSettingIsOff() {
        val viewModel = FreeCellViewModel(initialSeed = 1L)
        viewModel.setHintShowsWinningMove(false)
        composeRule.setContent {
            FinitePlayTheme { GameScreen(viewModel = viewModel) }
        }
        assertEquals(emptyList<Move>(), viewModel.legalMoveHighlights)

        composeRule.onNodeWithTag("action_hint").performClick()
        composeRule.waitForIdle()

        val shown = viewModel.legalMoveHighlights
        assertTrue(shown.isNotEmpty())
        for (move in shown) {
            assertTrue("every highlighted move must be legal against the board", isLegal(viewModel.session.state, move))
        }
        assertEquals(HintUiState.Hidden, viewModel.hintState)

        composeRule.onNodeWithTag("action_hint").performClick()
        composeRule.waitForIdle()

        assertEquals(emptyList<Move>(), viewModel.legalMoveHighlights)
    }

    @Test
    fun committingAMoveHidesLegalMoveHighlightsToo() {
        val viewModel = FreeCellViewModel(initialSeed = 1L)
        viewModel.setHintShowsWinningMove(false)
        composeRule.setContent {
            FinitePlayTheme { GameScreen(viewModel = viewModel) }
        }
        composeRule.onNodeWithTag("action_hint").performClick()
        composeRule.waitForIdle()
        assertTrue(viewModel.legalMoveHighlights.isNotEmpty())

        val column = viewModel.session.state.tableau.indexOfFirst { it.isNotEmpty() }
        viewModel.dragMove(Move.TableauToFreeCell(column, 0))
        composeRule.waitForIdle()

        assertEquals(emptyList<Move>(), viewModel.legalMoveHighlights)
    }

    @Test
    fun undoClearsAShownHint() {
        val viewModel = FreeCellViewModel(initialSeed = 1L)
        val column = viewModel.session.state.tableau.indexOfFirst { it.isNotEmpty() }
        viewModel.dragMove(Move.TableauToFreeCell(column, 0))

        composeRule.setContent {
            FinitePlayTheme { GameScreen(viewModel = viewModel) }
        }
        composeRule.onNodeWithTag("action_hint").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { viewModel.hintState !is HintUiState.Loading }
        check(viewModel.hintState is HintUiState.Guided)

        composeRule.onNodeWithTag("action_undo").performClick()
        composeRule.waitForIdle()

        assertEquals(HintUiState.Hidden, viewModel.hintState)
    }
}
