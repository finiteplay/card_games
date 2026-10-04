package org.finiteplay.freecell.solver

import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.rules.Move
import org.finiteplay.freecell.rules.applyMove
import org.finiteplay.freecell.rules.isLegal

/**
 * The interactive on-device hint budget (`docs/games/freecell/DESIGN.md` "Hint"), measured against
 * the real certified catalog's own fresh boards rather than guessed: every one of its 2,000 seeds
 * resolves to [HintOutcome.Guidance] from its raw deal within this budget — measured at 224 ms
 * average and 2,986 ms worst case across the whole catalog, well inside the 5-second cap
 * (`docs/games/freecell/EXECUTION_PLAN.md` F6's own gate, `HintSearchBudgetTest`). A catalog seed
 * is, by construction, a board the same search already solved once at generation time, so a
 * repeat search from the identical fresh board is not a coincidence — it is the same deterministic
 * search finding the same answer again, comfortably inside a fraction of the ~3,000,000-node,
 * 20-second budget generation itself was allowed. A board reached by real, mid-game play — after
 * the player's own moves have diverged from every certified line — has no such guarantee and can
 * still resolve [HintOutcome.Inconclusive].
 */
val HINT_SOLVER_LIMITS = SolverLimits(maxNodes = 1_000_000L, maxDurationMs = 5_000L)

/**
 * The share of [HINT_SOLVER_LIMITS] Hint spends looking for a *short* line ([BestFirstSolver]) before falling
 * back to the DFS with what is left. Measured on the catalog's fresh boards: all but one of 2,000 resolve inside
 * it, at an average of about 90 moves and 25 ms; the one that does not is what the fallback is for.
 */
val HINT_BEST_FIRST_LIMITS = SolverLimits(maxNodes = 100_000L, maxDurationMs = 2_000L)

/** Result of one [HintEngine.hint] call. */
sealed class HintOutcome {
    abstract val nodes: Long
    abstract val elapsedMs: Long

    /** [move] is the next step of a search-proven winning line from the current board. */
    data class Guidance(val move: Move, override val nodes: Long, override val elapsedMs: Long) : HintOutcome()

    /** The search fully exhausted the reachable space from the current board without finding a win. */
    data class NoSolution(override val nodes: Long, override val elapsedMs: Long) : HintOutcome()

    /**
     * The search hit its node or time budget before proving either outcome. FreeCell has no
     * difficulty rulesets to draw a fallback suggestion from the way Klondike's Inconclusive
     * outcome does (`docs/games/freecell/DESIGN.md` "Hint" leaves this open, not mandated) — carrying
     * one here would mean building a heuristic-preference system with no other use in this game,
     * disproportionate to how rarely this outcome is expected to fire (`HINT_SOLVER_LIMITS`'s own
     * measurement note).
     */
    data class Inconclusive(override val nodes: Long, override val elapsedMs: Long) : HintOutcome()
}

/**
 * A stateful, per-game solver session backing the interactive hint feature, mirroring the shape of
 * Klondike's own `HintEngine` (`docs/games/klondike/DESIGN.md` "On-Device Hint Search") — narrowed
 * to what FreeCell actually needs. A certificate cache carries the last winning line found forward
 * across calls within the same game: the last winning line found, plus the exact board hash before
 * each of its steps ([exactStateHash], not the search's own symmetry-collapsed
 * [canonicalSearchHash] — a cached move names specific column and free-cell indices, so matching
 * has to mean "the literal same board", not merely an equivalent one). When the live board matches
 * one of those states exactly — the common case of a player following hints one after another —
 * the next hint resolves instantly by reading off the cached line instead of searching again.
 *
 * This is what a bare independent search per call cannot give: nothing stops a fresh search from
 * finding, as its first move, the exact reversal of whatever move just produced the board it is
 * searching from, since that search has no notion of "previous" at its own starting point. Followed
 * hint after hint, that reads as a card being shuffled back and forth between two spots forever
 * instead of leading anywhere — a real report against exactly this design, and the same shape of
 * bug Klondike hit once itself (git history: "stop the hint line telling players to undo their own
 * moves"). [hint]'s own `lastMove` parameter still seeds the search's exact-inverse penalty at its
 * root for the *first* search after a cache miss, the narrower fix this engine had before caching
 * existed at all; the cache is what extends that guarantee across every hint that follows, not just
 * the one immediately after a fresh search.
 *
 * A search runs [BestFirstSolver] first and the catalog's DFS only with whatever budget that leaves.
 * The DFS alone proved the wrong line to follow: it keeps the first win it reaches, hundreds to
 * thousands of moves on a deal a person wins in about ninety, and following it read as the loop the
 * cache was meant to stop — runs shuttled between the same columns for dozens of hints, Kings parked
 * in free cells, on a real report (seed 1924).
 *
 * Deliberately without Klondike's dead-state cache: that cache exists to make Klondike's much more
 * expensive weighted-A* search cheap across repeated calls, but a fresh catalog board resolves here in
 * tens of milliseconds on its own (measured — see [HINT_BEST_FIRST_LIMITS]), so the extra cache would
 * add memory and complexity with no measured need for it.
 *
 * Not thread-confined: callers are expected to serialize calls to [hint] (the app wraps this in a
 * single-flight coroutine dispatch, the same way Klondike's own caller does).
 */
class HintEngine {
    private var cachedCertificate: List<Move>? = null
    private var cachedStateHashes: List<Long>? = null

