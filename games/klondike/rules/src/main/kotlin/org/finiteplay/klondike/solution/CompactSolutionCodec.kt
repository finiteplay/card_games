package org.finiteplay.klondike.solution

import org.finiteplay.cards.Suit
import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.applyMove
import java.io.ByteArrayOutputStream

/**
 * The shipped solution format: a **choice list**, bit-packed, with a seekable index.
 *
 * [SolutionCodec] writes two bytes for every move including the draws, and decodes a whole
 * catalog into memory at once. That was affordable for five thousand solutions and is not for
 * sixty thousand — the same catalog costs about 19 MB there and roughly 9 million `Move`
 * objects to read a single line. Two changes carry almost all of the difference:
 *
 * - **Draws and recycles are not stored.** They carry no decision: reaching a pile card means
 *   drawing until it is on the waste, recycling when the stock runs out, and which of the two
 *   applies is a fact about the board rather than about the line. A waste choice therefore
 *   stores *how far to advance the pile* and the expansion replays the draws against the real
 *   board. Roughly a third of every line disappears.
 * - **Operands are packed to the bits they need**, not to a two-byte cell: a
 *   tableau-to-foundation choice is six bits, the widest is fourteen.
 *
 * The index is written ahead of the payload so one line can be decoded without touching the
 * rest: seeds ascend as deltas, each with its block length, and a reader binary-searches the
 * seeds and seeks straight to the block.
 *
 * Layout: `KSOL`, version byte 2, entry count (varint), then per entry a seed delta (varint)
 * and block length in bytes (varint), then the blocks in the same order.
 *
 * **What a round trip preserves** is the choices, in order, and the win — not the original
 * line byte for byte. Draws come back at the last moment that still reaches the card, so a
 * line that drew early and played late decodes with those draws moved down to the play. The
 * board sequence is the same at every choice, which is all a replayed hint depends on.
 */
object CompactSolutionCodec {
    const val VERSION = 2

    private const val OP_TABLEAU_TO_TABLEAU = 0
    private const val OP_TABLEAU_TO_FOUNDATION = 1
    private const val OP_WASTE_TO_TABLEAU = 2
    private const val OP_WASTE_TO_FOUNDATION = 3
    private const val OP_FOUNDATION_TO_TABLEAU = 4

    /**
     * Widest pile advance stored inline. The pile holds 24 cards, so one full cycle always
     * fits; the escape exists for a found line that cycles further without playing, which a
     * search certificate occasionally does.
     */
    private const val ADVANCE_INLINE_MAX = 30
    private const val ADVANCE_ESCAPE = 31

    /** A move that changes the board. Draws and recycles are the ones this format drops. */
    private fun isChoice(move: Move) = move != Move.Draw && move != Move.Recycle

    /**
     * Turns a literal line into the choices this format stores, counting the pile advance each
     * waste play needs. Returns null when the line does not replay to a win from [start], so a
     * line is never encoded on trust.
     */
    internal fun choicesOf(start: GameState, line: List<Move>): List<PackedChoice>? {
        var state = start
        var advance = 0
        val choices = ArrayList<PackedChoice>(line.size)
        for (move in line) {
            state = runCatching { applyMove(state, move) }.getOrElse { return null }
            if (!isChoice(move)) {
                advance++
                continue
            }
            // Pile advances carry across the choices that do not use the pile. Drawing only
            // ever moves stock and waste, so it commutes with every tableau and foundation
            // move: what matters is how far the pile has advanced by the time a waste card is
            // played, not where the draws sat in the original line. Attaching them to the next
            // choice regardless would drop them, since only a waste choice stores one.
            val usesPile = move == Move.WasteToFoundation || move is Move.WasteToTableau
            choices += PackedChoice(move, if (usesPile) advance else 0)
            if (usesPile) advance = 0
        }
        return if (state.isWon) choices else null
    }

    internal data class PackedChoice(val move: Move, val advance: Int)

    /**
     * Expands a stored choice list back into the literal move list a replay needs, deriving
     * each draw or recycle from the board in front of it.
     *
     * Returns null if any step is not legal from [start] — a corrupt or stale block reports
     * itself rather than handing back a line that stops mid-game.
     */
    internal fun expand(start: GameState, choices: List<PackedChoice>): List<Move>? {
        var state = start
        val line = ArrayList<Move>(choices.size * 2)
        for ((move, advance) in choices) {
            repeat(advance) {
                val step = if (state.stock.isEmpty()) Move.Recycle else Move.Draw
                state = runCatching { applyMove(state, step) }.getOrElse { return null }
                line += step
            }
            state = runCatching { applyMove(state, move) }.getOrElse { return null }
            line += move
        }
        return if (state.isWon) line else null
    }

