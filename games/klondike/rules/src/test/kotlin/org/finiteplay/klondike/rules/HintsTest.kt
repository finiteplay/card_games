package org.finiteplay.klondike.rules

import org.finiteplay.klondike.board.GameStateFixtures
import org.finiteplay.klondike.board.GameStateFixtures.faceDown
import org.finiteplay.klondike.board.GameStateFixtures.faceUp
import org.finiteplay.klondike.board.GameStateFixtures.withFoundation
import org.finiteplay.klondike.board.GameStateFixtures.withStock
import org.finiteplay.klondike.board.GameStateFixtures.withTableauColumn
import org.finiteplay.klondike.board.GameStateFixtures.withWaste
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HintsTest {

    private fun board() = GameStateFixtures.emptyBoard()

    @Test
    fun `step 1 - revealing a hidden card beats a safe foundation move elsewhere`() {
        val hidden = Card(Suit.CLUBS, Rank.KING)
        val moved = Card(Suit.SPADES, Rank.SIX)
        val state = board()
            .withTableauColumn(0, listOf(faceDown(hidden), faceUp(moved)))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))
            .withTableauColumn(2, listOf(faceUp(Card(Suit.DIAMONDS, Rank.ACE))))

        assertEquals(Move.TableauToTableau(0, 1, 1), findHint(state))
    }

    @Test
    fun `step 2 - a safe foundation move beats waste-to-tableau`() {
        val state = board()
            .withWaste(listOf(Card(Suit.CLUBS, Rank.ACE)))
            .withTableauColumn(0, listOf(faceUp(Card(Suit.HEARTS, Rank.TWO))))

        assertEquals(Move.WasteToFoundation, findHint(state))
    }

    @Test
    fun `step 3 - waste to tableau is offered when the waste card is not safe for foundation`() {
        // Clubs is at Two, so Clubs Three is legal, but unsafe: the other suits haven't
        // placed their Twos yet. Twos and Aces are always safe, so this needs rank 3.
        val state = board()
            .withFoundation(Suit.CLUBS, Rank.TWO.value)
            .withWaste(listOf(Card(Suit.CLUBS, Rank.THREE)))
            .withTableauColumn(0, listOf(faceUp(Card(Suit.HEARTS, Rank.FOUR))))

        assertEquals(Move.WasteToTableau(0), findHint(state))
    }

    @Test
    fun `step 4 - the tableau move producing the longest resulting run is preferred`() {
        val moved = Card(Suit.SPADES, Rank.SIX)
        val state = board()
            // Hearts Nine underneath breaks the run at Spades Six, so this move never
            // empties the column and the emptied-column gate does not apply here.
            .withTableauColumn(0, listOf(faceUp(Card(Suit.HEARTS, Rank.NINE)), faceUp(moved)))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))
            .withTableauColumn(2, listOf(
                faceUp(Card(Suit.DIAMONDS, Rank.NINE)),
                faceUp(Card(Suit.CLUBS, Rank.EIGHT)),
                faceUp(Card(Suit.HEARTS, Rank.SEVEN)),
            ))

        // Both columns 1 and 2 legally accept Spades Six, but column 2's run (9,8,7)
        // yields a longer resulting alternating run (9,8,7,6) than column 1's (7,6).
        assertEquals(Move.TableauToTableau(0, 1, 2), findHint(state))
    }

    @Test
    fun `step 5 - draw or recycle is offered only once nothing else applies`() {
        // Hearts Five: not a King (no empty-column destination) and not foundation-eligible.
        val withStock = board().withStock(listOf(Card(Suit.HEARTS, Rank.FIVE)))
        assertEquals(Move.Draw, findHint(withStock))

        val withOnlyWaste = board().withWaste(listOf(Card(Suit.HEARTS, Rank.FIVE)))
        assertEquals(Move.Recycle, findHint(withOnlyWaste))
    }

    @Test
    fun `no legal move anywhere yields no hint`() {
        assertNull(findHint(board()))
    }

    @Test
    fun `ties break by source pile order then destination pile order`() {
        // Both waste and column 3's top are safe Aces; waste (pile order 0) must win.
        val state = board()
            .withWaste(listOf(Card(Suit.CLUBS, Rank.ACE)))
            .withTableauColumn(3, listOf(faceUp(Card(Suit.HEARTS, Rank.ACE))))

        assertEquals(Move.WasteToFoundation, findHint(state))
    }

    @Test
    fun `the inverse of the previous move is skipped when another candidate exists`() {
        val movedCard = Card(Suit.SPADES, Rank.SIX)
        val state = board()
            .withTableauColumn(0, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN)), faceUp(movedCard)))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))
            .withTableauColumn(2, listOf(faceUp(Card(Suit.DIAMONDS, Rank.SEVEN))))
        val previousMove = Move.TableauToTableau(fromColumn = 1, fromIndex = 0, toColumn = 0)

        // The naive best move would send column 0's Six back to column 1 (the reversal);
        // column 2 offers an equally-legal alternative destination for the same run.
        val hint = findHint(state, previousMove)

        assertEquals(Move.TableauToTableau(0, 1, 2), hint)
    }

    @Test
    fun `the inverse of the previous move is still offered when it is the only candidate`() {
        val movedCard = Card(Suit.SPADES, Rank.SIX)
        val state = board()
            .withTableauColumn(0, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN)), faceUp(movedCard)))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))
        val previousMove = Move.TableauToTableau(fromColumn = 1, fromIndex = 0, toColumn = 0)

        assertEquals(Move.TableauToTableau(0, 1, 1), findHint(state, previousMove))
    }

    @Test
    fun `finding a hint never mutates state or score`() {
        val state = board().withWaste(listOf(Card(Suit.CLUBS, Rank.ACE)))
        val before = state.copy()

        findHint(state)
        findHint(state)

        assertEquals(before, state)
    }
}
