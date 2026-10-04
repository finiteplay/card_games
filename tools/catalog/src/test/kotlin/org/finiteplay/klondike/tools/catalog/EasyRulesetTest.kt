package org.finiteplay.klondike.tools.catalog

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.TABLEAU_COLUMNS
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.rules.applyMove
import org.finiteplay.klondike.rules.canPlaceOnFoundation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins Easy against seeds whose verdicts were measured, and pins the two things about the
 * tier that could silently stop being true.
 *
 * Easy offers the *same* obvious moves as Trivial — only the order differs — so a defect in
 * the ordering does not break anything visibly. It just quietly regrades deals, which is
 * exactly the failure that shipped a Trivial tier a player could lose. Hence tests both that
 * the restraint rules fire at all and that they only fire for a documented reason.
 */
class EasyRulesetTest {

    @Test
    fun `seed 78 remains winnable when higher tiers explore equally ranked choices`() {
        for (ruleset in Ruleset.entries) {
            val search = searchStrategyLine(78L, ruleset)
            println("seed 78 $ruleset: ${search.statesExplored} strategy states, ${search.packedLine.size} choices")
            assertEquals(
                "$ruleset must not collapse a preference tie to generator order",
                StrategySearchOutcome.WON,
                search.outcome,
            )
            assertTrue("$ruleset search exceeded its explicit state cap", search.statesExplored < 100_000)
            assertTrue("$ruleset strategy certificate did not replay", replayableStrategyLine(78L, search.packedLine) != null)
        }
    }

    /** Measured over 500,000 seeds: won by Easy's order, not by Trivial's, and mistake-tolerant. */
    private val easyNotTrivial = listOf(365384L, 20961L, 54246L, 56328L, 82910L, 99243L)

    @Test
    fun `easy wins deals trivial's own order cannot`() {
        for (seed in easyNotTrivial) {
            assertFalse("seed $seed should defeat Trivial's order", undeviatingLineWins(seed, Ruleset.TRIVIAL))
            assertTrue("seed $seed should fall to Easy's order", undeviatingLineWins(seed, Ruleset.EASY))
        }
    }

    /** The state cap the calibration scan ran at; the default is far too low to settle these. */
    private val scanStates = 3_000_000

    @Test
    fun `the most robust easy seeds survive their recorded budgets`() {
        assertEquals(TrivialOutcome.ROBUST, checkRobustness(365384L, Ruleset.EASY, 4, scanStates).outcome)
        assertEquals(TrivialOutcome.ROBUST, checkRobustness(20961L, Ruleset.EASY, 3, scanStates).outcome)
    }

    @Test
    fun `easy robustness is monotone, so surviving four implies surviving fewer`() {
        for (budget in 0..3) {
            assertEquals("budget $budget", TrivialOutcome.ROBUST, checkRobustness(365384L, Ruleset.EASY, budget, scanStates).outcome)
        }
    }

    /**
     * The grader ranks packed taps on a [FastBoard]; the solution generator enumerates over
     * `GameState` because a shipped line needs the draws. They must still agree, or the line
     * that ships is not the line the tier was graded on.
     */
    @Test
    fun `graded easy seeds have a winning line the solution generator reproduces`() {
        for (seed in easyNotTrivial) {
            val line = tierSolution(seed, Ruleset.EASY)
            assertTrue("seed $seed produced no line", line != null && line.isNotEmpty())
            var state = dealGame(seed, D1S_SPIKE_VERSIONS)
            for (move in line!!) state = applyMove(state, move)
            assertTrue("seed $seed line does not win", state.isWon)
        }
    }

    @Test
    fun `easy withholds foundation plays, and only for a documented reason`() {
        var examined = 0
        var withheld = 0
        forEachBoard(seeds = 1L..60L) { state, board ->
            for (column in 0 until TABLEAU_COLUMNS) {
                val top = state.tableau[column].lastOrNull()?.takeIf { it.faceUp }?.card ?: continue
                if (!canPlaceOnFoundation(state.foundations, top)) continue
                examined++
                assertEquals(
                    "Trivial ignores safety, so it never withholds",
                    FastBoard.TAP_FOUNDATION * FastBoard.RANK_STEP,
                    board.foundationRank(column, Ruleset.TRIVIAL),
                )
                if (board.foundationRank(column, Ruleset.EASY) == FastBoard.RANK_WITHHELD) {
                    withheld++
                    assertTrue(
                        "withheld with neither rule satisfied (seed board, column $column)",
                        board.rushesFoundation(top.id) || board.isOnlyLandingSpot(column),
                    )
                }
            }
        }
        assertTrue("no foundation play was examined, so this proves nothing", examined > 200)
        assertTrue("the restraint never fired, so this proves nothing", withheld > 20)
    }

    @Test
    fun `easy prefers the reveal that uncovers the most buried column`() {
        var compared = 0
        val taps = IntArray(64)
        forEachBoard(seeds = 1L..200L) { _, board ->
            val count = board.generateTaps(taps)
            val reveals = (0 until count).filter { FastBoard.tapOf(taps[it]) == FastBoard.TAP_REVEAL }
            for (a in reveals) for (b in reveals) {
                val deeper = FastBoard.fieldA(taps[a])
                val shallower = FastBoard.fieldA(taps[b])
                if (board.columnDown[deeper] <= board.columnDown[shallower]) continue
                assertTrue(
                    "Easy ranked the shallower column first",
                    board.tapRank(taps[a], Ruleset.EASY) < board.tapRank(taps[b], Ruleset.EASY),
                )
                assertEquals(
                    "Trivial should not distinguish the two",
                    board.tapRank(taps[b], Ruleset.TRIVIAL),
                    board.tapRank(taps[a], Ruleset.TRIVIAL),
                )
                compared++
            }
        }
        assertTrue("never saw two reveals at once, so this proves nothing", compared > 10)
    }

}
