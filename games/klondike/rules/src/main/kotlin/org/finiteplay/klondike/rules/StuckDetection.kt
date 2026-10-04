package org.finiteplay.klondike.rules

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.TABLEAU_COLUMNS

/**
 * True when [state] has no legal move and no productive stock action: drawing or
 * recycling through the entire deterministic stock/waste cycle would never expose a
 * waste-top card the static tableau and foundations can accept. This is reported as
 * state, not a loss — undo can still recover it.
 */
fun isStuck(state: GameState): Boolean {
    if (legalMoves(state).any { it !is Move.Draw && it !is Move.Recycle }) return false

    var current = state
    val cycleLength = state.stock.size + state.waste.size
    repeat(cycleLength) {
        current = if (current.stock.isNotEmpty()) applyMove(current, Move.Draw) else applyMove(current, Move.Recycle)
        val wasteTop = current.waste.firstOrNull() ?: return@repeat
        val unlocksTableau = (0 until TABLEAU_COLUMNS).any { canPlaceOnTableau(state.tableau[it], wasteTop) }
        if (canPlaceOnFoundation(state.foundations, wasteTop) || unlocksTableau) return false
    }
    return true
}
