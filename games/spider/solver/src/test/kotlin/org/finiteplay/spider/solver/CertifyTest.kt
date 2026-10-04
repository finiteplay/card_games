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
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.applyMove
import org.finiteplay.spider.rules.isLegal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

/**
 * [SpiderSolver.certify]'s whole reason to exist is that its output gets replayed through the real
 * reducer before a seed is ever shipped — so the property that matters is not "certify returns
 * something" but "what it returns actually replays to a win move by move, on the real board, not
 * the search's own copy of it".
 */
class CertifyTest {
    @Test
    fun `two-suit seed one strategy certificate is legal and available to DFS`() {
        val start = dealGame(seed = 1L, versions = VERSIONS, suitCount = SuitCount.TWO)
        val solver = SpiderSolver(HINT_SOLVER_LIMITS)
        val outcome = solver.solve(start)
        assertEquals(SolveResult.SOLVED_BY_STRATEGY, outcome.result)
        val line = solver.certify(start)
        assertNotNull("seed one must produce a certificate", line)
        var state = start
        val candidates = IntArrayList(64)
        for (move in line!!) {
            assertTrue("illegal certificate move: $move", isLegal(state, move))
            generateMoves(FastBoard.from(state), candidates)
            assertTrue(
                "DFS does not generate certificate move: $move",
                (0 until candidates.size).any { toRulesMove(FastMove(candidates[it])) == move },
            )
            state = applyMove(state, move)
        }
        assertTrue("certificate must replay to a real win", state.isWon)
        println("TWO seed 1: strategy nodes=${outcome.nodes}, certificate moves=${line.size}")
    }

    private fun replayWinsForReal(seed: Long, suitCount: SuitCount, line: List<Move>): Boolean {
        var state = dealGame(seed = seed, versions = VERSIONS, suitCount = suitCount)
        for (move in line) {
            if (!isLegal(state, move)) return false
            state = applyMove(state, move)
        }
        return state.isWon
    }

    @Test
    fun `a strategy-solved one-suit deal's certificate replays to a win on the real reducer`() {
        val solver = SpiderSolver(SolverLimits(maxNodes = 50_000, maxMillis = 3_000, playouts = 30))
        var found = 0
        for (seed in 1L..40L) {
            val state = dealGame(seed = seed, versions = VERSIONS, suitCount = SuitCount.ONE)
            val outcome = solver.solve(state)
            if (!outcome.solved) continue
            val line = solver.certify(state)
            assertNotNull("solved but certify returned no line for seed $seed", line)
            assertTrue(
                "seed $seed: certificate did not replay to a win on the real reducer",
                replayWinsForReal(seed, SuitCount.ONE, line!!),
            )
            found++
            if (found >= 5) return
        }
        assertTrue("no solved one-suit deal found in the sample to certify", found > 0)
    }

    /**
     * A regression guard on the two things that moved two-suit off zero, neither of them a bigger
     * search budget: the playout's public-strategy heuristics (`scoreMove`/`boardScore`, from
     * `docs/games/spider/PUBLIC_STRATEGY_RESEARCH.md`) and the beam-limited DFS that ranks moves by
     * them (`SpiderSolver.beamSearch`). Measured on this exact sample: zero of twenty solved before
     * either, four with the strengthened playout alone, eight with the beam as well. Asserts a
     * floor rather than the measured count, since both stages are bounded by wall-clock time and a
     * loaded machine can lose a seed or two — but a drop back toward zero means one of them
     * regressed.
     */
    @Test
    fun `two-suit fresh deals solve at a measurable rate within the interactive budget`() {
        val limits = SolverLimits(maxNodes = 400_000, maxMillis = 4_000, playouts = 40)
        var found = 0
        for (seed in 1L..20L) {
            val solver = SpiderSolver(limits)
            val state = dealGame(seed = seed, versions = VERSIONS, suitCount = SuitCount.TWO)
            val line = solver.certify(state) ?: continue
            assertTrue(
                "seed $seed: certificate did not replay to a win on the real reducer",
                replayWinsForReal(seed, SuitCount.TWO, line),
            )
            found++
        }
        assertTrue("expected at least 5 of the first 20 two-suit seeds to solve, found $found", found >= 5)
    }

    /**
     * The beam can only ever discard moves, so it must never turn a board the full-legal search
     * solves into one nothing solves. One suit is where that is checkable at low cost: every seed
     * in this sample solves either way, and the beam is on by default.
     */
    @Test
    fun `the beam stage does not lose one-suit deals the pipeline solved without it`() {
        val withBeam = SolverLimits(maxNodes = 50_000, maxMillis = 3_000, playouts = 30, beamWidth = 3)
        val withoutBeam = withBeam.copy(beamWidth = 0)
        for (seed in 1L..10L) {
            val state = dealGame(seed = seed, versions = VERSIONS, suitCount = SuitCount.ONE)
            if (!SpiderSolver(withoutBeam).solve(state).solved) continue
            assertTrue(
                "seed $seed solved with the beam disabled but not with it",
                SpiderSolver(withBeam).solve(state).solved,
            )
        }
    }

    /** A board holding exactly the cards given, one list per column, nothing in the stock. */
    private fun boardOf(columns: List<List<Card>>, banked: Int): SpiderState {
        val tableau = (0 until TABLEAU_COLUMNS).map { i ->
            columns.getOrElse(i) { emptyList() }.map { TableauCard(it, faceUp = true) }
        }
        return SpiderState(
            tableau = tableau,
            stock = emptyList(),
            banked = Suit.entries.associateWith { if (it == Suit.SPADES) banked else 0 },
            suitCount = SuitCount.ONE,
            seed = 0L,
            versions = VERSIONS,
            moveCount = 0,
            status = GameStatus.IN_PROGRESS,
        )
    }

    @Test
    fun `a search-solved endgame's certificate is in forward order, not reversed`() {
        // playouts = 0 forces search to be the only stage that can win this, which is exactly the
        // path that had the ordering bug: search collects moves as its recursion unwinds, deepest
        // first, and forgetting to reverse them ships a certificate that replays backwards.
        val state = boardOf(
            listOf(
                listOf(Card(Suit.SPADES, Rank.KING), Card(Suit.SPADES, Rank.QUEEN), Card(Suit.SPADES, Rank.JACK)),
                listOf(
                    Card(Suit.SPADES, Rank.TEN), Card(Suit.SPADES, Rank.NINE),
                    Card(Suit.SPADES, Rank.EIGHT), Card(Suit.SPADES, Rank.SEVEN),
                ),
                listOf(
                    Card(Suit.SPADES, Rank.SIX), Card(Suit.SPADES, Rank.FIVE), Card(Suit.SPADES, Rank.FOUR),
                    Card(Suit.SPADES, Rank.THREE), Card(Suit.SPADES, Rank.TWO), Card(Suit.SPADES, Rank.ACE),
                ),
            ),
            banked = 7,
        )
        val solver = SpiderSolver(SolverLimits(maxNodes = 300_000, maxMillis = 5_000, playouts = 0))
        val outcome = solver.solve(state)
        assertEquals(SolveResult.SOLVED_BY_SEARCH, outcome.result)

        var replayed = state
        val line = solver.certify(state)
        assertNotNull(line)
        for (move in line!!) {
            assertTrue("move $move illegal on the board it was supposedly reached from", isLegal(replayed, move))
            replayed = applyMove(replayed, move)
        }
        assertTrue("search's own certificate did not replay to a win in order", replayed.isWon)
    }
}
