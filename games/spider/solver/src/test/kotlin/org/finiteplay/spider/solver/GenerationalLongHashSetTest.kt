package org.finiteplay.spider.solver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GenerationalLongHashSet]'s whole point is bounding memory by forgetting old entries — these
 * tests pin that it actually stays bounded, that recently-added entries are still found (or the
 * "cache" part of a transposition cache is worthless), and that sufficiently old entries really do
 * get forgotten (or the "bounded" part is a lie). [GenerationalLongHashSet]'s own doc carries the
 * argument for why forgetting an entry is safe for a search to rely on at all; this file does not
 * re-litigate that, only the data structure's own bookkeeping.
 */
class GenerationalLongHashSetTest {

    @Test
    fun `a freshly added entry is found`() {
        val set = GenerationalLongHashSet(entriesPerGeneration = 10, generations = 4)
        assertTrue(set.add(42L))
        assertFalse("just-added entry must be found", set.add(42L))
    }

    @Test
    fun `size never exceeds entriesPerGeneration times generations by more than one generation's worth`() {
        val entriesPerGeneration = 100
        val generations = 4
        val set = GenerationalLongHashSet(entriesPerGeneration, generations)
        for (i in 1L..100_000L) set.add(i)
        // The newest generation can hold up to entriesPerGeneration before rotating, so the true
        // ceiling is one generation above the nominal total — not exactly the nominal total.
        val ceiling = entriesPerGeneration * (generations + 1)
        assertTrue("size ${set.size} exceeded the eviction ceiling $ceiling", set.size <= ceiling)
    }

    @Test
    fun `sufficiently old entries are eventually forgotten`() {
        val set = GenerationalLongHashSet(entriesPerGeneration = 50, generations = 3)
        set.add(1L)
        // Enough insertions to rotate every generation this value could have landed in out at
        // least once over: generations * entriesPerGeneration is the total live capacity, so this
        // many *new* insertions guarantees at least one full rotation cycle has passed.
        for (i in 2L..(50L * 3 * 3)) set.add(i)
        assertTrue("value 1 should have been forgotten by now and re-insertable", set.add(1L))
    }

    @Test
    fun `recently added entries survive many more insertions than the oldest generation`() {
        val entriesPerGeneration = 50
        val set = GenerationalLongHashSet(entriesPerGeneration, generations = 4)
        for (i in 1L..entriesPerGeneration.toLong()) set.add(i)
        // Only enough new insertions to rotate the single oldest generation, not the whole cache.
        for (i in 1000L until 1000L + entriesPerGeneration) set.add(i)
        val stillPresent = (1L..entriesPerGeneration.toLong()).count { !set.add(it) }
        assertTrue("expected most of the pre-rotation entries to still be live, found $stillPresent/$entriesPerGeneration", stillPresent > entriesPerGeneration / 2)
    }

    @Test
    fun `actual allocated words never exceed the requested budget, even off a power of two`() {
        // A non-power-of-two entriesPerGeneration is the common case (a caller's memory budget
        // divided by generations rarely lands on one) and is exactly what a real S6 attempt hit: a
        // 10GB budget produced entriesPerGeneration=75,497,472, and LongHashSet's own up-rounding of
        // `entriesPerGeneration * 2` (150,994,944, not a power of two) turned that into 268,435,456 —
        // nearly double — per generation, ballooning a 9GB share into ~16GB actually allocated and
        // OOMing the run. 75 mirrors that ratio (75 -> doubled 150, between the powers of two 128
        // and 256) at a size cheap enough to allocate in a test.
        val entriesPerGeneration = 75
        val generations = 4
        val set = GenerationalLongHashSet(entriesPerGeneration, generations)
        val requestedWords = entriesPerGeneration.toLong() * 2 * generations
        assertTrue(
            "allocated ${set.capacityWords} words against a ${requestedWords}-word request — eviction sizing must round down, not up",
            set.capacityWords <= requestedWords,
        )
    }

    @Test
    fun `clear empties every generation`() {
        val set = GenerationalLongHashSet(entriesPerGeneration = 10, generations = 2)
        set.add(1L)
        set.add(2L)
        set.clear()
        assertEquals(0, set.size)
        assertTrue("1 should be re-insertable after clear", set.add(1L))
    }
}
