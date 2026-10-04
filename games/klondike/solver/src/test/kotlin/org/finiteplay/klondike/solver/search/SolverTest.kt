package org.finiteplay.klondike.solver.search

import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.GameStateFixtures
import org.finiteplay.klondike.board.GameStateFixtures.faceUp
import org.finiteplay.klondike.board.GameStateFixtures.withFoundation
import org.finiteplay.klondike.board.GameStateFixtures.withStock
import org.finiteplay.klondike.board.GameStateFixtures.withTableauColumn
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.klondike.rules.applyMove
import org.finiteplay.klondike.rules.isLegal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class SolverTest {

    /**
     * Plain breadth-first ground truth over the same search primitives ([SNode],
     * [generateMoves], [applySearchMove]) — independent of [Solver]'s own algorithm,
     * so comparing against it is a genuine check that [Solver.solve] returns a
     * *shortest* certificate, not just *a* certificate. Only tractable for small,
     * hand-built fixtures, never a real deal.
     */
    private fun bruteForceShortestLength(state: GameState, includeFoundationWithdrawal: Boolean): Int? {
        val root = snodeFrom(state)
        if (isWon(root)) return 0
        val visited = HashSet<Long>().apply { add(hashOf(root)) }
        val queue = ArrayDeque<Pair<SNode, Int>>().apply { add(root to 0) }
        while (queue.isNotEmpty()) {
            val (node, depth) = queue.removeFirst()
            for (scored in generateMoves(node, includeFoundationWithdrawal)) {
                val next = applySearchMove(node, scored.move)
                if (isWon(next)) return depth + 1
                if (visited.add(hashOf(next))) queue.add(next to depth + 1)
            }
        }
        return null
    }

    @Test
    fun `solve matches a brute-force shortest-length reference on a position with a real choice of routes`() {
        // Nine of Hearts blocks the King underneath and can be tucked onto either
        // exposed black ten before the King is reachable — a genuine branch in the
        // search, not a single forced line — so this guards against a regression
        // that returns a longer-than-necessary certificate, not just any certificate.
        val state = GameStateFixtures.emptyBoard(seed = 42L)
            .withFoundation(Suit.CLUBS, 13)
            .withFoundation(Suit.DIAMONDS, 13)
            .withFoundation(Suit.HEARTS, 13)
            .withFoundation(Suit.SPADES, 12)
            .withTableauColumn(0, listOf(faceUp(Card(Suit.SPADES, Rank.KING)), faceUp(Card(Suit.HEARTS, Rank.NINE))))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.CLUBS, Rank.TEN))))
            .withTableauColumn(2, listOf(faceUp(Card(Suit.SPADES, Rank.TEN))))

        val expected = bruteForceShortestLength(state, includeFoundationWithdrawal = true)
        val outcome = Solver.solve(state, SolverLimits(), includeFoundationWithdrawal = true)

        check(outcome is SolveOutcome.Solved) { "expected a solution, got $outcome" }
        assertEquals(expected, outcome.certificate.size)
    }

    @Test
    fun `solve finds the exact optimal length when no shuffling is needed at all`() {
        // Hearts needs exactly four more cards, all already exposed in peel order
        // (ten on top down to king at the bottom) with nothing else in play: the
        // true shortest solution is exactly the heuristic's own lower bound.
        val state = GameStateFixtures.emptyBoard(seed = 7L)
            .withFoundation(Suit.CLUBS, 13)
            .withFoundation(Suit.DIAMONDS, 13)
            .withFoundation(Suit.SPADES, 13)
            .withFoundation(Suit.HEARTS, 9)
            .withTableauColumn(
                0,
                listOf(
                    faceUp(Card(Suit.HEARTS, Rank.KING)),
                    faceUp(Card(Suit.HEARTS, Rank.QUEEN)),
                    faceUp(Card(Suit.HEARTS, Rank.JACK)),
                    faceUp(Card(Suit.HEARTS, Rank.TEN)),
                ),
            )

        val outcome = Solver.solve(state, SolverLimits())

        check(outcome is SolveOutcome.Solved) { "expected a solution, got $outcome" }
        assertEquals(4, outcome.certificate.size)
        assertEquals(4, foundationDeficit(snodeFrom(state)))
    }

    @Test
    fun `solve reports Unsolved for a genuinely dead position`() {
        // Every top card outranks its neighbors by more than one and no column is
        // empty: no tableau move, no foundation move, and stock/waste are both empty,
        // so nothing can ever happen from here.
        val state = GameStateFixtures.emptyBoard(seed = 9L)
            .withTableauColumn(0, listOf(faceUp(Card(Suit.CLUBS, Rank.THREE))))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.DIAMONDS, Rank.FIVE))))
            .withTableauColumn(2, listOf(faceUp(Card(Suit.CLUBS, Rank.SEVEN))))
            .withTableauColumn(3, listOf(faceUp(Card(Suit.DIAMONDS, Rank.NINE))))
            .withTableauColumn(4, listOf(faceUp(Card(Suit.CLUBS, Rank.JACK))))
            .withTableauColumn(5, listOf(faceUp(Card(Suit.DIAMONDS, Rank.KING))))
            .withTableauColumn(6, listOf(faceUp(Card(Suit.HEARTS, Rank.THREE))))

        val outcome = Solver.solve(state, SolverLimits())

        assertTrue(outcome is SolveOutcome.Unsolved)
    }

    @Test
    fun `solveWithCache reuses the dead-state cache across calls on the same board`() {
        val state = GameStateFixtures.emptyBoard(seed = 9L)
            .withTableauColumn(0, listOf(faceUp(Card(Suit.CLUBS, Rank.THREE))))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.DIAMONDS, Rank.FIVE))))
            .withTableauColumn(2, listOf(faceUp(Card(Suit.CLUBS, Rank.SEVEN))))
            .withTableauColumn(3, listOf(faceUp(Card(Suit.DIAMONDS, Rank.NINE))))
            .withTableauColumn(4, listOf(faceUp(Card(Suit.CLUBS, Rank.JACK))))
            .withTableauColumn(5, listOf(faceUp(Card(Suit.DIAMONDS, Rank.KING))))
            .withTableauColumn(6, listOf(faceUp(Card(Suit.HEARTS, Rank.THREE))))
        val deadCache = LongHashSet()

        val first = Solver.solveWithCache(state, SolverLimits(), deadCache)
        val second = Solver.solveWithCache(state, SolverLimits(), deadCache)

        check(first is SolveOutcome.Unsolved && second is SolveOutcome.Unsolved)
        assertTrue("cache should make the repeat call at least as cheap", second.nodes <= first.nodes)
        assertTrue("the dead board's own canonical state should be cached", !deadCache.isEmpty())
    }

    /**
     * The first draw-three batch takes 3 filler cards, leaving a useless card on top
     * (nothing to play — just clears the way). The second batch takes the three
     * Hearts cards stored *descending* (KING first/deepest, JACK last/topmost):
     * draw-three reverses whatever it takes, so the reveal order comes out
     * *ascending* — JACK on top, then QUEEN, then KING — exactly playable in that
     * order. Proves the search's own draw-three modeling ([applySearchMovePacked]'s
     * `TYPE_DRAW` branch, [movesLowerBound]'s stock term) matches the real engine
     * closely enough to find a line at all, and — since every certificate is
     * independently replayed through `applyMove` before being trusted — that the two
     * agree exactly on card order, not just move count.
     */
    private fun drawThreeState() = GameStateFixtures.emptyBoard(seed = 20L)
        .withFoundation(Suit.CLUBS, 13)
        .withFoundation(Suit.DIAMONDS, 13)
        .withFoundation(Suit.SPADES, 13)
        .withFoundation(Suit.HEARTS, 10)
        .withStock(
            listOf(
                Card(Suit.SPADES, Rank.TWO), Card(Suit.CLUBS, Rank.THREE), Card(Suit.DIAMONDS, Rank.FOUR),
                Card(Suit.HEARTS, Rank.KING), Card(Suit.HEARTS, Rank.QUEEN), Card(Suit.HEARTS, Rank.JACK),
            ),
        )
        .copy(drawMode = DrawMode.THREE)

    @Test
    fun `solve finds a winning line on a draw-three board, and it replay-validates through the real engine`() {
        val state = drawThreeState()

        val outcome = Solver.solve(state, SolverLimits())

        check(outcome is SolveOutcome.Solved) { "expected a solution, got $outcome" }
        var replayed = state
        for (move in outcome.certificate) {
            check(isLegal(replayed, move)) { "certificate step $move illegal against $replayed" }
            replayed = applyMove(replayed, move)
        }
        assertTrue(replayed.foundations.values.all { it == Rank.KING.value })
    }

    @Test
    fun `movesLowerBound counts draw-three stock as ceil(stock size over 3) draws, not one per card`() {
        val node = snodeFrom(drawThreeState())

        // 4 stock cards at drawCount 3 clear in 2 draws (3 then 1), not 4.
        assertEquals(2, (node.stockSize + 3 - 1) / 3)
        val withThree = movesLowerBound(node, drawCount = 3)
        val withOne = movesLowerBound(node, drawCount = 1)
        assertEquals(withOne - node.stockSize + 2, withThree)
    }

    @Test
    fun `search ordering coefficients are independent instead of being multiplied through one outer weight`() {
        val node = snodeFrom(drawThreeState())
        val downCount = node.downCounts.sum()
        val ordering = SearchOrdering(
            lowerBoundWeight = 10,
            downCardWeight = 7,
            aceBurialWeight = 3,
            neededCardDepthWeight = 5,
        )

        assertEquals(
            10 * movesLowerBound(node, drawCount = 3) +
                7 * downCount +
                3 * aceBurialCount(node) +
                5 * neededCardDepth(node),
            orderingScore(node, downCount, ordering, drawCount = 3),
        )
    }
}
