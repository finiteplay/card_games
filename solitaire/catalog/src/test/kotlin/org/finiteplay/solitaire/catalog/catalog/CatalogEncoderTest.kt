package org.finiteplay.solitaire.catalog.catalog

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogEncoderTest {

    @Test
    fun encodesTheFrozenHeaderLayout() {
        val metadata = CatalogMetadata(catalogVersion = 2, rulesVersion = 3, shuffleVersion = 4, solverVersion = 5)
        val bytes = encodeCatalog(metadata, listOf(10uL, 20uL, 30uL))

        assertEquals(CatalogFormat.HEADER_SIZE_V1 + 3 * CatalogFormat.SEED_RECORD_SIZE, bytes.size)
        assertEquals(CatalogFormat.MAGIC, String(bytes, 0, 4, Charsets.US_ASCII))

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(CatalogFormat.FORMAT_VERSION_1, buffer.getShort(CatalogFormat.OFFSET_FORMAT_VERSION))
        assertEquals(CatalogFormat.HEADER_SIZE_V1.toShort(), buffer.getShort(CatalogFormat.OFFSET_HEADER_SIZE))
        assertEquals(2, buffer.getInt(CatalogFormat.OFFSET_CATALOG_VERSION))
        assertEquals(3, buffer.getInt(CatalogFormat.OFFSET_RULES_VERSION))
        assertEquals(4, buffer.getInt(CatalogFormat.OFFSET_SHUFFLE_VERSION))
        assertEquals(5, buffer.getInt(CatalogFormat.OFFSET_SOLVER_VERSION))
        assertEquals(CatalogFormat.DRAW_MODE_DRAW_ONE, bytes[CatalogFormat.OFFSET_DRAW_MODE])
        // Byte 25 is the partition id and bytes 26-27 are what is left of the reserved run
        // (`EXECUTION_PLAN.md` "Deterministic Deal Contract"). Pinned here because the rules
        // freeze requires the contract and the implementation to agree by test, not by reading.
        assertEquals(25, CatalogFormat.OFFSET_PARTITION_ID)
        assertEquals(CatalogFormat.PARTITION_NONE, bytes[CatalogFormat.OFFSET_PARTITION_ID])
        assertEquals(26, CatalogFormat.OFFSET_RESERVED)
        assertEquals(2, CatalogFormat.RESERVED_BYTE_COUNT)
        for (i in 0 until CatalogFormat.RESERVED_BYTE_COUNT) {
            assertEquals(0.toByte(), bytes[CatalogFormat.OFFSET_RESERVED + i])
        }
        assertEquals(64, CatalogFormat.HEADER_SIZE_V1)
        assertEquals(3, buffer.getInt(CatalogFormat.OFFSET_RECORD_COUNT))
        assertEquals(10L, buffer.getLong(CatalogFormat.HEADER_SIZE_V1))
        assertEquals(20L, buffer.getLong(CatalogFormat.HEADER_SIZE_V1 + 8))
        assertEquals(30L, buffer.getLong(CatalogFormat.HEADER_SIZE_V1 + 16))
    }

    @Test
    fun roundTripsThroughTheLoader() {
        val metadata = CatalogMetadata(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1, solverVersion = 1)
        val seeds = listOf(1uL, 2uL, 3uL, 4uL)

        val result = loadCatalog(encodeCatalog(metadata, seeds))

        check(result is CatalogLoadResult.Valid) { "expected Valid, got $result" }
        assertEquals(seeds, result.seeds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnEmptySeedList() {
        encodeCatalog(CatalogMetadata(1, 1, 1, 1), emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateSeeds() {
        encodeCatalog(CatalogMetadata(1, 1, 1, 1), listOf(5uL, 5uL))
    }

    @Test
    fun encodesUnderACustomMagicWhenOneIsSupplied() {
        val magic = CatalogFormat.magicBytes("TEST")
        val bytes = encodeCatalog(CatalogMetadata(1, 1, 1, 1), listOf(1uL), magic)

        assertEquals("TEST", String(bytes, 0, 4, Charsets.US_ASCII))
    }

    @Test
    fun `a null mode byte moves the partition id one byte earlier and grows the reserved run by one`() {
        val bytes = encodeCatalog(CatalogMetadata(1, 1, 1, 1, modeByte = null, partitionId = 7), listOf(1uL))

        assertEquals(7.toByte(), bytes[CatalogFormat.OFFSET_PARTITION_ID_NO_MODE_BYTE])
        for (i in 0 until CatalogFormat.RESERVED_BYTE_COUNT_NO_MODE_BYTE) {
            assertEquals(0.toByte(), bytes[CatalogFormat.OFFSET_RESERVED_NO_MODE_BYTE + i])
        }
        // The header is still exactly 64 bytes: the byte a mode would have used is not dropped,
        // just repurposed, so nothing else in the layout shifts.
        assertEquals(CatalogFormat.HEADER_SIZE_V1 + CatalogFormat.SEED_RECORD_SIZE, bytes.size)
    }
}
