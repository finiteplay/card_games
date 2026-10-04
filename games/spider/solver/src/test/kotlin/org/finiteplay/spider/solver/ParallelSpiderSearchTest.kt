package org.finiteplay.spider.solver

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.spider.layout.GameStatus
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.layout.TableauCard
import org.finiteplay.spider.layout.dealGame
import org.finiteplay.spider.rules.applyMove
import org.finiteplay.spider.rules.isLegal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

/**
 * [parallelSolve]/[parallelCertify] share a workload across threads that never existed before this
 * pair of functions, so the risk they carry is different from every other search in this module: not
 * "does it find a win" but "can a data race make it report a win that is not real, or a proof of
 * unsolvability that is not sound". These tests exercise the whole pipeline at real, if small, scale
 * (many threads, forced sharding, forced cache saturation) rather than the cache in isolation, and
 * every claimed win here is independently replayed through the real reducer — the same trust model
 * `BoundedCacheIntegrationTest` and `SpiderSolver.certify`'s own doc already establish for the
 * single-threaded solver.
 */
class ParallelSpiderSearchTest {

    private fun boardOf(columns: List<List<Card>>, banked: Map<Suit, Int> = emptyMap()): SpiderState {
        val tableau = (0 until TABLEAU_COLUMNS).map { i ->
            columns.getOrElse(i) { emptyList() }.map { TableauCard(it, faceUp = true) }
        }
        return SpiderState(
            tableau = tableau,
            stock = emptyList(),
            banked = Suit.entries.associateWith { banked[it] ?: 0 },
            suitCount = SuitCount.FOUR,
            seed = 0L,
            versions = VERSIONS,
            moveCount = 0,
            status = GameStatus.IN_PROGRESS,
        )
    }

    private fun replay(state: SpiderState, line: List<org.finiteplay.spider.rules.Move>): SpiderState {
        var replayed = state
        for (move in line) {
            assertTrue("move $move illegal against the board it was supposedly found from", isLegal(replayed, move))
            replayed = applyMove(replayed, move)
        }
        return replayed
    }

    @Test
    fun `many threads sharing a cache find and replay a real solution`() {
        val ranks = Rank.entries.reversed()
        val state = boardOf(
            columns = listOf(
                ranks.dropLast(6).map { Card(Suit.SPADES, it) },
                ranks.takeLast(6).map { Card(Suit.SPADES, it) },
            ),
            banked = mapOf(Suit.SPADES to FastBoard.SEQUENCES_TO_WIN - 1),
        )
        val limits = SolverLimits(maxNodes = 100_000, maxMillis = 5_000, playouts = 0, beamWidth = 0)

        val line = parallelCertify(state, limits, threads = 8, totalCacheBytes = 64L * 1024 * 1024)

        assertTrue("expected a solution from the parallel engine", line != null)
        assertTrue("the line the parallel engine found did not replay to a win", replay(state, line!!).isWon)
    }

    @Test
    fun `many threads sharing a cache correctly prove a dead board unsolvable`() {
        val state = boardOf(
            columns = (0 until TABLEAU_COLUMNS).map {
                listOf(Card(Suit.SPADES, Rank.ACE), Card(Suit.HEARTS, Rank.ACE))
            },
        )
        val limits = SolverLimits(maxNodes = 100_000, maxMillis = 5_000, playouts = 0, beamWidth = 0)

        val record = parallelSolve(state, limits, threads = 8, totalCacheBytes = 64L * 1024 * 1024)

        assertEquals("expected", "exhausted", record.endedOn)
        assertEquals(false, record.solved)
    }

    @Test
    fun `a real four-suit deal runs cleanly under many threads without hanging or racing`() {
        // Not a search for a win (a real four-suit deal is far too hard for a few seconds) -- this
        // pins that the whole pipeline (frontier build, work stealing, shared cache, deadline
        // honoring) behaves under real board complexity and real thread counts, the shape a genuine
        // campaign attempt uses, not just the small hand-built boards above.
        val dealt = dealGame(seed = 1L, versions = VERSIONS, suitCount = SuitCount.FOUR)
        val limits = SolverLimits(maxNodes = 50_000_000L, maxMillis = 3_000, playouts = 0, beamWidth = 0)

        val start = System.nanoTime()
        val record = parallelSolve(dealt, limits, threads = 8, totalCacheBytes = 512L * 1024 * 1024)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue("parallelSolve ran ${elapsedMs}ms against a 3000ms budget (x10 for the streamlined phase's own slice)", elapsedMs < 15_000)
        assertTrue("expected LIMIT (not a real win in a few seconds), got ${record.endedOn}", !record.solved)
    }

    @Test
    fun `parallelSolve actually spends its time budget on a real four-suit deal, not a fraction of it`() {
        // A real four-suit deal cannot be solved or exhausted in a few seconds -- the single-threaded
        // campaign ran 66 minutes on this exact seed without either. So a healthy run should use
        // essentially its whole budget. Pins a real independent regression: `parallelSearchNode`
        // once treated hitting the depth cap the same as running out of the shared time/node budget
        // (CUT_SHORT, which propagates immediately and abandons every remaining sibling at every
        // level up the stack) instead of like SpiderSolver.search's own convention (an ordinary "no
        // win here", trying the next sibling normally) -- one long line anywhere in a task's subtree
        // was enough to abort that whole task in a fraction of a second, and the bug finished a
        // 60-second budget's worth of work in under two seconds.
        val limits = SolverLimits(maxNodes = 50_000_000_000L, maxMillis = 3_000, playouts = 0, beamWidth = 0)
        val state = dealGame(seed = 1L, versions = VERSIONS, suitCount = SuitCount.FOUR)

        val start = System.nanoTime()
        val record = parallelSolve(state, limits, threads = 8, totalCacheBytes = 512L * 1024 * 1024)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue(
            "expected close to the full ~3300ms budget (streamlined's own 300ms slice plus the 3000ms exact budget), got ${elapsedMs}ms -- finishing far short suggests something is aborting whole subtrees early rather than genuinely running out of budget",
            elapsedMs > 2_500,
        )
        assertTrue(!record.solved)
    }
}
