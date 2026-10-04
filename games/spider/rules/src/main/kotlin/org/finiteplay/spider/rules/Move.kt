package org.finiteplay.spider.rules

/**
 * Everything a player can ask for. Two entries against Klondike's seven, because Spider has no
 * waste to draw to, no recycle, no per-card foundation play and no withdrawal
 * (`docs/games/spider/RULES.md` "What Spider does not have").
 *
 * Turning an exposed card face up, banking a completed sequence and winning are consequences
 * the reducer applies, never moves the player makes.
 */
sealed class Move {

    /** Moves the sequence starting at [fromIndex] in [fromColumn] onto [toColumn]. */
    data class TableauToTableau(val fromColumn: Int, val fromIndex: Int, val toColumn: Int) : Move()

    /** Turns one card face up onto every column at once. */
    data object DealRow : Move()
}
