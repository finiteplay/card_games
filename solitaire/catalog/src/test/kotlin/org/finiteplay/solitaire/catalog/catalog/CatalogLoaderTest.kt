package org.finiteplay.solitaire.catalog.catalog

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One test per rejection reason required by the D1a gate, plus a positive control.
 * Malformed cases too specific to build with [encodeCatalog] (bad hash, non-canonical
 * record counts, duplicate seed bytes) are assembled directly with [rawCatalogBytes].
 */
class CatalogLoaderTest {

    private val seeds = listOf(100uL, 200uL, 300uL)
    private val metadata = CatalogMetadata(catalogVersion = 9, rulesVersion = 3, shuffleVersion = 2, solverVersion = 1)
    private val validBytes = encodeCatalog(metadata, seeds)

    @Test
    fun loadsAValidCatalog() {
        val result = loadCatalog(validBytes)

        check(result is CatalogLoadResult.Valid) { "expected Valid, got $result" }
        assertEquals(seeds, result.seeds)
        assertEquals(9, result.header.catalogVersion)
        assertEquals(3, result.header.recordCount)
        assertEquals(CatalogFormat.DRAW_MODE_DRAW_ONE, result.header.modeByte)
        assertEquals(CatalogFormat.PARTITION_NONE, result.header.partitionId)
    }

    @Test
    fun carriesThePartitionIdThroughUnchanged() {
        val partitioned = encodeCatalog(metadata.copy(partitionId = 4), seeds)

        val result = loadCatalog(partitioned)
        check(result is CatalogLoadResult.Valid) { "expected Valid, got $result" }
        assertEquals(4.toByte(), result.header.partitionId)
    }

    @Test
    fun acceptsAPartitionIdThisLayerHasNeverHeardOf() {
        // The id's meaning belongs to the game, so an unknown value is not a format error —
        // unlike the draw mode above, which this layer does define.
        val result = loadCatalog(rawCatalogBytes(partitionId = 99, recordCount = 1, payload = seedPayload(1L)))

        assertTrue("expected Valid, got $result", result is CatalogLoadResult.Valid)
    }

    @Test
    fun rejectsSeedsStoredOutOfOrder() {
        val result = loadCatalog(rawCatalogBytes(recordCount = 2, payload = seedPayload(9L, 4L)))

        check(result is CatalogLoadResult.Invalid) { "expected Invalid, got $result" }
        assertTrue(result.reason.contains("out of order", ignoreCase = true))
    }

    @Test
    fun rejectsATruncatedHeader() {
        assertInvalid(loadCatalog(validBytes.copyOf(10)))
    }

    @Test
    fun rejectsATruncatedPayload() {
        assertInvalid(loadCatalog(validBytes.copyOf(validBytes.size - 4)))
    }

    @Test
    fun rejectsTrailingBytesAfterThePayload() {
        assertInvalid(loadCatalog(validBytes + byteArrayOf(0)))
    }

    @Test
    fun rejectsABadMagicValue() {
        val corrupted = validBytes.copyOf()
        corrupted[0] = 'X'.code.toByte()
        assertInvalid(loadCatalog(corrupted))
    }

    @Test
    fun rejectsAnUnknownFormatVersion() {
        val corrupted = validBytes.copyOf()
        ByteBuffer.wrap(corrupted).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(CatalogFormat.OFFSET_FORMAT_VERSION, 2)
        assertInvalid(loadCatalog(corrupted))
    }

    @Test
    fun rejectsAHeaderSizeOtherThan64() {
        val corrupted = validBytes.copyOf()
        ByteBuffer.wrap(corrupted).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(CatalogFormat.OFFSET_HEADER_SIZE, 32)
        assertInvalid(loadCatalog(corrupted))
    }

    @Test
    fun rejectsAnUnknownDrawMode() {
        val corrupted = validBytes.copyOf()
        corrupted[CatalogFormat.OFFSET_DRAW_MODE] = 2
        assertInvalid(loadCatalog(corrupted))
    }

    @Test
    fun rejectsNonzeroReservedBytes() {
        val corrupted = validBytes.copyOf()
        corrupted[CatalogFormat.OFFSET_RESERVED] = 1
        assertInvalid(loadCatalog(corrupted))
    }

    @Test
    fun rejectsAnEmptyCatalog() {
        val bytes = rawCatalogBytes(recordCount = 0, payload = ByteArray(0))
        val result = loadCatalog(bytes)
        check(result is CatalogLoadResult.Invalid) { "expected Invalid, got $result" }
        assertTrue(result.reason.contains("empty", ignoreCase = true))
    }

