package org.finiteplay.freecell.solver

import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.rules.Move
import org.finiteplay.freecell.rules.applyMove
import org.finiteplay.freecell.rules.legalMoves

/**
 * Node, wall-clock, and depth budgets for one search attempt (one starting board).
 *
 * [maxDepth] is a last-resort circuit breaker against runaway recursion, not a quality control —
 * measured against real seeds, a *tight* depth cap made the solve rate dramatically worse rather
 * than shortening certificates the way it was meant to (see [moveScore]'s own note): it prunes a
 * genuinely necessary long branch just as readily as an unproductive one, forcing the search into
 * every *other*, usually no-better, branch instead. This DFS has no certificate-shortening pass,
 * so an accepted certificate's length is whatever this plain, unweighted search happens to find
 * first — occasionally several thousand moves on a board that needed real free-cell shuffling —
 * and nothing here claims otherwise (`docs/games/freecell/DEALS.md` "The solver").
 */
data class SolverLimits(
    val maxNodes: Long = 3_000_000L,
    val maxDurationMs: Long = 20_000L,
    val maxDepth: Int = 20_000,
)

/**
 * Result of one search attempt. Only [Solved] is ever admitted as usable — its
 * [Solved.certificate] must still pass independent replay through `:games:freecell:rules`'
 * own `isLegal`/`applyMove` (`docs/games/freecell/DEALS.md` "Generation") before a seed counts as
 * certified.
 */
sealed class SolveOutcome {
    abstract val nodes: Long
    abstract val elapsedMs: Long

    data class Solved(val certificate: List<Move>, override val nodes: Long, override val elapsedMs: Long) : SolveOutcome()
    data class Unsolved(override val nodes: Long, override val elapsedMs: Long) : SolveOutcome()
    data class Timeout(override val nodes: Long, override val elapsedMs: Long) : SolveOutcome()
    data class Error(val message: String, override val nodes: Long, override val elapsedMs: Long) : SolveOutcome()
}

/** True when [candidate] would exactly undo [previous] — the pure back-and-forth `RULES.md` never counts as progress. */
private fun isInverseOfPreviousMove(previous: Move?, candidate: Move): Boolean = when {
    previous is Move.TableauToFreeCell && candidate is Move.FreeCellToTableau ->
        candidate.cell == previous.cell && candidate.toColumn == previous.fromColumn
    previous is Move.FreeCellToTableau && candidate is Move.TableauToFreeCell ->
        candidate.fromColumn == previous.toColumn && candidate.cell == previous.cell
    previous is Move.TableauToTableau && candidate is Move.TableauToTableau ->
        candidate.fromColumn == previous.toColumn && candidate.toColumn == previous.fromColumn
    else -> false
}

/**
 * A single well-ordered depth-first search with transposition-table cycle detection
 * (`docs/games/freecell/DEALS.md` "The solver") — the starting approach FreeCell's routine
 * solvability is expected to need, unlike Klondike's weighted-A*, which exists specifically for
 * the deals a simple search cannot resolve within budget. Whether FreeCell ever needs more than
 * this DFS is exactly what `EXECUTION_PLAN.md`'s F5 package measures.
 *
 * "Well-ordered" is load-bearing: children are tried in [moveScore] order (foundation progress
 * first), not the generator's own emission order. Ordering only ever changes which certificate is
 * found first among many, never whether one is found: every legal move is still tried, with the
 * exact inverse of the previous move tried last rather than excluded (it is sometimes
 * load-bearing, on a board where nothing else makes progress).
 *
 * The transposition table is a plain, permanent visited set, not a path-relative one: this search
 * only ever asks "is a win reachable," never "what is the shortest path," so once a canonical
 * state's full subtree has been explored and found to have no path to a win, revisiting it by any
 * other route can only ever repeat that same answer.
 *
 * Measured against the first 30 ascending catalog seeds at the default limits: roughly 70% solve
 * within budget, none are proven unsolvable, and the rest time out — a larger budget (7×the nodes,
 * 4×the time) resolved none of that timed-out remainder, which reads as this search structurally
 * struggling on those particular boards rather than merely needing more of the same. That is
 * exactly the finding `docs/games/freecell/DEALS.md` "The solver" asks this package to report
 * rather than paper over: this plain DFS is not yet a complete answer for every deal, and
 * generation (below) is designed around skipping what it cannot resolve rather than blocking on
 * fixing it here.
 */
