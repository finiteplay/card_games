package org.finiteplay.blackjack.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank

/** A card's value with an Ace counted as 1: 2–10 their rank, the face cards 10 (`RULES.md` "Hand Values"). */
fun hardValue(rank: Rank): Int = minOf(rank.value, 10)

/** Whether [rank] is one of the ten-value cards: the 10, Jack, Queen and King. */
fun isTenValue(rank: Rank): Boolean = hardValue(rank) == 10

/**
 * A hand's [total] — the highest sum that does not exceed 21, or the all-Aces-as-1 sum when even
 * that does — and whether it is [soft], meaning an Ace is counting 11 in it.
 */
data class HandValue(val total: Int, val soft: Boolean) {
    val busted: Boolean get() = total > 21
}

fun handValue(cards: List<Card>): HandValue {
    var sum = 0
    var aces = 0
    for (card in cards) {
        sum += hardValue(card.rank)
        if (card.rank == Rank.ACE) aces++
    }
    // At most one Ace can count 11: two would make 22 (`RULES.md` "Hand Values").
    return if (aces > 0 && sum + 10 <= 21) HandValue(sum + 10, soft = true) else HandValue(sum, soft = false)
}

/** An Ace and a ten-value card, two cards exactly. Whether it *pays* as blackjack depends on where it came from. */
fun isBlackjackShape(cards: List<Card>): Boolean =
    cards.size == 2 && cards.any { it.rank == Rank.ACE } && cards.any { isTenValue(it.rank) }
