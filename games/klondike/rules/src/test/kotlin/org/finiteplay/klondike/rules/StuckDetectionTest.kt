package org.finiteplay.klondike.rules

import org.finiteplay.klondike.board.GameStateFixtures
import org.finiteplay.klondike.board.GameStateFixtures.faceUp
import org.finiteplay.klondike.board.GameStateFixtures.withStock
import org.finiteplay.klondike.board.GameStateFixtures.withTableauColumn
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StuckDetectionTest {

    /** Seven mutually non-stacking tops, none Ace-eligible, with empty stock and waste. */
    private fun deadEndBoard() = GameStateFixtures.emptyBoard()
        .withTableauColumn(0, listOf(faceUp(Card(Suit.CLUBS, Rank.THREE))))
        .withTableauColumn(1, listOf(faceUp(Card(Suit.CLUBS, Rank.FIVE))))
        .withTableauColumn(2, listOf(faceUp(Card(Suit.CLUBS, Rank.SEVEN))))
        .withTableauColumn(3, listOf(faceUp(Card(Suit.CLUBS, Rank.NINE))))
        .withTableauColumn(4, listOf(faceUp(Card(Suit.CLUBS, Rank.JACK))))
        .withTableauColumn(5, listOf(faceUp(Card(Suit.CLUBS, Rank.KING))))
        .withTableauColumn(6, listOf(faceUp(Card(Suit.DIAMONDS, Rank.THREE))))

    @Test
    fun `no legal move and no stock cards is stuck`() {
        assertTrue(isStuck(deadEndBoard()))
    }

    @Test
    fun `a drawable card that would unlock a move is not stuck`() {
        val state = deadEndBoard().withStock(listOf(Card(Suit.HEARTS, Rank.ACE)))
        assertFalse(isStuck(state))
    }

    @Test
    fun `a drawable card that unlocks nothing anywhere in the cycle is still stuck`() {
        val state = deadEndBoard().withStock(
            listOf(Card(Suit.HEARTS, Rank.THREE), Card(Suit.SPADES, Rank.NINE)),
        )
        assertTrue(isStuck(state))
    }

    @Test
    fun `never a false positive while a legal move remains`() {
        val state = deadEndBoard().withTableauColumn(6, listOf(faceUp(Card(Suit.HEARTS, Rank.FOUR))))
        // Hearts Four (red) can stack onto Clubs Five (black) in column 1.
        assertFalse(isStuck(state))
    }

    @Test
    fun `a freshly dealt board is never stuck`() {
        for (seed in 0 until 10) {
            assertFalse(isStuck(dealGame(seed.toLong(), GameStateFixtures.TEST_VERSIONS)))
        }
    }
}
