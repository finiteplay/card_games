package org.finiteplay.klondike.rules

import org.finiteplay.klondike.board.GameStateFixtures
import org.finiteplay.klondike.board.GameStateFixtures.faceUp
import org.finiteplay.klondike.board.GameStateFixtures.withFoundation
import org.finiteplay.klondike.board.GameStateFixtures.withTableauColumn
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerTransitionTest {

    private fun board() = GameStateFixtures.emptyBoard()

    @Test
    fun `withdrawing a foundation card parks it`() {
        val state = board().withFoundation(Suit.HEARTS, Rank.TWO.value)

        val result = applyPlayerTransition(state, Move.FoundationToTableau(Suit.HEARTS, toColumn = 0))

        assertEquals(Card(Suit.HEARTS, Rank.TWO), result.parkedCard)
    }

    @Test
    fun `park clears when the card is covered`() {
        val parked = Card(Suit.HEARTS, Rank.TWO)
        val state = board()
            .withTableauColumn(0, listOf(faceUp(parked)))
            .copy(parkedCard = parked)
            .withTableauColumn(1, listOf(faceUp(Card(Suit.CLUBS, Rank.SIX))))

        val result = applyPlayerTransition(state, Move.TableauToTableau(fromColumn = 1, fromIndex = 0, toColumn = 0))

        assertNull(result.parkedCard)
    }

    @Test
    fun `park clears when the parked card is moved again`() {
        val parked = Card(Suit.HEARTS, Rank.TWO)
        val state = board()
            .withTableauColumn(0, listOf(faceUp(parked)))
            .copy(parkedCard = parked)
            .withFoundation(Suit.HEARTS, Rank.ACE.value)

        val result = applyPlayerTransition(state, Move.TableauToFoundation(fromColumn = 0))

        assertNull(result.parkedCard)
    }

    @Test
    fun `park survives an unrelated move elsewhere on the board`() {
        val parked = Card(Suit.HEARTS, Rank.TWO)
        val state = board()
            .withTableauColumn(0, listOf(faceUp(parked)))
            .copy(parkedCard = parked)
            .withTableauColumn(1, listOf(faceUp(Card(Suit.SPADES, Rank.KING))))

        val result = applyPlayerTransition(state, Move.TableauToTableau(fromColumn = 1, fromIndex = 0, toColumn = 2))

        assertEquals(parked, result.parkedCard)
    }
}
