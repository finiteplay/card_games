package org.finiteplay.klondike.rules

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.TABLEAU_COLUMNS
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.klondike.deal.PileId

private fun sourcePile(move: Move): PileId = when (move) {
    Move.Draw, Move.Recycle -> PileId.STOCK
    is Move.TableauToTableau -> PileId.TABLEAU[move.fromColumn]
    is Move.TableauToFoundation -> PileId.TABLEAU[move.fromColumn]
    is Move.WasteToTableau -> PileId.WASTE
    Move.WasteToFoundation -> PileId.WASTE
    is Move.FoundationToTableau -> PileId.FOUNDATIONS[move.suit.ordinal]
}

private fun destinationPile(move: Move): PileId? = when (move) {
    Move.Draw -> PileId.WASTE
    Move.Recycle -> PileId.STOCK
    is Move.TableauToTableau -> PileId.TABLEAU[move.toColumn]
    is Move.TableauToFoundation -> null
    is Move.WasteToTableau -> PileId.TABLEAU[move.toColumn]
    Move.WasteToFoundation -> null
    is Move.FoundationToTableau -> PileId.TABLEAU[move.toColumn]
}

private val PILE_ORDER_COMPARATOR: Comparator<Move> =
    compareBy({ sourcePile(it).ordinal }, { destinationPile(it)?.ordinal ?: -1 })

/**
 * True when [candidate] would exactly reverse [previous]: the same run sent straight
 * back to where it came from, or a foundation withdrawal immediately re-banked (or vice
 * versa). Used to avoid suggesting a pointless back-and-forth when another candidate
 * exists — and, from `:solver`'s DFS move ordering, to steer the search away from the
 * same visible oscillation rather than merely excluding it here.
 */
fun isInverseOfPreviousMove(previous: Move?, candidate: Move): Boolean {
    if (previous == null) return false
    return when {
        previous is Move.TableauToTableau && candidate is Move.TableauToTableau ->
            previous.fromColumn == candidate.toColumn && previous.toColumn == candidate.fromColumn
        previous is Move.TableauToFoundation && candidate is Move.FoundationToTableau ->
            candidate.toColumn == previous.fromColumn
        previous is Move.FoundationToTableau && candidate is Move.TableauToFoundation ->
            candidate.fromColumn == previous.toColumn
        else -> false
    }
}

/**
 * The single ranked hint for [state], or `null` when no legal move exists. Ranking,
 * per `docs/games/klondike/DESIGN.md`: reveal a face-down tableau card, a safe foundation move, waste
 * to tableau, improving tableau structure, then draw or recycle. Ties break by source
 * pile order, then destination pile order. [previousMove], when given, is skipped when
 * another candidate is available.
 */
fun findHint(state: GameState, previousMove: Move? = null): Move? {
    val ranked = rankedHintCandidates(state)
    val withoutReversal = ranked.filterNot { isInverseOfPreviousMove(previousMove, it) }
    return (withoutReversal.ifEmpty { ranked }).firstOrNull()
}

/**
 * Every candidate in the first non-empty priority step, sorted by pile order. A hint
 * cursor pages through this list; repeated requests advance and wrap.
 */
internal fun rankedHintCandidates(state: GameState): List<Move> {
    revealingMoves(state).sortedWith(PILE_ORDER_COMPARATOR).let { if (it.isNotEmpty()) return it }
    safeFoundationMoves(state).sortedWith(PILE_ORDER_COMPARATOR).let { if (it.isNotEmpty()) return it }
    wasteToTableauMoves(state).sortedWith(PILE_ORDER_COMPARATOR).let { if (it.isNotEmpty()) return it }
    structuralMoves(state).let { if (it.isNotEmpty()) return it }
    return listOfNotNull(resolveStockTap(state))
}

