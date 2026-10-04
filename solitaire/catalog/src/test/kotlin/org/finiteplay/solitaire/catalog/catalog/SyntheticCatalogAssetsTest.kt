package org.finiteplay.solitaire.catalog.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Builds synthetic 1-, 10-, and 100,000-seed catalog assets with [encodeCatalog] from
 * arbitrary fixture seed values (no solving) and proves the encoder, loader, and
 * traversal all agree on the same saved-state schema, per the D1a gate.
 */
class SyntheticCatalogAssetsTest {

    private fun syntheticAsset(recordCount: Int): ByteArray {
        val seeds = (1..recordCount).map { it.toULong() }
        val metadata = CatalogMetadata(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1, solverVersion = 1)
        return encodeCatalog(metadata, seeds)
    }

    @Test
    fun encodeLoadAndTraverseAgreeAtEachCatalogSize() {
        for (recordCount in listOf(1, 10, 100_000)) {
            val asset = syntheticAsset(recordCount)

            val loaded = loadCatalog(asset)
            check(loaded is CatalogLoadResult.Valid) { "N=$recordCount: expected Valid, got $loaded" }
            assertEquals(recordCount, loaded.header.recordCount)
            assertEquals((1..recordCount).map { it.toULong() }, loaded.seeds)

            var state = DealTraversal.startTraversal(loaded.header.catalogVersion, recordCount)
            val visitedIndexes = mutableSetOf<Int>()
            repeat(recordCount) {
                val index = DealTraversal.indexAt(state, recordCount)
                assertTrue("N=$recordCount: index $index out of bounds", index in loaded.seeds.indices)
                visitedIndexes += index
                state = DealTraversal.advance(state, recordCount)
            }
            assertEquals("N=$recordCount: traversal must cover every seed exactly once", recordCount, visitedIndexes.size)
        }
    }
}
