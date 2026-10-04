package org.finiteplay.cards

/**
 * SplitMix64 PRNG, frozen by the deterministic deal contract in
 * `docs/PLATFORM.md` ("Deterministic Shuffle"). Changing this algorithm requires a new
 * shuffle version.
 */
class SplitMix64(seed: Long) {

    private var state: ULong = seed.toULong()

    fun nextULong(): ULong {
        state += 0x9E3779B97F4A7C15uL
        var z = state
        z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
        z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
        return z xor (z shr 31)
    }

    /** Unbiased selection in `[0, bound)` for nonzero [bound], per the deal contract. */
    fun nextBounded(bound: UInt): UInt {
        require(bound > 0u) { "bound must be nonzero" }
        val b = bound.toULong()
        val threshold = (0uL - b) % b
        var value: ULong
        do {
            value = nextULong()
        } while (value < threshold)
        return (value % b).toUInt()
    }
}
