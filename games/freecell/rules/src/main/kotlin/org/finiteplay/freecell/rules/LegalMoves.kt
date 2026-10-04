package org.finiteplay.freecell.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.Suit
import org.finiteplay.freecell.layout.FREE_CELLS
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.TABLEAU_COLUMNS

/** True when [card] is the next card its foundation needs. */
fun canPlaceOnFoundation(foundations: Map<Suit, Int>, card: Card): Boolean =
    foundations.getValue(card.suit) == card.rank.value - 1

/**
 * Index in [column] where the liftable sequence ending at its top begins: the deepest card from
 * which every card down to the top descends by one rank and alternates colour.
 *
 * Every card in FreeCell is face up (`docs/games/freecell/RULES.md` "The layout"), so unlike
 * Klondike's or Spider's own version of this function there is no face-down floor to stop at —
 * a whole column can be one sequence, start to top. Returns `-1` for an empty column and the last
 * index for a single card.
 */
fun sequenceStart(column: List<Card>): Int {
    if (column.isEmpty()) return -1
    var start = column.lastIndex
    while (start > 0 && canBuild(column[start - 1], column[start])) {
        start--
    }
    return start
}

/** True when the cards from [fromIndex] to the top of [column] form a liftable sequence. */
fun isMovableSequence(column: List<Card>, fromIndex: Int): Boolean =
    fromIndex in column.indices && fromIndex >= sequenceStart(column)

/**
 * The longest sequence [state] currently allows moving onto [toColumn]
 * (`docs/games/freecell/RULES.md` "Supermove"): `(free + 1) × 2^empty`, where `free` is the
 * number of empty free cells and `empty` is the number of empty tableau columns other than
 * [toColumn] itself. An empty destination is excluded from `empty` because the cards are going
 * there rather than passing through it — nothing more is subtracted for it.
 */
fun maxSupermoveLength(state: FreeCellState, toColumn: Int): Int {
    val emptyWaypoints = state.tableau.indices.count { it != toColumn && state.tableau[it].isEmpty() }
    return (state.emptyFreeCells + 1) * (1 shl emptyWaypoints)
}

fun isLegal(state: FreeCellState, move: Move): Boolean = when (move) {
    is Move.TableauToTableau -> isLegalTableauToTableau(state, move)
    is Move.TableauToFreeCell -> isLegalTableauToFreeCell(state, move)
    is Move.TableauToFoundation -> isLegalTableauToFoundation(state, move)
    is Move.FreeCellToTableau -> isLegalFreeCellToTableau(state, move)
    is Move.FreeCellToFoundation -> isLegalFreeCellToFoundation(state, move)
}

private fun isLegalTableauToTableau(state: FreeCellState, move: Move.TableauToTableau): Boolean {
    if (move.fromColumn !in 0 until TABLEAU_COLUMNS) return false
    if (move.toColumn !in 0 until TABLEAU_COLUMNS) return false
    if (move.fromColumn == move.toColumn) return false

    val source = state.tableau[move.fromColumn]
    if (!isMovableSequence(source, move.fromIndex)) return false

    val length = source.size - move.fromIndex
    if (length > maxSupermoveLength(state, move.toColumn)) return false

    val destination = state.tableau[move.toColumn]
    val landing = destination.lastOrNull() ?: return true
    return canBuild(landing, source[move.fromIndex])
}

private fun isLegalTableauToFreeCell(state: FreeCellState, move: Move.TableauToFreeCell): Boolean {
    if (move.fromColumn !in 0 until TABLEAU_COLUMNS) return false
    if (move.cell !in 0 until FREE_CELLS) return false
    if (state.tableau[move.fromColumn].isEmpty()) return false
    return state.freeCells[move.cell] == null
}

private fun isLegalTableauToFoundation(state: FreeCellState, move: Move.TableauToFoundation): Boolean {
    if (move.fromColumn !in 0 until TABLEAU_COLUMNS) return false
    val top = state.tableau[move.fromColumn].lastOrNull() ?: return false
    return canPlaceOnFoundation(state.foundations, top)
}

private fun isLegalFreeCellToTableau(state: FreeCellState, move: Move.FreeCellToTableau): Boolean {
    if (move.cell !in 0 until FREE_CELLS) return false
    if (move.toColumn !in 0 until TABLEAU_COLUMNS) return false
    val card = state.freeCells[move.cell] ?: return false

    val destination = state.tableau[move.toColumn]
    val landing = destination.lastOrNull() ?: return true
    return canBuild(landing, card)
}

private fun isLegalFreeCellToFoundation(state: FreeCellState, move: Move.FreeCellToFoundation): Boolean {
    if (move.cell !in 0 until FREE_CELLS) return false
    val card = state.freeCells[move.cell] ?: return false
    return canPlaceOnFoundation(state.foundations, card)
}

/**
 * Every legal move from [state].
 *
 * A sequence can be lifted from any point inside itself, and all of those are offered — moving
 * the whole run and moving its last three cards are different moves with different consequences,
 * and only the player knows which they meant (the same reasoning as Spider's own `legalMoves`).
 */
fun legalMoves(state: FreeCellState): List<Move> {
    if (state.isWon) return emptyList()
    val moves = ArrayList<Move>()

    for (fromColumn in 0 until TABLEAU_COLUMNS) {
        val source = state.tableau[fromColumn]
        val start = sequenceStart(source)
        if (start < 0) continue
        for (fromIndex in start..source.lastIndex) {
            for (toColumn in 0 until TABLEAU_COLUMNS) {
                val move = Move.TableauToTableau(fromColumn, fromIndex, toColumn)
                if (isLegal(state, move)) moves += move
            }
        }
        for (cell in 0 until FREE_CELLS) {
            val move = Move.TableauToFreeCell(fromColumn, cell)
            if (isLegal(state, move)) moves += move
        }
        val toFoundation = Move.TableauToFoundation(fromColumn)
        if (isLegal(state, toFoundation)) moves += toFoundation
    }

    for (cell in 0 until FREE_CELLS) {
        for (toColumn in 0 until TABLEAU_COLUMNS) {
            val move = Move.FreeCellToTableau(cell, toColumn)
            if (isLegal(state, move)) moves += move
        }
        val toFoundation = Move.FreeCellToFoundation(cell)
        if (isLegal(state, toFoundation)) moves += toFoundation
    }

    return moves
}

/** True when nothing can be played: the interface's cue, not a status the reducer sets. */
fun isStuck(state: FreeCellState): Boolean = !state.isWon && legalMoves(state).isEmpty()
