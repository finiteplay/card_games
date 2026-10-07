package org.finiteplay.holdem.ui

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.holdem.rules.HandCategory
import org.finiteplay.holdem.rules.HandValue
import org.finiteplay.holdem.rules.categoryOf
import org.finiteplay.holdem.rules.evaluate

/** An ace plays high everywhere but the wheel, where [handName] says five. */
private fun Rank.high(): Int = if (this == Rank.ACE) 14 else value

/**
 * The five cards that make [value] out of [cards] (the hole cards and the board): the first
 * combination whose own value equals it, so the cards raised at showdown always are the hand named.
 */
fun bestFive(cards: List<Card>, value: HandValue): List<Card> {
    require(cards.size in 5..7) { "a hand is the best five of 5 to 7 cards, was ${cards.size}" }
    val n = cards.size
    for (a in 0 until n - 4) for (b in a + 1 until n - 3) for (c in b + 1 until n - 2) for (d in c + 1 until n - 1) for (e in d + 1 until n) {
        val five = listOf(cards[a], cards[b], cards[c], cards[d], cards[e])
        if (evaluate(five) == value) return five
    }
    error("no five cards of $cards make $value")
}

/**
 * A hand's name as data: its category and the ranks the name quotes — `primary` and, for a full house
 * and two pair, `secondary`. A royal flush is a straight flush whose top rank is the ace.
 */
data class HandName(val category: HandCategory, val primary: Rank, val secondary: Rank? = null) {
    val royal: Boolean get() = category == HandCategory.STRAIGHT_FLUSH && primary == Rank.ACE
}

/** The name of the hand [value] that [cards] make: `Flush, king high`, `Two pair, aces and sevens`. */
fun handName(cards: List<Card>, value: HandValue): HandName {
    val five = bestFive(cards, value)
    val category = categoryOf(value)
    val byRank = five.groupBy { it.rank }
    fun ranksWith(count: Int) = byRank.filterValues { it.size == count }.keys.sortedByDescending { it.high() }
    val top = five.maxBy { it.rank.high() }.rank
    // A-2-3-4-5 is the lowest straight, five high.
    val straightTop = if (five.map { it.rank }.toSet() == setOf(Rank.ACE, Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE)) Rank.FIVE else top
    return when (category) {
        HandCategory.STRAIGHT_FLUSH, HandCategory.STRAIGHT -> HandName(category, straightTop)
        HandCategory.FOUR_OF_A_KIND -> HandName(category, ranksWith(4).first())
        HandCategory.FULL_HOUSE -> HandName(category, ranksWith(3).first(), ranksWith(2).first())
        HandCategory.THREE_OF_A_KIND -> HandName(category, ranksWith(3).first())
        HandCategory.TWO_PAIR -> ranksWith(2).let { HandName(category, it[0], it[1]) }
        HandCategory.PAIR -> HandName(category, ranksWith(2).first())
        HandCategory.FLUSH, HandCategory.HIGH_CARD -> HandName(category, top)
    }
}
