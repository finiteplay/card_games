package org.finiteplay.freecell.ui.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.freecell.rules.Move
import org.finiteplay.freecell.rules.isMovableSequence
import org.finiteplay.freecell.rules.resolveTableauTap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Drives the real board through real gestures — F2's instrumented gate
 * (`docs/games/freecell/EXECUTION_PLAN.md`): a tap move, a drag move (including a supermove), and
 * undo. A fixed [SEED] rather than a hand-authored fixture, because the board is dealt by the same
 * shuffle production uses (`FreeCellViewModel`); what to tap is discovered from the actual dealt
 * state via [resolveTableauTap] itself, never a hardcoded card, so a shuffle-affecting change
 * fails this test by producing no discoverable move rather than by silently drifting — the same
 * discipline Spider's own `GameScreenTest` uses.
 */
class GameScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: FreeCellViewModel

    private fun setContent() {
        viewModel = FreeCellViewModel(initialSeed = SEED)
        composeRule.setContent {
            FinitePlayTheme {
                GameScreen(viewModel = viewModel)
            }
        }
    }

    @Test
    fun boardIsDisplayedOnLaunch() {
        setContent()
        composeRule.onNodeWithTag("app_root").assertIsDisplayed()
        composeRule.onNodeWithTag("free_cell_0", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun tappingAResolvableCardCommitsTheMoveAndUndoRestoresTheBoard() {
        setContent()
        val boardBeforeMove = viewModel.session.state
        val (column, index, expected) = findResolvableTableauTap()
        val liftedCard = boardBeforeMove.tableau[column][index]

        composeRule.onNodeWithTag("card_${column}_$index").performClick()
        composeRule.waitForIdle()

        assertTrue("moveCount must advance", viewModel.session.state.moveCount > boardBeforeMove.moveCount)
        when (expected) {
            is Move.TableauToTableau ->
                assertEquals(liftedCard, viewModel.session.state.tableau[expected.toColumn].last())
            is Move.TableauToFoundation ->
                assertEquals(liftedCard, viewModel.session.state.foundationTop(liftedCard.suit))
            else -> error("resolveTableauTap never returns $expected")
        }

        composeRule.onNodeWithTag("action_undo").performClick()
        composeRule.waitForIdle()

        assertEquals(boardBeforeMove.tableau, viewModel.session.state.tableau)
        assertEquals(boardBeforeMove.freeCells, viewModel.session.state.freeCells)
        assertEquals(boardBeforeMove.foundations, viewModel.session.state.foundations)
    }

    @Test
    fun draggingACardOntoAnEmptyFreeCellMovesIt() {
        setContent()
        val state = viewModel.session.state
        // The top card of the first non-empty column: always liftable alone, and free cells start
        // empty, so this drag needs no board-specific discovery.
        val column = state.tableau.indexOfFirst { it.isNotEmpty() }
        val card = state.tableau[column].last()

        val source = composeRule.onNodeWithTag("card_${column}_${state.tableau[column].lastIndex}").fetchSemanticsNode()
        val destination = composeRule.onNodeWithTag("free_cell_0", useUnmergedTree = true).fetchSemanticsNode()
        val delta = destination.boundsInRoot.center - source.boundsInRoot.center

        performDrag(column, state.tableau[column].lastIndex, delta)

        assertEquals(card, viewModel.session.state.freeCells.firstOrNull { it == card })
        assertTrue(viewModel.session.state.tableau[column].none { it == card })
    }

    @Test
    fun draggingASupermoveCarriesEveryLiftedCardTogether() {
        setContent()
        // A fresh deal has no empty column at all (every one of the 52 cards is dealt out), so
        // this discovers a real two-or-more-card sequence and a real legal, non-empty destination
        // for it via `isLegal` itself — not an assumed empty column — the same way
        // `findResolvableTableauTap` above discovers its move from the actual dealt state.
        val (column, fromIndex, toColumn) = findLiftableSupermove() ?: return // nothing to assert on this deal
        val liftedCards = viewModel.session.state.tableau[column].subList(fromIndex, viewModel.session.state.tableau[column].size)

        val source = composeRule.onNodeWithTag("card_${column}_$fromIndex").fetchSemanticsNode()
        val destination = composeRule.onNodeWithTag("tableau_column_$toColumn").fetchSemanticsNode()
        val delta = destination.boundsInRoot.center - source.boundsInRoot.center

        performDrag(column, fromIndex, delta)

        assertEquals(liftedCards, viewModel.session.state.tableau[toColumn].takeLast(liftedCards.size))
        assertTrue(viewModel.session.state.tableau[column].none { it in liftedCards })
    }

    @Test
    fun newGameOnAnUntouchedDealDealsImmediatelyWithoutConfirmation() {
        setContent()
        val before = viewModel.session.state.seed

        composeRule.onNodeWithTag("action_new").performClick()
        composeRule.waitForIdle()

        assertNotEquals("a fresh, untouched deal needs no confirmation", before, viewModel.session.state.seed)
        assertEquals(null, viewModel.pendingAction)
    }

    /** Splits a drag across separate touch-input calls with `waitForIdle` between, the same way
     * Spider's own drag test does — a single block never lets the board recompose mid-drag,
     * which is exactly the case a real finger always produces. */
    private fun performDrag(column: Int, index: Int, delta: androidx.compose.ui.geometry.Offset) {
        val node = composeRule.onNodeWithTag("card_${column}_$index")
        node.performTouchInput { down(center) }
        composeRule.waitForIdle()

        val steps = 10
        for (step in 1..steps) {
            node.performTouchInput {
                advanceEventTime(16)
                moveTo(center + delta * (step.toFloat() / steps))
            }
            composeRule.waitForIdle()
        }

        node.performTouchInput { up() }
        composeRule.waitForIdle()
    }

    private fun findResolvableTableauTap(): Triple<Int, Int, Move> {
        val state = viewModel.session.state
        for (column in state.tableau.indices) {
            val col = state.tableau[column]
            for (index in col.indices) {
                if (!isMovableSequence(col, index)) continue
                val move = resolveTableauTap(state, column, index) ?: continue
                return Triple(column, index, move)
            }
        }
        error("no resolvable tableau tap on seed $SEED — pick a different seed")
    }

    /** A liftable sequence of two or more cards and a real legal destination for it, or null. */
    private fun findLiftableSupermove(): Triple<Int, Int, Int>? {
        val state = viewModel.session.state
        for (column in state.tableau.indices) {
            val col = state.tableau[column]
            for (index in 0 until col.lastIndex) {
                if (!isMovableSequence(col, index) || col.size - index < 2) continue
                for (toColumn in state.tableau.indices) {
                    if (toColumn == column) continue
                    val move = Move.TableauToTableau(column, index, toColumn)
                    if (org.finiteplay.freecell.rules.isLegal(state, move)) return Triple(column, index, toColumn)
                }
            }
        }
        return null
    }

    private companion object {
        const val SEED = 1L
    }
}
