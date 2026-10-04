package org.finiteplay.freecell.solution

import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.rules.Move
import org.finiteplay.freecell.rules.applyMove
import java.io.ByteArrayOutputStream

/**
 * The shipped solution format: a bit-packed choice list with a seekable index, mirroring
 * Klondike's own `CompactSolutionCodec` (`games/klondike/rules/.../solution/CompactSolutionCodec.kt`)
 * adapted to FreeCell's own move algebra.
 *
 * FreeCell has no draws or recycles to drop the way Klondike's format does — every move here is a
 * real, stored decision (`docs/games/freecell/RULES.md` "What FreeCell does not have") — so this
 * format is simpler: it packs the five [Move] shapes to the bits their operands need and nothing
 * else. `TABLEAU_COLUMNS` is 8 (3 bits), `FREE_CELLS` is 4 (2 bits), and a tableau index is given
 * 6 bits — generous against the 52-card deck bound, mirroring how Klondike sized its own
 * `fromIndex` to its pile's actual max rather than cutting it close.
 *
 * Layout: `FCSL`, version byte 1, entry count (varint), then per entry a seed delta (varint) and
 * block length in bytes (varint), then the blocks in the same order — the same seekable shape as
 * Klondike's format, so one line decodes without touching the rest of a ten-thousand-seed catalog.
 *
 * **Never trusted on write or read**: [encodeCatalog] independently replays every line before
 * encoding it and silently skips (`continue`) any seed that does not actually reach a win: the
 * solver's own verdict is never shipped untrusted (`docs/games/freecell/DEALS.md` "Generation").
 * [decodeLine] fails to null, never an exception, on a truncated or corrupt block.
 */
object CompactSolutionCodec {
    const val MAGIC = "FCSL"
    const val VERSION = 1

    private const val OP_TABLEAU_TO_TABLEAU = 0
    private const val OP_TABLEAU_TO_FREE_CELL = 1
    private const val OP_TABLEAU_TO_FOUNDATION = 2
    private const val OP_FREE_CELL_TO_TABLEAU = 3
    private const val OP_FREE_CELL_TO_FOUNDATION = 4

    private const val COLUMN_BITS = 3
    private const val CELL_BITS = 2
    private const val INDEX_BITS = 6
    private const val COUNT_BITS = 16

    /**
     * Replays [line] from [start] through the real reducer, returning it unchanged if it reaches a
     * win and null otherwise — a line is never encoded on trust.
     */
    internal fun choicesOf(start: FreeCellState, line: List<Move>): List<Move>? {
        var state = start
        for (move in line) {
            state = runCatching { applyMove(state, move) }.getOrElse { return null }
        }
        return if (state.isWon) line else null
    }

    fun encodeCatalog(solutions: Map<Long, List<Move>>, dealOf: (Long) -> FreeCellState): ByteArray {
        val blocks = LinkedHashMap<Long, ByteArray>()
        for ((seed, line) in solutions.toSortedMap()) {
            val choices = choicesOf(dealOf(seed), line) ?: continue
            blocks[seed] = packBlock(choices)
        }

        val out = ByteArrayOutputStream()
        out.write(MAGIC.toByteArray(Charsets.US_ASCII))
        out.write(VERSION)
        writeVarint(out, blocks.size)
        var previous = 0L
        for ((seed, block) in blocks) {
            writeVarint(out, (seed - previous).toInt().also { require(it >= 0) { "seeds must ascend" } })
            writeVarint(out, block.size)
            previous = seed
        }
        for (block in blocks.values) out.write(block)
        return out.toByteArray()
    }

    /** The seeds a blob holds, with where each one's block starts and how long it is. */
    class Index internal constructor(
        internal val seeds: LongArray,
        internal val offsets: IntArray,
        internal val lengths: IntArray,
    ) {
        val size: Int get() = seeds.size
        operator fun contains(seed: Long): Boolean = seeds.binarySearch(seed) >= 0
    }

    fun readIndex(bytes: ByteArray): Index {
        val magic = String(bytes, 0, MAGIC.length, Charsets.US_ASCII)
        require(magic == MAGIC) { "solution catalog magic was '$magic', expected '$MAGIC'" }
        var offset = MAGIC.length
        val version = bytes[offset++].toInt() and 0xFF
        require(version == VERSION) { "solution catalog is version $version, expected $VERSION" }

        var cursor = Cursor(offset)
        val count = readVarint(bytes, cursor)
        val seeds = LongArray(count)
        val lengths = IntArray(count)
        var previous = 0L
        for (i in 0 until count) {
            previous += readVarint(bytes, cursor)
            seeds[i] = previous
            lengths[i] = readVarint(bytes, cursor)
        }
        val offsets = IntArray(count)
        var payload = cursor.offset
        for (i in 0 until count) {
            offsets[i] = payload
            payload += lengths[i]
        }
        require(payload <= bytes.size) { "truncated solution catalog: payload runs past the blob" }
        return Index(seeds, offsets, lengths)
    }