    fun encodeCatalog(solutions: Map<Long, List<Move>>, dealOf: (Long) -> GameState): ByteArray {
        val blocks = LinkedHashMap<Long, ByteArray>()
        for ((seed, line) in solutions.toSortedMap()) {
            val choices = choicesOf(dealOf(seed), line) ?: continue
            blocks[seed] = packBlock(choices)
        }

        val out = ByteArrayOutputStream()
        out.write(SolutionCodec.MAGIC.toByteArray(Charsets.US_ASCII))
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
        val magic = String(bytes, 0, SolutionCodec.MAGIC.length, Charsets.US_ASCII)
        require(magic == SolutionCodec.MAGIC) { "solution catalog magic was '$magic', expected '${SolutionCodec.MAGIC}'" }
        var offset = SolutionCodec.MAGIC.length
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

    /** Decodes one seed's line, or null when the blob does not hold it. */
    fun decodeLine(bytes: ByteArray, index: Index, deal: GameState, seed: Long): List<Move>? {
        val at = index.seeds.binarySearch(seed)
        if (at < 0) return null
        val choices = unpackBlock(bytes, index.offsets[at], index.lengths[at])
        return expand(deal, choices)
    }

    // --- bit packing -------------------------------------------------------------------

    private fun packBlock(choices: List<PackedChoice>): ByteArray {
        val bits = BitWriter()
        bits.write(choices.size, 16)
        for ((move, advance) in choices) {
            when (move) {
                is Move.TableauToTableau -> {
                    bits.write(OP_TABLEAU_TO_TABLEAU, 3)
                    bits.write(move.fromColumn, 3)
                    bits.write(move.toColumn, 3)
                    bits.write(move.fromIndex, 5)
                }
                is Move.TableauToFoundation -> {
                    bits.write(OP_TABLEAU_TO_FOUNDATION, 3)
                    bits.write(move.fromColumn, 3)
                }
                is Move.WasteToTableau -> {
                    bits.write(OP_WASTE_TO_TABLEAU, 3)
                    writeAdvance(bits, advance)
                    bits.write(move.toColumn, 3)
                }
                Move.WasteToFoundation -> {
                    bits.write(OP_WASTE_TO_FOUNDATION, 3)
                    writeAdvance(bits, advance)
                }
                is Move.FoundationToTableau -> {
                    bits.write(OP_FOUNDATION_TO_TABLEAU, 3)
                    bits.write(move.suit.ordinal, 2)
                    bits.write(move.toColumn, 3)
                }
                Move.Draw, Move.Recycle -> error("draws are not stored")
            }
        }
        return bits.toByteArray()
    }

    private fun unpackBlock(bytes: ByteArray, offset: Int, length: Int): List<PackedChoice> {
        val bits = BitReader(bytes, offset, length)
        val count = bits.read(16)
        val choices = ArrayList<PackedChoice>(count)
        repeat(count) {
            when (bits.read(3)) {
                OP_TABLEAU_TO_TABLEAU -> {
                    // Read into locals: the fields are written from, to, index but the
                    // constructor takes from, index, to, and positional arguments silently
                    // swapped two of them.
                    val from = bits.read(3)
                    val to = bits.read(3)
                    val index = bits.read(5)
                    choices += PackedChoice(
                        Move.TableauToTableau(fromColumn = from, toColumn = to, fromIndex = index),
                        0,
                    )
                }
                OP_TABLEAU_TO_FOUNDATION -> choices += PackedChoice(Move.TableauToFoundation(bits.read(3)), 0)
                OP_WASTE_TO_TABLEAU -> {
                    val advance = readAdvance(bits)
                    choices += PackedChoice(Move.WasteToTableau(toColumn = bits.read(3)), advance)
                }
                OP_WASTE_TO_FOUNDATION -> choices += PackedChoice(Move.WasteToFoundation, readAdvance(bits))
                OP_FOUNDATION_TO_TABLEAU -> {
                    val suit = Suit.entries[bits.read(2)]
                    choices += PackedChoice(Move.FoundationToTableau(suit = suit, toColumn = bits.read(3)), 0)
                }
                else -> throw IllegalArgumentException("unknown opcode in a solution block")
            }
        }
        return choices
    }

    private fun writeAdvance(bits: BitWriter, advance: Int) {
        if (advance <= ADVANCE_INLINE_MAX) {
            bits.write(advance, 5)
        } else {
            bits.write(ADVANCE_ESCAPE, 5)
            bits.write(advance, 12)
        }
    }

    private fun readAdvance(bits: BitReader): Int {
        val head = bits.read(5)
        return if (head == ADVANCE_ESCAPE) bits.read(12) else head
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
