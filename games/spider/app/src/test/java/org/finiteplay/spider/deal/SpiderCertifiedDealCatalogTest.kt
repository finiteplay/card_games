package org.finiteplay.spider.deal

import org.finiteplay.solitaire.catalog.catalog.CatalogMetadata
import org.finiteplay.solitaire.catalog.catalog.encodeCatalog
import org.finiteplay.spider.layout.SuitCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpiderCertifiedDealCatalogTest {
    @Test
    fun `loads ONE, TWO and FOUR from their matching validated partition`() {
        val loaded = SpiderCertifiedDealCatalog.load(::catalogBytes)
        assertTrue(loaded is SpiderCertifiedDealCatalog.LoadResult.Valid)
        val catalog = (loaded as SpiderCertifiedDealCatalog.LoadResult.Valid).catalog

        assertEquals(1, catalog.catalogVersion)
        assertEquals(100L, catalog.seedsFor(SuitCount.ONE)?.single())
        assertEquals(200L, catalog.seedsFor(SuitCount.TWO)?.single())
        assertEquals(300L, catalog.seedsFor(SuitCount.FOUR)?.single())
    }

    @Test
    fun `rejects a partition assigned to the wrong suit count`() {
        val loaded = SpiderCertifiedDealCatalog.load { name ->
            catalogBytes(name, partitionOffset = if (name == "two.catalog") 1 else 0)
        }
        assertTrue(loaded is SpiderCertifiedDealCatalog.LoadResult.Invalid)
    }

    @Test
    fun `the shipped assets load, FOUR included`() {
        val dir = java.io.File("src/main/assets/catalogs")
        val loaded = SpiderCertifiedDealCatalog.load { name -> java.io.File(dir, name).readBytes() }
        assertTrue(loaded.toString(), loaded is SpiderCertifiedDealCatalog.LoadResult.Valid)
        val catalog = (loaded as SpiderCertifiedDealCatalog.LoadResult.Valid).catalog
        assertTrue(catalog.seedsFor(SuitCount.FOUR).orEmpty().size > 5_000)
    }

    private fun catalogBytes(name: String, partitionOffset: Int = 0): ByteArray {
        val suitCount = SuitCount.entries.first { "${it.name.lowercase()}.catalog" == name }
        return encodeCatalog(
            CatalogMetadata(1, 1, 1, 1, partitionId = (suitCount.ordinal + 1 + partitionOffset).toByte()),
            listOf(((suitCount.ordinal + 1) * 100).toULong()),
        )
    }
}
