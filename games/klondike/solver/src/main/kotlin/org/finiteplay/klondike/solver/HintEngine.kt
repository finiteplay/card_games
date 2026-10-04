package org.finiteplay.klondike.solver

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.applyMove
import org.finiteplay.klondike.rules.isLegal
import org.finiteplay.klondike.solver.search.LongHashSet
import org.finiteplay.klondike.solver.search.SearchOrdering
import org.finiteplay.klondike.solver.search.SolveOutcome
import org.finiteplay.klondike.solver.search.SolverLimits
import org.finiteplay.klondike.solver.search.solveDepthFirstOnLargeStackWithCache
import org.finiteplay.klondike.solver.search.hashOf
import org.finiteplay.klondike.solver.search.snodeFrom
import org.finiteplay.klondike.solver.search.solveOnLargeStackWithCache

/** Result of one [HintEngine.hint] call. */
sealed class HintOutcome {
    abstract val nodes: Long
    abstract val elapsedMs: Long

    /** [move] is the next step of a search-proven winning line from the current board. */
    data class Guidance(val move: Move, override val nodes: Long, override val elapsedMs: Long) : HintOutcome()

    /** The search fully exhausted the reachable space from the current board without finding a win. */
    data class NoSolution(override val nodes: Long, override val elapsedMs: Long) : HintOutcome()

    /**
     * The search hit its node or time budget before proving either outcome.
     *
     * [suggestion], when present, is the move the Expert strategy ruleset
     * (`docs/games/klondike/DIFFICULTY_LEVELS.md`) would play here. It is a heuristic preference, *not*
     * a proven step toward a win — nothing about this board has been proven either way,
     * which is exactly why the outcome is inconclusive. It exists so the player still
     * gets something actionable instead of only an apology, and callers must present it
     * as a suggestion rather than as guidance.
     */
    data class Inconclusive(
        override val nodes: Long,
        override val elapsedMs: Long,
        val suggestion: Move? = null,
    ) : HintOutcome()
}

/** Caps memory spent on cross-call dead-state knowledge, per the 120 MB PSS budget (`docs/games/klondike/DESIGN.md` "Performance"). */
private const val MAX_DEAD_CACHE_SIZE = 400_000

/**
 * Move cap for each strategy-ruleset attempt that runs before the search. Matches the cap
 * the offline catalog generator grades against (`MAX_WINNING_LINE_MOVES`), so a hint
 * agrees with the grade a deal shipped with; a run that needs longer than this is
 * treated as a miss and handed to the search.
 */
private const val STRATEGY_LINE_MOVE_CAP = 500

/** The fixed DFS allowance, additional to the caller-supplied A* allowance. */
private const val DFS_NODE_LIMIT = 300_000L

/** Catalog-coverage ordering used by the final full-move A* stage. */
private val HINT_ASTAR_ORDERING = SearchOrdering(
    lowerBoundWeight = 25,
    downCardWeight = 150,
    aceBurialWeight = 25,
)

/** Observable pipeline stages, exposed internally so ordering is pinned by a unit test. */
internal enum class HintStage { TRIVIAL, EASY, MEDIUM, HARD, EXPERT, DFS, A_STAR_WITHDRAWAL }

/**
 * A stateful, per-game solver session backing the interactive hint feature
 * (`docs/games/klondike/DESIGN.md` "On-Device Hint Search"). Two caches carry work forward across
 * calls within the same game, so repeated hint requests — the normal way a player
 * uses this feature — get progressively cheaper instead of re-solving from scratch
 * every time:
 *
 * - A dead-state cache: every state the most recent exhaustive ([SolveOutcome.Unsolved])
 *   search proved has no path to a win, seeded into the next search's visited set so
 *   it never re-explores them. Valid for the lifetime of one game, including across
 *   undo, since a state's solvability never depends on how the board reached it.
 * - A certificate cache: the last winning line found — reordered for a player
 *   following it move by move ([reorderCertificateForFollowing]) — plus the state
 *   hash before each of its steps. When the live board matches one of those states
 *   exactly — the common case of a player following hints, or of automation
 *   replaying the same moves — the next hint resolves instantly by reading off the
 *   cached line instead of invoking the search at all.
 *
 * Not thread-confined: callers are expected to serialize calls to [hint] (the app
 * wraps this in a single-flight coroutine dispatch).
 */
