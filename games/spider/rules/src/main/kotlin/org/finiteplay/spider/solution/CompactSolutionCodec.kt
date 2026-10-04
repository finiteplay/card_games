package org.finiteplay.spider.solution

import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.applyMove
import org.finiteplay.spider.rules.sequenceStart
import java.io.ByteArrayOutputStream

/**
 * The shipped solution format: a bit-packed choice list with a seekable index, mirroring
 * Klondike's own `CompactSolutionCodec` (`games/klondike/rules/.../solution/CompactSolutionCodec.kt`)
 * and FreeCell's own adaptation of it (`games/freecell/rules/.../solution/CompactSolutionCodec.kt`),
 * both narrowed to Spider's own move algebra.
 *
 * Spider has only two [Move] shapes and, unlike Klondike, nothing derivable the way draws and
 * recycles are — every [Move.DealRow] is a real decision and is stored. What this format compacts
 * instead is [Move.TableauToTableau]'s own operands:
 *
 * - **`fromColumn`/`toColumn` are packed as one field**, not two: they are never equal (a move
 *   never targets its own source), so the pair fits `TABLEAU_COLUMNS * (TABLEAU_COLUMNS - 1)` = 90
 *   values in 7 bits rather than 8 for two independent 4-bit columns.
 * - **`fromIndex` is stored as a delta from [sequenceStart], not an absolute pile position.** A
 *   move that lifts the whole movable run — overwhelmingly the common case in a real winning
 *   line, since a shorter lift is only ever chosen to split a run for a specific tactical reason —
 *   has `fromIndex == sequenceStart(source)`, a delta of zero costing one bit. A shorter lift costs
 *   more, escalating exactly like Klondike's own pile-advance escape.
 *
 * Computing [sequenceStart] needs the board the move is played from, so — like Klondike's own
 * waste-pile advance — the delta is derived by replaying the line once at encode time
 * ([choicesOf]) and rebuilt by replaying the choices once at decode time ([expand]); the packed
 * bytes themselves ([packBlock]/[unpackBlock]) hold no board state at all.
 *
 * Layout: `MAGIC`, version byte, entry count (varint), then per entry a seed delta (varint) and
 * block length in bytes (varint), then the blocks in the same order — the same seekable shape as
 * Klondike's and FreeCell's own formats, so one line decodes without touching the rest of a
 * ten-thousand-seed catalog.
 *
 * **Never trusted on write or read**: [choicesOf] independently replays every line through the
 * real reducer before it is packed, and [encodeCatalog] silently skips (`continue`) any seed that
 * does not actually reach a win — the solver's own verdict is never shipped untrusted
 * (`docs/games/spider/DEALS.md` "Generation"). [decodeLine] fails to null, never an exception, on
 * a truncated or corrupt block.
 */
object CompactSolutionCodec {
    const val MAGIC = "SPSL"
    const val VERSION = 1

    private const val OP_TABLEAU_TO_TABLEAU = 0
    private const val OP_DEAL_ROW = 1

    /** `fromColumn * (TABLEAU_COLUMNS - 1) + adjustedToColumn` fits 0..89 in 7 bits. */
    private const val PAIR_BITS = 7
    private const val COUNT_BITS = 16

    /**
     * Widest inline delta from [sequenceStart]. A tableau column cannot hold more than
     * [org.finiteplay.spider.layout.DECK_CARDS] cards, so the escape exists only for a
     * pathological lift far short of the full run, which real play essentially never chooses.
     */
    private const val DELTA_INLINE_BITS = 5
    private const val DELTA_INLINE_MAX = (1 shl DELTA_INLINE_BITS) - 2
    private const val DELTA_ESCAPE = DELTA_INLINE_MAX + 1
    private const val DELTA_ESCAPE_BITS = 12

    /** One stored choice: a move's operands, with [Move.TableauToTableau]'s pile position relative to the board it was played from. */
    internal sealed class Choice {
        data class Transfer(val fromColumn: Int, val toColumn: Int, val delta: Int) : Choice()
        data object Deal : Choice()
    }

    /**
     * Replays [line] from [start] through the real reducer, recording each
     * [Move.TableauToTableau]'s lift depth relative to [sequenceStart] of the board in front of it.
     * Returns null when the line does not replay to a win — a line is never encoded on trust.
     */
    internal fun choicesOf(start: SpiderState, line: List<Move>): List<Choice>? {
        var state = start
        val choices = ArrayList<Choice>(line.size)
        for (move in line) {
            val choice = when (move) {
                is Move.TableauToTableau -> {
                    val source = state.tableau.getOrNull(move.fromColumn) ?: return null
                    Choice.Transfer(move.fromColumn, move.toColumn, move.fromIndex - sequenceStart(source))
                }
                Move.DealRow -> Choice.Deal
            }
            state = runCatching { applyMove(state, move) }.getOrElse { return null }
            choices += choice
        }
        return if (state.isWon) choices else null
    }

