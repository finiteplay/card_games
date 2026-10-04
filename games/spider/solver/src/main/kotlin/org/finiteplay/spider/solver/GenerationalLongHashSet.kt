package org.finiteplay.spider.solver

/**
 * A transposition cache bounded to roughly [entriesPerGeneration] * [generations] live entries —
 * the closest practical counterpart to the reference solver's own bounded, evicting cache
 * (`docs/games/spider/EXECUTION_PLAN.md` "S6"; Blake & Gent's LRU cache, citations in
 * `docs/games/spider/PUBLIC_STRATEGY_RESEARCH.md` "Academic sources"), sized to run a real search
 * to a genuine one-hour wall-clock budget without the unbounded [LongHashSet]'s memory growing
 * without limit for that whole hour.
 *
 * **Eviction here means dropping a whole generation at once, not tracking exact per-entry
 * recency.** [add] checks every generation for the hash first; if absent everywhere, it inserts
 * into the newest generation, and once that generation reaches [entriesPerGeneration], the *oldest*
 * generation is dropped outright and a fresh one takes its place. This is coarser than textbook
 * LRU (which would need to track individual access order and move a hit entry to the front) but
 * needs none of that bookkeeping's failure modes: nothing here ever moves or deletes a single
 * entry out of an open-addressed array, so there is no backward-shift-deletion or tombstone
 * mechanism to get subtly wrong. A whole generation's [LongHashSet] is simply discarded and
 * garbage-collected. With several generations (the campaign default uses eight), the loss from any
 * one rotation is a small fraction of total live capacity, not a sudden halving.
 *
 * **Why dropping entries can only cost time, never correctness — the property that makes any
 * eviction policy here safe at all, coarse or exact.** A transposition cache exists purely to skip
 * *re-searching* a board already ruled out; it never invents a move and never merges two different
 * boards into one entry the way the unsound suit-discarding [streamlinedHashOf] does. So eviction
 * has exactly one effect: a board that was visited and then forgotten gets explored again if the
 * search happens to reach it a second time. Re-exploring a board that has no win still finds no
 * win — determinism guarantees that — so this can only spend extra nodes on work already done,
 * never hide a win that a full-memory search would have found. The one scenario worth naming
 * directly: an *ancestor* board on the current recursion path gets evicted, and a cycle in the
 * search graph leads back to it. Without a cache hit there, the search recurses into a board that
 * is technically already on its own call stack — but every recursive search in this file checks
 * `depth >= MAX_DEPTH - 1` on **every** call regardless of what the cache says, so a cycle like this
 * is still bounded by the same 600-move depth cap as everything else, not by the cache's own
 * correctness. The worst outcome is wasted nodes on a redundant branch, converting what might have
 * been a clean `SolveResult.EXHAUSTED` into `SolveResult.LIMIT` (the budget ran out doing avoidable
 * work) — never a false `EXHAUSTED`, and never a missed win. `LIMIT` already means "proves nothing
 * about this board," so that degradation is honest, not silent.
 */
internal class GenerationalLongHashSet(
    private val entriesPerGeneration: Int,
    generations: Int = 8,
) : TranspositionCache {
    init {
        require(generations >= 2) { "need at least 2 generations for a rotation to ever drop anything" }
        require(entriesPerGeneration >= 1)
    }

    // Oldest generation at the front, newest at the back — the two ends a rotation touches.
    private val gens: ArrayDeque<LongHashSet> = ArrayDeque<LongHashSet>(generations).apply {
        repeat(generations) { addLast(freshGeneration()) }
    }

    override val size: Int get() = gens.sumOf { it.size }

    /** Total backing-array words across every generation — for tests pinning real memory use, not live entry count ([size]). */
    internal val capacityWords: Int get() = gens.sumOf { it.capacityWords }

    override fun clear() {
        for (i in gens.indices) gens[i] = freshGeneration()
    }

    override fun add(hash: Long): Boolean {
        for (g in gens) if (g.contains(hash)) return false

        val newest = gens.last()
        newest.add(hash)
        if (newest.size >= entriesPerGeneration) {
            gens.removeFirst()
            gens.addLast(freshGeneration())
        }
        return true
    }

    // A modest load factor headroom (x2) so a generation rarely triggers LongHashSet's own internal
    // grow() before this class rotates it out on its own terms. entriesPerGeneration is rounded
    // *down* to a power of two first so LongHashSet's own up-rounding is a no-op: passed a
    // non-power-of-two capacity directly (the common case — a caller's memory budget divided by
    // generations rarely lands on one), LongHashSet.nextPowerOfTwo rounds it *up*, which can nearly
    // double the actual allocation. At a real S6 attempt's numbers (a 10GB budget, 8 generations,
    // 9/10 share for the exact cache) that turned into ~16GB actually allocated for a 9GB request —
    // eager, since every generation is created up front in the constructor — and OOMed the run
    // (`docs/games/spider/EXECUTION_PLAN.md` "S6" incident log). Rounding down instead means the
    // real total is always at or under the requested budget, never over it; the only cost is a
    // generation's live capacity landing on the power of two below entriesPerGeneration rather than
    // entriesPerGeneration itself, which only changes how often it rotates, never correctness.
    private fun freshGeneration(): LongHashSet = LongHashSet(Integer.highestOneBit(entriesPerGeneration.coerceAtLeast(1)) * 2)
}
