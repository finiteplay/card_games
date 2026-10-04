package org.finiteplay.solitaire.catalog.catalog

/**
 * Versioned deal-catalog binary format, frozen by the deal contract in
 * `docs/solitaire/CATALOG.md`. Byte order is little-endian throughout. D1a implements the
 * strict loader against this schema; this file only fixes the layout.
 *
 * The header shape at bytes 24–27 has two dialects, chosen per game by whether it has a
 * draw-mode axis at all — some solitaires draw from a stock in more than one configurable way,
 * and some have no such concept whatsoever: a game with one carries it at [OFFSET_DRAW_MODE],
 * with the partition id and a 2-byte reserved run after it; a game without one has no byte there
 * to validate at all, so its partition id moves into that byte's place
 * ([OFFSET_PARTITION_ID_NO_MODE_BYTE]) and the reserved run grows to 3 bytes
 * ([RESERVED_BYTE_COUNT_NO_MODE_BYTE]) to fill what the mode byte would otherwise have used.
 * Both dialects total the same 4 bytes at 24–27 and the same 64-byte header overall. [MAGIC] is
 * the first game's own and is the default only for backward compatibility — every game states
 * its own magic and dialect in its own deal-catalog spec and passes them to
 * [org.finiteplay.solitaire.catalog.catalog.encodeCatalog]/`loadCatalog` explicitly.
 */
object CatalogFormat {
    const val MAGIC = "KLDK"
    val MAGIC_BYTES: ByteArray = magicBytes(MAGIC)

    const val FORMAT_VERSION_1: Short = 1
    const val HEADER_SIZE_V1 = 64

    const val OFFSET_MAGIC = 0
    const val OFFSET_FORMAT_VERSION = 4
    const val OFFSET_HEADER_SIZE = 6
    const val OFFSET_CATALOG_VERSION = 8
    const val OFFSET_RULES_VERSION = 12
    const val OFFSET_SHUFFLE_VERSION = 16
    const val OFFSET_SOLVER_VERSION = 20

    /** A draw-mode byte, for the game that has one; present only in the "has a mode byte" dialect. */
    const val OFFSET_DRAW_MODE = 24
    const val OFFSET_PARTITION_ID = 25
    const val OFFSET_RESERVED = 26
    const val RESERVED_BYTE_COUNT = 2

    /** The "no mode byte" dialect's own offsets for the same 24–27 range. */
    const val OFFSET_PARTITION_ID_NO_MODE_BYTE = 24
    const val OFFSET_RESERVED_NO_MODE_BYTE = 25
    const val RESERVED_BYTE_COUNT_NO_MODE_BYTE = 3

    const val OFFSET_RECORD_COUNT = 28
    const val OFFSET_PAYLOAD_HASH = 32
    const val PAYLOAD_HASH_SIZE = 32

    const val DRAW_MODE_DRAW_ONE: Byte = 1

    /**
     * A game that ships one undivided catalog (`docs/solitaire/CATALOG.md` "Partitions").
     * Any other value names one of that game's partitions and means nothing here.
     */
    const val PARTITION_NONE: Byte = 0

    /** Each catalog record is one unsigned 64-bit seed. */
    const val SEED_RECORD_SIZE = 8

    /** [magic] as the exactly-4-byte ASCII array the header stores at [OFFSET_MAGIC]. */
    fun magicBytes(magic: String): ByteArray {
        require(magic.length == 4) { "a catalog magic must be exactly 4 ASCII characters, got \"$magic\"" }
        return magic.toByteArray(Charsets.US_ASCII)
    }
}

/**
 * Parsed format-version-1 header. Field validation belongs to the D1a loader. [modeByte] is null
 * when the catalog was written under the "no mode byte" dialect (see [CatalogFormat]'s own doc).
 */
data class CatalogHeader(
    val formatVersion: Short,
    val headerSize: Short,
    val catalogVersion: Int,
    val rulesVersion: Int,
    val shuffleVersion: Int,
    val solverVersion: Int,
    val modeByte: Byte?,
    val partitionId: Byte,
    val recordCount: Int,
    val payloadHash: ByteArray,
)
