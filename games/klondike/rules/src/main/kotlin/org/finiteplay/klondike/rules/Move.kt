package org.finiteplay.klondike.rules

import org.finiteplay.cards.Suit

sealed class Move {
    data object Draw : Move()
    data object Recycle : Move()

    /** Moves the valid run starting at [fromIndex] in [fromColumn] onto [toColumn]. */
    data class TableauToTableau(val fromColumn: Int, val fromIndex: Int, val toColumn: Int) : Move()
    data class TableauToFoundation(val fromColumn: Int) : Move()
    data class WasteToTableau(val toColumn: Int) : Move()
    data object WasteToFoundation : Move()
    data class FoundationToTableau(val suit: Suit, val toColumn: Int) : Move()
}
