package org.finiteplay.solitaire.catalog.catalog

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Result of parsing a catalog binary against [CatalogFormat]. Loading a malformed asset
 * never throws: every rejection reason is reported as [Invalid] so the app can fall back
 * to the non-playable Unrecoverable state (`docs/solitaire/CATALOG.md`) instead of
 * crashing or synthesizing an unverified deal.
 */
sealed class CatalogLoadResult {
    data class Valid(val header: CatalogHeader, val seeds: List<ULong>) : CatalogLoadResult()
    data class Invalid(val reason: String) : CatalogLoadResult()
}

/**
 * Parses and strictly validates a catalog binary against the frozen format-version-1 layout under
 * [magic]. Every field is bounds-checked before use and all size arithmetic is done in `Long` so a
 * malicious or corrupt record count cannot wrap around 32-bit arithmetic and slip past the length
 * check.
 *
 * [hasModeByte] selects which of [CatalogFormat]'s two header dialects bytes 24–27 are read as —
 * `true` for a game with a draw-mode axis, `false` for one with no such axis at all. Both [magic]
 * and [hasModeByte] default to the first game's own values only so every caller that predates
 * these parameters is unaffected.
 */
fun loadCatalog(
    bytes: ByteArray,
    magic: ByteArray = CatalogFormat.MAGIC_BYTES,
    hasModeByte: Boolean = true,
): CatalogLoadResult {
    require(magic.size == 4) { "magic must be exactly 4 bytes, got ${magic.size}" }
    if (bytes.size < CatalogFormat.HEADER_SIZE_V1) {
        return CatalogLoadResult.Invalid(
            "truncated header: expected at least ${CatalogFormat.HEADER_SIZE_V1} bytes, got ${bytes.size}",
        )
    }

    for (i in magic.indices) {
        if (bytes[CatalogFormat.OFFSET_MAGIC + i] != magic[i]) {
            return CatalogLoadResult.Invalid("bad magic value")
        }
    }

    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    val formatVersion = buffer.getShort(CatalogFormat.OFFSET_FORMAT_VERSION)
    if (formatVersion != CatalogFormat.FORMAT_VERSION_1) {
        return CatalogLoadResult.Invalid("unknown format version: $formatVersion")
    }

    val headerSize = buffer.getShort(CatalogFormat.OFFSET_HEADER_SIZE).toUShort()
    if (headerSize.toInt() != CatalogFormat.HEADER_SIZE_V1) {
        return CatalogLoadResult.Invalid("unexpected header size: $headerSize")
    }

    val catalogVersion = buffer.getInt(CatalogFormat.OFFSET_CATALOG_VERSION)
    val rulesVersion = buffer.getInt(CatalogFormat.OFFSET_RULES_VERSION)
    val shuffleVersion = buffer.getInt(CatalogFormat.OFFSET_SHUFFLE_VERSION)
    val solverVersion = buffer.getInt(CatalogFormat.OFFSET_SOLVER_VERSION)

    val modeByte: Byte?
    val partitionId: Byte
    val reservedOffset: Int
    val reservedCount: Int
    if (hasModeByte) {
        val mode = bytes[CatalogFormat.OFFSET_DRAW_MODE]
        if (mode != CatalogFormat.DRAW_MODE_DRAW_ONE) {
            return CatalogLoadResult.Invalid("unknown draw mode: $mode")
        }
        modeByte = mode
        // Any value is structurally legal: what a nonzero id means is the game's, and this layer
        // must not reject a partition it has never heard of (`docs/solitaire/CATALOG.md`).
        partitionId = bytes[CatalogFormat.OFFSET_PARTITION_ID]
        reservedOffset = CatalogFormat.OFFSET_RESERVED
        reservedCount = CatalogFormat.RESERVED_BYTE_COUNT
    } else {
        modeByte = null
        partitionId = bytes[CatalogFormat.OFFSET_PARTITION_ID_NO_MODE_BYTE]
        reservedOffset = CatalogFormat.OFFSET_RESERVED_NO_MODE_BYTE
        reservedCount = CatalogFormat.RESERVED_BYTE_COUNT_NO_MODE_BYTE
    }

    for (i in 0 until reservedCount) {
        if (bytes[reservedOffset + i] != 0.toByte()) {
            return CatalogLoadResult.Invalid("nonzero reserved byte at offset ${reservedOffset + i}")
        }
    }

    // Read the record count as unsigned and keep every downstream size calculation in
    // Long: a hostile/corrupt count near UInt.MAX_VALUE would wrap silently in 32-bit
    // arithmetic and could slip past the length check below.
    val recordCount = buffer.getInt(CatalogFormat.OFFSET_RECORD_COUNT).toUInt()
    if (recordCount == 0u) {
        return CatalogLoadResult.Invalid("empty catalog: record count is zero")
    }

    val payloadSize: Long = recordCount.toLong() * CatalogFormat.SEED_RECORD_SIZE
    val expectedTotalSize: Long = CatalogFormat.HEADER_SIZE_V1.toLong() + payloadSize
    if (expectedTotalSize != bytes.size.toLong()) {
        return CatalogLoadResult.Invalid(
            "record count mismatch: header declares $recordCount records " +
                "($expectedTotalSize bytes expected) but payload is ${bytes.size} bytes",
        )
    }

    // expectedTotalSize == bytes.size, and bytes.size is a valid (non-negative) Int, so
    // recordCount is now known to fit comfortably within Int range.
    val recordCountInt = recordCount.toInt()

    val declaredHash = bytes.copyOfRange(
        CatalogFormat.OFFSET_PAYLOAD_HASH,
        CatalogFormat.OFFSET_PAYLOAD_HASH + CatalogFormat.PAYLOAD_HASH_SIZE,
    )
    val payloadBytes = bytes.copyOfRange(CatalogFormat.HEADER_SIZE_V1, bytes.size)
    val actualHash = MessageDigest.getInstance("SHA-256").digest(payloadBytes)
    if (!declaredHash.contentEquals(actualHash)) {
        return CatalogLoadResult.Invalid("payload hash mismatch")
    }

    // Ascending order subsumes the duplicate check — an equal neighbour is not ascending — but
    // both messages are kept, because "duplicate seed" names the defect a generator actually has
    // and "out of order" names a different one.
    val seeds = ArrayList<ULong>(recordCountInt)
    for (i in 0 until recordCountInt) {
        val seed = buffer.getLong(CatalogFormat.HEADER_SIZE_V1 + i * CatalogFormat.SEED_RECORD_SIZE).toULong()
        val previous = seeds.lastOrNull()
        if (previous != null) {
            if (seed == previous) return CatalogLoadResult.Invalid("duplicate seed: $seed")
            if (seed < previous) return CatalogLoadResult.Invalid("seeds out of order at record $i: $seed after $previous")
        }
        seeds.add(seed)
    }

    val header = CatalogHeader(
        formatVersion = formatVersion,
        headerSize = headerSize.toShort(),
        catalogVersion = catalogVersion,
        rulesVersion = rulesVersion,
        shuffleVersion = shuffleVersion,
        solverVersion = solverVersion,
        modeByte = modeByte,
        partitionId = partitionId,
        recordCount = recordCountInt,
        payloadHash = declaredHash,
    )
    return CatalogLoadResult.Valid(header, seeds)
}