object DepthFirstSolver {
    /**
     * [rootPreviousMove] seeds the exact-inverse penalty at the search's own root, not just at
     * every node beneath it. Catalog generation never sets this (a fresh deal has no move to call
     * "previous" at all), but Hint does, passing the real move that produced [start] — without it,
     * a fresh search from a board just reached by following a hint has no way to know that
     * undoing that very move is the one thing guaranteed not to be progress, and can rank it as
     * highly as any other same-type move: on a real board that showed up as successive hints
     * shuffling a run back and forth between two columns forever instead of leading toward a win
     * (`docs/games/freecell/DESIGN.md` "Hint" — Klondike hit the same shape of bug once, from a
     * different cause, and the fix there is the same idea in spirit: make what was just done count
     * against doing it again).
     */
    fun solve(start: FreeCellState, limits: SolverLimits = SolverLimits(), rootPreviousMove: Move? = null): SolveOutcome {
        val startNanos = System.nanoTime()
        val deadlineNanos = startNanos + limits.maxDurationMs * 1_000_000L

        val visited = HashSet<Long>()
        val path = ArrayList<Move>()
        var nodeCount = 0L
        var timedOut = false
        var errorMessage: String? = null

        fun dfs(state: FreeCellState, previousMove: Move?): Boolean {
            nodeCount++
            if (nodeCount > limits.maxNodes || System.nanoTime() > deadlineNanos) {
                timedOut = true
                return false
            }
            if (state.isWon) return true
            if (path.size >= limits.maxDepth) return false

            val hash = canonicalSearchHash(state)
            if (!visited.add(hash)) return false

            val candidates = legalMoves(state).sortedBy { moveScore(it, isInverseOfPreviousMove(previousMove, it)) }
            for (move in candidates) {
                if (timedOut) return false
                path += move
                if (dfs(applyMove(state, move), move)) return true
                path.removeAt(path.lastIndex)
            }
            return false
        }

        val solved = try {
            dfs(start, previousMove = rootPreviousMove)
        } catch (t: Throwable) {
            errorMessage = t.message ?: t.toString()
            false
        }

        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
        return when {
            errorMessage != null -> SolveOutcome.Error(errorMessage, nodeCount, elapsedMs)
            solved -> SolveOutcome.Solved(path.toList(), nodeCount, elapsedMs)
            timedOut -> SolveOutcome.Timeout(nodeCount, elapsedMs)
            else -> SolveOutcome.Unsolved(nodeCount, elapsedMs)
        }
    }
}

/**
 * Lower tries first. Foundation moves (guaranteed progress) before a move that relocates a card
 * onto the tableau or drains a free cell back onto it (`TableauToTableau`/`FreeCellToTableau` —
 * these can only ever reduce how many free cells are occupied, never increase it), before
 * `TableauToFreeCell` (the one move that spends a free cell rather than freeing one), with the
 * exact inverse of the move that reached this node tried dead last.
 *
 * A heuristic ordering by the resulting state's own foundation deficit was tried and measured
 * worse, not better: greedily committing to the locally best-looking child at every node let DFS
 * sink arbitrarily deep into one unproductive branch that kept looking marginally better without
 * ever converging, burning the entire node budget on a single excursion instead of backtracking to
 * try alternatives. A *tight* [SolverLimits.maxDepth] was tried against that same problem next,
 * and made the solve rate dramatically worse instead of shortening certificates: it prunes a
 * genuinely necessary long branch just as readily as an unproductive one, forcing the search into
 * every other, usually no-better, branch. This plain ordering is what measured best of the three,
 * occasional very long certificates included.
 */
private fun moveScore(move: Move, isInverse: Boolean): Int {
    if (isInverse) return 100
    return when (move) {
        is Move.TableauToFoundation, is Move.FreeCellToFoundation -> 0
        is Move.TableauToTableau, is Move.FreeCellToTableau -> 1
        is Move.TableauToFreeCell -> 2
    }
}

/** DFS needs a larger stack than the JVM default because its traversal is recursive. */
fun solveOnLargeStack(start: FreeCellState, limits: SolverLimits = SolverLimits(), rootPreviousMove: Move? = null): SolveOutcome {
    var result: SolveOutcome = SolveOutcome.Error("solver thread did not complete", 0, 0)
    val thread = Thread(
        null,
        { result = DepthFirstSolver.solve(start, limits, rootPreviousMove) },
        "freecell-solver",
        256L * 1024 * 1024,
    )
    thread.priority = Thread.NORM_PRIORITY
    thread.start()
    thread.join()
    return result
}
