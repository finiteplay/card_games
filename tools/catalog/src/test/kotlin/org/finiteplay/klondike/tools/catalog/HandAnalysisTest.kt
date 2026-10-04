package org.finiteplay.klondike.tools.catalog

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Test

class HandAnalysisTest {
    @Test
    fun `disabling path counts preserves the state census`() {
        val counted = censusByDepth(seed = 78, maxDepth = 5, maxFrontier = 100_000, log = {})
        val uncounted = censusByDepth(
            seed = 78,
            maxDepth = 5,
            maxFrontier = 100_000,
            countPaths = false,
            log = {},
        )

        assertEquals(counted.size, uncounted.size)
        for (index in counted.indices) {
            assertEquals(counted[index].copy(paths = BigInteger.ZERO), uncounted[index])
        }
    }
}
