package org.finiteplay.klondike.solver.search

/**
 * Open-addressing hash structures over primitive `Long` keys, replacing boxed
 * `HashMap<Long, Int>`/`HashSet<Long>` on the search hot path: the A* loop probes and
 * inserts one of these per generated child, and boxing every key was a measurable
 * share of node throughput (and of the frontier's memory) at interactive budgets.
 *
 * Both reserve the key `0L` internally and track it with a side flag, so the backing
 * array can use `0L` as its empty-slot marker without ever misreading a genuine zero
 * key (astronomically rare for the hash-valued keys stored here, but free to support).
 * Neither supports removal — the search only ever adds.
 */
private fun tableSizeFor(expectedEntries: Int): Int {
    var size = 16
    while (size < expectedEntries * 2) size = size shl 1
    return size
}

/** Finalizing mixer (SplitMix64's constant) so clustered key bits don't cluster probes. */
private fun mixKey(key: Long): Int {
    var h = key * -7046029254386353131L
    h = h xor (h ushr 32)
    return h.toInt()
}

/** Grows at 60% load. Values are whatever the caller stores; `getOrDefault` is the only read. */
class LongIntHashMap(expectedEntries: Int = 1 shl 15) {
    private var keys = LongArray(tableSizeFor(expectedEntries))
    private var values = IntArray(keys.size)
    private var entries = 0
    private var hasZeroKey = false
    private var zeroValue = 0

    val size: Int get() = entries + if (hasZeroKey) 1 else 0

    fun getOrDefault(key: Long, default: Int): Int {
        if (key == 0L) return if (hasZeroKey) zeroValue else default
        val mask = keys.size - 1
        var i = mixKey(key) and mask
        while (true) {
            val k = keys[i]
            if (k == key) return values[i]
            if (k == 0L) return default
            i = (i + 1) and mask
        }
    }

    fun put(key: Long, value: Int) {
        if (key == 0L) {
            hasZeroKey = true
            zeroValue = value
            return
        }
        val mask = keys.size - 1
        var i = mixKey(key) and mask
        while (true) {
            val k = keys[i]
            if (k == key) {
                values[i] = value
                return
            }
            if (k == 0L) {
                keys[i] = key
                values[i] = value
                entries++
                if (entries * 5 >= keys.size * 3) grow()
                return
            }
            i = (i + 1) and mask
        }
    }

    fun forEachKey(action: (Long) -> Unit) {
        if (hasZeroKey) action(0L)
        for (k in keys) if (k != 0L) action(k)
    }

    private fun grow() {
        val oldKeys = keys
        val oldValues = values
        keys = LongArray(oldKeys.size shl 1)
        values = IntArray(keys.size)
        entries = 0
        for (i in oldKeys.indices) {
            if (oldKeys[i] != 0L) put(oldKeys[i], oldValues[i])
        }
    }
}

/** The dead-state cache's storage; see [Solver.solveWithCache]. */
class LongHashSet(expectedEntries: Int = 1 shl 15) {
    private var keys = LongArray(tableSizeFor(expectedEntries))
    private var entries = 0
    private var hasZeroKey = false

    val size: Int get() = entries + if (hasZeroKey) 1 else 0

    fun isEmpty(): Boolean = size == 0

    fun contains(key: Long): Boolean {
        if (key == 0L) return hasZeroKey
        val mask = keys.size - 1
        var i = mixKey(key) and mask
        while (true) {
            val k = keys[i]
            if (k == key) return true
            if (k == 0L) return false
            i = (i + 1) and mask
        }
    }

    fun add(key: Long) {
        if (key == 0L) {
            hasZeroKey = true
            return
        }
        val mask = keys.size - 1
        var i = mixKey(key) and mask
        while (true) {
            val k = keys[i]
            if (k == key) return
            if (k == 0L) {
                keys[i] = key
                entries++
                if (entries * 5 >= keys.size * 3) grow()
                return
            }
            i = (i + 1) and mask
        }
    }

    fun clear() {
        keys = LongArray(tableSizeFor(1 shl 15))
        entries = 0
        hasZeroKey = false
    }

    private fun grow() {
        val oldKeys = keys
        keys = LongArray(oldKeys.size shl 1)
        entries = 0
        for (k in oldKeys) if (k != 0L) add(k)
    }
}
