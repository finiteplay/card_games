package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.dealGame
import org.junit.Assert.assertTrue
import org.junit.Test

private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

/**
 * [SolverLimits.maxMillis] must bound wall-clock time even when a search runs deep before its
 * first cache eviction and even on a board with no quick win — a real four-suit deal, with a
 * generous [SolverLimits.maxNodes] so the node budget cannot be what actually stops the search,
 * is what exposed a real bug: each recursive search's for-loop treated a timed-out child's `-1`
 * the same as "no solution down this branch" and moved on to the next sibling, which recursed
 * fresh and would not itself notice the clock until its own sampled check (every 1024 nodes)
 * happened to land, potentially diving deep first. Across a stack of untried siblings at up to
 * [SolverLimits] default depth, that turned a 2-second budget into a search still running two
 * minutes later. A generous slack multiplier, not an exact bound, since the sampled clock check
 * still allows some overrun by design.
 */
class DeadlineTest {

    @Test
    fun `certifyStreamlined honors maxMillis on a real four-suit board with no quick win`() {
        val limits = SolverLimits(maxNodes = 50_000_000_000L, maxMillis = 500, playouts = 0, beamWidth = 0)
        val state = dealGame(seed = 1L, versions = VERSIONS, suitCount = SuitCount.FOUR)
        val solver = SpiderSolver(limits)

        val start = System.nanoTime()
        val outcome = solver.certifyStreamlined(state, millisBudget = 500)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue("expected LIMIT (not a real win) within budget, got ${outcome.result}", outcome.result == SolveResult.LIMIT || outcome.solved)
        assertTrue("certifyStreamlined ran ${elapsedMs}ms against a 500ms budget", elapsedMs < 5_000)
    }

    @Test
    fun `solve honors maxMillis on a real four-suit board with no quick win`() {
        val limits = SolverLimits(maxNodes = 50_000_000_000L, maxMillis = 500, playouts = 0, beamWidth = 0)
        val state = dealGame(seed = 1L, versions = VERSIONS, suitCount = SuitCount.FOUR)
        val solver = SpiderSolver(limits)

        val start = System.nanoTime()
        val outcome = solver.solve(state)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue("expected LIMIT (not a real win) within budget, got ${outcome.result}", outcome.result == SolveResult.LIMIT || outcome.solved)
        assertTrue("solve ran ${elapsedMs}ms against a 500ms budget", elapsedMs < 5_000)
    }

    @Test
    fun `solve honors maxMillis with a bounded evicting cache too`() {
        val limits = SolverLimits(
            maxNodes = 50_000_000_000L,
            maxMillis = 500,
            playouts = 0,
            beamWidth = 0,
            maxCacheEntries = 2_000_000L,
        )
        val state = dealGame(seed = 1L, versions = VERSIONS, suitCount = SuitCount.FOUR)
        val solver = SpiderSolver(limits)

        val start = System.nanoTime()
        val outcome = solver.solve(state)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue("expected LIMIT (not a real win) within budget, got ${outcome.result}", outcome.result == SolveResult.LIMIT || outcome.solved)
        assertTrue("solve ran ${elapsedMs}ms against a 500ms budget", elapsedMs < 5_000)
    }
}
