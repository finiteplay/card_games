package org.finiteplay.klondike.rules

import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.GameStatus
import org.finiteplay.klondike.board.TableauCard
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit

/** Flips the new top card face-up if [column] is nonempty and its top is face-down. */
private fun withAutoFlip(column: List<TableauCard>): List<TableauCard> {
    if (column.isEmpty()) return column
    val top = column.last()
    if (top.faceUp) return column
    return column.dropLast(1) + top.copy(faceUp = true)
}

private fun withFoundationCard(foundations: Map<Suit, Int>, card: Card): Map<Suit, Int> =
    foundations + (card.suit to card.rank.value)

private fun wonIfComplete(foundations: Map<Suit, Int>, fallback: GameStatus): GameStatus =
    if (foundations.values.all { it == Rank.KING.value }) GameStatus.WON else fallback

/**
 * Applies [move] to [state], assuming it is legal (see [isLegal]). This is pure
 * mechanics only: no scoring, undo grouping, or parked-card bookkeeping — those belong
 * to the transaction layer built on top (E2a/E2b). Automatic exposed-card flips are
 * applied here since they are an unconditional consequence of uncovering a face-down
 * tableau card.
 */
fun applyMove(state: GameState, move: Move): GameState = when (move) {
    is Move.Draw -> {
        // Draw-three (docs/games/klondike/DESIGN.md "Draw-Three Mode") draws up to 3 at once, or
        // fewer if the stock has less than 3 left — a real, common edge case near the
        // end of a pass, not an error. Only the resulting waste top is ever playable
        // either way (isLegal/generateMoves never look past waste.first()), so the
        // other drawn cards only matter as a still-inspectable, still-recyclable
        // stack underneath it, exactly like extra cards already buried in the waste.
        val count = when (state.drawMode) {
            DrawMode.ONE -> 1
            DrawMode.THREE -> 3
        }.coerceAtMost(state.stock.size)
        val drawn = state.stock.subList(0, count)
        state.copy(stock = state.stock.drop(count), waste = drawn.reversed() + state.waste)
    }

    is Move.Recycle -> state.copy(stock = state.waste.reversed(), waste = emptyList())

    is Move.TableauToTableau -> {
        val fromCol = state.tableau[move.fromColumn]
        val run = fromCol.subList(move.fromIndex, fromCol.size)
        val remainingFrom = withAutoFlip(fromCol.subList(0, move.fromIndex))
        val newTo = state.tableau[move.toColumn] + run
        val newTableau = state.tableau.toMutableList().apply {
            this[move.fromColumn] = remainingFrom
            this[move.toColumn] = newTo
        }
        state.copy(tableau = newTableau)
    }

    is Move.TableauToFoundation -> {
        val fromCol = state.tableau[move.fromColumn]
        val card = fromCol.last().card
        val remaining = withAutoFlip(fromCol.subList(0, fromCol.size - 1))
        val newTableau = state.tableau.toMutableList().apply { this[move.fromColumn] = remaining }
        val newFoundations = withFoundationCard(state.foundations, card)
        state.copy(tableau = newTableau, foundations = newFoundations, status = wonIfComplete(newFoundations, state.status))
    }

    is Move.WasteToTableau -> {
        val card = state.waste.first()
        val newTableau = state.tableau.toMutableList().apply {
            this[move.toColumn] = state.tableau[move.toColumn] + TableauCard(card, true)
        }
        state.copy(tableau = newTableau, waste = state.waste.drop(1))
    }

    is Move.WasteToFoundation -> {
        val card = state.waste.first()
        val newFoundations = withFoundationCard(state.foundations, card)
        state.copy(waste = state.waste.drop(1), foundations = newFoundations, status = wonIfComplete(newFoundations, state.status))
    }

    is Move.FoundationToTableau -> {
        val card = state.foundationTop(move.suit)
            ?: throw IllegalArgumentException("Foundation ${move.suit} is empty")
        val newFoundations = state.foundations + (move.suit to card.rank.value - 1)
        val newTableau = state.tableau.toMutableList().apply {
            this[move.toColumn] = state.tableau[move.toColumn] + TableauCard(card, true)
        }
        state.copy(tableau = newTableau, foundations = newFoundations)
    }
}
