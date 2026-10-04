package org.finiteplay.klondike.rules

import org.finiteplay.klondike.board.GameStateFixtures
import org.finiteplay.klondike.board.GameStateFixtures.faceDown
import org.finiteplay.klondike.board.GameStateFixtures.faceUp
import org.finiteplay.klondike.board.GameStateFixtures.withFoundation
import org.finiteplay.klondike.board.GameStateFixtures.withTableauColumn
import org.finiteplay.klondike.board.GameStatus
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoFinishTest {

    private fun board() = GameStateFixtures.emptyBoard()

    @Test
    fun `a fully revealed board whose sweep completes can auto-finish`() {
        var state = board()
        for (suit in Suit.entries) state = state.withFoundation(suit, Rank.QUEEN.value)
        state = state
            .withTableauColumn(0, listOf(faceUp(Card(Suit.CLUBS, Rank.KING))))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.DIAMONDS, Rank.KING))))
            .withTableauColumn(2, listOf(faceUp(Card(Suit.HEARTS, Rank.KING))))
            .withTableauColumn(3, listOf(faceUp(Card(Suit.SPADES, Rank.KING))))

        assertTrue(canAutoFinish(state))
        val result = simulateSweep(state)
        assertEquals(GameStatus.WON, result?.status)
        assertEquals(4, result!!.moveCount)
    }

    @Test
    fun `a fully revealed board whose sweep blocks cannot auto-finish, and play continues normally`() {
        // Spades Two sits under Hearts Ace with no Spades Ace anywhere to unblock it.
        val state = board().withTableauColumn(
            0,
            listOf(faceUp(Card(Suit.SPADES, Rank.TWO)), faceUp(Card(Suit.HEARTS, Rank.ACE))),
        )

        assertFalse(canAutoFinish(state))
        assertNull(simulateSweep(state))
    }

    @Test
    fun `a board with any face-down card cannot auto-finish even if the sweep would work`() {
        var state = board()
        for (suit in Suit.entries) state = state.withFoundation(suit, Rank.QUEEN.value)
        state = state
            .withTableauColumn(0, listOf(faceUp(Card(Suit.CLUBS, Rank.KING))))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.DIAMONDS, Rank.KING))))
            .withTableauColumn(2, listOf(faceUp(Card(Suit.HEARTS, Rank.KING))))
            .withTableauColumn(3, listOf(faceDown(Card(Suit.SPADES, Rank.KING))))

        assertFalse(canAutoFinish(state))
    }

    @Test
    fun `the sweep ignores the parked card`() {
        var state = board()
        for (suit in Suit.entries) state = state.withFoundation(suit, if (suit == Suit.HEARTS) Rank.QUEEN.value else Rank.KING.value)
        val parked = Card(Suit.HEARTS, Rank.KING)
        state = state.withTableauColumn(0, listOf(faceUp(parked))).copy(parkedCard = parked)

        assertTrue(canAutoFinish(state))
        assertEquals(GameStatus.WON, simulateSweep(state)?.status)
    }
}
