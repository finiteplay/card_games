package org.finiteplay.klondike.rules

import org.finiteplay.klondike.board.GameState
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank

/**
 * Returns a description of every invariant [state] violates, or an empty list when the
 * state is internally consistent: exactly the 52 unique cards, each owned by exactly
 * one pile, valid face states, foundations built Ace-up with no gaps, and every
 * face-up tableau run a valid alternating-descending sequence.
 */
fun findInvariantViolations(state: GameState): List<String> {
    val violations = mutableListOf<String>()

    val allCards = buildList {
        state.tableau.forEach { column -> column.forEach { add(it.card) } }
        addAll(state.waste)
        addAll(state.stock)
        for (suit in org.finiteplay.cards.Suit.entries) {
            val topValue = state.foundations.getValue(suit)
            for (value in 1..topValue) add(Card(suit, Rank.fromValue(value)))
        }
    }
    if (allCards.size != Card.DECK_SIZE) {
        violations.add("expected ${Card.DECK_SIZE} cards, found ${allCards.size}")
    }
    if (allCards.toSet().size != allCards.size) {
        violations.add("duplicate card(s) present across piles")
    }
    if (allCards.toSet() != Card.CANONICAL_DECK.toSet()) {
        violations.add("card set does not match the canonical 52-card deck")
    }

    state.tableau.forEachIndexed { columnIndex, column ->
        val firstFaceUp = column.indexOfFirst { it.faceUp }
        if (firstFaceUp != -1) {
            for (i in firstFaceUp until column.size) {
                if (!column[i].faceUp) {
                    violations.add("column $columnIndex has a face-down card above a face-up card")
                }
            }
            for (i in firstFaceUp until column.lastIndex) {
                if (!canBuild(column[i].card, column[i + 1].card)) {
                    violations.add("column $columnIndex has an invalid face-up run at index $i")
                }
            }
        }
    }

    return violations
}
