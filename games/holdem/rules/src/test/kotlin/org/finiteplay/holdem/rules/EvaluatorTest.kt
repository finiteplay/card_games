package org.finiteplay.holdem.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class EvaluatorTest {
    private fun hand(vararg s: String): List<Card> = s.map {
        val rank = when (it[0]) { 'A' -> Rank.ACE; 'K' -> Rank.KING; 'Q' -> Rank.QUEEN; 'J' -> Rank.JACK; 'T' -> Rank.TEN; else -> Rank.fromValue(it[0].digitToInt()) }
        val suit = when (it[1]) { 'c' -> Suit.CLUBS; 'd' -> Suit.DIAMONDS; 'h' -> Suit.HEARTS; else -> Suit.SPADES }
        Card(suit, rank)
    }

    private fun v(vararg s: String) = evaluate(hand(*s))

    @Test
    fun `all 2,598,960 five-card hands give 7,462 values in the standard counts and the oracle's order`() {
        val oracleToFast = IntArray(1 shl 24) { NONE }
        val deck = Card.CANONICAL_DECK
        val counts = IntArray(HandCategory.entries.size)
        val ids = IntArray(5)
        var hands = 0
        for (a in 0 until 48) for (b in a + 1 until 49) for (c in b + 1 until 50) for (d in c + 1 until 51) for (e in d + 1 until 52) {
            ids[0] = a; ids[1] = b; ids[2] = c; ids[3] = d; ids[4] = e
            val fast = evaluate(ids)
            val oracle = ReferenceEvaluator.evaluate(listOf(deck[a], deck[b], deck[c], deck[d], deck[e]))
            val seen = oracleToFast[oracle]
            if (seen == NONE) oracleToFast[oracle] = fast.strength
            else assertEquals("oracle tie must be a tie: $a $b $c $d $e", seen, fast.strength)
            val category = categoryOf(fast)
            assertEquals(ReferenceEvaluator.categoryOrdinal(oracle), category.ordinal)
            counts[category.ordinal]++
            hands++
        }
        assertEquals(2_598_960, hands)

        var distinct = 0
        var previous = Int.MIN_VALUE
        for (oracle in oracleToFast.indices) {
            val fast = oracleToFast[oracle]
            if (fast == NONE) continue
            assertTrue("fast order must follow the oracle's, strictly between distinct oracle values", fast > previous)
            previous = fast
            distinct++
        }
        assertEquals(7_462, distinct)

        val expected = mapOf(
            HandCategory.STRAIGHT_FLUSH to 40, HandCategory.FOUR_OF_A_KIND to 624, HandCategory.FULL_HOUSE to 3_744,
            HandCategory.FLUSH to 5_108, HandCategory.STRAIGHT to 10_200, HandCategory.THREE_OF_A_KIND to 54_912,
            HandCategory.TWO_PAIR to 123_552, HandCategory.PAIR to 1_098_240, HandCategory.HIGH_CARD to 1_302_540,
        )
        for ((category, count) in expected) assertEquals(category.name, count, counts[category.ordinal])
    }

    @Test
    fun `random five to seven card hands order like the oracle`() {
        val rng = Random(20260607)
        val deck = Card.CANONICAL_DECK
        val ids = IntArray(52) { it }
        fun draw(): Pair<List<Card>, Int> {
            val n = 5 + rng.nextInt(3)
            for (i in 0 until n) { val j = i + rng.nextInt(52 - i); val t = ids[i]; ids[i] = ids[j]; ids[j] = t }
            val cards = (0 until n).map { deck[ids[it]] }
            val fast = evaluate(ids, n)
            assertEquals(evaluate(cards), fast)
            return cards to fast.strength
        }
        var (prevCards, prevFast) = draw()
        var prevOracle = ReferenceEvaluator.evaluate(prevCards)
        repeat(60_000) {
            val (cards, fast) = draw()
            val oracle = ReferenceEvaluator.evaluate(cards)
            assertEquals(oracle.compareTo(prevOracle).coerceIn(-1, 1), fast.compareTo(prevFast).coerceIn(-1, 1))
            prevOracle = oracle; prevFast = fast
        }
    }

    @Test
    fun `the wheel is the lowest straight`() {
        val wheel = v("Ac", "2d", "3h", "4s", "5c")
        assertEquals(HandCategory.STRAIGHT, categoryOf(wheel))
        assertTrue(wheel < v("2c", "3d", "4h", "5s", "6c"))
        assertTrue(wheel > v("9c", "9d", "9h", "4s", "5c"))
        assertEquals(wheel, v("Ac", "2d", "3h", "4s", "5c", "Kd", "Kh"))
    }

    @Test
    fun `a straight does not wrap around the ace`() {
        val wrap = v("Qc", "Kd", "Ac", "2h", "3s")
        assertEquals(HandCategory.HIGH_CARD, categoryOf(wrap))
        assertEquals(HandCategory.STRAIGHT, categoryOf(v("Tc", "Jd", "Qh", "Ks", "Ac")))
    }

    @Test
    fun `a board that plays for both players ties`() {
        val board = arrayOf("Tc", "Jd", "Qh", "Ks", "Ac")
        assertEquals(v(*board, "2c", "3d"), v(*board, "4h", "9s"))
        val wheelBoard = arrayOf("Ac", "2d", "3h", "4s", "5c")
        assertEquals(v(*wheelBoard, "Kc", "Kd"), v(*wheelBoard, "7h", "8s"))
    }

    @Test
    fun `kickers decide pairs and a sixth and seventh card never change a result`() {
        assertTrue(v("Ac", "Ad", "Kh", "7s", "3c") > v("Ah", "As", "Qd", "7c", "3d"))
        assertTrue(v("Ac", "Ad", "Kh", "7s", "3c") > v("Ah", "As", "Kd", "6c", "3d"))
        assertEquals(v("Ac", "Ad", "Kh", "Qs", "Jc"), v("Ac", "Ad", "Kh", "Qs", "Jc", "3d", "2h"))
        assertEquals(v("Ac", "Ad", "Kh", "Qs", "Jc", "3d", "2h"), v("Ac", "Ad", "Kh", "Qs", "Jc", "4d", "5h"))
        assertTrue(v("Kc", "Kd", "Qh", "Qs", "4c") > v("Kh", "Ks", "Qc", "Qd", "3c"))
        assertEquals(HandCategory.TWO_PAIR, categoryOf(v("Kc", "Kd", "Qh", "Qs", "4c", "3d", "3h")))
        assertEquals(v("Kc", "Kd", "Qh", "Qs", "4c"), v("Kc", "Kd", "Qh", "Qs", "4c", "3d", "3h"))
        assertTrue(v("Kc", "Kd", "Qh", "Qs", "4c", "3d", "3h") < v("Kc", "Kd", "Qh", "Qs", "5c", "3d", "3h"))
    }

    @Test
    fun `a seven-card flush beats a straight and a full house beats a flush`() {
        val straightAndFlush = v("2h", "9h", "Kh", "4h", "7h", "5c", "6d")
        assertEquals(HandCategory.FLUSH, categoryOf(straightAndFlush))
        assertTrue(straightAndFlush > v("5c", "6d", "7h", "8s", "9c"))
        assertTrue(v("3c", "3d", "3h", "9s", "9c") > straightAndFlush)
        assertEquals(HandCategory.STRAIGHT_FLUSH, categoryOf(v("2h", "3h", "4h", "5h", "6h", "7h", "Kc")))
        assertEquals(v("3h", "4h", "5h", "6h", "7h"), v("2h", "3h", "4h", "5h", "6h", "7h", "Kc"))
        assertEquals(HandCategory.FLUSH, categoryOf(v("2h", "3h", "4h", "5h", "7h", "6d", "Kc")))
    }

    @Test
    fun `a full house from two trips takes the higher trips over the lower`() {
        val two = v("9c", "9d", "9h", "4s", "4c", "4d", "Ks")
        assertEquals(HandCategory.FULL_HOUSE, categoryOf(two))
        assertEquals(v("9c", "9d", "9h", "4s", "4c"), two)
        assertTrue(two > v("8c", "8d", "8h", "As", "Ac"))
        assertTrue(two < v("9c", "9d", "9h", "5s", "5c"))
        assertEquals(HandCategory.FOUR_OF_A_KIND, categoryOf(v("9c", "9d", "9h", "9s", "4c", "4d", "4h")))
    }

    @Test
    fun `suits never break ties`() {
        assertEquals(v("Ac", "Kd", "9h", "4s", "2c"), v("Ad", "Kh", "9s", "4c", "2d"))
        assertEquals(v("Ac", "Kc", "9c", "4c", "2c"), v("Ad", "Kd", "9d", "4d", "2d"))
        assertEquals(v("Ac", "Kc", "9c", "4c", "2c"), v("Ah", "Kh", "9h", "4h", "2h"))
    }

    @Test
    fun `an id array and a card list agree and a short or long hand is refused`() {
        val cards = hand("Ac", "Kd", "9h", "4s", "2c", "2d", "7h")
        assertEquals(evaluate(cards), evaluate(cards.map { it.id }.toIntArray()))
        assertEquals(evaluate(cards.take(5)), evaluate(cards.map { it.id }.toIntArray(), 5))
        for (n in intArrayOf(4, 8)) {
            val ids = IntArray(n) { it }
            val refused = runCatching { evaluate(ids) }.exceptionOrNull()
            assertTrue(refused is IllegalArgumentException)
        }
    }

    private companion object {
        const val NONE = -1
    }
}
