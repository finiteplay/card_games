package org.finiteplay.klondike.rules

import org.finiteplay.klondike.board.GameStateFixtures
import org.finiteplay.klondike.board.GameStateFixtures.faceUp
import org.finiteplay.klondike.board.GameStateFixtures.withTableauColumn
import org.finiteplay.klondike.board.GameStateFixtures.withWaste
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationTest {

    private fun board() = GameStateFixtures.emptyBoard()

    @Test
    fun `checks the waste before tableau columns`() {
        val state = board()
            .withWaste(listOf(Card(Suit.HEARTS, Rank.ACE)))
            .withTableauColumn(0, listOf(faceUp(Card(Suit.CLUBS, Rank.ACE))))

        assertEquals(Move.WasteToFoundation, findNextSafeAutomaticMove(state))
    }

    @Test
    fun `checks tableau columns left to right`() {
        val state = board()
            .withTableauColumn(3, listOf(faceUp(Card(Suit.CLUBS, Rank.ACE))))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.HEARTS, Rank.ACE))))

        assertEquals(Move.TableauToFoundation(fromColumn = 1), findNextSafeAutomaticMove(state))
    }

    @Test
    fun `never returns the parked card`() {
        val parked = Card(Suit.HEARTS, Rank.ACE)
        val state = board()
            .withTableauColumn(0, listOf(faceUp(parked)))
            .copy(parkedCard = parked)

        assertNull(findNextSafeAutomaticMove(state))
    }

    @Test
    fun `cascades until no safe card remains, one move at a time counts as one move`() {
        val state = board()
            .withTableauColumn(0, listOf(faceUp(Card(Suit.CLUBS, Rank.ACE))))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.CLUBS, Rank.TWO))))
            .withTableauColumn(2, listOf(faceUp(Card(Suit.HEARTS, Rank.ACE))))

        val (result, applied) = runAutomaticFoundationCascade(state)

        assertEquals(3, applied.size)
        assertEquals(3, result.moveCount)
        assertEquals(Rank.TWO.value, result.foundations.getValue(Suit.CLUBS))
        assertEquals(Rank.ACE.value, result.foundations.getValue(Suit.HEARTS))
        assertTrue(result.tableau.all { it.isEmpty() })
    }

    @Test
    fun `is deterministic for the same board`() {
        val state = board()
            .withTableauColumn(0, listOf(faceUp(Card(Suit.CLUBS, Rank.ACE))))
            .withTableauColumn(4, listOf(faceUp(Card(Suit.SPADES, Rank.ACE))))

        val (first, firstMoves) = runAutomaticFoundationCascade(state)
        val (second, secondMoves) = runAutomaticFoundationCascade(state)

        assertEquals(firstMoves, secondMoves)
        assertEquals(first, second)
    }

    @Test
    fun `dealGame starts at zero moves regardless of automation, since setup automation no longer runs at deal time`() {
        val versions = GameStateFixtures.TEST_VERSIONS
        val raw = dealGame(seed = 42L, versions = versions)

        assertEquals(0, raw.moveCount)
    }
}
