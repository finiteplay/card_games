package org.finiteplay.holdem.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluatorSmokeTest {
    private fun hand(vararg s: String): List<Card> = s.map {
        val rank = when (it[0]) { 'A' -> Rank.ACE; 'K' -> Rank.KING; 'Q' -> Rank.QUEEN; 'J' -> Rank.JACK; 'T' -> Rank.TEN; else -> Rank.fromValue(it[0].digitToInt()) }
        val suit = when (it[1]) { 'c' -> Suit.CLUBS; 'd' -> Suit.DIAMONDS; 'h' -> Suit.HEARTS; else -> Suit.SPADES }
        Card(suit, rank)
    }

    @Test
    fun `the wheel is the lowest straight and beats three of a kind`() {
        val wheel = evaluate(hand("Ac", "2d", "3h", "4s", "5c"))
        assertEquals(HandCategory.STRAIGHT, categoryOf(wheel))
        assertTrue(wheel < evaluate(hand("6c", "2d", "3h", "4s", "5c")))
        assertTrue(wheel > evaluate(hand("9c", "9d", "9h", "4s", "5c")))
    }

    @Test
    fun `a sixth and seventh card never break a tie and suits never rank`() {
        assertEquals(evaluate(hand("Ac", "Kd", "9h", "4s", "2c")), evaluate(hand("Ad", "Kh", "9s", "4c", "2d")))
        assertEquals(
            evaluate(hand("Ac", "Ad", "Kh", "Qs", "Jc", "3d", "2h")),
            evaluate(hand("Ac", "Ad", "Kh", "Qs", "Jc", "4d", "5h")),
        )
    }
}
