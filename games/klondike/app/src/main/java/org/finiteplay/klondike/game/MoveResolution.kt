package org.finiteplay.klondike

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.TABLEAU_COLUMNS
import org.finiteplay.cards.Suit
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.canPlaceOnTableau
import org.finiteplay.klondike.rules.legalMoves

/**
 * Resolves a tap on a foundation's top card, per `UI_SPEC.md` ("Tap" > "Foundation
 * top"): move to the lowest-index legal tableau column. Foundation withdrawal has no
 * priority chain of its own and is not part of the frozen `:game` tap-resolution
 * surface (`resolveTableauTap`/`resolveWasteTap`/`resolveStockTap`); [legalMoves] still
 * validates the result and the engine parks the withdrawn card.
 */
fun resolveFoundationTap(state: GameState, suit: Suit): Move? {
    state.foundationTop(suit) ?: return null
    val toColumn = (0 until TABLEAU_COLUMNS).firstOrNull { canPlaceOnTableau(state.tableau[it], state.foundationTop(suit)!!) }
    return toColumn?.let { Move.FoundationToTableau(suit, it) }
}

/** Identifies the pile a drag gesture picked a card or sequence up from. */
sealed class DragSource {
    data class Tableau(val column: Int, val fromIndex: Int, val isTopCard: Boolean) : DragSource()
    data object Waste : DragSource()
    data class Foundation(val suit: Suit) : DragSource()
}

/**
 * Every legal destination for the sequence picked up at [source], drawn from [legalMoves] so
 * drag destinations never diverge from engine legality. Drag "accepts any legal
 * destination" (`DESIGN.md` Interaction), unlike tap's safe-foundation preference.
 */
fun legalDestinationsForDragSource(state: GameState, source: DragSource): List<Move> =
    legalMoves(state).filter { move ->
        when (source) {
            is DragSource.Tableau -> when (move) {
                is Move.TableauToTableau -> move.fromColumn == source.column && move.fromIndex == source.fromIndex
                is Move.TableauToFoundation -> source.isTopCard && move.fromColumn == source.column
                else -> false
            }
            DragSource.Waste -> move is Move.WasteToTableau || move is Move.WasteToFoundation
            is DragSource.Foundation -> move is Move.FoundationToTableau && move.suit == source.suit
        }
    }
