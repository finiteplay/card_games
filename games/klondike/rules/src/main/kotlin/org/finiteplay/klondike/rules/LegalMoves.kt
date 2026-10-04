package org.finiteplay.klondike.rules

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.TABLEAU_COLUMNS
import org.finiteplay.klondike.board.TableauCard
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit

/** True when [card] may be placed on top of [column] (empty accepts only a King). */
fun canPlaceOnTableau(column: List<TableauCard>, card: Card): Boolean =
    if (column.isEmpty()) card.rank == Rank.KING else canBuild(column.last().card, card)

fun canPlaceOnFoundation(foundations: Map<Suit, Int>, card: Card): Boolean =
    foundations.getValue(card.suit) == card.rank.value - 1

/**
 * Indices in [column] where a valid face-up run to the top begins, ordered from the
 * topmost card downward. A single exposed card is always a valid one-card run.
 */
fun validRunStartIndices(column: List<TableauCard>): List<Int> {
    if (column.isEmpty()) return emptyList()
    val faceUpStart = column.indexOfFirst { it.faceUp }
    if (faceUpStart == -1) return emptyList()

    val starts = mutableListOf(column.lastIndex)
    var index = column.lastIndex
    while (index > faceUpStart) {
        if (!canBuild(column[index - 1].card, column[index].card)) break
        index--
        starts.add(index)
    }
    return starts
}

/** Every legal move available in [state], in no particular priority order. */
fun legalMoves(state: GameState): List<Move> {
    val moves = mutableListOf<Move>()

    if (state.stock.isNotEmpty()) moves.add(Move.Draw)
    if (state.stock.isEmpty() && state.waste.isNotEmpty()) moves.add(Move.Recycle)

    for (fromColumn in 0 until TABLEAU_COLUMNS) {
        val column = state.tableau[fromColumn]
        for (fromIndex in validRunStartIndices(column)) {
            val sequenceBottom = column[fromIndex].card
            for (toColumn in 0 until TABLEAU_COLUMNS) {
                if (toColumn == fromColumn) continue
                if (canPlaceOnTableau(state.tableau[toColumn], sequenceBottom)) {
                    moves.add(Move.TableauToTableau(fromColumn, fromIndex, toColumn))
                }
            }
        }
        column.lastOrNull()?.let { top ->
            if (canPlaceOnFoundation(state.foundations, top.card)) {
                moves.add(Move.TableauToFoundation(fromColumn))
            }
        }
    }

    state.waste.firstOrNull()?.let { wasteTop ->
        for (toColumn in 0 until TABLEAU_COLUMNS) {
            if (canPlaceOnTableau(state.tableau[toColumn], wasteTop)) {
                moves.add(Move.WasteToTableau(toColumn))
            }
        }
        if (canPlaceOnFoundation(state.foundations, wasteTop)) {
            moves.add(Move.WasteToFoundation)
        }
    }

    for (suit in Suit.entries) {
        val top = state.foundationTop(suit) ?: continue
        for (toColumn in 0 until TABLEAU_COLUMNS) {
            if (canPlaceOnTableau(state.tableau[toColumn], top)) {
                moves.add(Move.FoundationToTableau(suit, toColumn))
            }
        }
    }

    return moves
}

fun isLegal(state: GameState, move: Move): Boolean = move in legalMoves(state)
