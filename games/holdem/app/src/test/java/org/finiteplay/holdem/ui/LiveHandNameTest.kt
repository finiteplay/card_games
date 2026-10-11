package org.finiteplay.holdem.ui

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.holdem.rules.HandCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveHandNameTest {
    private fun c(rank: Rank, suit: Suit) = Card(suit, rank)

    @Test
    fun `before the flop only a pocket pair is a hand`() {
        val pair = liveHandName(listOf(c(Rank.KING, Suit.SPADES), c(Rank.KING, Suit.HEARTS)), emptyList())
        assertEquals(HandName(HandCategory.PAIR, Rank.KING), pair)
        assertNull(liveHandName(listOf(c(Rank.KING, Suit.SPADES), c(Rank.QUEEN, Suit.SPADES)), emptyList()))
    }

    @Test
    fun `from the flop it names the best five of the hole cards and the board`() {
        val hole = listOf(c(Rank.ACE, Suit.SPADES), c(Rank.SEVEN, Suit.HEARTS))
        val flop = listOf(c(Rank.ACE, Suit.CLUBS), c(Rank.SEVEN, Suit.DIAMONDS), c(Rank.TWO, Suit.CLUBS))
        assertEquals(HandName(HandCategory.TWO_PAIR, Rank.ACE, Rank.SEVEN), liveHandName(hole, flop))
        val river = flop + listOf(c(Rank.NINE, Suit.HEARTS), c(Rank.ACE, Suit.HEARTS))
        assertEquals(HandName(HandCategory.FULL_HOUSE, Rank.ACE, Rank.SEVEN), liveHandName(hole, river))
    }

    @Test
    fun `unpaired cards on the flop are a high card, named by the top rank`() {
        val hole = listOf(c(Rank.KING, Suit.SPADES), c(Rank.NINE, Suit.HEARTS))
        val flop = listOf(c(Rank.FOUR, Suit.CLUBS), c(Rank.SEVEN, Suit.DIAMONDS), c(Rank.TWO, Suit.CLUBS))
        assertEquals(HandName(HandCategory.HIGH_CARD, Rank.KING), liveHandName(hole, flop))
    }
}
