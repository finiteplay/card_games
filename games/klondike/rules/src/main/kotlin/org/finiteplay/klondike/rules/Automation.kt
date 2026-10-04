package org.finiteplay.klondike.rules

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.TABLEAU_COLUMNS

/**
 * Finds the next automatic foundation transfer in [state], checking the waste top
 * first and then tableau columns left to right, skipping the parked card. Returns
 * `null` once no safe card remains.
 */
fun findNextSafeAutomaticMove(state: GameState): Move? {
    state.waste.firstOrNull()?.let { card ->
        if (card != state.parkedCard &&
            canPlaceOnFoundation(state.foundations, card) &&
            isSafeFoundationMove(state.foundations, card)
        ) {
            return Move.WasteToFoundation
        }
    }

    for (column in 0 until TABLEAU_COLUMNS) {
        val top = state.tableau[column].lastOrNull() ?: continue
        if (!top.faceUp || top.card == state.parkedCard) continue
        if (canPlaceOnFoundation(state.foundations, top.card) && isSafeFoundationMove(state.foundations, top.card)) {
            return Move.TableauToFoundation(column)
        }
    }

    return null
}

/**
 * Runs the automatic foundation cascade to completion: repeatedly applies
 * [findNextSafeAutomaticMove] until no safe card remains. Deterministic given a fixed
 * board, and never touches the parked card. Each transfer counts as one move (E2a/E2b
 * scoring). Callers decide whether and when to invoke this — it is never triggered
 * implicitly by [applyMove], so an undo path that simply avoids calling it will not
 * re-run automation.
 */
fun runAutomaticFoundationCascade(initialState: GameState): Pair<GameState, List<Move>> {
    var state = initialState
    val applied = mutableListOf<Move>()
    while (true) {
        val move = findNextSafeAutomaticMove(state) ?: break
        state = applyMove(state, move).copy(moveCount = state.moveCount + 1)
        applied.add(move)
    }
    return state to applied
}
