package org.finiteplay.klondike.solver.search

import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.TABLEAU_COLUMNS
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.isInverseOfPreviousMove

/**
 * Depth-first full-legal search used as the cheap bounded stage before A* in the hint
 * pipeline. It changes only traversal order: certificates are replay-validated by
 * [org.finiteplay.klondike.solver.HintEngine], and only exhaustive full-move searches
 * may contribute states to [deadCache].
 */
object DepthFirstSolver {
    fun solveWithCache(
        start: GameState,
        limits: SolverLimits,
        deadCache: LongHashSet,
        includeFoundationWithdrawal: Boolean = true,
        maxDeadCacheSize: Int = Int.MAX_VALUE,
    ): SolveOutcome {
        val startNanos = System.nanoTime()
        val deadlineNanos = startNanos + limits.maxDurationMs * 1_000_000L
        val root = snodeFrom(start)
        val signatureScratch = LongArray(TABLEAU_COLUMNS)
        val rootHash = canonicalStateHashOf(root, signatureScratch)
        if (deadCache.contains(rootHash)) return SolveOutcome.Unsolved(0, 0)

        val drawCount = if (start.drawMode == DrawMode.THREE) 3 else 1
        val visited = LongIntHashMap()
        val path = ArrayList<Move>()
        var nodeCount = 0L
        var timedOut = false
        var errorMessage: String? = null

        fun dfs(node: SNode, previousMove: Move?): Boolean {
            nodeCount++
            if (nodeCount > limits.maxNodes || System.nanoTime() > deadlineNanos) {
                timedOut = true
                return false
            }
            if (isWon(node)) return true
            val hash = canonicalStateHashOf(node, signatureScratch)
            if (deadCache.contains(hash) || visited.getOrDefault(hash, 0) != 0) return false
            visited.put(hash, 1)

            // Preserve the generator's productive-first order, except that an exact
            // inverse of the move that reached this node is tried last.
            val candidates = generateMoves(node, includeFoundationWithdrawal)
                .sortedBy { if (isInverseOfPreviousMove(previousMove, it.move)) 1 else 0 }
            for (scored in candidates) {
                if (timedOut) return false
                path += scored.move
                val solved = dfs(applySearchMove(node, scored.move, drawCount), scored.move)
                if (solved) return true
                path.removeAt(path.lastIndex)
            }
            return false
        }

        val solved = try {
            dfs(root, previousMove = null)
        } catch (t: Throwable) {
            errorMessage = t.message ?: t.toString()
            false
        }
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
        return when {
            errorMessage != null -> SolveOutcome.Error(errorMessage, nodeCount, elapsedMs)
            solved -> SolveOutcome.Solved(path.toList(), nodeCount, elapsedMs)
            timedOut -> SolveOutcome.Timeout(nodeCount, elapsedMs)
            else -> {
                if (includeFoundationWithdrawal && deadCache.size < maxDeadCacheSize) {
                    visited.forEachKey(deadCache::add)
                }
                SolveOutcome.Unsolved(nodeCount, elapsedMs)
            }
        }
    }
}

/** DFS needs a larger stack than the JVM default because its traversal is recursive. */
fun solveDepthFirstOnLargeStackWithCache(
    start: GameState,
    limits: SolverLimits,
    deadCache: LongHashSet,
    includeFoundationWithdrawal: Boolean = true,
    maxDeadCacheSize: Int = Int.MAX_VALUE,
): SolveOutcome {
    var result: SolveOutcome = SolveOutcome.Error("DFS thread did not complete", 0, 0)
    val thread = Thread(
        null,
        {
            result = DepthFirstSolver.solveWithCache(
                start, limits, deadCache, includeFoundationWithdrawal, maxDeadCacheSize,
            )
        },
        "klondike-dfs",
        256L * 1024 * 1024,
    )
    thread.priority = Thread.NORM_PRIORITY
    thread.start()
    thread.join()
    return result
}
