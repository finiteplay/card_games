package org.finiteplay.cards

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DeckShuffleTest {

    @Test
    fun `matches committed reference vectors`() {
        for ((seed, expected) in DECK_SHUFFLE_REFERENCE_VECTORS) {
            assertArrayEquals(
                "seed=$seed",
                expected.toIntArray(),
                shuffleDeckIndices(seed),
            )
        }
    }

    @Test
    fun `every shuffle is a permutation of the canonical deck`() {
        val seeds = listOf(0L, 1L, 2L, 100L, -100L, 555_555L, Long.MAX_VALUE)
        for (seed in seeds) {
            val result = shuffleDeckIndices(seed)
            assertEquals(DECK_SIZE, result.size)
            assertEquals((0 until DECK_SIZE).toSet(), result.toSet())
        }
    }

    @Test
    fun `is deterministic for a given seed`() {
        assertArrayEquals(shuffleDeckIndices(2026L), shuffleDeckIndices(2026L))
    }
}