private fun revealingMoves(state: GameState): List<Move> {
    val moves = mutableListOf<Move>()
    for (column in 0 until TABLEAU_COLUMNS) {
        val tableauColumn = state.tableau[column]
        for (fromIndex in validRunStartIndices(tableauColumn)) {
            if (fromIndex == 0 || tableauColumn[fromIndex - 1].faceUp) continue
            val sequenceBottom = tableauColumn[fromIndex].card
            for (toColumn in 0 until TABLEAU_COLUMNS) {
                if (toColumn == column) continue
                if (canPlaceOnTableau(state.tableau[toColumn], sequenceBottom)) {
                    moves.add(Move.TableauToTableau(column, fromIndex, toColumn))
                }
            }
        }
        if (tableauColumn.size >= 2 && tableauColumn.last().faceUp && !tableauColumn[tableauColumn.size - 2].faceUp) {
            val top = tableauColumn.last().card
            if (canPlaceOnFoundation(state.foundations, top)) moves.add(Move.TableauToFoundation(column))
        }
    }
    return moves
}

private fun safeFoundationMoves(state: GameState): List<Move> {
    val moves = mutableListOf<Move>()
    state.waste.firstOrNull()?.let { card ->
        if (canPlaceOnFoundation(state.foundations, card) && isSafeFoundationMove(state.foundations, card)) {
            moves.add(Move.WasteToFoundation)
        }
    }
    for (column in 0 until TABLEAU_COLUMNS) {
        val top = state.tableau[column].lastOrNull() ?: continue
        if (top.faceUp && canPlaceOnFoundation(state.foundations, top.card) && isSafeFoundationMove(state.foundations, top.card)) {
            moves.add(Move.TableauToFoundation(column))
        }
    }
    return moves
}

private fun wasteToTableauMoves(state: GameState): List<Move> {
    val card = state.waste.firstOrNull() ?: return emptyList()
    return (0 until TABLEAU_COLUMNS)
        .filter { canPlaceOnTableau(state.tableau[it], card) }
        .map { Move.WasteToTableau(it) }
}

/**
 * The tableau move that most extends an alternating face-up run. A move that would
 * empty its source column is heavily deprioritized unless another tableau column
 * already carries an accessible King-led run with no empty column to use yet — so it
 * still surfaces when it is the only candidate, but never edges out a move that
 * preserves structure for no reachable benefit.
 */
private fun structuralMoves(state: GameState): List<Move> {
    val hasEmptyColumn = state.tableau.any { it.isEmpty() }
    val anotherKingCouldUseIt = state.tableau.indices.any { column ->
        val col = state.tableau[column]
        col.isNotEmpty() && col.first { it.faceUp }.card.rank == Rank.KING &&
            !(col.size == 1 && col[0].faceUp)
    }
    val emptyingHelps = !hasEmptyColumn && anotherKingCouldUseIt
    val emptyingPenalty = Card.DECK_SIZE

    val scored = mutableListOf<Pair<Int, Move>>()
    for (fromColumn in 0 until TABLEAU_COLUMNS) {
        val column = state.tableau[fromColumn]
        for (fromIndex in validRunStartIndices(column)) {
            val sequenceBottom = column[fromIndex].card
            val sequenceLength = column.size - fromIndex
            for (toColumn in 0 until TABLEAU_COLUMNS) {
                if (toColumn == fromColumn) continue
                if (!canPlaceOnTableau(state.tableau[toColumn], sequenceBottom)) continue
                val destinationRunLength = state.tableau[toColumn].count { it.faceUp }
                var score = sequenceLength + destinationRunLength
                if (fromIndex == 0 && !emptyingHelps) score -= emptyingPenalty
                scored.add(score to Move.TableauToTableau(fromColumn, fromIndex, toColumn))
            }
        }
    }
    val bestScore = scored.maxOfOrNull { it.first } ?: return emptyList()
    return scored.filter { it.first == bestScore }.map { it.second }.sortedWith(PILE_ORDER_COMPARATOR)
}
