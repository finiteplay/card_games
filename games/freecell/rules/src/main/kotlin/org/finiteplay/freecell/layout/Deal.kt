package org.finiteplay.freecell.layout

import org.finiteplay.cards.Card
import org.finiteplay.cards.Suit
import org.finiteplay.cards.shuffleDeckIndices

/**
 * The dealt board for [seed] under [versions], per the deterministic deal contract
 * (`docs/games/freecell/EXECUTION_PLAN.md` "Deterministic Deal Contract"): a Fisher-Yates shuffle
 * over the 52 canonical indices, dealt round-robin left to right — shuffled index 0 to column 0,
 * index 1 to column 1, ... wrapping after column 7 — until the deck is exhausted. Every card is
 * face up the instant it is dealt; there is no later flip anywhere in this game.
 */
fun dealGame(seed: Long, versions: GameVersions): FreeCellState {
    val shuffled = shuffleDeckIndices(seed).map(Card.Companion::fromId)

    val tableau = List(TABLEAU_COLUMNS) { mutableListOf<Card>() }
    for ((index, card) in shuffled.withIndex()) {
        tableau[index % TABLEAU_COLUMNS].add(card)
    }

    return FreeCellState(
        seed = seed,
        versions = versions,
        tableau = tableau.map { it.toList() },
        freeCells = List(FREE_CELLS) { null },
        foundations = Suit.entries.associateWith { 0 },
        status = GameStatus.IN_PROGRESS,
    )
}
