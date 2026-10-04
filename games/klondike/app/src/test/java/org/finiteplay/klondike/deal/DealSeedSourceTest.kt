package org.finiteplay.klondike.deal

import org.junit.Assert.assertEquals
import org.junit.Test

class DealSeedSourceTest {

    @Test
    fun `steps through the interim list in order starting from hand 1 by default`() {
        val source = InterimSolvableDealSeedSource()

        val drawn = (1..INTERIM_SOLVABLE_SEEDS.size).map { source.nextSeed() }

        assertEquals(INTERIM_SOLVABLE_SEEDS, drawn)
    }

    @Test
    fun `wraps back to the first seed after the last one is dealt`() {
        val source = InterimSolvableDealSeedSource(startIndex = INTERIM_SOLVABLE_SEEDS.size - 1)

        assertEquals(INTERIM_SOLVABLE_SEEDS.last(), source.nextSeed())
        assertEquals(INTERIM_SOLVABLE_SEEDS.first(), source.nextSeed())
    }

    @Test
    fun `resumes from a persisted start index instead of hand 1`() {
        val source = InterimSolvableDealSeedSource(startIndex = 3)

        assertEquals(INTERIM_SOLVABLE_SEEDS[3], source.nextSeed())
        assertEquals(INTERIM_SOLVABLE_SEEDS[4], source.nextSeed())
    }

    @Test
    fun `currentIndex reflects the next seed to be handed out, for persisting position`() {
        val source = InterimSolvableDealSeedSource()
        assertEquals(0, source.currentIndex)

        source.nextSeed()
        assertEquals(1, source.currentIndex)
    }

    @Test
    fun `an out-of-range start index normalizes instead of crashing`() {
        // A stale persisted index from a shrunk interim list, or plain corruption.
        val source = InterimSolvableDealSeedSource(startIndex = INTERIM_SOLVABLE_SEEDS.size + 5)

        assertEquals(true, source.currentIndex in INTERIM_SOLVABLE_SEEDS.indices)
    }
}
