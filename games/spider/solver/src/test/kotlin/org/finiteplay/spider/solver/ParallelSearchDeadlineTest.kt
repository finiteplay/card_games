package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.dealGame
import org.junit.Assert.assertTrue
import org.junit.Test

private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

/**
 * [parallelSolve] must honor [SolverLimits.maxMillis] the same way the single-threaded searches do
 * (`DeadlineTest`) — and, once, independently did not: a `for` loop in [parallelSearchNode] noticed
 * a child had been cut short but only recorded that fact to check *after* trying every remaining
 * sibling, instead of stopping right there. That is the identical shape of the single-threaded
 * incident `DeadlineTest` already pins, reintroduced in new code; here it ran eight threads for over
 * thirteen minutes against a five-second budget before a thread dump caught it, since every thread
 * pays the same unbounded-unwind cost at once.
 */
class ParallelSearchDeadlineTest {

    @Test
    fun `parallelSolve honors maxMillis on a real four-suit board with no quick win`() {
        val limits = SolverLimits(maxNodes = 50_000_000_000L, maxMillis = 500, playouts = 0, beamWidth = 0)
        val state = dealGame(seed = 1L, versions = VERSIONS, suitCount = SuitCount.FOUR)

        val start = System.nanoTime()
        val record = parallelSolve(state, limits, threads = 8, totalCacheBytes = 256L * 1024 * 1024)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        // x10 since the streamlined phase gets its own tenth-budget slice on top of the exact
        // phase's full one, then a generous multiplier for real slack (thread startup, frontier
        // build, JIT warmup) -- the point is bounding this to seconds, not to the minutes the bug
        // actually produced.
        assertTrue("parallelSolve ran ${elapsedMs}ms against a 500ms exact budget (plus its own 10% streamlined slice), result=${record.endedOn}", elapsedMs < 10_000)
    }
}
