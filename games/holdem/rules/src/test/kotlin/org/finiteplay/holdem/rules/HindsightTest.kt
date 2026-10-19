package org.finiteplay.holdem.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Looking back at a hand that was folded (`UI_SPEC.md` "Hint Review"). */
class HindsightTest {
    /** Everyone folds in turn until one seat is left with the pot. */
    private fun foldAround(seed: Long): HoldemState {
        var state = startTournament(seed).state
        while (state.phase != Phase.HAND_OVER && state.phase != Phase.TOURNAMENT_OVER) {
            state = apply(state, state.toAct, Action.Fold) ?: error("fold refused")
        }
        return state
    }

    @Test
    fun `the run out board is the one the engine deals when a hand is played to the river`() {
        // Called down to the river, the dealt board is the run-out board.
        var state = startTournament(21L).state
        while (state.phase != Phase.HAND_OVER && state.phase != Phase.TOURNAMENT_OVER) {
            val legal = legalActions(state)!!
            state = apply(state, state.toAct, if (legal.canCheck) Action.Check else Action.Call) ?: error("refused")
        }
        assertEquals(5, state.board.size)
        assertEquals(state.board, state.runOutBoard())
    }

    @Test
    fun `a folded hand is judged on the cards against whoever was left`() {
        var wins = 0
        var losses = 0
        for (seed in 1L..60L) {
            val state = foldAround(seed)
            val rival = state.seatsInHand.single { !state.folded[it] }
            val board = state.runOutBoard()
            for (seat in state.seatsInHand) {
                if (seat == rival) {
                    assertNull("the winner never folded", state.hindsightResult(seat))
                    continue
                }
                val comparison = evaluate(state.holeCards[seat] + board).compareTo(evaluate(state.holeCards[rival] + board))
                val expected = when {
                    comparison > 0 -> HindsightResult.WON
                    comparison < 0 -> HindsightResult.LOST
                    else -> HindsightResult.TIED
                }
                assertEquals("seed $seed seat $seat", expected, state.hindsightResult(seat))
                if (expected == HindsightResult.WON) wins++ else losses++
            }
        }
        assertTrue("both outcomes occur: $wins wins, $losses losses", wins > 20 && losses > 20)
    }
}
