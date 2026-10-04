package org.finiteplay.spider.rules

import org.finiteplay.cards.Card
import org.finiteplay.spider.layout.DECK_CARDS
import org.finiteplay.spider.layout.GameStatus
import org.finiteplay.spider.layout.SEQUENCES_TO_WIN
import org.finiteplay.spider.layout.SpiderState

/**
 * Everything `RULES.md` says holds after every committed transition, as one check.
 *
 * Public rather than test-only on purpose: a soak test asserting these after every move of
 * thousands of random games is the cheapest proof the reducer is sound, and the same function
 * is what a debug build would call to catch a bad state before it reaches the screen.
 *
 * Returns the violations rather than throwing, so a failure can name every one at once.
 */
fun invariantViolations(state: SpiderState): List<String> {
    val problems = ArrayList<String>()

    val inTableau = state.tableau.sumOf { it.size }
    val inBanked = state.sequencesBanked * Card.RANKS_PER_SUIT
    val total = inTableau + state.stock.size + inBanked
    if (total != DECK_CARDS) {
        problems += "$total cards exist: $inTableau in the tableau, ${state.stock.size} in stock, $inBanked banked"
    }

    for ((index, column) in state.tableau.withIndex()) {
        if (column.isNotEmpty() && !column.last().faceUp) {
            problems += "column $index ends face down"
        }
        val firstFaceUp = column.indexOfFirst { it.faceUp }
        if (firstFaceUp >= 0 && column.drop(firstFaceUp).any { !it.faceUp }) {
            problems += "column $index has a face-down card above a face-up one"
        }
    }

    if (state.stock.size % org.finiteplay.spider.layout.TABLEAU_COLUMNS != 0) {
        problems += "stock holds ${state.stock.size} cards, which is not a whole number of rows"
    }

    if (state.sequencesBanked > SEQUENCES_TO_WIN) {
        problems += "${state.sequencesBanked} sequences banked, more than the $SEQUENCES_TO_WIN a deal holds"
    }

    val won = state.status == GameStatus.WON
    if (won != (state.sequencesBanked == SEQUENCES_TO_WIN)) {
        problems += "status is ${state.status} with ${state.sequencesBanked} sequences banked"
    }

    // A card and its twin are interchangeable, so the multiset is what has to be right: every
    // rank of every suit in play appears exactly as many times as the deal put in.
    val expected = org.finiteplay.spider.layout.buildDeck(state.suitCount)
        .groupingBy { it }.eachCount()
    val present = (state.tableau.flatten().map { it.card } + state.stock)
        .groupingBy { it }.eachCount().toMutableMap()
    for ((suit, count) in state.banked) {
        if (count == 0) continue
        for (rank in org.finiteplay.cards.Rank.entries) {
            present[Card(suit, rank)] = (present[Card(suit, rank)] ?: 0) + count
        }
    }
    if (present != expected) {
        problems += "the cards in play are not the deck that was dealt"
    }

    return problems
}
