package org.finiteplay.klondike.solver.search

import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.TABLEAU_COLUMNS
import org.finiteplay.klondike.rules.Move
import java.util.PriorityQueue

/**
 * A crossing of the same run already counted this many times between the same pair
 * of columns (there and back, at least once) is deprioritized further: a third
 * crossing is rarely necessary and is exactly what reads as aimless shuttling to a
 * player following a hint.
 */
private const val SHUTTLE_REPEAT_THRESHOLD = 2

/** Order-independent key for one run crossing between [columnA] and [columnB]. */
private fun shuttleKey(columnA: Int, columnB: Int, sequenceBottomCardId: Int): Long {
    val lo = minOf(columnA, columnB).toLong()
    val hi = maxOf(columnA, columnB).toLong()
    return (lo shl 40) or (hi shl 32) or sequenceBottomCardId.toLong()
}

/** Node and wall-clock budgets for one search attempt (one starting board). */
data class SolverLimits(
    val maxNodes: Long = 1_500_000L,
    val maxDurationMs: Long = 15_000L,
)

/**
 * How the search orders its frontier: `f = g + lowerBoundWeight × movesLowerBound +
 * downCardWeight × faceDownCards + aceBurialWeight × cardsAboveAces +
 * neededCardDepthWeight × neededCardDepth`.
 *
 * The defaults are exact A*: weight 1 over the admissible [movesLowerBound] alone, so
 * the first goal popped is provably shortest. Raising [lowerBoundWeight] is standard
 * weighted A* — the found certificate is capped at weight × shortest. Nonzero
 * [downCardWeight]/[aceBurialWeight] blend in progress signals the admissible bound
 * cannot justify (face-down cards left; cards piled above aces) — these make the
 * search *informed* rather than admissible, so all length guarantees lapse, but on
 * hard boards they are what finds a certificate inside an interactive budget at all.
 * None of this ever affects soundness: a certificate is validated by replay, and an
 * exhaustion proof does not depend on visit order.
 */
data class SearchOrdering(
    val lowerBoundWeight: Int = 1,
    val downCardWeight: Int = 0,
    val aceBurialWeight: Int = 0,
    val neededCardDepthWeight: Int = 0,
) {
    init {
        require(lowerBoundWeight > 0) { "lower-bound weight must be positive" }
        require(downCardWeight >= 0 && aceBurialWeight >= 0 && neededCardDepthWeight >= 0) {
            "progress-signal weights must be non-negative"
        }
    }
}

/**
 * Result of one search attempt. Only [Solved] is ever admitted as usable — its
 * [Solved.certificate] must still pass independent replay (see `ReplayValidation.kt`)
 * before a seed counts as certified.
 */
sealed class SolveOutcome {
    abstract val nodes: Long
    abstract val elapsedMs: Long

    data class Solved(val certificate: List<Move>, override val nodes: Long, override val elapsedMs: Long) : SolveOutcome()
    data class Unsolved(override val nodes: Long, override val elapsedMs: Long) : SolveOutcome()
    data class Timeout(override val nodes: Long, override val elapsedMs: Long) : SolveOutcome()
    data class Error(val message: String, override val nodes: Long, override val elapsedMs: Long) : SolveOutcome()
}

/** One admitted state in the open/closed sets: enough to expand it and, once a goal is popped, to walk back to the root. */
private class SearchNode(
    val state: SNode,
    /** [canonicalStateHashOf] of [state], computed once at admission — recomputing at poll was pure waste. */
    val hash: Long,
    val g: Int,
    val f: Int,
    /** Total face-down cards on [state]'s board — the tie-break's progress signal. */
    val downCount: Int,
    val tieBreak: Long,
    /** Packed move that produced this state ([PACKED_MOVE_NONE] at the root) — a [Move] is only materialized during certificate walk-back. */
    val move: Int,
    val parent: SearchNode?,
)

/**
 * Any tie-break among equal f is safe for optimality, so everything past the first
 * key is free steering — and it matters enormously here, because the bound plateaus
 * hard (long stretches where nothing can bank and no draw helps leave f flat across
 * thousands of tableau shuffles). Within a plateau: fewest face-down cards first —
 * revealing hidden cards is the one form of progress the bound cannot see — then the
 * *deepest* node, probing depth-first toward the goal instead of sweeping
 * breadth-first, then the accumulated shuttle/inverse penalty, still steering which
 * equally-short line is found first.
 */
private val NODE_ORDER = Comparator<SearchNode> { first, second ->
    var comparison = first.f.compareTo(second.f)
    if (comparison == 0) comparison = first.downCount.compareTo(second.downCount)
    if (comparison == 0) comparison = second.g.compareTo(first.g)
    if (comparison == 0) comparison = first.tieBreak.compareTo(second.tieBreak)
    comparison
}

