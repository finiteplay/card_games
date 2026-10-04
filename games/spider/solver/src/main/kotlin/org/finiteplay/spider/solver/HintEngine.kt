package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.applyMove
import org.finiteplay.spider.rules.isLegal

/**
 * The interactive on-device hint budget (`docs/games/spider/DESIGN.md` "Hint"). Unlike Klondike's
 * or FreeCell's own solvers, Spider's hint proves a whole line to a win rather than one step at a
 * time, so this budget is spent on the *entire* remaining game. [HintEngine] spends most of
 * [SolverLimits.maxMillis] on [PhaseSearch] and hands what is left to [SpiderSolver] under these
 * limits; [maxMillis] here is only the default, the app replaces it with the player's own timeout.
 *
 * [SolverLimits.useDfsAndAStarFallback] is off here: the full-legal DFS keeps a much larger
 * transposition set than this search can afford to hold interactively, and A*'s own node cap
 * (`AStarSolver.MAX_CREATED_NODES`) exhausts in well under a second regardless of the wall-clock
 * budget handed to it without solving the deals greedy and beam-limited DFS could not — both stages
 * exist for offline catalog generation, which can spend far more time and memory per deal, not for
 * a player waiting on a UI response. With them off, the only way this can prove a board lost is a
 * beam that never had to discard a move ([SpiderSolver]'s `beamPruned`).
 */
val HINT_SOLVER_LIMITS = SolverLimits(
    maxNodes = 400_000L,
    maxMillis = 4_000L,
    playouts = 40,
    useDfsAndAStarFallback = false,
    cacheCapacityPowerOfTwo = 1 shl 22,
)

/** Result of one [HintEngine.hint] call. */
sealed class HintOutcome {
    /** [move] is the next step of a search-proven line to a win from the current board. */
    data class Guidance(val move: Move) : HintOutcome()

    /** The search fully exhausted the reachable space from the current board without finding a win. */
    data object NoSolution : HintOutcome()

    /** The search hit its node or time budget before proving either outcome. */
    data object Inconclusive : HintOutcome()
}

/**
 * A stateful, per-game solver session backing Spider's own solver-backed Hint mode, mirroring the
 * shape of Klondike's and FreeCell's own `HintEngine` classes narrowed to what Spider's searches
 * actually give: [PhaseSearch] and [SpiderSolver] each hand back a whole line to a win, rather than
 * proving one step at a time, so there is no need for Klondike's/FreeCell's own dead-state or
 * short-line search stages here — the entire line is simply cached and read off move by move for
 * as long as the live board keeps matching it, only re-solving once the player deviates.
 *
 * Not thread-confined: callers are expected to serialize calls to [hint] (the app wraps this in a
 * single-flight coroutine dispatch, the same way Klondike's and FreeCell's own callers do).
 */
