package org.finiteplay.klondike.rules

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.TABLEAU_COLUMNS
import org.finiteplay.cards.Card

/**
 * Resolves a tap on the face-up run starting at [fromIndex] in tableau column
 * [fromColumn], following the documented tap priority: the lowest-index legal
 * tableau destination strictly to the right of [fromColumn]; then a legal foundation
 * move regardless of safety; then — only when neither exists — the lowest-index legal
 * tableau destination to the left of [fromColumn]. Returns `null` only when none of
 * the three exists.
 */
fun resolveTableauTap(state: GameState, fromColumn: Int, fromIndex: Int): Move? {
    val column = state.tableau[fromColumn]
    if (fromIndex !in column.indices || !column[fromIndex].faceUp) return null

    val run = column.subList(fromIndex, column.size).map { it.card }
    val sequenceBottom = run.first()

    val rightwardDestination = (fromColumn + 1 until TABLEAU_COLUMNS)
        .firstOrNull { canPlaceOnTableau(state.tableau[it], sequenceBottom) }
    if (rightwardDestination != null) {
        return Move.TableauToTableau(fromColumn, fromIndex, rightwardDestination)
    }
    if (run.size == 1 && canPlaceOnFoundation(state.foundations, run.single())) {
        return Move.TableauToFoundation(fromColumn)
    }
    val leftwardDestination = (0 until fromColumn)
        .firstOrNull { canPlaceOnTableau(state.tableau[it], sequenceBottom) }
    if (leftwardDestination != null) {
        return Move.TableauToTableau(fromColumn, fromIndex, leftwardDestination)
    }
    return null
}

/**
 * Resolves a tap on the waste top card: a safe foundation move first, then the
 * lowest-index legal tableau destination (left or right — the waste has no source
 * column to be directional about), and an unsafe-but-legal foundation move only as a
 * last resort. Unlike [resolveTableauTap], not changed to the deterministic
 * right-then-foundation rule, since that rule is specifically about a tableau card's
 * position relative to other tableau columns.
 */
fun resolveWasteTap(state: GameState): Move? {
    val card = state.waste.firstOrNull() ?: return null

    if (canPlaceOnFoundation(state.foundations, card) && isSafeFoundationMove(state.foundations, card)) {
        return Move.WasteToFoundation
    }

    val destinations = legalTableauDestinations(state, card, excludeColumn = null)
    if (destinations.isNotEmpty()) {
        return Move.WasteToTableau(destinations.first())
    }
    if (canPlaceOnFoundation(state.foundations, card)) {
        return Move.WasteToFoundation
    }
    return null
}

/** Resolves a tap on the stock: draw when possible, otherwise recycle, otherwise `null`. */
fun resolveStockTap(state: GameState): Move? = when {
    state.stock.isNotEmpty() -> Move.Draw
    state.waste.isNotEmpty() -> Move.Recycle
    else -> null
}

private fun legalTableauDestinations(state: GameState, sequenceBottom: Card, excludeColumn: Int?): List<Int> =
    (0 until TABLEAU_COLUMNS)
        .filter { it != excludeColumn }
        .filter { canPlaceOnTableau(state.tableau[it], sequenceBottom) }
        .sorted()
