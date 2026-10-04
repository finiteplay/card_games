package org.finiteplay.freecell.rules

/**
 * Everything a player can ask for (`docs/games/freecell/RULES.md` "Moves"). Five entries against
 * Klondike's seven and Spider's two: there is no draw, no recycle, and no foundation withdrawal
 * to add one for (`docs/games/freecell/RULES.md` "What FreeCell does not have").
 *
 * Turning a supermove into its one-at-a-time constituent transfers, and banking nothing
 * automatically (there is no automatic banking rule the way Spider has — foundation moves are
 * either what the player asked for or what [org.finiteplay.freecell.rules.automaticFoundationCascade]
 * runs on their behalf), are consequences the reducer applies, never separate moves.
 */
sealed class Move {

    /**
     * Moves the sequence starting at [fromIndex] in [fromColumn] onto [toColumn] — a single card
     * when the sequence is one card, a supermove otherwise (`RULES.md` "Supermove").
     */
    data class TableauToTableau(val fromColumn: Int, val fromIndex: Int, val toColumn: Int) : Move()

    /** Moves the top card of [fromColumn] into empty free cell [cell]. */
    data class TableauToFreeCell(val fromColumn: Int, val cell: Int) : Move()

    /** Moves the top card of [fromColumn] to its foundation. */
    data class TableauToFoundation(val fromColumn: Int) : Move()

    /** Moves the card in free cell [cell] onto [toColumn]. */
    data class FreeCellToTableau(val cell: Int, val toColumn: Int) : Move()

    /** Moves the card in free cell [cell] to its foundation. */
    data class FreeCellToFoundation(val cell: Int) : Move()
}
