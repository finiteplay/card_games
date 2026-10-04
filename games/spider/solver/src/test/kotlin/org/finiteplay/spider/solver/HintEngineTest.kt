package org.finiteplay.spider.solver

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.spider.layout.GameStatus
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.TableauCard
import org.finiteplay.spider.layout.dealGame
import org.finiteplay.spider.rules.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [HintEngine.primeWithKnownSolution] priming coverage, mirroring Klondike's and FreeCell's own
 * `HintEngineTest` — the fast path it seeds is exercised end to end by
 * `PrimedHintFlowTest` (`games/spider/app`) against the real shipped catalog; this checks the
 * mechanism in isolation, against a search budget too small to solve anything on its own, so a
 * `Guidance` result here can only have come from the primed cache.
 */
class HintEngineTest {
    private val versions = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1)

    /** A search budget too small to prove anything either way — any [HintOutcome.Guidance] this
     * produces has to have come from the primed cache, never a live solve. */
    private val noBudget = SolverLimits(maxNodes = 1, maxMillis = 1, playouts = 0)

    private fun up(card: Card) = TableauCard(card, faceUp = true)

    /** Seven suits already banked; the eighth needs only the exposed ace onto the exposed two. */
    private fun endgame(seed: Long = 1L): SpiderState {
        val runRanks = listOf(
            Rank.KING, Rank.QUEEN, Rank.JACK, Rank.TEN, Rank.NINE,
            Rank.EIGHT, Rank.SEVEN, Rank.SIX, Rank.FIVE, Rank.FOUR, Rank.THREE, Rank.TWO,
        )
        val column0 = runRanks.map { up(Card(Suit.SPADES, it)) }
        val column1 = listOf(up(Card(Suit.SPADES, Rank.ACE)))
        val fillerColumns = (2 until 10).map { listOf(up(Card(Suit.CLUBS, Rank.KING))) }
        val tableau = listOf(column0, column1) + fillerColumns

        return SpiderState(
            seed = seed,
            versions = versions,
            suitCount = SuitCount.FOUR,
            tableau = tableau,
            stock = emptyList(),
            banked = mapOf(Suit.SPADES to 2, Suit.HEARTS to 2, Suit.CLUBS to 2, Suit.DIAMONDS to 1),
            status = GameStatus.IN_PROGRESS,
        )
    }

    private val endgameLine = listOf(Move.TableauToTableau(fromColumn = 1, fromIndex = 0, toColumn = 0))

    @Test
    fun `a primed known solution resolves the first hint from the cache with no search`() {
        val engine = HintEngine(SpiderSolver(noBudget))
        val start = endgame()

        engine.primeWithKnownSolution(start, endgameLine)
        val first = engine.hint(start)

        check(first is HintOutcome.Guidance) { "expected Guidance from the primed cache, got $first" }
        assertEquals(endgameLine.first(), first.move)
    }

    /**
     * A fresh four-suit deal — essentially never solved by any budget tried (`HINT_SOLVER_LIMITS`'s
     * own doc) and certainly not by [noBudget] — so a live search here reports [HintOutcome.Inconclusive],
     * never [HintOutcome.Guidance]. The two tests below rely on that: a `Guidance` result would have
     * to have come from the primed cache, which is exactly what they are checking did *not* happen.
     */
    private fun unsolvedFreshDeal(seed: Long = 1L): SpiderState =
        dealGame(seed = seed, versions = versions, suitCount = SuitCount.FOUR)

    @Test
    fun `priming with an empty solution is a no-op`() {
        val engine = HintEngine(SpiderSolver(noBudget))
        val start = unsolvedFreshDeal()

        engine.primeWithKnownSolution(start, emptyList())
        val first = engine.hint(start)

        assertTrue("nothing was primed, so an exhausted budget must not report Guidance", first !is HintOutcome.Guidance)
    }

    @Test
    fun `priming with a solution that does not replay is ignored, not trusted`() {
        val engine = HintEngine(SpiderSolver(noBudget))
        val start = unsolvedFreshDeal()

        // Index 0 of a freshly dealt column is a face-down card well below its movable sequence.
        val badMove = Move.TableauToTableau(fromColumn = 0, fromIndex = 0, toColumn = 1)
        engine.primeWithKnownSolution(start, listOf(badMove))
        val first = engine.hint(start)

        assertTrue("an untrustworthy primed line must not resolve to Guidance", first !is HintOutcome.Guidance)
    }

    @Test
    fun `a board reached off the shipped line gets a winning move, not a no-solution notice`() {
        val start = HintVerdictTest.offLineBoard(versions)
        val outcome = HintEngine(SpiderSolver(HINT_SOLVER_LIMITS)).hint(start)
        assertTrue("expected guidance, got $outcome", outcome is HintOutcome.Guidance)
        assertTrue(org.finiteplay.spider.rules.isLegal(start, (outcome as HintOutcome.Guidance).move))
    }

    /**
     * Three single-suit cards that can shuffle among themselves and ten full columns: more than one
     * legal move, so a beam of width one must discard some, yet a tiny reachable space that never
     * banks a suit — a lost board that only an exhaustive search can prove lost.
     */
    private fun smallLostBoard(): SpiderState {
        val tableau = listOf(
            listOf(up(Card(Suit.SPADES, Rank.FIVE))),
            listOf(up(Card(Suit.SPADES, Rank.FOUR))),
            listOf(up(Card(Suit.SPADES, Rank.THREE))),
        ) + (3 until 10).map { listOf(up(Card(Suit.CLUBS, Rank.KING))) }
        return SpiderState(
            seed = 1L,
            versions = versions,
            suitCount = SuitCount.TWO,
            tableau = tableau,
            stock = emptyList(),
            banked = mapOf(Suit.SPADES to 0, Suit.HEARTS to 0, Suit.CLUBS to 0, Suit.DIAMONDS to 0),
            status = GameStatus.IN_PROGRESS,
        )
    }

    @Test
    fun `a beam that had to discard moves proves nothing, but the exhaustive stage does`() {
        val board = smallLostBoard()
        val beamOnly = SolverLimits(playouts = 0, beamWidth = 1, useDfsAndAStarFallback = false)
        assertEquals(SolveResult.LIMIT, SpiderSolver(beamOnly).certifyWithOutcome(board).outcome.result)

        val exhaustive = beamOnly.copy(exhaustiveDfs = true)
        assertEquals(SolveResult.EXHAUSTED, SpiderSolver(exhaustive).certifyWithOutcome(board).outcome.result)
    }

    @Test
    fun `the hint reports no solution once every path from the board is exhausted`() {
        assertEquals(HintOutcome.NoSolution, HintEngine(SpiderSolver(HINT_SOLVER_LIMITS)).hint(smallLostBoard()))
    }
}
