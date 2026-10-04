package org.finiteplay.klondike.rules

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.GameStatus
import org.finiteplay.klondike.board.TABLEAU_COLUMNS

/**
 * True only once no face-down tableau card remains and a simulated foundation-only
 * sweep provably completes: repeatedly send the lowest-ranked accessible card (waste
 * top or a tableau top, ignoring the parked-card exclusion — nothing is left to want
 * once the finish engages) to its foundation, drawing or recycling as needed. A board
 * with everything exposed whose sweep cannot complete is not offered the finish; play
 * continues normally.
 */
fun canAutoFinish(state: GameState): Boolean {
    if (state.tableau.any { column -> column.any { !it.faceUp } }) return false
    return simulateSweep(state) != null
}

/**
 * Runs the finish simulation described by [canAutoFinish]. Returns the fully-won
 * state, with every applied draw/recycle/foundation transfer counted as one move, or
 * `null` if the sweep cannot complete (stock/waste cycled once with no progress).
 * Callers must check [canAutoFinish] (or a non-null result here) before committing —
 * this function never partially applies a sweep to real game state.
 */
fun simulateSweep(initial: GameState): GameState? {
    var current = initial
    var progressedSinceLastRecycle = true

    while (current.status != GameStatus.WON) {
        val move = lowestRankAccessibleFoundationMove(current)
        current = when {
            move != null -> {
                progressedSinceLastRecycle = true
                applyMove(current, move)
            }
            current.stock.isNotEmpty() -> applyMove(current, Move.Draw)
            current.waste.isNotEmpty() && progressedSinceLastRecycle -> {
                progressedSinceLastRecycle = false
                applyMove(current, Move.Recycle)
            }
            else -> return null
        }.let { it.copy(moveCount = it.moveCount + 1) }
    }
    return current
}

private fun lowestRankAccessibleFoundationMove(state: GameState): Move? {
    var best: Move? = null
    var bestRank = Int.MAX_VALUE

    state.waste.firstOrNull()?.let { card ->
        if (canPlaceOnFoundation(state.foundations, card)) {
            best = Move.WasteToFoundation
            bestRank = card.rank.value
        }
    }
    for (column in 0 until TABLEAU_COLUMNS) {
        val top = state.tableau[column].lastOrNull() ?: continue
        if (canPlaceOnFoundation(state.foundations, top.card) && top.card.rank.value < bestRank) {
            best = Move.TableauToFoundation(column)
            bestRank = top.card.rank.value
        }
    }
    return best
}
