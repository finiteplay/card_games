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
import org.finiteplay.spider.rules.applyMove
import org.finiteplay.spider.rules.isLegal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

/**
 * [SpiderSolver.certifyStreamlined] is built on an unsound key ([streamlinedHashOf]) specifically
 * so it can merge more positions than [canonicalHashOf] safely can — but that soundness gap is only
 * safe for the codebase to carry if the one thing an unsound search must never do is structurally
 * impossible, not just documented: reporting [SolveResult.EXHAUSTED]. These tests pin that contract
 * directly, the way [CanonicalHashTest] pins the sound key's own invariance.
 */
class StreamlinedSearchTest {

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

    /**
     * A board with no legal move anywhere and no stock: a genuine dead end, and exactly the shape
     * of position where the plain, sound [SpiderSolver.solve] would (correctly) report
     * [SolveResult.EXHAUSTED]. The streamlined search must not, even here — the guarantee is
     * structural, not "usually true".
     */
    private fun deadEndState(): SpiderState = boardOf(
        columns = (0 until TABLEAU_COLUMNS).map {
            listOf(Card(Suit.SPADES, Rank.ACE), Card(Suit.HEARTS, Rank.ACE))
        },
    )

    @Test
    fun `never reports EXHAUSTED, even on a board the exact search does`() {
        val limits = SolverLimits(maxNodes = 10_000, maxMillis = 2_000, playouts = 0)
        val state = deadEndState()

        val exact = SpiderSolver(limits).solve(state)
        assertEquals("fixture must actually be a dead end", SolveResult.EXHAUSTED, exact.result)

        val streamlined = SpiderSolver(limits).certifyStreamlined(state, millisBudget = 2_000)
        assertNotEquals(SolveResult.EXHAUSTED, streamlined.result)
        assertEquals(SolveResult.LIMIT, streamlined.result)
    }

    @Test
    fun `a line it finds is real and replays to a win on the real reducer`() {
        val ranks = Rank.entries.reversed()
        val state = boardOf(
            columns = listOf(
                ranks.dropLast(6).map { Card(Suit.SPADES, it) },
                ranks.takeLast(6).map { Card(Suit.SPADES, it) },
            ),
            banked = mapOf(Suit.SPADES to FastBoard.SEQUENCES_TO_WIN - 1),
        )
        val limits = SolverLimits(maxNodes = 10_000, maxMillis = 2_000, playouts = 0)
        val record = IntArrayList(8)

        val outcome = SpiderSolver(limits).certifyStreamlined(state, millisBudget = 2_000, record = record)

        assertEquals(SolveResult.SOLVED_BY_SEARCH, outcome.result)
        var replayed = state
        for (i in 0 until record.size) {
            val move = FastMove(record[i])
            val rulesMove = if (move.isDeal) {
                org.finiteplay.spider.rules.Move.DealRow
            } else {
                org.finiteplay.spider.rules.Move.TableauToTableau(move.from, move.fromIndex, move.to)
            }
            assertTrue("move $rulesMove illegal against the board it was supposedly found from", isLegal(replayed, rulesMove))
            replayed = applyMove(replayed, rulesMove)
        }
        assertTrue("the streamlined line did not replay to a win", replayed.isWon)
    }

    @Test
    fun `discards suit entirely, unlike the sound canonical key`() {
        // Column 0 holds one Spades and one Hearts card; column 1 holds another Hearts card, so
        // swapping only column 0's Spades/Hearts labels is *not* a consistent relabelling of the
        // whole board (Hearts still means Hearts elsewhere) — exactly the case
        // `CanonicalHashTest`'s own "not a consistent relabelling" test uses to show the sound key
        // must still distinguish it.
        val board = FastBoard.from(
            boardOf(
                columns = listOf(
                    listOf(Card(Suit.SPADES, Rank.KING), Card(Suit.HEARTS, Rank.QUEEN)),
                    listOf(Card(Suit.HEARTS, Rank.TWO)),
                ),
            ),
        )
        val swapped = board.copy()
        for (i in 0 until swapped.len[0]) {
            val card = swapped.cards[0][i]
            val suit = card.toInt() / FastBoard.RANKS
            val rank = card.toInt() % FastBoard.RANKS
            val newSuit = when (suit) { Suit.SPADES.ordinal -> Suit.HEARTS.ordinal; Suit.HEARTS.ordinal -> Suit.SPADES.ordinal; else -> suit }
            swapped.cards[0][i] = (newSuit * FastBoard.RANKS + rank).toByte()
        }

        val scratch = HashScratch()
        assertNotEquals(
            "the sound key must still distinguish an inconsistent suit change",
            canonicalHashOf(board, scratch),
            canonicalHashOf(swapped, scratch),
        )
        assertEquals(
            "the streamlined key discards suit, so the same change must not matter to it",
            streamlinedHashOf(board, scratch),
            streamlinedHashOf(swapped, scratch),
        )
    }
}