    @Test
    fun rejectsARecordCountThatWouldOverflow32BitSizeArithmetic() {
        // 0x2000_0000 * 8 == 2^32, which wraps to 0 under naive 32-bit signed
        // multiplication. A loader that computed sizes in Int would then think this
        // header-only, zero-payload file exactly matches its declared record count.
        // Long arithmetic must catch the real (multi-gigabyte) mismatch instead.
        val bytes = rawCatalogBytes(recordCount = 0x20000000, payload = ByteArray(0))
        assertInvalid(loadCatalog(bytes))
    }

    @Test
    fun rejectsARecordCountThatDoesNotMatchThePayloadLength() {
        val payload = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(1L).putLong(2L).putLong(3L).array()
        val bytes = rawCatalogBytes(recordCount = 2, payload = payload)
        assertInvalid(loadCatalog(bytes))
    }

    @Test
    fun rejectsDuplicateSeeds() {
        val payload = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(7L).putLong(7L).array()
        val bytes = rawCatalogBytes(recordCount = 2, payload = payload)
        val result = loadCatalog(bytes)
        check(result is CatalogLoadResult.Invalid) { "expected Invalid, got $result" }
        assertTrue(result.reason.contains("duplicate", ignoreCase = true))
    }

    @Test
    fun rejectsABadPayloadHash() {
        val corrupted = validBytes.copyOf()
        corrupted[CatalogFormat.OFFSET_PAYLOAD_HASH] = (corrupted[CatalogFormat.OFFSET_PAYLOAD_HASH] + 1).toByte()
        assertInvalid(loadCatalog(corrupted))
    }

    @Test
    fun `a catalog written under a custom magic is rejected under the default magic and accepted under its own`() {
        val magic = CatalogFormat.magicBytes("TEST")
        val bytes = encodeCatalog(metadata, seeds, magic)

        assertInvalid(loadCatalog(bytes))
        val result = loadCatalog(bytes, magic)
        check(result is CatalogLoadResult.Valid) { "expected Valid, got $result" }
        assertEquals(seeds, result.seeds)
    }

    @Test
    fun `a catalog written with no mode byte round-trips only when read with hasModeByte false`() {
        val bytes = encodeCatalog(metadata.copy(modeByte = null, partitionId = 3), seeds)

        // Read as the default dialect, byte 24 is this catalog's partition id (3), which the
        // "has a mode byte" reading rejects outright as an unknown draw mode.
        assertInvalid(loadCatalog(bytes, hasModeByte = true))

        val result = loadCatalog(bytes, hasModeByte = false)
        check(result is CatalogLoadResult.Valid) { "expected Valid, got $result" }
        assertEquals(seeds, result.seeds)
        assertEquals(null, result.header.modeByte)
        assertEquals(3.toByte(), result.header.partitionId)
    }

    @Test
    fun `nonzero reserved bytes are rejected under the no-mode-byte dialect too`() {
        val bytes = encodeCatalog(metadata.copy(modeByte = null), seeds).copyOf()
        bytes[CatalogFormat.OFFSET_RESERVED_NO_MODE_BYTE] = 1
        assertInvalid(loadCatalog(bytes, hasModeByte = false))
    }

    private fun seedPayload(vararg values: Long): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * CatalogFormat.SEED_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        for (value in values) buffer.putLong(value)
        return buffer.array()
    }

    private fun assertInvalid(result: CatalogLoadResult) {
        assertTrue("expected Invalid, got $result", result is CatalogLoadResult.Invalid)
    }

    /** Builds header+payload bytes directly, bypassing [encodeCatalog]'s own validation. */
    private fun rawCatalogBytes(
        formatVersion: Short = CatalogFormat.FORMAT_VERSION_1,
        headerSize: Short = CatalogFormat.HEADER_SIZE_V1.toShort(),
        catalogVersion: Int = 1,
        rulesVersion: Int = 1,
        shuffleVersion: Int = 1,
        solverVersion: Int = 1,
        drawMode: Byte = CatalogFormat.DRAW_MODE_DRAW_ONE,
        partitionId: Byte = CatalogFormat.PARTITION_NONE,
        reserved: ByteArray = ByteArray(CatalogFormat.RESERVED_BYTE_COUNT),
        recordCount: Int,
        payload: ByteArray,
        hash: ByteArray? = null,
    ): ByteArray {
        val actualHash = hash ?: MessageDigest.getInstance("SHA-256").digest(payload)
        val buffer = ByteBuffer.allocate(CatalogFormat.HEADER_SIZE_V1 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(CatalogFormat.MAGIC_BYTES)
        buffer.putShort(formatVersion)
        buffer.putShort(headerSize)
        buffer.putInt(catalogVersion)
        buffer.putInt(rulesVersion)
        buffer.putInt(shuffleVersion)
        buffer.putInt(solverVersion)
        buffer.put(drawMode)
        buffer.put(partitionId)
        buffer.put(reserved)
        buffer.putInt(recordCount)
        buffer.put(actualHash)
        buffer.put(payload)
        return buffer.array()
    }
}
