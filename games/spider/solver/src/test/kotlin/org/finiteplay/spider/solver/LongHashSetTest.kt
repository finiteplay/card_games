package org.finiteplay.spider.solver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LongHashSet]'s open-addressed probe (`(i + 1) and mask`) only visits every slot when the
 * backing array's size is an actual power of two. A non-power-of-two [LongHashSet] constructor
 * argument broke that once — a real S6 campaign command line typo, not a hypothetical — and hung a
 * solver thread in [LongHashSet.add]'s linear probe for 1h24m before it was noticed and
 * force-killed, because a full sub-cycle of slots never reaches an empty one to terminate on. This
 * pins the fix: any requested capacity, however malformed, must still terminate and still hold as
 * many entries as it claims to.
 */
class LongHashSetTest {
    @Test
    fun `a non-power-of-two capacity does not hang -- it is rounded up instead`() {
        // 20 is the exact value that hung a real campaign run; regression-testing that literal
        // number, not just "some odd number", is deliberate.
        val set = LongHashSet(20)

        // Must terminate at all, within a small bound well short of any real timeout — the bug
        // this pins made add() spin forever, so a naive "does this return" is the actual test.
        for (i in 1L..64L) set.add(i)

        assertEquals(64, set.size)
    }

    @Test
    fun `capacity 1 and 0 do not corrupt the mask into an out-of-bounds index`() {
        for (requested in listOf(0, 1, -5)) {
            val set = LongHashSet(requested)
            assertTrue(set.add(42L))
            assertFalse(set.add(42L))
            assertEquals(1, set.size)
        }
    }

    @Test
    fun `holds as many distinct entries as requested, growing past a small odd request`() {
        val set = LongHashSet(3)
        val values = (1L..5_000L).toList()
        for (v in values) set.add(v)
        assertEquals(values.size, set.size)
        for (v in values) assertFalse("value $v should already be present", set.add(v))
    }
}
