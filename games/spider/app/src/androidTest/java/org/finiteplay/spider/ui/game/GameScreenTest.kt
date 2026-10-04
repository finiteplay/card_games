package org.finiteplay.spider.ui.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.spider.game.resolveTap
import org.finiteplay.spider.game.tapDestinations
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.isMovableSequence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Drives the real board through real gestures — the instrumented counterpart to the JVM
 * `TapResolutionTest`/`SpiderSessionTest` coverage, and the first proof that the board's drag
 * (`SpiderBoard.kt`) actually resolves a drop, not just that the callback wiring compiles.
 *
 * A fixed [SEED] rather than a hand-authored fixture board, because the board is dealt by the
 * same shuffle production uses (`SpiderViewModel`) — hand-authoring one would test a board no
 * player's game ever reaches. What to tap is discovered from the *actual* dealt state via the
 * same rules functions [resolveTap] uses, never a hardcoded card, so a shuffle-affecting change
 * fails this test by producing no discoverable move rather than by silently drifting.
 */
class GameScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: SpiderViewModel

    private fun setContent() {
        viewModel = SpiderViewModel(initialSeed = SEED)
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
        composeRule.onNodeWithTag("stock").assertIsDisplayed()
    }

    @Test
    fun tappingStockDealsARowAndCountsOneMove() {
        setContent()
        val before = viewModel.session.state.rowDealsRemaining

        composeRule.onNodeWithTag("stock").performClick()
        composeRule.waitForIdle()

        assertEquals(before - 1, viewModel.session.state.rowDealsRemaining)
        assertEquals(1, viewModel.session.state.moveCount)
    }

    @Test
    fun tappingAResolvableCardCommitsTheLeftmostLegalMove() {
        setContent()
        // A fresh deal rarely has an immediately resolvable tap (verified by hand: the first
        // screenshot of a fresh four-suit deal had none); one row deal reliably produces one.
        composeRule.onNodeWithTag("stock").performClick()
        composeRule.waitForIdle()
        val movesAfterDeal = viewModel.session.state.moveCount

        val (column, index, expected) = findResolvableTap()
        val liftedCard = viewModel.session.state.tableau[column][index].card

        composeRule.onNodeWithTag("card_${column}_$index").performClick()
        composeRule.waitForIdle()

        assertEquals(movesAfterDeal + 1, viewModel.session.state.moveCount)
        assertEquals(liftedCard, viewModel.session.state.tableau[expected.toColumn].last().card)
        assertTrue(viewModel.session.state.tableau[column].none { it.card == liftedCard })
    }

    @Test
    fun undoAfterATapMoveRestoresTheExactPriorBoard() {
        setContent()
        composeRule.onNodeWithTag("stock").performClick()
        composeRule.waitForIdle()
        val boardBeforeMove = viewModel.session.state

        val (column, index, _) = findResolvableTap()
        composeRule.onNodeWithTag("card_${column}_$index").performClick()
        composeRule.waitForIdle()
        assertTrue(viewModel.session.state != boardBeforeMove)

        composeRule.onNodeWithTag("action_undo").performClick()
        composeRule.waitForIdle()

        assertEquals(boardBeforeMove.tableau, viewModel.session.state.tableau)
        assertEquals(boardBeforeMove.stock, viewModel.session.state.stock)
        // The platform rule (`docs/PLATFORM.md` "Persistence"): moves already counted are kept,
        // then one more is added — never reset to the pre-move count.
        assertEquals(boardBeforeMove.moveCount + 2, viewModel.session.state.moveCount)
    }

    @Test
    fun draggingACardOntoItsResolvedDestinationMovesIt() {
        setContent()
        composeRule.onNodeWithTag("stock").performClick()
        composeRule.waitForIdle()

        val (column, index, expected) = findResolvableTap()
        val sourceColumnBefore = viewModel.session.state.tableau[column]
        // `index` may be mid-run, not just the top card — `resolveTap`/`isMovableSequence` allow
        // lifting from any point in a liftable sequence (`RULES.md`), so the card that ends up on
        // top of the destination is the *run's own top card*, not necessarily the one tapped.
        val runTopCard = sourceColumnBefore.last().card
        val liftedCards = sourceColumnBefore.subList(index, sourceColumnBefore.size).map { it.card }

        val source = composeRule.onNodeWithTag("card_${column}_$index").fetchSemanticsNode()
        val destination = composeRule.onNodeWithTag("tableau_column_${expected.toColumn}").fetchSemanticsNode()
        val delta = destination.boundsInRoot.center - source.boundsInRoot.center

        // Split across separate performTouchInput calls with waitForIdle between, rather than one
        // block: a single block dispatches the whole gesture before Compose recomposes, so the
        // board never re-runs mid-drag and a bug that only appears *because* of that
        // recomposition — the dragged card's own gesture modifier being torn down once it is
        // marked lifted — cannot be observed. A real finger always recomposes mid-drag.
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

        val sourceColumnAfter = viewModel.session.state.tableau[column]
        assertTrue(
            "drag was a no-op: source column $column unchanged (still ${sourceColumnAfter.size} cards, " +
                "top ${sourceColumnAfter.lastOrNull()?.card}), moveCount stayed at ${viewModel.session.state.moveCount}",
            sourceColumnAfter != sourceColumnBefore,
        )

        val destinationColumnAfter = viewModel.session.state.tableau[expected.toColumn]
        assertEquals(runTopCard, destinationColumnAfter.last().card)
        assertEquals(liftedCards, destinationColumnAfter.takeLast(liftedCards.size).map { it.card })
        assertTrue(viewModel.session.state.tableau[column].none { it.card in liftedCards })
    }

    @Test
    fun tappingTheSameSequenceAgainWalksItToTheNextLegalColumn() {
        setContent()
        composeRule.onNodeWithTag("stock").performClick()
        composeRule.waitForIdle()

        // Only a sequence with two or more destinations can show a walk at all; find one on the
        // board actually dealt rather than assuming the first resolvable tap has several.
        val state = viewModel.session.state
        var found: Pair<Int, Int>? = null
        outer@ for (column in state.tableau.indices) {
            for (index in state.tableau[column].indices) {
                if (tapDestinations(state, column, index).size >= 2) {
                    found = column to index
                    break@outer
                }
            }
        }
        val (column, index) = found ?: return  // nothing to assert on this deal
        val destinations = tapDestinations(state, column, index)

        composeRule.onNodeWithTag("card_${column}_$index").performClick()
        composeRule.waitForIdle()
        assertEquals(destinations[0], lastMovedToColumn())

        // The same sequence, now sitting in the column the first tap sent it to.
        val landed = destinations[0]
        val landedIndex = viewModel.session.state.tableau[landed].size - (state.tableau[column].size - index)
        composeRule.onNodeWithTag("card_${landed}_$landedIndex").performClick()
        composeRule.waitForIdle()

        assertTrue(
            "a second tap must move the sequence on rather than leaving it where it was",
            lastMovedToColumn() != landed,
        )
    }

    /** The destination column of the most recent committed move. */
    private fun lastMovedToColumn(): Int =
        (viewModel.session.log.filterIsInstance<org.finiteplay.spider.session.SpiderLogEntry.PlayerMove>()
            .last().move as Move.TableauToTableau).toColumn

    /** The (column, index) of the first liftable card with a legal destination, and that move. */
    private fun findResolvableTap(): Triple<Int, Int, Move.TableauToTableau> {
        val state = viewModel.session.state
        for (column in state.tableau.indices) {
            val col = state.tableau[column]
            for (index in col.indices) {
                if (!col[index].faceUp || !isMovableSequence(col, index)) continue
                val move = resolveTap(state, column, index) ?: continue
                return Triple(column, index, move)
            }
        }
        error("no resolvable tap on seed $SEED after one row deal — pick a different seed")
    }

    private companion object {
        const val SEED = 1L
    }
}