private fun totalDownCount(node: SNode): Int {
    var count = 0
    for (column in node.downCounts) count += column
    return count
}

/** The independently weighted h side of [SearchOrdering]'s f formula. */
internal fun orderingScore(node: SNode, downCount: Int, ordering: SearchOrdering, drawCount: Int): Int {
    var h = ordering.lowerBoundWeight * movesLowerBound(node, drawCount)
    if (ordering.downCardWeight != 0) h += ordering.downCardWeight * downCount
    if (ordering.aceBurialWeight != 0) h += ordering.aceBurialWeight * aceBurialCount(node)
    if (ordering.neededCardDepthWeight != 0) h += ordering.neededCardDepthWeight * neededCardDepth(node)
    return h
}

/**
 * A* over [SNode]s reachable from [start], keyed for the closed set by
 * [canonicalStateHashOf] and bounded by [movesLowerBound] (see both docs) — admissible, so
 * with [SearchOrdering.lowerBoundWeight] 1 and the progress-signal weights zero the
 * first goal popped is on a *shortest* certificate, not merely *a* certificate, unlike
 * a plain DFS. A lower-bound weight above 1 trades that
 * for speed the standard weighted-A* way: the bound is scaled by the weight, which
 * focuses expansion much harder toward the goal and caps the found certificate at
 * weight × shortest rather than shortest exactly. Exhaustion proofs are unaffected —
 * the weight changes expansion *order*, never which states are reachable — so
 * [SolveOutcome.Unsolved] remains a proof at any weight. This is
 * optimized-for-speed search state, not the canonical reducer — every certificate it
 * emits must be independently replayed through `applyMove` before it proves anything
 * (D1s: "the solver uses separate optimized transitions and must not validate
 * itself").
 *
 * Reports exactly one of [SolveOutcome.Solved] (found a path to all-foundations-13,
 * shortest under weight 1), [SolveOutcome.Unsolved] (the reachable search space was
 * exhausted with no win), [SolveOutcome.Timeout] (the node or time budget was hit
 * before either), or [SolveOutcome.Error] (an unexpected exception during search).
 */
object Solver {
    fun solve(
        start: GameState,
        limits: SolverLimits = SolverLimits(),
        includeFoundationWithdrawal: Boolean = false,
        ordering: SearchOrdering = SearchOrdering(),
    ): SolveOutcome =
        run(start, limits, includeFoundationWithdrawal, knownDead = null, mergeDeadInto = null, ordering = ordering)

    /**
     * As [solve], but seeds the closed set with [deadCache] (canonical hashes already
     * proven, in an earlier call, to have no path to a win) so it never re-expands
     * them, and — only when the search fully exhausts the reachable space without
     * finding a win ([SolveOutcome.Unsolved]) — merges every canonical state expanded
     * this call into [deadCache] for the next one. A state's dead-ness does not depend
     * on how the board reached it, so [deadCache] stays valid across an entire game,
     * including across undo. A [SolveOutcome.Timeout] merges nothing back, since a
     * node opened before the budget ran out is not proven dead, only unexpanded.
     *
     * This is the fast path for repeated interactive hint requests within one game: a
     * position identical to (or sharing dead subtrees with) one already searched
     * resolves far faster than a cold search would.
     *
     * [recordDeadOnExhaustion] must be `false` whenever this call searches a
     * *restricted* move set (e.g. [includeFoundationWithdrawal] off while the game
     * allows withdrawal): exhausting a restricted space proves nothing about the full
     * game, so its states must never enter [deadCache] as proven dead. Reading the
     * cache stays sound either way — a state with no path to a win under the full
     * move set certainly has none under a subset.
     */
    fun solveWithCache(
        start: GameState,
        limits: SolverLimits,
        deadCache: LongHashSet,
        includeFoundationWithdrawal: Boolean = true,
        maxDeadCacheSize: Int = Int.MAX_VALUE,
        ordering: SearchOrdering = SearchOrdering(),
        recordDeadOnExhaustion: Boolean = true,
    ): SolveOutcome = run(
        start, limits, includeFoundationWithdrawal,
        knownDead = deadCache,
        mergeDeadInto = if (recordDeadOnExhaustion) deadCache else null,
        maxDeadCacheSize, ordering,
    )

