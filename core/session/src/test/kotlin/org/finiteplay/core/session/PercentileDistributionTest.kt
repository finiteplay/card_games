package org.finiteplay.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PercentileDistributionTest {

    @Test
    fun `an empty sample yields no distribution`() {
        assertNull(PercentileDistribution.of(emptyList()))
    }

    @Test
    fun `a single value is every percentile`() {
        val distribution = PercentileDistribution.of(listOf(42L))!!

        assertEquals(1, distribution.sampleSize)
        assertEquals(42L, distribution.min)
        assertEquals(42L, distribution.p10)
        assertEquals(42L, distribution.p50)
        assertEquals(42L, distribution.p90)
        assertEquals(42L, distribution.max)
    }

    @Test
    fun `nearest-rank on an even sample`() {
        // rank = ceil(percentile * 10), clamped to 1..10.
        val distribution = PercentileDistribution.of((1L..10L).toList())!!

        assertEquals(10, distribution.sampleSize)
        assertEquals(1L, distribution.min)
        assertEquals(1L, distribution.p10) // ceil(0.10*10)=1
        assertEquals(5L, distribution.p50) // ceil(0.50*10)=5
        assertEquals(9L, distribution.p90) // ceil(0.90*10)=9
        assertEquals(10L, distribution.max)
    }

    @Test
    fun `nearest-rank on an odd sample`() {
        val distribution = PercentileDistribution.of((1L..7L).toList())!!

        assertEquals(1L, distribution.p10) // ceil(0.7)=1
        assertEquals(4L, distribution.p50) // ceil(3.5)=4
        assertEquals(7L, distribution.p90) // ceil(6.3)=7
    }

    @Test
    fun `unsorted and repeated values are handled the same as sorted ones`() {
        val distribution = PercentileDistribution.of(listOf(5L, 1L, 5L, 3L, 1L))!!
        val sorted = PercentileDistribution.of(listOf(1L, 1L, 3L, 5L, 5L))!!

        assertEquals(sorted, distribution)
    }
}
