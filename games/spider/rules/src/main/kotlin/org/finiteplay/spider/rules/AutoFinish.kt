package org.finiteplay.spider.rules

import org.finiteplay.cards.Card
import org.finiteplay.spider.layout.SpiderState

/**
 * Cards left in play — tableau plus stock — at or below which the game finishes itself.
 *
 * Thirteen is exactly one sequence: every other sequence is already banked, so whatever remains is
 * the last one and there is nothing left to decide beyond the order it gets assembled in. Above
 * this the player still has real choices and the game must not make them.
 */
const val AUTO_FINISH_CARDS_REMAINING = Card.RANKS_PER_SUIT

/**
 * Whether [state] is at the point where the rest is bookkeeping: at most one sequence left in
 * play, and not already won.
 *
 * This is a *precondition*, not a promise. The remaining cards can still be arranged so that no
 * legal order assembles them — [findAutoFinish] is what decides whether a finish actually exists.
 */
fun isAutoFinishAvailable(state: SpiderState): Boolean =
    !state.isWon && cardsInPlay(state) <= AUTO_FINISH_CARDS_REMAINING

private fun cardsInPlay(state: SpiderState): Int =
    state.tableau.sumOf { it.size } + state.stock.size

/**
 * The shortest sequence of moves that finishes [state], or null when none does.
 *
 * Breadth-first rather than depth-first, for two reasons that both showed up the moment this was
 * tested. A depth-first walk returns the first finish it stumbles into, which was three moves long
 * on a board one move from won — and the player watches these moves happen, so a wandering finish
 * looks like the game making mistakes. It also recursed deep enough on a tangled endgame to
 * overflow the stack. Breadth-first is iterative and yields the shortest line by construction.
 *
 * Searching at all, rather than greedily banking whatever is available, means the game only ever
 * starts a finish it can actually carry out: a greedy walk can strand the last cards in an order
 * it cannot recover from, having already moved them.
 *
 * Returns real [Move]s for the caller to commit one at a time through the ordinary reducer, so the
 * log, the undo stack, and the move count are exactly what they would be had the player made them.
 * Nothing here applies anything itself.
 *
 * [maxNodes] caps the work. Exhausting it returns null — no finish — rather than blocking the
 * interface on a position that turns out to be hard.
 */
fun findAutoFinish(state: SpiderState, maxNodes: Int = MAX_AUTO_FINISH_NODES): List<Move>? {
    if (state.isWon) return emptyList()
    if (!isAutoFinishAvailable(state)) return null

    // Parent pointers rather than a path per queue entry: the same prefix is shared by every
    // branch out of a node, and copying it per branch is what makes a search like this expensive.
    val states = ArrayList<SpiderState>()
    val parents = ArrayList<Int>()
    val moves = ArrayList<Move?>()
    // Keyed on stock size, not stock content: within one call, the remaining stock is always a
    // suffix of the root's own stock in the root's own order — only its length ever changes, via
    // DealRow taking a fixed ten off the front — so two branches with equally many cards left in
    // stock necessarily hold the identical cards there.
    val visited = HashSet<Pair<List<String>, Int>>()

    states += state
    parents += -1
    moves += null
    visited += canonicalColumns(state) to state.stock.size

    var head = 0
    while (head < states.size) {
        if (states.size > maxNodes) return null
        val index = head++
        val current = states[index]
        // Every empty column is interchangeable for whatever happens next, so a move onto one is
        // tried only for the first one found — trying all of them explores the identical position
        // once per empty column for no benefit.
        val firstEmptyColumn = current.tableau.indexOfFirst { it.isEmpty() }

        for (move in legalMoves(current)) {
            val ontoADifferentEmptyColumn = move is Move.TableauToTableau &&
                current.tableau[move.toColumn].isEmpty() &&
                move.toColumn != firstEmptyColumn
            if (ontoADifferentEmptyColumn) continue
            val next = applyMove(current, move)
            if (next.isWon) return pathTo(index, move, parents, moves)
            if (!visited.add(canonicalColumns(next) to next.stock.size)) continue
            states += next
            parents += index
            moves += move
        }
    }
    return null
}

/**
 * [state]'s columns, each rendered exactly and sorted against each other.
 *
 * Nothing in Spider depends on which column a pile sits in — two boards differing only by a
 * permutation of columns are the same position for every move still available from either one.
 * Without this, moving an already-sorted run between two empty-equivalent columns (of which a
 * near-finished board usually has several) reappears as a "new" state once per column it could
 * have landed on, which is exactly the blow-up that let this search exhaust its node budget on
 * true three-move endgames — collapsing only the *immediately next* empty-column move (above)
 * caught the shallowest case but not the deeper ones a few moves on, where the interchangeable
 * columns are no longer literally empty at the point of comparison. Sorted, exact strings rather
 * than a hash: a collision here would prune a branch that led to a genuine win, silently turning
 * a solvable board into a declined one, and this search is cheap enough that there is no reason
 * to accept that risk the way a full-deal solve does.
 */
private fun canonicalColumns(state: SpiderState): List<String> =
    state.tableau.map { column -> column.joinToString(",") { "${it.card.id}:${if (it.faceUp) 1 else 0}" } }.sorted()

/** Walks the parent chain back to the root, then reads it forwards. */
private fun pathTo(parentIndex: Int, finalMove: Move, parents: List<Int>, moves: List<Move?>): List<Move> {
    val reversed = ArrayList<Move>()
    reversed += finalMove
    var i = parentIndex
    while (i > 0) {
        reversed += moves[i]!!
        i = parents[i]
    }
    return reversed.reversed()
}

/**
 * Node ceiling for the finish search.
 *
 * A genuine last-sequence endgame — thirteen cards that *can* be assembled — is solved in a tiny
 * fraction of this. A position that needs more is one where the search is grinding through an
 * unsolvable space, and the right answer there is to decline rather than to keep going: nothing is
 * lost by not offering a finish that does not exist.
 *
 * The number is a measured budget, not a guess. At 200,000 an unsolvable thirteen-card endgame
 * took 767 ms on a desktop JVM — seconds on a phone — because every node holds a full immutable
 * board. That is what made the game freeze at the end of a game (`AutoFinishCostTest` pins it).
 */
private const val MAX_AUTO_FINISH_NODES = 20_000