    private fun run(
        start: GameState,
        limits: SolverLimits,
        includeFoundationWithdrawal: Boolean,
        knownDead: LongHashSet?,
        mergeDeadInto: LongHashSet?,
        maxDeadCacheSize: Int = Int.MAX_VALUE,
        ordering: SearchOrdering = SearchOrdering(),
    ): SolveOutcome {
        val startNanos = System.nanoTime()
        val deadlineNanos = startNanos + limits.maxDurationMs * 1_000_000L
        val root = snodeFrom(start)
        // Fixed for the whole search: draw mode never changes mid-game, so this is
        // read once from the starting board rather than threaded as a caller param.
        val drawCount = when (start.drawMode) {
            DrawMode.ONE -> 1
            DrawMode.THREE -> 3
        }

        val rootHash = canonicalStateHashOf(root)
        if (knownDead != null && knownDead.contains(rootHash)) {
            // Already exhaustively proven dead by an earlier call this game.
            return SolveOutcome.Unsolved(0, (System.nanoTime() - startNanos) / 1_000_000L)
        }

        // canonicalStateHashOf(state) -> the best (smallest) g at which a state in that
        // canonical class has been admitted to open/closed so far. A canonical class
        // in knownDead is instead pruned at admission — it can never lead anywhere new.
        // Pre-size toward the node budget to avoid repeatedly rehashing the hottest
        // table, but cap the initial primitive arrays at about 6 MiB for app hints.
        val bestG = LongIntHashMap(
            expectedEntries = limits.maxNodes.coerceIn(1L shl 15, 1L shl 18).toInt(),
        )
        val open = PriorityQueue(NODE_ORDER)
        bestG.put(rootHash, 0)
        val rootDownCount = totalDownCount(root)
        open.add(
            SearchNode(
                root, rootHash, g = 0, f = orderingScore(root, rootDownCount, ordering, drawCount),
                downCount = rootDownCount, tieBreak = 0L, move = PACKED_MOVE_NONE, parent = null,
            ),
        )

        // How many times a move has admitted a run crossing a given pair of columns,
        // anywhere in the search so far (shuttleKey -> count). Not path-relative like
        // the old DFS version — A* has no single "current path" to be relative to —
        // just a global count of how well-worn that crossing already is; the outcome
        // this guards against (a visibly aimless hint) tolerates the approximation.
        val crossingCounts = LongIntHashMap(1 shl 10)

        // Per-search scratch, so the per-child hot path allocates only what a child
        // genuinely keeps (its SNode and SearchNode) — ART was measured GC-bound here.
        val moveBuffer = MoveBuffer()
        val destinationMasks = DestinationMaskBuffer()
        val signatureScratch = LongArray(TABLEAU_COLUMNS)

        var nodeCount = 0L
        var timedOut = false
        var errorMessage: String? = null
        var goal: SearchNode? = null

        try {
            while (open.isNotEmpty()) {
                nodeCount++
                if (nodeCount > limits.maxNodes || System.nanoTime() > deadlineNanos) {
                    timedOut = true
                    break
                }

                val current = open.poll()
                // Lazy deletion: a cheaper path to this canonical class may have been
                // found and admitted after this entry was enqueued; a plain priority
                // queue has no decrease-key, so stale entries are just skipped here
                // instead. (`> ` rather than `!=`: an equal-g duplicate is redundant
                // but not stale — either is safe to expand, but only one is needed.)
                if (current.g > bestG.getOrDefault(current.hash, Int.MAX_VALUE)) continue
                if (isWon(current.state)) {
                    goal = current
                    break
                }

                generateMovesPacked(current.state, moveBuffer, includeFoundationWithdrawal, destinationMasks)
                for (i in 0 until moveBuffer.size) {
                    val move = moveBuffer.moves[i]
                    // Tried last, never excluded — either can still occasionally be
                    // load-bearing for a win — but trying them first is what makes a
                    // found certificate visibly oscillate a card back and forth for
                    // the player following the hint: exactly undoing the move that
                    // just landed here, or crossing the same two columns with the
                    // same run a third time after it has already gone there and back
                    // once. This only affects which *equally short* solution is
                    // found first, never whether a shorter one is missed — that
                    // guarantee comes from `f`, the priority queue's primary key.
                    var penalty = packedPriority(move).toLong()
                    if (isInverseOfPreviousPacked(current.move, move)) penalty += 10L
                    var shuttle = 0L
                    var hasShuttle = false
                    if (packedIsTableauToTableau(move)) {
                        val fromColumn = packedFromColumn(move)
                        val sequenceBottomCardId =
                            current.state.columns[fromColumn][packedFromIndex(move)].toInt()
                        shuttle = shuttleKey(fromColumn, packedToColumn(move), sequenceBottomCardId)
                        hasShuttle = true
                        if (crossingCounts.getOrDefault(shuttle, 0) >= SHUTTLE_REPEAT_THRESHOLD) penalty += 20L
                    }

                    val nextState = applySearchMovePacked(current.state, move, drawCount)
                    val nextG = current.g + 1
                    val nextHash = canonicalStateHashOf(nextState, signatureScratch)
                    if (knownDead != null && knownDead.contains(nextHash)) continue
                    val existing = bestG.getOrDefault(nextHash, Int.MAX_VALUE)
                    if (existing <= nextG) continue
                    bestG.put(nextHash, nextG)
                    if (hasShuttle) crossingCounts.put(shuttle, crossingCounts.getOrDefault(shuttle, 0) + 1)
                    val nextDownCount = totalDownCount(nextState)
                    val nextF = nextG + orderingScore(nextState, nextDownCount, ordering, drawCount)
                    open.add(
                        SearchNode(
                            nextState, nextHash, nextG, nextF,
                            nextDownCount, current.tieBreak + penalty, move, current,
                        ),
                    )
                }
            }
        } catch (t: Throwable) {
            errorMessage = t.message ?: t.toString()
        }

        val nodes = nodeCount
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
        return when {
            errorMessage != null -> SolveOutcome.Error(errorMessage, nodes, elapsedMs)
            goal != null -> SolveOutcome.Solved(certificateOf(goal), nodes, elapsedMs)
            timedOut -> SolveOutcome.Timeout(nodes, elapsedMs)
            else -> {
                // open ran empty without a goal: every reachable canonical state was
                // admitted and (bar lazy-stale duplicates) expanded, so bestG's keys
                // are the full reachable closure from root, exhaustively proven dead.
                if (mergeDeadInto != null && mergeDeadInto.size < maxDeadCacheSize) bestG.forEachKey(mergeDeadInto::add)
                SolveOutcome.Unsolved(nodes, elapsedMs)
            }
        }
    }

