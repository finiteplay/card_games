package org.finiteplay.klondike.tools.catalog

import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.rules.applyMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins Hard, the first tier that **widens the move set** rather than reordering it.
 *
 * Every tier below offers the same moves, so their tests only had to watch the ordering. Hard
 * adds the setup move, and the ways that can go wrong are different in kind: it can leak into
 * a lower tier, it can quietly become a reveal or a column-emptying move under another name,
 * or it can admit the unproductive shuffling `DIFFICULTY_LEVELS.md` records as having
 * collapsed the tier's win rate to 2/100. Each of those has a test here.
 */
class MediumSetupMoveTest {

    /** Measured over 250,000 seeds: won by Hard's order, by none of the three below it. */
    private val hardOnly = listOf(30540L, 104385L, 15417L, 20072L, 400L, 1858L)

    /** The state cap the calibration scan ran at; the default is far too low to settle these. */
    private val scanStates = 3_000_000

    @Test
    fun `hard wins deals no lower order can`() {
        for (seed in hardOnly) {
            for (lower in Ruleset.MEDIUM.tiersBelow) {
                assertFalse("seed $seed should defeat $lower's order", undeviatingLineWins(seed, lower))
            }
            assertTrue("seed $seed should fall to Hard's order", undeviatingLineWins(seed, Ruleset.MEDIUM))
        }
    }

    @Test
    fun `the most robust hard seeds survive their recorded budgets`() {
        assertEquals(TrivialOutcome.ROBUST, checkRobustness(30540L, Ruleset.MEDIUM, 4, scanStates).outcome)
        assertEquals(TrivialOutcome.ROBUST, checkRobustness(15417L, Ruleset.MEDIUM, 3, scanStates).outcome)
    }

    @Test
    fun `hard robustness is monotone, so surviving four implies surviving fewer`() {
        for (budget in 0..3) {
            assertEquals("budget $budget", TrivialOutcome.ROBUST, checkRobustness(30540L, Ruleset.MEDIUM, budget, scanStates).outcome)
        }
    }

    @Test
    fun `graded hard seeds have a winning line the solution generator reproduces`() {
        for (seed in hardOnly) {
            val line = tierSolution(seed, Ruleset.MEDIUM)
            assertTrue("seed $seed produced no line", line != null && line.isNotEmpty())
            var state = dealGame(seed, D1S_SPIKE_VERSIONS)
            for (move in line!!) state = applyMove(state, move)
            assertTrue("seed $seed line does not win", state.isWon)
        }
    }

    @Test
    fun `only hard is offered setup moves, and they are the only difference`() {
        val below = IntArray(160)
        val hard = IntArray(160)
        var extras = 0
        forEachBoard(seeds = 1L..120L) { _, board ->
            val belowCount = board.generateTaps(below, Ruleset.EASY)
            val hardCount = board.generateTaps(hard, Ruleset.MEDIUM)
            val belowSet = (0 until belowCount).map { below[it] }.toSet()
            val hardSet = (0 until hardCount).map { hard[it] }.toSet()
            assertTrue("Hard dropped a move the tiers below offer", hardSet.containsAll(belowSet))
            for (move in hardSet - belowSet) {
                assertEquals("Hard added a non-setup move", FastBoard.TAP_SETUP, FastBoard.tapOf(move))
                extras++
            }
            for (index in 0 until belowCount) {
                assertNotEquals("a tier below was offered a setup move", FastBoard.TAP_SETUP, FastBoard.tapOf(below[index]))
            }
        }
        assertTrue("no setup move was ever generated, so this proves nothing", extras > 50)
    }

    @Test
    fun `a setup move neither turns a card over nor empties its column`() {
        val taps = IntArray(160)
        var checked = 0
        forEachBoard(seeds = 1L..120L) { _, board ->
            val count = board.generateTaps(taps, Ruleset.MEDIUM)
            for (index in 0 until count) {
                if (FastBoard.tapOf(taps[index]) != FastBoard.TAP_SETUP) continue
                val from = FastBoard.fieldA(taps[index])
                val faceDownBefore = board.columnDown[from]
                board.make(taps[index])
                assertTrue("a setup move emptied its column", board.columnSize[from] > 0)
                assertEquals("a setup move turned a card over", faceDownBefore, board.columnDown[from])
                board.unmake()
                checked++
            }
        }
        assertTrue("no setup move was checked, so this proves nothing", checked > 50)
    }

    /**
     * The failure mode the spec records: unrestricted setup moves let the ruleset relocate
     * Kings between empty columns for ever. A face-up King is always at the deepest run start,
     * since nothing outranks it, so a split run can never carry one — this pins that argument
     * rather than trusting it.
     */
    @Test
    fun `a setup move never relocates a king, and never lands on an empty column`() {
        val taps = IntArray(160)
        var checked = 0
        forEachBoard(seeds = 1L..120L) { _, board ->
            val count = board.generateTaps(taps, Ruleset.MEDIUM)
            for (index in 0 until count) {
                if (FastBoard.tapOf(taps[index]) != FastBoard.TAP_SETUP) continue
                val from = FastBoard.fieldA(taps[index])
                val at = FastBoard.fieldB(taps[index])
                val to = FastBoard.fieldC(taps[index])
                assertNotEquals("a setup move carried a King", FastBoard.RANKS, FastBoard.rankOf(board.columnCards[from][at]))
                assertTrue("a setup move landed on an empty column", board.columnSize[to] > 0)
                checked++
            }
        }
        assertTrue("no setup move was checked, so this proves nothing", checked > 50)
    }

    @Test
    fun `the productive filter rejects setup moves, rather than passing everything`() {
        var offered = 0
        var rejected = 0
        forEachBoard(seeds = 1L..120L) { state, board ->
            for (column in state.tableau.indices) {
                val deepest = board.deepestRunStart(column)
                if (deepest < 0) continue
                for (fromIndex in deepest + 1 until board.columnSize[column]) {
                    if (board.isProductiveSetup(column, fromIndex)) offered++ else rejected++
                }
            }
        }
        assertTrue("nothing was ever productive, so Hard adds nothing", offered > 50)
        assertTrue("nothing was ever rejected, so the filter is a no-op", rejected > offered)
    }
}
