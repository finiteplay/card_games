package org.finiteplay.spider.rules

import org.finiteplay.cards.Card
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.layout.TableauCard

/**
 * True when [over] may be placed directly on [under]: one rank lower, any suit.
 *
 * Deliberately not `core/cards`' `canBuild`, which is Klondike's alternating-colour rule.
 * `docs/ARCHITECTURE.md` records why a packing rule stays with its game rather than becoming a
 * card fact: this function and that one are both "the packing rule", and they agree on nothing
 * but the rank step.
 */
fun canPlaceOn(under: Card, over: Card): Boolean = over.rank.value == under.rank.value - 1

/**
 * Index in [column] where the movable sequence ending at its top begins: the deepest card from
 * which every card down to the top descends by one and shares a suit.
 *
 * Returns the last index for a single face-up card, and `-1` for an empty column. The face-down
 * cards below are never part of it, whatever they are.
 */
fun sequenceStart(column: List<TableauCard>): Int {
    if (column.isEmpty()) return -1
    var start = column.lastIndex
    while (start > 0) {
        val below = column[start - 1]
        val above = column[start]
        if (!below.faceUp) break
        if (below.card.suit != above.card.suit) break
        if (below.card.rank.value != above.card.rank.value + 1) break
        start--
    }
    return start
}

/** True when the cards from [fromIndex] to the top of [column] may be lifted as one. */
fun isMovableSequence(column: List<TableauCard>, fromIndex: Int): Boolean =
    fromIndex in column.indices && column[fromIndex].faceUp && fromIndex >= sequenceStart(column)

/**
 * True when a row deal is available: cards left in the stock and not one empty column.
 *
 * The empty-column condition is the standard rule and the one that makes emptying a column a
 * real decision rather than a free one (`RULES.md` "The stock").
 */
fun canDealRow(state: SpiderState): Boolean =
    state.stock.isNotEmpty() && state.tableau.none { it.isEmpty() }

fun isLegal(state: SpiderState, move: Move): Boolean = when (move) {
    is Move.DealRow -> canDealRow(state)
    is Move.TableauToTableau -> isLegalTransfer(state, move)
}

private fun isLegalTransfer(state: SpiderState, move: Move.TableauToTableau): Boolean {
    if (move.fromColumn !in 0 until TABLEAU_COLUMNS) return false
    if (move.toColumn !in 0 until TABLEAU_COLUMNS) return false
    if (move.fromColumn == move.toColumn) return false

    val source = state.tableau[move.fromColumn]
    if (!isMovableSequence(source, move.fromIndex)) return false

    val destination = state.tableau[move.toColumn]
    // An empty column takes anything; this is where Spider and Klondike differ most, and it is
    // why an empty column is the game's most valuable asset rather than a King's waiting room.
    val landing = destination.lastOrNull() ?: return true
    return landing.faceUp && canPlaceOn(landing.card, source[move.fromIndex].card)
}

/**
 * Every legal move from [state].
 *
 * A sequence can be lifted from any point inside itself, and all of those are offered: moving
 * the whole eight-card run and moving its last three cards are different moves with different
 * consequences, and only the player knows which they meant.
 */
fun legalMoves(state: SpiderState): List<Move> {
    if (state.isWon) return emptyList()
    val moves = ArrayList<Move>()

    for (fromColumn in 0 until TABLEAU_COLUMNS) {
        val source = state.tableau[fromColumn]
        val start = sequenceStart(source)
        if (start < 0) continue
        for (fromIndex in start..source.lastIndex) {
            for (toColumn in 0 until TABLEAU_COLUMNS) {
                val move = Move.TableauToTableau(fromColumn, fromIndex, toColumn)
                if (isLegal(state, move)) moves += move
            }
        }
    }

    if (canDealRow(state)) moves += Move.DealRow
    return moves
}

/** True when nothing can be played: the interface's cue, not a status the reducer sets. */
fun isStuck(state: SpiderState): Boolean = !state.isWon && legalMoves(state).isEmpty()
