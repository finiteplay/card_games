package org.finiteplay.klondike.tools.catalog

import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.applyMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the **Hard ruleset** — foundation withdrawal — the top rung of the ladder, graded on
 * neither robustness nor a ruleset winning first.
 *
 * Not the shipped Expert or Insane levels, which no ruleset defines: those are cut from what DFS
 * and A\* say about a deal no order wins (`DIFFICULTY_LEVELS.md` "Levels as shipped"). Hard is
 * the highest level a ruleset reaches.
 *
 * This was `ExpertRulesetTest` while the ladder had five rungs and withdrawal was called Expert.
 *
 * The tier's defining claim is that a player has to take a banked card back. That claim is not
 * checked directly by the tier build — it falls out of the disjointness gate — so it is
 * asserted here on the shipped line itself, because a claim nothing measures is a claim that
 * quietly stops being true.
 */
class HardRulesetTest {

    /**
     * Every seed in the first 40,000 that qualifies for this tier, re-derived with
     * `survey-expert` against the current grader — the whole population at that scan size, not a
     * sample of it, so a deal silently leaving the tier fails this list rather than thinning it.
     *
     * The previous fixtures were graded before full foundation restraint became inherited and
     * before the ladder merged to four tiers. Three of the five survived both; 19533 and 22529
     * did not, and 22529 is the clearest case — Medium's order now wins it outright, so it is
     * not a withdrawal deal at all. Re-derive the same way after any change to the ladder or to
     * what a tier withholds.
     */
    private val shipped = listOf(10066L, 10690L, 21430L, 30344L, 33484L)

    @Test
    fun `every shipped hard line actually withdraws from a foundation`() {
        for (seed in shipped) {
            val line = tierSolution(seed, Ruleset.HARD)
            assertTrue("seed $seed produced no line", line != null && line.isNotEmpty())
            val withdrawals = line!!.count { it is Move.FoundationToTableau }
            assertTrue("seed $seed wins without ever withdrawing", withdrawals >= 1)
        }
    }

    @Test
    fun `every shipped hard line wins when replayed`() {
        for (seed in shipped) {
            var state = dealGame(seed, D1S_SPIKE_VERSIONS)
            for (move in tierSolution(seed, Ruleset.HARD)!!) state = applyMove(state, move)
            assertTrue("seed $seed line does not win", state.isWon)
        }
    }

    @Test
    fun `no lower order wins a shipped hard deal`() {
        for (seed in shipped) {
            for (lower in Ruleset.HARD.tiersBelow) {
                assertFalse("seed $seed should defeat $lower's order", undeviatingLineWins(seed, lower))
            }
        }
    }

    /**
     * The band `DIFFICULTY_LEVELS.md` requires. Measured over a million seeds, critical points
     * run from 0 to 18, so this is a genuine window rather than a bound that never binds.
     */
    @Test
    fun `shipped hard deals sit inside the critical-choice band`() {
        for (seed in shipped) {
            val verdict = checkWithdrawalTier(seed, maxStates = 2_000_000, ablate = false)
            assertEquals("seed $seed", WithdrawalTierReason.QUALIFIES, verdict.reason)
            assertTrue(
                "seed $seed has ${verdict.criticalPoints} critical points",
                verdict.criticalPoints in MIN_CRITICAL..MAX_CRITICAL,
            )
        }
    }

    /**
     * The gate that was missing. A deal whose withdrawal-free win a bounded search turns up in
     * milliseconds is one a player turns up too, whatever the ruleset ladder says.
     */
    @Test
    fun `no shipped hard deal has a findable withdrawal-free win`() {
        for (seed in shipped) {
            assertFalse("seed $seed can be won without withdrawing", winnableWithoutWithdrawal(seed) == true)
        }
    }

    /**
     * Hard's move set is Medium's plus withdrawal, and every shared move is ranked identically
     * at both. That is what makes a withdrawal-free Hard line also a Medium line — the premise
     * behind the disjointness gate implying the withdrawal, so it is asserted rather than
     * assumed.
     */
    @Test
    fun `hard and medium rank every shared move identically`() {
        val taps = IntArray(160)
        var compared = 0
        forEachBoard(seeds = 1L..60L) { _, board ->
            val count = board.generateTaps(taps, Ruleset.HARD)
            for (index in 0 until count) {
                if (FastBoard.tapOf(taps[index]) == FastBoard.TAP_WITHDRAW) continue
                assertEquals(
                    "a shared move is ranked differently at Hard",
                    board.tapRank(taps[index], Ruleset.MEDIUM),
                    board.tapRank(taps[index], Ruleset.HARD),
                )
                compared++
            }
        }
        assertTrue("no moves were compared, so this proves nothing", compared > 500)
    }
}
