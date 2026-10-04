package org.finiteplay.freecell.deal

import org.finiteplay.solitaire.catalog.catalog.CatalogFormat
import org.finiteplay.solitaire.catalog.catalog.CatalogMetadata
import org.finiteplay.solitaire.catalog.catalog.encodeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeCellCertifiedDealCatalogTest {
    private val magic = CatalogFormat.magicBytes("FRCL")

    private fun validBytes(seeds: List<ULong> = listOf(100uL, 200uL, 300uL)) = encodeCatalog(
        CatalogMetadata(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1, solverVersion = 1, modeByte = null),
        seeds,
        magic,
    )

    @Test
    fun `loads the validated catalog`() {
        val loaded = FreeCellCertifiedDealCatalog.load { validBytes() }

        check(loaded is FreeCellCertifiedDealCatalog.LoadResult.Valid) { "expected Valid, got $loaded" }
        assertEquals(1, loaded.catalog.catalogVersion)
        assertEquals(listOf(100L, 200L, 300L), loaded.catalog.seeds)
    }

    @Test
    fun `rejects a catalog under the wrong magic`() {
        val bytes = encodeCatalog(
            CatalogMetadata(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1, solverVersion = 1, modeByte = null),
            listOf(1uL),
            CatalogFormat.MAGIC_BYTES,
        )
        val loaded = FreeCellCertifiedDealCatalog.load { bytes }

        assertTrue(loaded is FreeCellCertifiedDealCatalog.LoadResult.Invalid)
    }

    @Test
    fun `rejects unexpected versions`() {
        val bytes = encodeCatalog(
            CatalogMetadata(catalogVersion = 2, rulesVersion = 1, shuffleVersion = 1, solverVersion = 1, modeByte = null),
            listOf(1uL),
            magic,
        )
        val loaded = FreeCellCertifiedDealCatalog.load { bytes }

        assertTrue(loaded is FreeCellCertifiedDealCatalog.LoadResult.Invalid)
    }

    @Test
    fun `rejects an unexpected partition id`() {
        val bytes = encodeCatalog(
            CatalogMetadata(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1, solverVersion = 1, modeByte = null, partitionId = 1),
            listOf(1uL),
            magic,
        )
        val loaded = FreeCellCertifiedDealCatalog.load { bytes }

        assertTrue(loaded is FreeCellCertifiedDealCatalog.LoadResult.Invalid)
    }

    @Test
    fun `surfaces a read failure as Invalid rather than throwing`() {
        val loaded = FreeCellCertifiedDealCatalog.load { throw java.io.FileNotFoundException("missing") }

        assertTrue(loaded is FreeCellCertifiedDealCatalog.LoadResult.Invalid)
    }
}
