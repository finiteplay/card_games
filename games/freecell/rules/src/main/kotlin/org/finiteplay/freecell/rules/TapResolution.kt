package org.finiteplay.freecell.rules

import org.finiteplay.freecell.layout.FREE_CELLS
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.TABLEAU_COLUMNS

/**
 * Resolves a tap on the sequence starting at [fromIndex] in tableau column [fromColumn]
 * (`docs/games/freecell/DESIGN.md` "Interaction"): the nearest legal tableau column to the
 * right, then a provably safe foundation, then the nearest legal tableau column to the left,
 * then an unsafe foundation, then an empty free cell as the last resort. Returns null when none
 * of the five exists — including when [fromIndex] is not a legal lift point at all, or its
 * sequence is too long for every candidate destination's supermove maximum (`RULES.md`
 * "Supermove") — a multi-card sequence never reaches the free-cell fallback either, since a free
 * cell holds exactly one card.
 *
 * Mirrors Klondike's own `resolveTableauTap` in shape; "safe" changing meaning and the free-cell
 * fallback existing at all are this game's own additions, since Klondike has neither a supermove
 * to fall out of nor a free cell to park in
 * (`docs/games/freecell/DESIGN.md` "Interaction" explains why unsafe comes before free-cell
 * rather than after: irreversibility, not order of appearance, is what "last resort" means here,
 * and parking is exactly as much a last resort as an unsafe bank is — this tap only ever reaches
 * either once every other option is gone).
 */
fun resolveTableauTap(state: FreeCellState, fromColumn: Int, fromIndex: Int): Move? {
    val column = state.tableau[fromColumn]
    if (!isMovableSequence(column, fromIndex)) return null
    val bottomCard = column[fromIndex]
    val isSingleCard = column.size - fromIndex == 1

    val rightwardDestination = (fromColumn + 1 until TABLEAU_COLUMNS)
        .firstOrNull { isLegal(state, Move.TableauToTableau(fromColumn, fromIndex, it)) }
    if (rightwardDestination != null) {
        return Move.TableauToTableau(fromColumn, fromIndex, rightwardDestination)
    }

    if (isSingleCard && canPlaceOnFoundation(state.foundations, bottomCard) && isSafeFoundationMove(state.foundations, bottomCard)) {
        return Move.TableauToFoundation(fromColumn)
    }

    val leftwardDestination = (0 until fromColumn)
        .firstOrNull { isLegal(state, Move.TableauToTableau(fromColumn, fromIndex, it)) }
    if (leftwardDestination != null) {
        return Move.TableauToTableau(fromColumn, fromIndex, leftwardDestination)
    }

    if (isSingleCard && canPlaceOnFoundation(state.foundations, bottomCard)) {
        return Move.TableauToFoundation(fromColumn)
    }

    if (isSingleCard) {
        val emptyCell = (0 until FREE_CELLS).firstOrNull { state.freeCells[it] == null }
        if (emptyCell != null) return Move.TableauToFreeCell(fromColumn, emptyCell)
    }

    return null
}

/**
 * Resolves a tap on the card in free cell [cell]: a provably safe foundation first, else the
 * nearest legal tableau column, else an unsafe-but-legal foundation as the last resort
 * (`docs/games/freecell/DESIGN.md` "Interaction").
 */
fun resolveFreeCellTap(state: FreeCellState, cell: Int): Move? {
    val card = state.freeCells[cell] ?: return null

    if (canPlaceOnFoundation(state.foundations, card) && isSafeFoundationMove(state.foundations, card)) {
        return Move.FreeCellToFoundation(cell)
    }

    val destination = (0 until TABLEAU_COLUMNS).firstOrNull { isLegal(state, Move.FreeCellToTableau(cell, it)) }
    if (destination != null) return Move.FreeCellToTableau(cell, destination)

    if (canPlaceOnFoundation(state.foundations, card)) return Move.FreeCellToFoundation(cell)

    return null
}
