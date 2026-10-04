package org.finiteplay.freecell.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.Suit
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.GameStatus

/**
 * The one reducer. Every committed transition goes through here, and it is the only thing that
 * produces a [FreeCellState] after the deal.
 *
 * Unlike Klondike's or Spider's own reducer, there is no consequence to apply beyond the move
 * itself: no card flips (everything is already face up), no automatic banking (a foundation move
 * is either the player's own or `automaticFoundationCascade`'s, never a side effect of some other
 * move — `docs/games/freecell/RULES.md` "Foundations"), and win is simply "every foundation
 * reached King," checked after every move rather than triggered by a special one.
 *
 * Deliberately does not touch [FreeCellState.moveCount], matching Klondike's own `applyMove`: a
 * pure state transition has no opinion on scoring, and each of this reducer's three callers —
 * the session's own committed player move, the automatic cascade (one increment per transfer),
 * and the automatic-finish sweep — counts moves for its own reason (`RULES.md` "Scoring").
 */
fun applyMove(state: FreeCellState, move: Move): FreeCellState {
    require(isLegal(state, move)) { "illegal move: $move" }
    val next = when (move) {
        is Move.TableauToTableau -> applyTableauToTableau(state, move)
        is Move.TableauToFreeCell -> applyTableauToFreeCell(state, move)
        is Move.TableauToFoundation -> applyTableauToFoundation(state, move)
        is Move.FreeCellToTableau -> applyFreeCellToTableau(state, move)
        is Move.FreeCellToFoundation -> applyFreeCellToFoundation(state, move)
    }
    return withStatus(next)
}

/** [foundations] with [card] banked — the caller has already checked it is legal to bank. */
private fun bank(foundations: Map<Suit, Int>, card: Card): Map<Suit, Int> =
    foundations + (card.suit to card.rank.value)

/** Every foundation at King is the whole deck banked — the one win condition this game has. */
private fun withStatus(state: FreeCellState): FreeCellState {
    val allBanked = state.foundations.values.sum() == Card.RANKS_PER_SUIT * Suit.entries.size
    return if (allBanked) state.copy(status = GameStatus.WON) else state
}

private fun applyTableauToTableau(state: FreeCellState, move: Move.TableauToTableau): FreeCellState {
    val source = state.tableau[move.fromColumn]
    val lifted = source.subList(move.fromIndex, source.size).toList()

    val tableau = state.tableau.toMutableList()
    tableau[move.fromColumn] = source.subList(0, move.fromIndex).toList()
    tableau[move.toColumn] = state.tableau[move.toColumn] + lifted

    return state.copy(tableau = tableau)
}

private fun applyTableauToFreeCell(state: FreeCellState, move: Move.TableauToFreeCell): FreeCellState {
    val source = state.tableau[move.fromColumn]
    val card = source.last()

    val tableau = state.tableau.toMutableList()
    tableau[move.fromColumn] = source.dropLast(1)
    val freeCells = state.freeCells.toMutableList()
    freeCells[move.cell] = card

    return state.copy(tableau = tableau, freeCells = freeCells)
}

private fun applyTableauToFoundation(state: FreeCellState, move: Move.TableauToFoundation): FreeCellState {
    val source = state.tableau[move.fromColumn]
    val card = source.last()

    val tableau = state.tableau.toMutableList()
    tableau[move.fromColumn] = source.dropLast(1)

    return state.copy(tableau = tableau, foundations = bank(state.foundations, card))
}

private fun applyFreeCellToTableau(state: FreeCellState, move: Move.FreeCellToTableau): FreeCellState {
    val card = requireNotNull(state.freeCells[move.cell]) { "free cell ${move.cell} is empty" }

    val freeCells = state.freeCells.toMutableList()
    freeCells[move.cell] = null
    val tableau = state.tableau.toMutableList()
    tableau[move.toColumn] = state.tableau[move.toColumn] + card

    return state.copy(tableau = tableau, freeCells = freeCells)
}

private fun applyFreeCellToFoundation(state: FreeCellState, move: Move.FreeCellToFoundation): FreeCellState {
    val card = requireNotNull(state.freeCells[move.cell]) { "free cell ${move.cell} is empty" }

    val freeCells = state.freeCells.toMutableList()
    freeCells[move.cell] = null

    return state.copy(freeCells = freeCells, foundations = bank(state.foundations, card))
}
