package org.finiteplay.klondike.deal

import org.finiteplay.solitaire.catalog.catalog.CatalogMetadata
import org.finiteplay.solitaire.catalog.catalog.encodeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CertifiedDealCatalogTest {
    @Test
    fun `loads every difficulty from its matching validated partition`() {
        val loaded = CertifiedDealCatalog.load(::catalogBytes)
        assertTrue(loaded is CertifiedDealCatalog.LoadResult.Valid)
        val catalog = (loaded as CertifiedDealCatalog.LoadResult.Valid).catalog

        assertEquals(1, catalog.catalogVersion)
        assertEquals(100L, catalog.seedsFor(DifficultyTier.TRIVIAL).single())
        assertEquals(DifficultyTier.EXPERT, catalog.difficultyOf(500L))
        assertEquals(null, catalog.difficultyOf(999L))
    }

    @Test
    fun `rejects a partition assigned to the wrong difficulty`() {
        val loaded = CertifiedDealCatalog.load { name ->
            catalogBytes(name, partitionOffset = if (name == "hard.catalog") 1 else 0)
        }

        assertTrue(loaded is CertifiedDealCatalog.LoadResult.Invalid)
    }

    private fun catalogBytes(name: String, partitionOffset: Int = 0): ByteArray {
        val tier = DifficultyTier.entries.first { "${it.name.lowercase()}.catalog" == name }
        return encodeCatalog(
            CatalogMetadata(1, 1, 1, 1, partitionId = (tier.ordinal + 1 + partitionOffset).toByte()),
            listOf(((tier.ordinal + 1) * 100).toULong()),
        )
    }
}