    /**
     * Expands a stored choice list back into the literal move list a replay needs, rebuilding each
     * [Move.TableauToTableau]'s absolute `fromIndex` from the board in front of it.
     *
     * Returns null if any step is not legal from [start] — a corrupt or stale block reports itself
     * rather than handing back a line that stops mid-game.
     */
    internal fun expand(start: SpiderState, choices: List<Choice>): List<Move>? {
        var state = start
        val line = ArrayList<Move>(choices.size)
        for (choice in choices) {
            val move = when (choice) {
                is Choice.Transfer -> {
                    val source = state.tableau.getOrNull(choice.fromColumn) ?: return null
                    Move.TableauToTableau(choice.fromColumn, sequenceStart(source) + choice.delta, choice.toColumn)
                }
                Choice.Deal -> Move.DealRow
            }
            state = runCatching { applyMove(state, move) }.getOrElse { return null }
            line += move
        }
        return if (state.isWon) line else null
    }

    fun encodeCatalog(solutions: Map<Long, List<Move>>, dealOf: (Long) -> SpiderState): ByteArray {
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

    /** Decodes one seed's line, or null when the blob does not hold it or its block is corrupt. */
    fun decodeLine(bytes: ByteArray, index: Index, deal: SpiderState, seed: Long): List<Move>? {
        val at = index.seeds.binarySearch(seed)
        if (at < 0) return null
        val choices = runCatching { unpackBlock(bytes, index.offsets[at], index.lengths[at]) }.getOrNull() ?: return null
        return expand(deal, choices)
    }

    // --- bit packing -------------------------------------------------------------------

    private fun packBlock(choices: List<Choice>): ByteArray {
        val bits = BitWriter()
        bits.write(choices.size, COUNT_BITS)
        for (choice in choices) {
            when (choice) {
                is Choice.Transfer -> {
                    bits.write(OP_TABLEAU_TO_TABLEAU, 1)
                    val adjustedTo = if (choice.toColumn < choice.fromColumn) choice.toColumn else choice.toColumn - 1
                    bits.write(choice.fromColumn * (TABLEAU_COLUMNS - 1) + adjustedTo, PAIR_BITS)
                    writeDelta(bits, choice.delta)
                }
                Choice.Deal -> bits.write(OP_DEAL_ROW, 1)
            }
        }
        return bits.toByteArray()
    }

    private fun unpackBlock(bytes: ByteArray, offset: Int, length: Int): List<Choice> {
        val bits = BitReader(bytes, offset, length)
        val count = bits.read(COUNT_BITS)
        val choices = ArrayList<Choice>(count)
        repeat(count) {
            when (bits.read(1)) {
                OP_TABLEAU_TO_TABLEAU -> {
                    val pair = bits.read(PAIR_BITS)
                    require(pair < TABLEAU_COLUMNS * (TABLEAU_COLUMNS - 1)) { "invalid column pair in a solution block" }
                    val fromColumn = pair / (TABLEAU_COLUMNS - 1)
                    val adjustedTo = pair % (TABLEAU_COLUMNS - 1)
                    val toColumn = if (adjustedTo < fromColumn) adjustedTo else adjustedTo + 1
                    val delta = readDelta(bits)
                    choices += Choice.Transfer(fromColumn, toColumn, delta)
                }
                OP_DEAL_ROW -> choices += Choice.Deal
                else -> throw IllegalArgumentException("unknown opcode in a solution block")
            }
        }
        return choices
    }

    private fun writeDelta(bits: BitWriter, delta: Int) {
        require(delta >= 0) { "a lift cannot start above the movable sequence" }
        if (delta == 0) {
            bits.write(0, 1)
            return
        }
        bits.write(1, 1)
        val value = delta - 1
        if (value <= DELTA_INLINE_MAX) {
            bits.write(value, DELTA_INLINE_BITS)
        } else {
            bits.write(DELTA_ESCAPE, DELTA_INLINE_BITS)
            bits.write(value, DELTA_ESCAPE_BITS)
        }
    }

    private fun readDelta(bits: BitReader): Int {
        if (bits.read(1) == 0) return 0
        val head = bits.read(DELTA_INLINE_BITS)
        val value = if (head == DELTA_ESCAPE) bits.read(DELTA_ESCAPE_BITS) else head
        return value + 1
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
