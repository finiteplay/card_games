package org.finiteplay.klondike.debug

import org.finiteplay.klondike.board.GameStatus
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.klondike.rules.applyMove
import org.finiteplay.klondike.rules.canAutoFinish
import org.finiteplay.klondike.rules.findInvariantViolations
import org.finiteplay.klondike.rules.legalMoves
import org.finiteplay.klondike.rules.simulateSweep
import org.finiteplay.klondike.rules.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ACCEPTANCE.md`'s manual smoke test loads this fixture, exposes the last face-down
 * card, and expects the automatic finish to sweep to a single win — proving the
 * hand-built board is actually correct, not just structurally present, in this
 * testDebug-only source set (lives alongside `src/debug`'s main code).
 */
class NearWinFixtureTest {

    @Test
    fun `is a valid 52-card board with exactly one face-down card`() {
        val state = nearWinGameState()

        assertTrue(findInvariantViolations(state).isEmpty())
        val faceDownCount = state.tableau.sumOf { column -> column.count { !it.faceUp } }
        assertEquals(1, faceDownCount)
    }

    @Test
    fun `cannot auto-finish until the King is moved off the Queen`() {
        val state = nearWinGameState()

        assertFalse(canAutoFinish(state))
    }

    @Test
    fun `moving the King to an empty column exposes the Queen and the sweep wins`() {
        val state = nearWinGameState()

        val moveKingToEmptyColumn = legalMoves(state).single {
            it is Move.TableauToTableau && it.fromColumn == 0 && it.toColumn == 1
        }
        val exposed = applyMove(state, moveKingToEmptyColumn)

        assertTrue(canAutoFinish(exposed))
        val result = simulateSweep(exposed)
        assertEquals(GameStatus.WON, result?.status)
    }
}
