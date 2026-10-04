package org.finiteplay.freecell.solver

import org.finiteplay.freecell.layout.FREE_CELLS
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.TABLEAU_COLUMNS
import org.finiteplay.freecell.rules.Move
import org.finiteplay.freecell.rules.applyMove
import org.finiteplay.freecell.rules.legalMoves
import org.finiteplay.freecell.rules.runAutomaticFoundationCascade
import java.util.PriorityQueue

/**
 * Weighted best-first search for a *short* winning line. Hint's search, not the catalog's: [DepthFirstSolver]
 * takes whatever line it reaches first, which can run to thousands of moves.
 *
 * Exhausting the space is still a proof of no solution: the only moves cut are ones that reach a state another
 * kept move reaches ([candidateMoves]), and the cascade only makes foundation moves that can never be needed back
 * (`isSafeFoundationMove`).
 */
object BestFirstSolver {
    /**
     * An open entry holds no board — only how to rebuild it from [parent]'s — because the open set runs to
     * several times the node budget, and a board per entry is what would not fit a phone's heap. [state] is set
     * once the entry is popped and expanded.
     */
    private class Node(
        val parent: Node?,
        val moves: List<Move>,
        val hash: Long,
        val g: Int,
        val priority: Int,
        val order: Long,
    ) {
        var state: FreeCellState? = null
    }

    /** Each player move is followed by the automatic foundation cascade, which costs nothing — see HintEngine. */
    fun solve(start: FreeCellState, limits: SolverLimits): SolveOutcome {
        val startNanos = System.nanoTime()
        val deadlineNanos = startNanos + limits.maxDurationMs * 1_000_000L
        fun elapsedMs() = (System.nanoTime() - startNanos) / 1_000_000L

        if (start.isWon) return SolveOutcome.Solved(emptyList(), 0, 0)

        val open = PriorityQueue<Node>(compareBy<Node>({ it.priority }, { it.order }))
        val closed = HashSet<Long>()
        var order = 0L
        var nodes = 0L
        open += Node(null, emptyList(), canonicalSearchHash(start), 0, 0, order++).also { it.state = start }

        try {
            while (open.isNotEmpty()) {
                val node = open.poll()
                if (!closed.add(node.hash)) continue
                nodes++
                if (nodes > limits.maxNodes || System.nanoTime() > deadlineNanos) {
                    return SolveOutcome.Timeout(nodes, elapsedMs())
                }
                val state = node.state ?: node.moves.fold(node.parent!!.state!!, ::applyMove).also { node.state = it }

                for (move in candidateMoves(state)) {
                    val (child, automatic) = runAutomaticFoundationCascade(applyMove(state, move))
                    val moves = listOf(move) + automatic
                    if (child.isWon) return SolveOutcome.Solved(pathTo(node) + moves, nodes, elapsedMs())
                    val hash = canonicalSearchHash(child)
                    if (hash in closed) continue
                    val g = node.g + 1
                    open += Node(node, moves, hash, g, g + HEURISTIC_WEIGHT * remainingEstimate(child), order++)
                }
            }
            return SolveOutcome.Unsolved(nodes, elapsedMs())
        } catch (t: Throwable) {
            return SolveOutcome.Error(t.message ?: t.toString(), nodes, elapsedMs())
        }
    }

    private fun pathTo(node: Node): List<Move> {
        val chunks = ArrayList<List<Move>>()
        var current: Node? = node
        while (current != null) {
            chunks += current.moves
            current = current.parent
        }
        return chunks.asReversed().flatten()
    }
}

/** Measured on the catalog: 1 finds slightly shorter lines at several times the search; 3 is no faster than 2. */
private const val HEURISTIC_WEIGHT = 2

/**
 * [legalMoves] with the moves that only differ by *which* empty free cell or empty column they use cut to the
 * first, and a whole column moved into an empty one dropped — none of them reach a state the kept moves do not.
 */
private fun candidateMoves(state: FreeCellState): List<Move> {
    val firstEmptyCell = state.freeCells.indexOfFirst { it == null }
    val firstEmptyColumn = state.tableau.indexOfFirst { it.isEmpty() }
    return legalMoves(state).filter { move ->
        when (move) {
            is Move.TableauToFreeCell -> move.cell == firstEmptyCell
            is Move.TableauToTableau ->
                !state.tableau[move.toColumn].isEmpty() || (move.toColumn == firstEmptyColumn && move.fromIndex > 0)
            is Move.FreeCellToTableau -> !state.tableau[move.toColumn].isEmpty() || move.toColumn == firstEmptyColumn
            else -> true
        }
    }
}

/**
 * Not admissible, and not meant to be: cards still to bank, plus every card sitting above a lower card of its
 * own column (each must move before that card can go up), plus occupied free cells, less empty columns.
 */
private fun remainingEstimate(state: FreeCellState): Int {
    val banked = state.foundations.values.sum()
    var buried = 0
    for (column in 0 until TABLEAU_COLUMNS) {
        var lowestBelow = Int.MAX_VALUE
        for (card in state.tableau[column]) {
            if (card.rank.value > lowestBelow) buried++ else lowestBelow = card.rank.value
        }
    }
    val occupiedCells = FREE_CELLS - state.emptyFreeCells
    val emptyColumns = state.tableau.count { it.isEmpty() }
    return (52 - banked) + buried + occupiedCells - emptyColumns
}