class HintEngine(
    private val solver: SpiderSolver = SpiderSolver(HINT_SOLVER_LIMITS),
    private val phaseSearch: PhaseSearch = PhaseSearch(),
) {
    private var cachedLine: List<Move>? = null
    private var cachedStates: List<SpiderState>? = null

    /**
     * Never trusts the search's own verdict: [HintOutcome.Guidance]'s move is confirmed legal
     * against [state] itself before being returned, the same "never validate itself" rule catalog
     * generation follows for a whole certificate (`docs/games/spider/DEALS.md` "Generation") — here
     * narrowed to the one move actually shown.
     */
    fun hint(state: SpiderState): HintOutcome {
        fastPathMove(state)?.let { return HintOutcome.Guidance(it) }

        val started = System.nanoTime()
        val budget = solver.limits.maxMillis
        val phaseLine = phaseSearch.certify(state, budget * PHASE_SEARCH_SHARE_PERCENT / 100)
        if (!phaseLine.isNullOrEmpty() && cacheLine(state, phaseLine)) return HintOutcome.Guidance(cachedLine!!.first())

        val remaining = budget - (System.nanoTime() - started) / 1_000_000
        val certified = solver.certifyWithOutcome(state, remaining.coerceAtLeast(MIN_FALLBACK_MILLIS))
        val line = certified.line
        return when {
            !line.isNullOrEmpty() && cacheLine(state, line) -> HintOutcome.Guidance(cachedLine!!.first())
            certified.outcome.provedUnsolvable -> {
                clearCache()
                HintOutcome.NoSolution
            }
            else -> {
                clearCache()
                HintOutcome.Inconclusive
            }
        }
    }

    /** Resets the cached line — call on New Game and Replay, where the search space starts over. */
    fun reset() {
        clearCache()
    }

    /**
     * Seeds the cached line with a solution already known for [start] — the line shipped with each
     * catalogued seed (`docs/games/spider/DEALS.md` "Shipped Solutions"), mirroring Klondike's and
     * FreeCell's own `HintEngine.primeWithKnownSolution`.
     *
     * Reuses [cacheLine] rather than a parallel mechanism, so a stored solution is subject to
     * exactly the same guarantee as a searched one: it is independently replayed through the
     * canonical reducer before being trusted, and silently ignored if any step turns out illegal.
     * A player following the hints then walks that line with no search at all; the moment they
     * deviate, the board stops matching and the search takes over as before.
     */
    fun primeWithKnownSolution(start: SpiderState, solution: List<Move>) {
        if (solution.isEmpty()) return
        cacheLine(start, solution)
    }

    /**
     * The cached line's own next move for [state], or null when there is no cached line or the
     * live board is not on it. Deliberately does not require the live board to be at the *start*
     * of the cached line: an undo can land back on an earlier state the same line already stood
     * on, and that earlier position still resolves instantly rather than forcing a fresh search.
     */
    private fun fastPathMove(state: SpiderState): Move? {
        val line = cachedLine ?: return null
        val states = cachedStates ?: return null
        val index = states.indexOf(state)
        if (index < 0) {
            clearCache()
            return null
        }
        val move = line[index]
        if (!isLegal(state, move)) {
            clearCache()
            return null
        }
        return move
    }

    /**
     * Independently replays [line] through the canonical reducer, recording the exact board before
     * each step. Returns `false` — without caching anything — if any step turns out illegal, which
     * would mean a solver/reducer mismatch rather than a real solution.
     *
     * A line that comes back to a board it already stood on has done nothing in between, so that
     * whole stretch is dropped — mirroring Klondike's and FreeCell's own `cacheCertificate`: telling
     * a player to shuffle a card out and back is pointless advice, and because the live board is
     * located on the line by matching state, a repeat would make the *first* occurrence match
     * forever, so following hints would oscillate between the two boards instead of advancing.
     */
    private fun cacheLine(start: SpiderState, line: List<Move>): Boolean {
        val moves = ArrayList<Move>(line.size)
        val states = ArrayList<SpiderState>(line.size)
        val positionOfState = HashMap<SpiderState, Int>(line.size)
        var current = start

        for (move in line) {
            if (!isLegal(current, move)) return false

            positionOfState[current]?.let { seenAt ->
                for (i in states.lastIndex downTo seenAt) positionOfState.remove(states[i])
                while (states.size > seenAt) {
                    states.removeAt(states.lastIndex)
                    moves.removeAt(moves.lastIndex)
                }
            }

            positionOfState[current] = states.size
            states += current
            moves += move
            current = applyMove(current, move)
        }

        cachedLine = moves
        cachedStates = states
        return true
    }

    private fun clearCache() {
        cachedLine = null
        cachedStates = null
    }

    private companion object {
        /**
         * Share of the budget [PhaseSearch] gets before [SpiderSolver] takes the rest. Phase search
         * goes first because its lines are the ones worth showing — they read like a player's, a
         * third to a half the length of [SpiderSolver]'s — and it wins far more of the boards a
         * player actually reaches. [SpiderSolver] still wins a few it misses, usually within a
         * second when it does, and is the only stage that can prove a board lost.
         */
        const val PHASE_SEARCH_SHARE_PERCENT = 75L

        /** Floor on the fallback's time, so a phase search that ran to its deadline still leaves it room for its cheap greedy playouts. */
        const val MIN_FALLBACK_MILLIS = 100L
    }
}
