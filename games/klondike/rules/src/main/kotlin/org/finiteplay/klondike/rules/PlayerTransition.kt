package org.finiteplay.klondike.rules

import org.finiteplay.klondike.board.GameState
import org.finiteplay.cards.Card

/**
 * Applies a player-initiated [move] (tap or drag destination) and maintains the
 * parked-card flag: a card withdrawn from a foundation is parked, and the park clears
 * when that card is covered, moved again, or a new [Move.FoundationToTableau]
 * withdraws a different card. Automation never calls this wrapper directly — it uses
 * [applyMove], which leaves [GameState.parkedCard] untouched, since automation already
 * excludes the parked card by construction ([findNextSafeAutomaticMove]).
 */
fun applyPlayerTransition(state: GameState, move: Move): GameState {
    val priorParked = state.parkedCard
    val newState = applyMove(state, move)

    if (move is Move.FoundationToTableau) {
        val withdrawn = state.foundationTop(move.suit)
        return newState.copy(parkedCard = withdrawn)
    }

    if (priorParked == null) return newState

    val clearsPark = coversCard(state, move, priorParked) || isSourcedFromCard(state, move, priorParked)
    return if (clearsPark) newState.copy(parkedCard = null) else newState
}

private fun destinationColumn(move: Move): Int? = when (move) {
    is Move.TableauToTableau -> move.toColumn
    is Move.WasteToTableau -> move.toColumn
    is Move.FoundationToTableau -> move.toColumn
    else -> null
}

private fun coversCard(state: GameState, move: Move, card: Card): Boolean {
    val column = destinationColumn(move) ?: return false
    return state.tableau[column].lastOrNull()?.card == card
}

private fun isSourcedFromCard(state: GameState, move: Move, card: Card): Boolean = when (move) {
    is Move.TableauToTableau -> state.tableau[move.fromColumn].lastOrNull()?.card == card
    is Move.TableauToFoundation -> state.tableau[move.fromColumn].lastOrNull()?.card == card
    else -> false
}