    /**
     * Decodes one seed's line, or null when the blob does not hold it or its block is corrupt —
     * a decode failure is never allowed to throw into the caller.
     */
    fun decodeLine(bytes: ByteArray, index: Index, seed: Long): List<Move>? {
        val at = index.seeds.binarySearch(seed)
        if (at < 0) return null
        return runCatching { unpackBlock(bytes, index.offsets[at], index.lengths[at]) }.getOrNull()
    }

    // --- bit packing -------------------------------------------------------------------

    private fun packBlock(moves: List<Move>): ByteArray {
        val bits = BitWriter()
        bits.write(moves.size, COUNT_BITS)
        for (move in moves) {
            when (move) {
                is Move.TableauToTableau -> {
                    bits.write(OP_TABLEAU_TO_TABLEAU, 3)
                    bits.write(move.fromColumn, COLUMN_BITS)
                    bits.write(move.toColumn, COLUMN_BITS)
                    bits.write(move.fromIndex, INDEX_BITS)
                }
                is Move.TableauToFreeCell -> {
                    bits.write(OP_TABLEAU_TO_FREE_CELL, 3)
                    bits.write(move.fromColumn, COLUMN_BITS)
                    bits.write(move.cell, CELL_BITS)
                }
                is Move.TableauToFoundation -> {
                    bits.write(OP_TABLEAU_TO_FOUNDATION, 3)
                    bits.write(move.fromColumn, COLUMN_BITS)
                }
                is Move.FreeCellToTableau -> {
                    bits.write(OP_FREE_CELL_TO_TABLEAU, 3)
                    bits.write(move.cell, CELL_BITS)
                    bits.write(move.toColumn, COLUMN_BITS)
                }
                is Move.FreeCellToFoundation -> {
                    bits.write(OP_FREE_CELL_TO_FOUNDATION, 3)
                    bits.write(move.cell, CELL_BITS)
                }
            }
        }
        return bits.toByteArray()
    }

    private fun unpackBlock(bytes: ByteArray, offset: Int, length: Int): List<Move> {
        val bits = BitReader(bytes, offset, length)
        val count = bits.read(COUNT_BITS)
        val moves = ArrayList<Move>(count)
        repeat(count) {
            when (bits.read(3)) {
                OP_TABLEAU_TO_TABLEAU -> {
                    val from = bits.read(COLUMN_BITS)
                    val to = bits.read(COLUMN_BITS)
                    val index = bits.read(INDEX_BITS)
                    moves += Move.TableauToTableau(fromColumn = from, fromIndex = index, toColumn = to)
                }
                OP_TABLEAU_TO_FREE_CELL -> {
                    val from = bits.read(COLUMN_BITS)
                    val cell = bits.read(CELL_BITS)
                    moves += Move.TableauToFreeCell(fromColumn = from, cell = cell)
                }
                OP_TABLEAU_TO_FOUNDATION -> moves += Move.TableauToFoundation(bits.read(COLUMN_BITS))
                OP_FREE_CELL_TO_TABLEAU -> {
                    val cell = bits.read(CELL_BITS)
                    val to = bits.read(COLUMN_BITS)
                    moves += Move.FreeCellToTableau(cell = cell, toColumn = to)
                }
                OP_FREE_CELL_TO_FOUNDATION -> moves += Move.FreeCellToFoundation(bits.read(CELL_BITS))
                else -> throw IllegalArgumentException("unknown opcode in a solution block")
            }
        }
        return moves
    }

    private class BitWriter {
        private val out = ByteArrayOutputStream()
        private var current = 0
        private var filled = 0

        fun write(value: Int, width: Int) {
            require(value in 0 until (1 shl width)) { "$value does not fit $width bits" }
            for (bit in width - 1 downTo 0) {
                current = (current shl 1) or ((value shr bit) and 1)
                if (++filled == 8) {
                    out.write(current)
                    current = 0
                    filled = 0
                }
            }
        }

        fun toByteArray(): ByteArray {
            if (filled > 0) out.write(current shl (8 - filled))
            return out.toByteArray()
        }
    }

    private class BitReader(private val bytes: ByteArray, offset: Int, length: Int) {
        private val end = offset + length
        private var at = offset
        private var current = 0
        private var remaining = 0

        fun read(width: Int): Int {
            var value = 0
            repeat(width) {
                if (remaining == 0) {
                    require(at < end) { "truncated solution block" }
                    current = bytes[at++].toInt() and 0xFF
                    remaining = 8
                }
                value = (value shl 1) or ((current shr (remaining - 1)) and 1)
                remaining--
            }
            return value
        }
    }

    private class Cursor(var offset: Int)

    private fun writeVarint(out: ByteArrayOutputStream, value: Int) {
        require(value >= 0) { "varints are unsigned" }
        var rest = value
        while (rest >= 0x80) {
            out.write((rest and 0x7F) or 0x80)
            rest = rest ushr 7
        }
        out.write(rest)
    }

    private fun readVarint(bytes: ByteArray, cursor: Cursor): Int {
        var value = 0
        var shift = 0
        while (true) {
            require(cursor.offset < bytes.size) { "truncated varint in a solution catalog" }
            val byte = bytes[cursor.offset++].toInt() and 0xFF
            value = value or ((byte and 0x7F) shl shift)
            if (byte < 0x80) return value
            shift += 7
            require(shift <= 28) { "varint too long in a solution catalog" }
        }
    }
}
