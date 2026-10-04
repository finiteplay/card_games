package org.finiteplay.klondike.tools.catalog

import org.finiteplay.klondike.board.TABLEAU_COLUMNS
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.rules.applyMove
import org.finiteplay.klondike.rules.canPlaceOnFoundation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins Medium against seeds whose verdicts were measured.
 *
 * Medium's only substantive addition is **full foundation restraint**, so almost everything
 * that could go wrong here is a wrong sign or a wrong colour in one predicate — a defect that
 * would leave the tier compiling, grading, and quietly identical to Easy. The restraint is
 * therefore checked twice: against an independent re-derivation of the rule, and for the
 * containment that "Easy's rules, plus" requires.
 */
class MediumRulesetTest {

    /** Measured over 1,500,000 seeds: won by Medium's order, by neither Easy's nor Trivial's. */
    private val mediumOnly = listOf(907035L, 10811L, 32147L, 55597L, 63525L, 75670L)

    /** The state cap the calibration scan ran at; the default is far too low to settle these. */
    private val scanStates = 3_000_000

    @Test
    fun `medium wins deals no lower order can`() {
        for (seed in mediumOnly) {
            for (lower in Ruleset.MEDIUM.tiersBelow) {
                assertFalse("seed $seed should defeat $lower's order", undeviatingLineWins(seed, lower))
            }
            assertTrue("seed $seed should fall to Medium's order", undeviatingLineWins(seed, Ruleset.MEDIUM))
        }
    }

    @Test
    fun `the most robust medium seeds survive their recorded budgets`() {
        assertEquals(TrivialOutcome.ROBUST, checkRobustness(907035L, Ruleset.MEDIUM, 4, scanStates).outcome)
        assertEquals(TrivialOutcome.ROBUST, checkRobustness(10811L, Ruleset.MEDIUM, 3, scanStates).outcome)
    }

    @Test
    fun `medium robustness is monotone, so surviving four implies surviving fewer`() {
        for (budget in 0..3) {
            assertEquals("budget $budget", TrivialOutcome.ROBUST, checkRobustness(907035L, Ruleset.MEDIUM, budget, scanStates).outcome)
        }
    }

    @Test
    fun `graded medium seeds have a winning line the solution generator reproduces`() {
        for (seed in mediumOnly) {
            val line = tierSolution(seed, Ruleset.MEDIUM)
            assertTrue("seed $seed produced no line", line != null && line.isNotEmpty())
            var state = dealGame(seed, D1S_SPIKE_VERSIONS)
            for (move in line!!) state = applyMove(state, move)
            assertTrue("seed $seed line does not win", state.isWon)
        }
    }

    /**
     * The restraint, re-derived here from the spec rather than from the implementation: rank 3
     * and above waits until **both opposite-colour foundations** are at the rank beneath.
     */
    private fun provablySafe(board: FastBoard, card: Int): Boolean {
        if (FastBoard.rankOf(card) <= 2) return true
        val needed = FastBoard.rankOf(card) - 1
        return (0 until FastBoard.SUITS)
            .filter { FastBoard.isRed(it * FastBoard.RANKS) != FastBoard.isRed(card) }
            .all { board.foundations[it] >= needed }
    }

    @Test
    fun `medium holds every unsafe card of rank three or above`() {
        var held = 0
        var examined = 0
        forEachBoard(seeds = 1L..80L) { state, board ->
            for (column in 0 until TABLEAU_COLUMNS) {
                val top = state.tableau[column].lastOrNull()?.takeIf { it.faceUp }?.card ?: continue
                if (!canPlaceOnFoundation(state.foundations, top)) continue
                examined++
                val withheld = board.foundationRank(column, Ruleset.MEDIUM) == FastBoard.RANK_WITHHELD
                if (!provablySafe(board, top.id)) {
                    assertTrue("Medium sent an unsafe rank-${FastBoard.rankOf(top.id)} card up", withheld)
                    held++
                }
            }
        }
        assertTrue("no foundation play was examined, so this proves nothing", examined > 200)
        assertTrue("the restraint never fired, so this proves nothing", held > 20)
    }

    @Test
    fun `medium is easy's rules plus, so it withholds everything easy does`() {
        var stricter = 0
        forEachBoard(seeds = 1L..80L) { state, board ->
            for (column in 0 until TABLEAU_COLUMNS) {
                val top = state.tableau[column].lastOrNull()?.takeIf { it.faceUp }?.card ?: continue
                if (!canPlaceOnFoundation(state.foundations, top)) continue
                val easy = board.foundationRank(column, Ruleset.EASY)
                val medium = board.foundationRank(column, Ruleset.MEDIUM)
                if (easy == FastBoard.RANK_WITHHELD) {
                    assertEquals("Medium released a card Easy held", FastBoard.RANK_WITHHELD, medium)
                } else if (medium == FastBoard.RANK_WITHHELD) {
                    stricter++
                }
            }
        }
        assertTrue("Medium never held a card Easy released, so it is not a distinct tier", stricter > 10)
    }
}
