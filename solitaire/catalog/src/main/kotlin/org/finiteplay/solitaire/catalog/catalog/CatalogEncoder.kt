package org.finiteplay.solitaire.catalog.catalog

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Versioned metadata declared by a catalog header, excluding the record count and
 * payload hash, which [encodeCatalog] derives from the seed list itself.
 */
data class CatalogMetadata(
    val catalogVersion: Int,
    val rulesVersion: Int,
    val shuffleVersion: Int,
    val solverVersion: Int,
    /**
     * A draw-mode byte, for the game that has that axis. Null for a game with no such concept:
     * [encodeCatalog] then writes [partitionId] into the byte the mode would have used and grows
     * the reserved run by one byte to fill what is left (`CatalogFormat`'s own doc explains both
     * dialects in full).
     */
    val modeByte: Byte? = CatalogFormat.DRAW_MODE_DRAW_ONE,
    /** Which of the game's partitions this catalog holds (`docs/solitaire/CATALOG.md` "Partitions"). */
    val partitionId: Byte = CatalogFormat.PARTITION_NONE,
)

/**
 * Writes [seeds] under [metadata] into the frozen format-version-1 binary layout
 * (`docs/solitaire/CATALOG.md`'s deterministic deal contract) under [magic], computing the
 * SHA-256 payload hash itself. Shared by every game's own catalog build, so this is the single
 * source of truth for producing a catalog binary; [magic] defaults to the first game's own only
 * so every caller that predates this parameter is unaffected, never because it is a sensible
 * default for a new game.
 */
fun encodeCatalog(metadata: CatalogMetadata, seeds: List<ULong>, magic: ByteArray = CatalogFormat.MAGIC_BYTES): ByteArray {
    require(magic.size == 4) { "magic must be exactly 4 bytes, got ${magic.size}" }
    require(seeds.isNotEmpty()) { "catalog must contain at least one seed" }
    require(seeds.size.toLong() <= UInt.MAX_VALUE.toLong()) { "record count must fit in an unsigned 32-bit integer" }
    require(seeds.toSet().size == seeds.size) { "catalog seeds must be unique" }
    // Ascending is the format's rule, not a convenience: the loader rejects anything else, and
    // which deal a player meets first is traversal's job (`docs/solitaire/CATALOG.md`).
    require(seeds.zipWithNext().all { (a, b) -> a < b }) { "catalog seeds must be stored ascending" }

    val payloadSize = seeds.size * CatalogFormat.SEED_RECORD_SIZE
    val payload = ByteBuffer.allocate(payloadSize).order(ByteOrder.LITTLE_ENDIAN)
    for (seed in seeds) payload.putLong(seed.toLong())
    val payloadBytes = payload.array()
    val payloadHash = MessageDigest.getInstance("SHA-256").digest(payloadBytes)

    val buffer = ByteBuffer.allocate(CatalogFormat.HEADER_SIZE_V1 + payloadSize).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put(magic)
    buffer.putShort(CatalogFormat.FORMAT_VERSION_1)
    buffer.putShort(CatalogFormat.HEADER_SIZE_V1.toShort())
    buffer.putInt(metadata.catalogVersion)
    buffer.putInt(metadata.rulesVersion)
    buffer.putInt(metadata.shuffleVersion)
    buffer.putInt(metadata.solverVersion)
    val modeByte = metadata.modeByte
    if (modeByte != null) {
        buffer.put(modeByte)
        buffer.put(metadata.partitionId)
        buffer.put(ByteArray(CatalogFormat.RESERVED_BYTE_COUNT))
    } else {
        buffer.put(metadata.partitionId)
        buffer.put(ByteArray(CatalogFormat.RESERVED_BYTE_COUNT_NO_MODE_BYTE))
    }
    buffer.putInt(seeds.size)
    buffer.put(payloadHash)
    buffer.put(payloadBytes)

    check(buffer.position() == buffer.capacity()) { "encoder wrote an unexpected byte count" }
    return buffer.array()
}