    /**
     * [lastMove] is whatever move actually produced [state] — the caller's own last committed
     * player move, whether played by hand or by following a previous hint — passed through as
     * [DepthFirstSolver.solve]'s `rootPreviousMove` when a fresh search actually runs. Null for
     * the very first hint of a game, or after an undo — there is no single move to call "previous"
     * in either case.
     *
     * Never trusts the search's own verdict: [HintOutcome.Guidance]'s move is confirmed legal
     * against [state] itself before being returned, the same "never validate itself" rule catalog
     * generation follows for a whole certificate (`docs/games/freecell/DEALS.md` "Generation") —
     * here narrowed to the one move actually shown.
     */
    fun hint(state: FreeCellState, limits: SolverLimits = HINT_SOLVER_LIMITS, lastMove: Move? = null): HintOutcome {
        fastPathMove(state)?.let { return HintOutcome.Guidance(it, nodes = 0, elapsedMs = 0) }

        val shortLine = BestFirstSolver.solve(
            state,
            SolverLimits(
                maxNodes = minOf(limits.maxNodes, HINT_BEST_FIRST_LIMITS.maxNodes),
                maxDurationMs = minOf(limits.maxDurationMs, HINT_BEST_FIRST_LIMITS.maxDurationMs),
            ),
        )
        when (shortLine) {
            is SolveOutcome.Solved -> return onSolved(state, shortLine.certificate, shortLine.nodes, shortLine.elapsedMs)
            is SolveOutcome.Unsolved -> {
                clearCertificateCache()
                return HintOutcome.NoSolution(shortLine.nodes, shortLine.elapsedMs)
            }
            is SolveOutcome.Timeout, is SolveOutcome.Error -> Unit
        }

        val remaining = limits.copy(
            maxNodes = (limits.maxNodes - shortLine.nodes).coerceAtLeast(1),
            maxDurationMs = (limits.maxDurationMs - shortLine.elapsedMs).coerceAtLeast(1),
        )
        val outcome = solveOnLargeStack(state, remaining, rootPreviousMove = lastMove)
        val nodes = shortLine.nodes + outcome.nodes
        val elapsedMs = shortLine.elapsedMs + outcome.elapsedMs
        return when (outcome) {
            // Shown but never cached: following the DFS's line is the loop this engine exists to avoid, so the
            // next request tries for a short line again from wherever this move leaves the board.
            is SolveOutcome.Solved -> onSolved(state, outcome.certificate, nodes, elapsedMs, keepLine = false)
            is SolveOutcome.Unsolved -> {
                clearCertificateCache()
                HintOutcome.NoSolution(nodes, elapsedMs)
            }
            is SolveOutcome.Timeout, is SolveOutcome.Error -> HintOutcome.Inconclusive(nodes, elapsedMs)
        }
    }

    /** Resets the certificate cache — call on New Game and Replay, where the search space starts over. */
    fun reset() {
        clearCertificateCache()
    }

    /**
     * Seeds the certificate cache with a solution already known for [start] — the line shipped
     * with each catalogued seed (`docs/games/freecell/DEALS.md` "Shipped Solutions"), mirroring
     * Klondike's own `HintEngine.primeWithKnownSolution`.
     *
     * Reuses the ordinary certificate cache rather than a parallel mechanism, so a stored solution
     * is subject to exactly the same guarantee as a searched one: it is replayed through the
     * canonical reducer before being trusted, and silently ignored if any step turns out illegal.
     * A player following the hints then walks that line with no search at all; the moment they
     * deviate, the board stops matching and the search takes over as before.
     */
    fun primeWithKnownSolution(start: FreeCellState, solution: List<Move>) {
        if (solution.isEmpty()) return
        cacheCertificate(start, solution)
    }

    /**
     * The cached line's own next move for [state], or null when there is no cached line or the
     * live board is not on it. Deliberately does not require the live board to be at the *start*
     * of the cached line: an undo can land back on an earlier state the same line already stood
     * on, and that earlier position still resolves instantly rather than forcing a fresh search
     * (a state's place on the line, not how the board reached it, is what fast-path matching
     * cares about).
     */
    private fun fastPathMove(state: FreeCellState): Move? {
        val certificate = cachedCertificate ?: return null
        val hashes = cachedStateHashes ?: return null
        val liveHash = exactStateHash(state)
        val index = hashes.indexOf(liveHash)
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

    private fun onSolved(state: FreeCellState, certificate: List<Move>, nodes: Long, elapsedMs: Long, keepLine: Boolean = true): HintOutcome {
        if (certificate.isEmpty() || !cacheCertificate(state, certificate)) {
            clearCertificateCache()
            return HintOutcome.Inconclusive(nodes, elapsedMs)
        }
        val move = cachedCertificate!!.first()
        if (!keepLine) clearCertificateCache()
        return HintOutcome.Guidance(move, nodes, elapsedMs)
    }

    /**
     * Independently replays [certificate] through the canonical reducer, recording the exact board
     * hash before each step. Returns `false` — without caching anything — if any step turns out
     * illegal, which would mean a solver/reducer mismatch rather than a real solution; the search's
     * own state is never trusted as self-certifying.
     *
     * A line that comes back to a board it already stood on has done nothing in between, so that
     * whole stretch is dropped. Two reasons, mirroring Klondike's own `cacheCertificate` exactly:
     * telling a player to shuffle a card out and back is pointless advice, and — because the live
     * board is located on the line by matching state — a repeat would make the *first* occurrence
     * match forever, so following hints would oscillate between the two boards instead of
     * advancing, the very failure mode this whole cache exists to close.
     */
    private fun cacheCertificate(start: FreeCellState, certificate: List<Move>): Boolean {
        val moves = ArrayList<Move>(certificate.size)
        val hashes = ArrayList<Long>(certificate.size)
        val positionOfState = HashMap<Long, Int>(certificate.size)
        var current = start

        for (move in certificate) {
            if (!isLegal(current, move)) return false
            val hash = exactStateHash(current)

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
