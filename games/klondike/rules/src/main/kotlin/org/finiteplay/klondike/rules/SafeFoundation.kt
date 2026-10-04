package org.finiteplay.klondike.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.Suit

/**
 * The single safe-foundation predicate shared by tap priority, hints, and automation:
 * Aces and Twos are always safe; a higher rank is safe only once every suit has
 * already placed the previous rank.
 */
fun isSafeFoundationMove(foundations: Map<Suit, Int>, card: Card): Boolean {
    if (card.rank.value <= 2) return true
    val previousRank = card.rank.value - 1
    return foundations.values.all { it >= previousRank }
}