class HintEngine internal constructor(
    private val dfsNodeLimit: Long,
    private val stageObserver: (HintStage) -> Unit,
    private val astarOrdering: SearchOrdering = HINT_ASTAR_ORDERING,
) {
    constructor() : this(DFS_NODE_LIMIT, {})

    private val deadCache = LongHashSet()
    private var cachedCertificate: List<Move>? = null
    private var cachedStateHashes: List<Long>? = null

    fun hint(state: GameState, limits: SolverLimits): HintOutcome {
        fastPathMove(state)?.let { return HintOutcome.Guidance(it, nodes = 0, elapsedMs = 0) }

        // Before spending search budget, try every cumulative strategy in tier order.
        // When one wins, cache its whole line exactly as a searched one would be.
        //
        // Caching the line is what makes successive hints lead somewhere. Returning only
        // the first move meant every later request replayed the ruleset from scratch
        // against a fresh cycle-prevention set, which cheerfully suggested the move that
        // undid the last one: on a real board that showed up as a run being shuffled
        // between two columns forever instead of progressing toward a win.
        for (tier in StrategyTier.entries) {
            stageObserver(HintStage.entries[tier.ordinal])
            strategyWinningLine(state, tier)?.let { return onSolved(state, it, nodes = 0, elapsedMs = 0) }
        }

        val startNanos = System.nanoTime()
        var nodesUsed = 0L

        val dfsNodes = minOf(dfsNodeLimit, limits.maxNodes)
        if (dfsNodes > 0 && limits.maxDurationMs > 0) {
            stageObserver(HintStage.DFS)
            val dfs = solveDepthFirstOnLargeStackWithCache(
                state,
                SolverLimits(maxNodes = dfsNodes, maxDurationMs = limits.maxDurationMs),
                deadCache,
                includeFoundationWithdrawal = true,
                maxDeadCacheSize = MAX_DEAD_CACHE_SIZE,
            )
            nodesUsed += dfs.nodes
            val elapsed = (System.nanoTime() - startNanos) / 1_000_000L
            when (dfs) {
                is SolveOutcome.Solved -> return onSolved(state, dfs.certificate, nodesUsed, elapsed)
                is SolveOutcome.Unsolved -> {
                    clearCertificateCache()
                    return HintOutcome.NoSolution(nodesUsed, elapsed)
                }
                is SolveOutcome.Timeout -> Unit
                is SolveOutcome.Error -> return HintOutcome.Inconclusive(nodesUsed, elapsed, expertSuggestion(state))
            }
        }

        // DFS is deliberately an additional cheap allowance. Do not debit its nodes
        // from the caller-supplied A* budget; only the wall-clock deadline is shared.
        val nodesLeft = limits.maxNodes
        val msLeft = limits.maxDurationMs - (System.nanoTime() - startNanos) / 1_000_000L
        if (nodesLeft > 0 && msLeft > 0) {
            stageObserver(HintStage.A_STAR_WITHDRAWAL)
            val astar = solveOnLargeStackWithCache(
                state,
                SolverLimits(maxNodes = nodesLeft, maxDurationMs = msLeft),
                deadCache,
                includeFoundationWithdrawal = true,
                maxDeadCacheSize = MAX_DEAD_CACHE_SIZE,
                ordering = astarOrdering,
            )
            nodesUsed += astar.nodes
            val elapsed = (System.nanoTime() - startNanos) / 1_000_000L
            when (astar) {
                is SolveOutcome.Solved -> return onSolved(state, astar.certificate, nodesUsed, elapsed)
                is SolveOutcome.Unsolved -> {
                    clearCertificateCache()
                    return HintOutcome.NoSolution(nodesUsed, elapsed)
                }
                is SolveOutcome.Timeout -> Unit
                is SolveOutcome.Error -> return HintOutcome.Inconclusive(nodesUsed, elapsed, expertSuggestion(state))
            }
        }
        // Nothing was proven within budget. Fall back to what the Expert ruleset would
        // play here, so the player has a move to act on alongside the notice.
        return HintOutcome.Inconclusive(nodesUsed, (System.nanoTime() - startNanos) / 1_000_000L, expertSuggestion(state))
    }

    /** The full [tier] line that reaches a win from [state], or null when it cannot. */
    private fun strategyWinningLine(state: GameState, tier: StrategyTier): List<Move>? =
        playPureRuleset(state, tier, maxMoves = STRATEGY_LINE_MOVE_CAP)
            .takeIf { it.outcome == PureOutcome.WON }
            ?.line
            ?.takeIf { it.isNotEmpty() }

    /** What the Expert ruleset would play next, whether or not it leads anywhere provable. */
    private fun expertSuggestion(state: GameState): Move? =
        preferenceGroups(state, StrategyTier.EXPERT).firstOrNull()?.firstOrNull()?.takeIf { isLegal(state, it) }

    /**
     * Seeds the certificate cache with a solution already known for [start] — the line
     * shipped with each catalogued seed (`docs/games/klondike/DEALS.md` "Shipped Solutions").
     *
     * Reuses the ordinary certificate cache rather than a parallel mechanism, so a
     * stored solution is subject to exactly the same guarantees as a searched one: it is
     * replayed through the canonical reducer before being trusted, and silently ignored
     * if any step turns out illegal. A player following the hints then walks that line
     * with no search at all; the moment they deviate, the board stops matching and the
     * search takes over as before.
     */
    fun primeWithKnownSolution(start: GameState, solution: List<Move>) {
        if (solution.isEmpty()) return
        if (!cacheCertificate(start, followable(start, solution))) cacheCertificate(start, solution)
    }

    /** Resets both caches — call on New Game and Replay, where the search space starts over. */
    fun reset() {
        deadCache.clear()
        clearCertificateCache()
    }

    private fun fastPathMove(state: GameState): Move? {
        val certificate = cachedCertificate ?: return null
        val stateHashes = cachedStateHashes ?: return null
        val liveHash = hashOf(snodeFrom(state))
        val index = stateHashes.indexOf(liveHash)
        if (index < 0) {
            clearCertificateCache()
            return null
        }
        val move = certificate[index]
        if (!isLegal(state, move)) {
            clearCertificateCache()
            return null
        }
        return move
    }

    private fun onSolved(state: GameState, certificate: List<Move>, nodes: Long, elapsedMs: Long): HintOutcome {
        if (certificate.isEmpty()) {
            clearCertificateCache()
            return HintOutcome.Inconclusive(nodes, elapsedMs)
        }
        val cached = cacheCertificate(state, followable(state, certificate)) || cacheCertificate(state, certificate)
        if (!cached) {
            clearCertificateCache()
            return HintOutcome.Inconclusive(nodes, elapsedMs)
        }
        return HintOutcome.Guidance(cachedCertificate!!.first(), nodes, elapsedMs)
    }

    /**
     * A proven line, turned into one worth handing to a player a move at a time.
     *
     * Reorder first, so optimization judges the line in the order it will actually be
     * shown; optimize, which drops out-and-back shuffling and stock rotation that buys
     * nothing ([optimizeWinningLine]); then reorder once more, since removing moves can
     * leave a draw sitting in front of a tableau move that should now precede it.
     *
     * Every caller keeps the as-found line as a fallback, so a defect anywhere in here
     * can only cost presentation — never turn a found win into Inconclusive.
     */
    private fun followable(state: GameState, certificate: List<Move>): List<Move> {
        val ordered = reorderCertificateForFollowing(state, certificate)
        return reorderCertificateForFollowing(state, optimizeWinningLine(state, ordered))
    }

    /**
     * Independently replays [certificate] through the canonical reducer, same as
     * `ReplayValidation.kt`, recording the state hash before each step. Returns
     * `false` — without caching anything — if any step turns out illegal, which would
     * mean a solver/reducer mismatch rather than a real solution; the search state
     * (`SNode`) is never trusted as self-certifying.
     */
    private fun cacheCertificate(start: GameState, certificate: List<Move>): Boolean {
        val moves = ArrayList<Move>(certificate.size)
        val hashes = ArrayList<Long>(certificate.size)
        val positionOfState = HashMap<Long, Int>(certificate.size)
        var current = start

        for (move in certificate) {
            if (!isLegal(current, move)) return false
            val hash = hashOf(snodeFrom(current))

            // A line that comes back to a board it already stood on has done nothing in
            // between, so drop that stretch. Two reasons, and the second is a real bug
            // this prevents: telling a player to shuffle a run out and back is pointless
            // advice, and - because the live board is located on the line by matching
            // state - a repeat makes the *first* occurrence match forever, so following
            // hints oscillates between the two boards instead of advancing. Reordering
            // for readability is what tends to bring an otherwise-separated pair of
            // mutually inverse moves together, so this is stripped after that, not before.
            positionOfState[hash]?.let { seenAt ->
                for (i in hashes.lastIndex downTo seenAt) positionOfState.remove(hashes[i])
                while (hashes.size > seenAt) {
                    hashes.removeAt(hashes.lastIndex)
                    moves.removeAt(moves.lastIndex)
                }
            }

            positionOfState[hash] = hashes.size
            hashes += hash
            moves += move
            current = applyMove(current, move)
        }

        cachedCertificate = moves
        cachedStateHashes = hashes
        return true
    }

    private fun clearCertificateCache() {
        cachedCertificate = null
        cachedStateHashes = null
    }
}
