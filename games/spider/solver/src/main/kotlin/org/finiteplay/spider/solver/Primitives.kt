package org.finiteplay.spider.solver

/** A growable int list, so move generation allocates nothing per node. */
class IntArrayList(initialCapacity: Int = 64) {
    var data = IntArray(initialCapacity)
        private set
    var size = 0
        private set

    fun clear() { size = 0 }

    fun truncate(newSize: Int) {
        require(newSize in 0..size)
        size = newSize
    }

    fun add(value: Int) {
        ensure(size + 1)
        data[size++] = value
    }

    /** Inserts at [index], shifting the rest right — used to keep productive moves at the front. */
    fun insert(index: Int, value: Int) {
        ensure(size + 1)
        System.arraycopy(data, index, data, index + 1, size - index)
        data[index] = value
        size++
    }

    operator fun get(index: Int): Int = data[index]

    /**
     * Reverses in place. Search records a winning line from the deepest move outward as its
     * recursion unwinds, so what it collects is the line backwards; this is how the caller turns
     * it back into replay order without a second pass over the board.
     */
    fun reverse() {
        var i = 0
        var j = size - 1
        while (i < j) {
            val tmp = data[i]; data[i] = data[j]; data[j] = tmp
            i++; j--
        }
    }

    private fun ensure(capacity: Int) {
        if (capacity <= data.size) return
        data = data.copyOf(maxOf(capacity, data.size * 2))
    }
}

/**
 * What a transposition cache needs to offer the search, whether it is [LongHashSet]'s unbounded
 * form or [GenerationalLongHashSet]'s bounded, evicting one — so `SpiderSolver` can use either
 * without its search loops caring which (`docs/games/spider/EXECUTION_PLAN.md` "S6").
 */
internal interface TranspositionCache {
    /** Adds [hash], returning false when it was already present (in the unbounded form) or found
     * in any still-live generation (in the bounded form). */
    fun add(hash: Long): Boolean
    fun clear()
    val size: Int
}

/**
 * Open-addressed set of 64-bit hashes, for states already visited.
 *
 * A hash set of boxed Longs costs more in allocation than the search costs in work. This stores
 * the hashes directly and never stores the board, so a collision can in principle prune a line
 * that was actually new — at 64 bits that is rare enough to be worth the memory it saves, and it
 * can only ever lose a solution, never invent one.
 *
 * [requestedCapacity] is rounded up to the next power of two rather than trusted as one. The probe
 * sequence's `(i + 1) and mask` only visits every slot when the array size actually is a power of
 * two; passed a non-power-of-two size directly, it can settle into a sub-cycle that never reaches
 * an empty slot, and [add] spins forever the moment that sub-cycle fills — measured directly, on a
 * real S6 campaign command line typo (`cacheCapacityPowerOfTwo = 20`) that hung a solver thread for
 * 1h24m before it was noticed and force-killed. Rounding up here is what makes that class of typo
 * merely suboptimal instead of silently unbounded.
 */
class LongHashSet(requestedCapacity: Int = 1 shl 20) : TranspositionCache {
    private var keys = LongArray(nextPowerOfTwo(requestedCapacity))
    private var mask = keys.size - 1
    private var count = 0
    private var limit = keys.size / 2

    override val size: Int get() = count

    /** Backing array length, i.e. actual words allocated — for tests pinning real memory use, not live entry count ([size]). */
    internal val capacityWords: Int get() = keys.size

    override fun clear() {
        java.util.Arrays.fill(keys, 0L)
        count = 0
    }

    /** Adds [hash], returning false when it was already present. */
    override fun add(hash: Long): Boolean {
        val key = if (hash == 0L) 1L else hash
        var i = (key * -7046029254386353131L ushr 40).toInt() and mask
        while (true) {
            val existing = keys[i]
            if (existing == 0L) {
                keys[i] = key
                count++
                if (count > limit) grow()
                return true
            }
            if (existing == key) return false
            i = (i + 1) and mask
        }
    }

    /** Like [add], but never inserts — for [GenerationalLongHashSet]'s "is this in some *other*
     * generation" check, which must not create a phantom entry in the generation it happens to probe. */
    fun contains(hash: Long): Boolean {
        val key = if (hash == 0L) 1L else hash
        var i = (key * -7046029254386353131L ushr 40).toInt() and mask
        while (true) {
            val existing = keys[i]
            if (existing == 0L) return false
            if (existing == key) return true
            i = (i + 1) and mask
        }
    }

    private fun grow() {
        val old = keys
        val size = old.size * 2
        keys = LongArray(size)
        mask = size - 1
        limit = size / 2
        count = 0
        for (key in old) if (key != 0L) add(key)
    }
}

/** The smallest power of two at least [n], clamped to at least 1 (an empty or negative request would otherwise mask to -1 and index every access out of bounds). */
private fun nextPowerOfTwo(n: Int): Int {
    if (n <= 1) return 1
    return Integer.highestOneBit(n - 1) shl 1
}
