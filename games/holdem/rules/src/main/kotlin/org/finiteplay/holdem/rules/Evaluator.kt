package org.finiteplay.holdem.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank

/** `RULES.md` "Hand Rankings", lowest first so `ordinal` orders the categories. */
enum class HandCategory { HIGH_CARD, PAIR, TWO_PAIR, THREE_OF_A_KIND, STRAIGHT, FLUSH, FULL_HOUSE, FOUR_OF_A_KIND, STRAIGHT_FLUSH }

/**
 * A hand's strength: a bigger [strength] beats a smaller one, equal strengths tie, and nothing
 * else — suits never break a tie. The number itself is not a contract; only its order is.
 */
@JvmInline
value class HandValue(val strength: Int) : Comparable<HandValue> {
    override fun compareTo(other: HandValue): Int = strength.compareTo(other.strength)
}

/** Ace-high rank 14 down to deuce 2; an Ace is also 1 at the bottom of a wheel. */
internal fun Rank.high(): Int = if (this == Rank.ACE) 14 else value

/**
 * The best five-card hand among 5 to 7 [cards]. This is the reference implementation: small enough
 * to be obviously right, and the oracle a faster one is held to. It may be replaced behind this
 * signature.
 */
fun evaluate(cards: List<Card>): HandValue {
    require(cards.size in 5..7) { "a hand is the best five of 5 to 7 cards, was ${cards.size}" }
    var best = -1
    val n = cards.size
    for (a in 0 until n - 4) for (b in a + 1 until n - 3) for (c in b + 1 until n - 2)
        for (d in c + 1 until n - 1) for (e in d + 1 until n) {
            val score = scoreFive(listOf(cards[a], cards[b], cards[c], cards[d], cards[e]))
            if (score > best) best = score
        }
    return HandValue(best)
}

/** The category of the best hand: what its name is called at showdown. */
fun categoryOf(value: HandValue): HandCategory = HandCategory.entries[value.strength shr 20]

private fun scoreFive(five: List<Card>): Int {
    val ranks = five.map { it.rank.high() }.sortedDescending()
    val flush = five.all { it.suit == five[0].suit }
    val counts = ranks.groupingBy { it }.eachCount()
    // Group by count then rank: the ranks that decide the comparison, most important first.
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
