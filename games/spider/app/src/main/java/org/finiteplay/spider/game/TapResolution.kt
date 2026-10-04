package org.finiteplay.spider.game

import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.isLegal
import org.finiteplay.spider.rules.isMovableSequence

/**
 * Every column, left to right, the sequence at [fromIndex] of [fromColumn] can legally land on.
 * Empty when the card isn't liftable from there or has nowhere legal to go yet.
 */
fun tapDestinations(state: SpiderState, fromColumn: Int, fromIndex: Int): List<Int> {
    if (fromColumn !in 0 until TABLEAU_COLUMNS) return emptyList()
    if (!isMovableSequence(state.tableau[fromColumn], fromIndex)) return emptyList()
    return (0 until TABLEAU_COLUMNS).filter { toColumn ->
        toColumn != fromColumn && isLegal(state, Move.TableauToTableau(fromColumn, fromIndex, toColumn))
    }
}

/**
 * What a tap on the card at [fromIndex] of [fromColumn] does: the nearest legal column to the
 * *right* of the one it is in, or the leftmost legal column when there is none to the right.
 *
 * An empty column is only considered when it is the sole legal destination. Any legal column
 * already holding a sequence is a real building choice — continuing a run, say, or setting up a
 * reveal — while an empty column is always legal for a liftable sequence regardless of what it
 * is, so picking one automatically whenever it happened to sit nearest would spend a column a
 * player likely wanted free for something more deliberate.
 *
 * Tapping the same sequence again then walks it on, without anything having to remember that the
 * previous tap happened: the sequence has moved, so "next to the right" is measured from where it
 * now sits, and a sequence at the right-hand end wraps to the left. An earlier version kept a
 * cursor recording the last destination; it needed clearing on every other action and could
 * disagree with the board after an undo. Deriving the answer from the current position alone has
 * neither problem, and matches the direction Klondike's own tap priority already uses.
 */
fun resolveTap(state: SpiderState, fromColumn: Int, fromIndex: Int): Move.TableauToTableau? {
    val destinations = tapDestinations(state, fromColumn, fromIndex)
    if (destinations.isEmpty()) return null
    val nonEmptyDestinations = destinations.filter { state.tableau[it].isNotEmpty() }
    val candidates = nonEmptyDestinations.ifEmpty { destinations }
    val next = candidates.firstOrNull { it > fromColumn } ?: candidates.first()
    return Move.TableauToTableau(fromColumn, fromIndex, next)
}
