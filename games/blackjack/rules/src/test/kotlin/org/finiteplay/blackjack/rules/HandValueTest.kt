package org.finiteplay.blackjack.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandValueTest {
    @Test
    fun `the examples in the rules`() {
        assertEquals(HandValue(17, soft = true), handValue(cards("AS", "6H")))
        assertEquals(HandValue(17, soft = true), handValue(cards("AS", "AH", "5C")))
        assertEquals(HandValue(17, soft = false), handValue(cards("AS", "6H", "TC")))
        assertEquals(HandValue(12, soft = true), handValue(cards("AS", "AH")))
        assertEquals(HandValue(18, soft = false), handValue(cards("TS", "6H", "AC", "AD")))
        assertEquals(HandValue(21, soft = true), handValue(cards("AS", "KH")))
        assertTrue(handValue(cards("KS", "QH", "5C")).busted)
    }

    @Test
    fun `face cards count ten and only equal ranks pair`() {
        assertEquals(10, hardValue(Rank.KING))
        assertEquals(11, hardValue(Rank.ACE) + 10)
        assertTrue(PlayerHand(cards("KS", "KH"), 10).isPair)
        assertFalse("a King and a Ten have equal value, not equal rank", PlayerHand(cards("KS", "TH"), 10).isPair)
    }

    @Test
    fun `blackjack is an Ace and a ten-value card, two cards exactly`() {
        assertTrue(isBlackjackShape(cards("AS", "KH")))
        assertTrue(isBlackjackShape(cards("TD", "AC")))
        assertFalse(isBlackjackShape(cards("AS", "5H", "5C")))
        assertFalse(isBlackjackShape(cards("AS", "9H")))
    }

    /**
     * Every multiset of card values a hand can reach, up to the twenty-one-card maximum, against a
     * brute-force evaluator that tries every way of counting each Ace as 1 or 11.
     *
     * A hand only grows while it can still take a card, so a reachable multiset has an all-Aces-as-1
     * sum of at most 21 before its last card; enumerating every multiset whose hard sum is at most
     * 31 (the 21 plus the largest last card) covers them all, and then some.
     */
    @Test
    fun `hand values match a brute-force evaluator over every reachable multiset of values`() {
        val counts = IntArray(11) // index 1..10 = value, 1 is the Ace
        var checked = 0

        fun bruteForce(): HandValue {
            val aces = counts[1]
            val others = (2..10).sumOf { it * counts[it] }
            var best = -1
            var bestSoft = false
            for (elevens in 0..aces) {
                val sum = others + (aces - elevens) + 11 * elevens
                if (sum <= 21 && sum > best) {
                    best = sum
                    bestSoft = elevens > 0
                }
            }
            if (best >= 0) return HandValue(best, bestSoft)
            return HandValue(others + aces, soft = false)
        }

        fun cardsFor(): List<Card> {
            val suits = Suit.entries
            val out = mutableListOf<Card>()
            var n = 0
            for (value in 1..10) {
                repeat(counts[value]) {
                    val rank = if (value == 10) listOf(Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING)[n % 4] else Rank.fromValue(value)
                    out += Card(suits[n % 4], rank)
                    n++
                }
            }
            return out
        }

        fun visit(value: Int, hardSum: Int, size: Int) {
            if (value > 10) {
                if (size in 1..21) {
                    assertEquals(bruteForce(), handValue(cardsFor()))
                    checked++
                }
                return
            }
            var used = 0
            while (hardSum + used * value <= 31 && size + used <= 21) {
                counts[value] = used
                visit(value + 1, hardSum + used * value, size + used)
                used++
            }
            counts[value] = 0
        }
        visit(1, 0, 0)
        assertTrue("only $checked multisets were checked", checked > 10_000)
    }

    @Test
    fun `the longest hand six decks allow is twenty-one cards, all Aces`() {
        val aces = List(21) { Card(Suit.entries[it % 4], Rank.ACE) }
        // Twenty-one Aces all count 1: an Ace counting 11 would make 31, so the hand is hard 21.
        assertEquals(HandValue(21, soft = false), handValue(aces))
        assertEquals(HandValue(22, soft = false), handValue(aces + c("AS")))
        assertEquals(HandValue(12, soft = true), handValue(aces.take(2)))
    }
}
