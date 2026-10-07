package org.finiteplay.holdem.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank

/**
 * The oracle the fast `evaluate` is held to: small enough to be obviously right, and slow. Its
 * numbers are its own; only order and ties are compared.
 */
object ReferenceEvaluator {
    private fun Rank.high(): Int = if (this == Rank.ACE) 14 else value

    fun categoryOrdinal(score: Int): Int = score shr 20

    fun evaluate(cards: List<Card>): Int {
        require(cards.size in 5..7)
        var best = -1
        val n = cards.size
        for (a in 0 until n - 4) for (b in a + 1 until n - 3) for (c in b + 1 until n - 2)
            for (d in c + 1 until n - 1) for (e in d + 1 until n) {
                val score = scoreFive(listOf(cards[a], cards[b], cards[c], cards[d], cards[e]))
                if (score > best) best = score
            }
        return best
    }

    private fun scoreFive(five: List<Card>): Int {
        val ranks = five.map { it.rank.high() }.sortedDescending()
        val flush = five.all { it.suit == five[0].suit }
        val counts = ranks.groupingBy { it }.eachCount()
        val ordered = counts.entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })
        val tiebreak = ordered.map { it.key }
        val distinct = ranks.distinct()
        val straightHigh = when {
            distinct.size != 5 -> 0
            distinct[0] - distinct[4] == 4 -> distinct[0]
            distinct == listOf(14, 5, 4, 3, 2) -> 5
            else -> 0
        }
        val category = when {
            straightHigh > 0 && flush -> HandCategory.STRAIGHT_FLUSH
            ordered[0].value == 4 -> HandCategory.FOUR_OF_A_KIND
            ordered[0].value == 3 && ordered[1].value == 2 -> HandCategory.FULL_HOUSE
            flush -> HandCategory.FLUSH
            straightHigh > 0 -> HandCategory.STRAIGHT
            ordered[0].value == 3 -> HandCategory.THREE_OF_A_KIND
            ordered[0].value == 2 && ordered[1].value == 2 -> HandCategory.TWO_PAIR
            ordered[0].value == 2 -> HandCategory.PAIR
            else -> HandCategory.HIGH_CARD
        }
        val keys = if (straightHigh > 0) listOf(straightHigh) else tiebreak
        var score = category.ordinal shl 20
        for ((i, key) in keys.withIndex()) score = score or (key shl (16 - 4 * i))
        return score
    }
}
