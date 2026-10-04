package org.finiteplay.freecell.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.Suit
import org.finiteplay.freecell.layout.FreeCellState

/**
 * True only when [findAutoFinish] proves a full foundation sweep exists from [state]
 * (`docs/games/freecell/RULES.md` "Automatic Finish"). A board whose sweep cannot complete —
 * or whose search budget runs out before deciding — is not offered the finish; play continues
 * normally.
 */
fun canAutoFinish(state: FreeCellState): Boolean = !state.isWon && findAutoFinish(state) != null

/**
 * The only moves this search considers: bank a tableau top, bank a free-cell card, or park a
 * tableau top in a free cell — never a tableau-to-tableau rearrangement and never a free cell
 * back onto the tableau (`RULES.md` "Automatic Finish" names exactly these three kinds).
 *
 * Reuses [legalMoves]' own legality rather than a second copy of it, and dedupes
 * [Move.TableauToFreeCell] to one candidate per source column: every empty free cell is an
 * identical destination for this purpose, so trying more than one only multiplies the search
 * without ever finding something a single one would have missed.
 */
private fun autoFinishMoves(state: FreeCellState): List<Move> {
    val moves = ArrayList<Move>()
    val parkedFrom = HashSet<Int>()
    for (move in legalMoves(state)) {
        when (move) {
            is Move.TableauToFoundation, is Move.FreeCellToFoundation -> moves += move
            is Move.TableauToFreeCell -> if (parkedFrom.add(move.fromColumn)) moves += move
            is Move.TableauToTableau, is Move.FreeCellToTableau -> Unit
        }
    }
    return moves
}

/**
 * The shortest sequence of [autoFinishMoves] that finishes [state], or null when the search is
 * sure none exists within [maxNodes].
 *
 * Breadth-first, so a found finish is the shortest one and the player never watches the game
 * take a longer route than it needed to — the same reasoning as Spider's own `findAutoFinish`.
 * The restricted move set makes this search naturally shallow: a free cell can be filled by this
 * search at most once each (nothing in [autoFinishMoves] ever empties one back to the tableau),
 * so a branch either makes foundation progress or runs out of moves within [FREE_CELLS] parking
 * steps — there is no way for it to wander the way a full-legal-move search could.
 */
fun findAutoFinish(state: FreeCellState, maxNodes: Int = MAX_AUTO_FINISH_NODES): List<Move>? {
    if (state.isWon) return emptyList()

    val states = ArrayList<FreeCellState>()
    val parents = ArrayList<Int>()
    val moves = ArrayList<Move?>()
    val visited = HashSet<SweepKey>()

    states += state
    parents += -1
    moves += null
    visited += state.key()

    var head = 0
    while (head < states.size) {
        if (states.size > maxNodes) return null
        val index = head++
        val current = states[index]

        for (move in autoFinishMoves(current)) {
            val next = applyMove(current, move)
            if (next.isWon) return pathTo(index, move, parents, moves)
            if (!visited.add(next.key())) continue
            states += next
            parents += index
            moves += move
        }
    }
    return null
}

/**
 * Runs the finish [findAutoFinish] proves exists, folding each move through the ordinary
 * reducer with the same accounting the automatic cascade uses: one move per transfer. Returns
 * the fully-won state, or `null` if no finish is found within [maxNodes] — callers must check
 * this (or [canAutoFinish]) before committing; this function never partially applies a sweep to
 * real game state.
 */
fun simulateSweep(state: FreeCellState, maxNodes: Int = MAX_AUTO_FINISH_NODES): FreeCellState? {
    val moves = findAutoFinish(state, maxNodes) ?: return null
    var current = state
    for (move in moves) {
        current = applyMove(current, move).copy(moveCount = current.moveCount + 1)
    }
    return current
}

/**
 * What actually distinguishes two boards for this search: the piles, nothing else. [applyMove]
 * never touches [FreeCellState.seed], [FreeCellState.versions], or [FreeCellState.moveCount]
 * during this search (this file always adds the move count back afterward, in [simulateSweep]),
 * so those fields carry no information a visited-set needs — but a dedicated key, rather than the
 * full data class, keeps that guarantee explicit instead of accidental.
 */
private data class SweepKey(val tableau: List<List<Card>>, val freeCells: List<Card?>, val foundations: Map<Suit, Int>)

private fun FreeCellState.key(): SweepKey = SweepKey(tableau, freeCells, foundations)

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
 * Node ceiling for the finish search. Not yet measured against real endgame boards the way
 * Spider's own budget was (`docs/games/spider/rules/.../AutoFinish.kt`) — this search's shape
 * (below) makes runaway growth unlikely, but `AutoFinishBudgetTest` is what should replace this
 * comment with a measured number once real deals exist to measure against.
 */
private const val MAX_AUTO_FINISH_NODES = 20_000
