package org.finiteplay.freecell.rules

import org.finiteplay.freecell.layout.FREE_CELLS
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.TABLEAU_COLUMNS

/**
 * Finds the next automatic foundation transfer in [state], checking free cells first and then
 * tableau columns left to right (`docs/games/freecell/RULES.md` "Automatic Foundation Moves").
 * Returns `null` once no safe card remains.
 *
 * Free cells first, unlike Klondike's waste-then-tableau order: a free cell holds nothing the
 * player is mid-arrangement with, so clearing it first never interferes with a plan the way
 * reordering tableau columns might.
 */
fun findNextSafeAutomaticMove(state: FreeCellState): Move? {
    for (cell in 0 until FREE_CELLS) {
        val card = state.freeCells[cell] ?: continue
        if (canPlaceOnFoundation(state.foundations, card) && isSafeFoundationMove(state.foundations, card)) {
            return Move.FreeCellToFoundation(cell)
        }
    }

    for (column in 0 until TABLEAU_COLUMNS) {
        val top = state.tableau[column].lastOrNull() ?: continue
        if (canPlaceOnFoundation(state.foundations, top) && isSafeFoundationMove(state.foundations, top)) {
            return Move.TableauToFoundation(column)
        }
    }

    return null
}

/**
 * Runs the automatic foundation cascade to completion: repeatedly applies
 * [findNextSafeAutomaticMove] until no safe card remains. Deterministic given a fixed board.
 * Each transfer counts as one move (`RULES.md` "Scoring"). Callers decide whether and when to
 * invoke this — it is never triggered implicitly by [applyMove], so an undo path that simply
 * avoids calling it will not re-run automation, and the raw dealt board is never touched by it
 * unless a player's own move triggers the call (`RULES.md` "Automatic Foundation Moves": never
 * before the player's first action).
 */
fun runAutomaticFoundationCascade(initialState: FreeCellState): Pair<FreeCellState, List<Move>> {
    var state = initialState
    val applied = mutableListOf<Move>()
    while (true) {
        val move = findNextSafeAutomaticMove(state) ?: break
        state = applyMove(state, move).copy(moveCount = state.moveCount + 1)
        applied.add(move)
    }
    return state to applied
}
