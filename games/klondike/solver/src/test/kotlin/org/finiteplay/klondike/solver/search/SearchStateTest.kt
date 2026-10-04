package org.finiteplay.klondike.solver.search

import org.finiteplay.klondike.board.GameStateFixtures
import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.board.GameStateFixtures.faceUp
import org.finiteplay.klondike.board.GameStateFixtures.withFoundation
import org.finiteplay.klondike.board.GameStateFixtures.withTableauColumn
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.applyMove
import org.finiteplay.klondike.rules.isLegal
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchStateTest {

    @Test
    fun `compact state matches the canonical reducer across generated move sequences`() {
        for (seed in 1L..40L) {
            var state = dealGame(seed, GameStateFixtures.TEST_VERSIONS)
            var node = snodeFrom(state)
            for (step in 0 until 160) {
                assertMatches(state, node, "seed $seed step $step")
                val moves = generateMoves(node, includeFoundationWithdrawal = true).map { it.move }
                assertTrue("seed $seed step $step generated an illegal move", moves.all { isLegal(state, it) })
                if (moves.isEmpty()) break
                val move = moves[((seed + step) % moves.size).toInt()]
                state = applyMove(state, move)
                node = applySearchMove(node, move, if (state.drawMode == DrawMode.THREE) 3 else 1)
            }
        }
    }

    @Test
    fun `draw and recycle retain the compact pile storage`() {
        val root = snodeFrom(dealGame(1L, GameStateFixtures.TEST_VERSIONS))
        var drawn = root
        while (drawn.stockSize > 0) {
            val next = applySearchMove(drawn, Move.Draw)
            assertSame("Draw should only move the stock-waste boundary", drawn.pile, next.pile)
            drawn = next
        }

        val recycled = applySearchMove(drawn, Move.Recycle)

        assertSame("Recycle should only reset the stock-waste boundary", drawn.pile, recycled.pile)
        assertEquals(0, recycled.wasteCount)
    }

    private fun assertMatches(state: org.finiteplay.klondike.board.GameState, node: SNode, label: String) {
        for (column in state.tableau.indices) {
            assertEquals(label, state.tableau[column].map { it.card.id }, node.columns[column].map { it.toInt() })
            assertEquals(label, state.tableau[column].count { !it.faceUp }, node.downCounts[column])
        }
        for ((suit, rank) in state.foundations) assertEquals(label, rank, node.foundations[suit.ordinal])
        val expectedPile = state.waste.asReversed().map { it.id } + state.stock.map { it.id }
        assertEquals(label, expectedPile, node.pile.map { it.toInt() })
        assertEquals(label, state.waste.size, node.wasteCount)
    }

    /** Clubs foundation holds a Jack; a red Queen sits alone in column 1, ready to receive it. */
    private fun withdrawableFoundation() = GameStateFixtures.emptyBoard(seed = 1L)
        .withFoundation(Suit.CLUBS, 11)
        .withTableauColumn(1, listOf(faceUp(Card(Suit.HEARTS, Rank.QUEEN))))

    @Test
    fun `generateMoves omits FoundationToTableau by default`() {
        val node = snodeFrom(withdrawableFoundation())
        val moves = generateMoves(node).map { it.move }

        assertTrue(moves.none { it is Move.FoundationToTableau })
    }

    @Test
    fun `generateMoves includes a legal FoundationToTableau when explicitly enabled`() {
        val node = snodeFrom(withdrawableFoundation())
        val moves = generateMoves(node, includeFoundationWithdrawal = true).map { it.move }

        assertTrue(moves.contains(Move.FoundationToTableau(Suit.CLUBS, toColumn = 1)))
    }

    @Test
    fun `applySearchMove withdraws the foundation card onto the destination column and lowers the foundation`() {
        val node = snodeFrom(withdrawableFoundation())
        val before = hashOf(node)

        val after = applySearchMove(node, Move.FoundationToTableau(Suit.CLUBS, toColumn = 1))

        assertEquals(10, after.foundations[Suit.CLUBS.ordinal])
        assertEquals(2, after.columns[1].size - after.downCounts[1])
        assertNotEquals(before, hashOf(after))
    }

    @Test
    fun `generateMoves produces no FoundationToTableau when no foundation card is exposed`() {
        val node = snodeFrom(GameStateFixtures.emptyBoard(seed = 2L))
        val moves = generateMoves(node, includeFoundationWithdrawal = true).map { it.move }

        assertFalse(moves.any { it is Move.FoundationToTableau })
    }

    @Test
    fun `foundationDeficit counts cards not yet banked`() {
        val allEmpty = snodeFrom(GameStateFixtures.emptyBoard(seed = 3L))
        assertEquals(52, foundationDeficit(allEmpty))

        val partlyBanked = snodeFrom(
            GameStateFixtures.emptyBoard(seed = 3L)
                .withFoundation(Suit.CLUBS, 13)
                .withFoundation(Suit.HEARTS, 5),
        )
        assertEquals(52 - 13 - 5, foundationDeficit(partlyBanked))
    }

    @Test
    fun `canonicalHashOf is the same for two states differing only by which column holds which pile`() {
        val original = GameStateFixtures.emptyBoard(seed = 4L)
            .withTableauColumn(0, listOf(faceUp(Card(Suit.CLUBS, Rank.SEVEN)), faceUp(Card(Suit.HEARTS, Rank.SIX))))
            .withTableauColumn(2, listOf(faceUp(Card(Suit.DIAMONDS, Rank.KING))))
        val columnsSwapped = GameStateFixtures.emptyBoard(seed = 4L)
            .withTableauColumn(2, listOf(faceUp(Card(Suit.CLUBS, Rank.SEVEN)), faceUp(Card(Suit.HEARTS, Rank.SIX))))
            .withTableauColumn(0, listOf(faceUp(Card(Suit.DIAMONDS, Rank.KING))))

        val originalNode = snodeFrom(original)
        val swappedNode = snodeFrom(columnsSwapped)

        assertNotEquals("sanity check: the two boards are not literally identical", hashOf(originalNode), hashOf(swappedNode))
        assertEquals(canonicalHashOf(originalNode), canonicalHashOf(swappedNode))
    }

    @Test
    fun `canonical hashing sorts scratch signatures without modifying the board`() {
        val node = snodeFrom(dealGame(78L, GameStateFixtures.TEST_VERSIONS))
        val columnsBefore = node.columns.map(ByteArray::copyOf)
        val downBefore = node.downCounts.copyOf()
        val foundationsBefore = node.foundations.copyOf()
        val pileBefore = node.pile.copyOf()
        val scratch = LongArray(7)

        canonicalHashOf(node, scratch)
        canonicalStateHashOf(node, scratch)

        assertEquals(scratch.sorted(), scratch.toList())
        for (column in node.columns.indices) assertArrayEquals(columnsBefore[column], node.columns[column])
        assertArrayEquals(downBefore, node.downCounts)
        assertArrayEquals(foundationsBefore, node.foundations)
        assertArrayEquals(pileBefore, node.pile)
    }

    @Test
    fun `canonical hashing is invariant across every tableau column permutation`() {
        val node = snodeFrom(dealGame(78L, GameStateFixtures.TEST_VERSIONS))
        val expected = canonicalHashOf(node)
        val order = IntArray(7) { it }

        fun verifyPermutations(index: Int) {
            if (index == order.size) {
                val permuted = SNode(
                    columns = Array(7) { node.columns[order[it]] },
                    downCounts = IntArray(7) { node.downCounts[order[it]] },
                    foundations = node.foundations,
                    pile = node.pile,
                    wasteCount = node.wasteCount,
                )
                assertEquals(expected, canonicalHashOf(permuted))
                return
            }
            for (swapWith in index until order.size) {
                val value = order[index]
                order[index] = order[swapWith]
                order[swapWith] = value
                verifyPermutations(index + 1)
                order[swapWith] = order[index]
                order[index] = value
            }
        }

        verifyPermutations(0)
    }

    @Test
    fun `board fingerprint excludes pile position while transposition key distinguishes it`() {
        val root = snodeFrom(dealGame(78L, GameStateFixtures.TEST_VERSIONS))
        val afterDraw = applySearchMove(root, Move.Draw)

        assertEquals(canonicalHashOf(root), canonicalHashOf(afterDraw))
        assertNotEquals(canonicalStateHashOf(root), canonicalStateHashOf(afterDraw))
    }

    @Test
    fun `canonicalHashOf still distinguishes states with genuinely different column contents`() {
        val sevenOfClubs = snodeFrom(
            GameStateFixtures.emptyBoard(seed = 5L).withTableauColumn(0, listOf(faceUp(Card(Suit.CLUBS, Rank.SEVEN)))),
        )
        val sevenOfSpades = snodeFrom(
            GameStateFixtures.emptyBoard(seed = 5L).withTableauColumn(0, listOf(faceUp(Card(Suit.SPADES, Rank.SEVEN)))),
        )

        assertNotEquals(canonicalHashOf(sevenOfClubs), canonicalHashOf(sevenOfSpades))
    }
}