    /** Walks real parent pointers from [goal] back to the root, never the canonical hash chain, so reconstruction is always exact. */
    private fun certificateOf(goal: SearchNode): List<Move> {
        val moves = ArrayList<Move>()
        var node: SearchNode? = goal
        while (node != null && node.move != PACKED_MOVE_NONE) {
            moves.add(unpackMove(node.move))
            node = node.parent
        }
        moves.reverse()
        return moves
    }
}

/**
 * Runs [Solver.solve] on a dedicated thread with a large stack. A* itself recurses
 * nowhere near as deep as the old DFS did, but [generateMoves] and friends still run
 * on this thread, and giving them the same headroom avoids reintroducing a stack-depth
 * dependency by accident.
 */
fun solveOnLargeStack(start: GameState, limits: SolverLimits = SolverLimits()): SolveOutcome =
    runOnLargeStack { Solver.solve(start, limits) }

/** As [solveOnLargeStack], but for [Solver.solveWithCache]. */
fun solveOnLargeStackWithCache(
    start: GameState,
    limits: SolverLimits,
    deadCache: LongHashSet,
    includeFoundationWithdrawal: Boolean = true,
    maxDeadCacheSize: Int = Int.MAX_VALUE,
    ordering: SearchOrdering = SearchOrdering(),
    recordDeadOnExhaustion: Boolean = true,
): SolveOutcome = runOnLargeStack {
    Solver.solveWithCache(
        start, limits, deadCache, includeFoundationWithdrawal, maxDeadCacheSize, ordering, recordDeadOnExhaustion,
    )
}

/**
 * Runs every block on its own solver thread at once and returns their outcomes in
 * the order given, after **all** of them finish. Joining all — rather than returning
 * at the first success — is what keeps a caller that prefers the earliest-listed
 * success deterministic: which thread finishes first varies with scheduling, but the
 * full outcome list never does.
 */
fun solveConcurrentlyOnLargeStacks(blocks: List<() -> SolveOutcome>): List<SolveOutcome> {
    val results = MutableList<SolveOutcome>(blocks.size) { SolveOutcome.Error("solver thread did not complete", 0, 0) }
    val threads = blocks.mapIndexed { index, block -> newSolverThread { results[index] = block() } }
    threads.forEach(Thread::start)
    threads.forEach(Thread::join)
    return results
}

private fun runOnLargeStack(block: () -> SolveOutcome): SolveOutcome {
    var result: SolveOutcome = SolveOutcome.Error("solver thread did not complete", 0, 0)
    val thread = newSolverThread { result = block() }
    thread.start()
    thread.join()
    return result
}

private fun newSolverThread(body: () -> Unit): Thread {
    val thread = Thread(null, body, "klondike-solver", 256L * 1024 * 1024)
    // Plain normal priority, deliberately: any positive nice value steers the
    // thread toward efficiency cores under big.LITTLE scheduling — measured on a
    // Pixel-class phone at roughly a third of a nice-0 thread's search throughput
    // (MIN_PRIORITY and NORM_PRIORITY-1 alike), which turned budget-sized searches
    // into user-visible timeouts. The UI thread is not at risk: Android schedules
    // it above normal on its own, so a nice-0 compute burst cannot starve it.
    thread.priority = Thread.NORM_PRIORITY
    return thread
}
