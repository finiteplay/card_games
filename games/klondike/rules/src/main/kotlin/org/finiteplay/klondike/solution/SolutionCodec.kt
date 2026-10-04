package org.finiteplay.klondike.solution

import org.finiteplay.cards.Suit
import org.finiteplay.klondike.rules.Move
import java.io.ByteArrayOutputStream

/**
 * Packs a winning move sequence into two bytes per move, and a whole catalog of them
 * into one seekable blob — the shipped solution index behind hint-following
 * (`docs/games/klondike/DEALS.md`, "Shipped Solutions").
 *
 * Two bytes rather than a variable-width scheme: the widest move
 * ([Move.TableauToTableau]) needs eleven bits, so everything fits with room to spare,
 * decoding needs no bit-stitching across byte boundaries, and the file compresses well
 * inside the APK anyway because the same handful of move shapes repeat endlessly.
 *
 * Layout of the packed 16 bits, low to high: opcode (3), then the operands each move
 * kind needs — column indexes are 3 bits (0-6), a run's start index 5 bits (0-18, the
 * deepest a tableau column can grow), a suit 2 bits.
 */
object SolutionCodec {
    private const val OP_DRAW = 0
    private const val OP_RECYCLE = 1
    private const val OP_TABLEAU_TO_TABLEAU = 2
    private const val OP_TABLEAU_TO_FOUNDATION = 3
    private const val OP_WASTE_TO_TABLEAU = 4
    private const val OP_WASTE_TO_FOUNDATION = 5
    private const val OP_FOUNDATION_TO_TABLEAU = 6

    fun encodeMove(move: Move): Int = when (move) {
        Move.Draw -> OP_DRAW
        Move.Recycle -> OP_RECYCLE
        is Move.TableauToTableau -> OP_TABLEAU_TO_TABLEAU or (move.fromColumn shl 3) or (move.toColumn shl 6) or (move.fromIndex shl 9)
        is Move.TableauToFoundation -> OP_TABLEAU_TO_FOUNDATION or (move.fromColumn shl 3)
        Move.WasteToFoundation -> OP_WASTE_TO_FOUNDATION
        is Move.WasteToTableau -> OP_WASTE_TO_TABLEAU or (move.toColumn shl 3)
        is Move.FoundationToTableau -> OP_FOUNDATION_TO_TABLEAU or (move.suit.ordinal shl 3) or (move.toColumn shl 5)
    }

    fun decodeMove(packed: Int): Move = when (packed and 0x7) {
        OP_DRAW -> Move.Draw
        OP_RECYCLE -> Move.Recycle
        OP_TABLEAU_TO_TABLEAU -> Move.TableauToTableau(
            fromColumn = (packed shr 3) and 0x7,
            toColumn = (packed shr 6) and 0x7,
            fromIndex = (packed shr 9) and 0x1F,
        )
        OP_TABLEAU_TO_FOUNDATION -> Move.TableauToFoundation(fromColumn = (packed shr 3) and 0x7)
        OP_WASTE_TO_TABLEAU -> Move.WasteToTableau(toColumn = (packed shr 3) and 0x7)
        OP_WASTE_TO_FOUNDATION -> Move.WasteToFoundation
        OP_FOUNDATION_TO_TABLEAU -> Move.FoundationToTableau(
            suit = Suit.entries[(packed shr 3) and 0x3],
            toColumn = (packed shr 5) and 0x7,
        )
        else -> throw IllegalArgumentException("Unknown move opcode in packed value $packed")
    }

    /**
     * Writes seed-keyed solutions as: `MAGIC`, entry count, then per entry the seed
     * (8 bytes), move count (2 bytes), and the packed moves. Entries are written in
     * ascending seed order so a reader can binary-search without an extra index.
     */
    fun encodeCatalog(solutions: Map<Long, List<Move>>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(MAGIC.toByteArray(Charsets.US_ASCII))
        writeInt(out, solutions.size)
        for ((seed, moves) in solutions.toSortedMap()) {
            require(moves.size <= MAX_MOVES) { "solution for seed $seed is ${moves.size} moves, over the $MAX_MOVES cap" }
            writeLong(out, seed)
            writeShort(out, moves.size)
            for (move in moves) writeShort(out, encodeMove(move))
        }
        return out.toByteArray()
    }

    /** Reads what [encodeCatalog] wrote. Throws on a truncated or non-matching blob rather than returning a partial catalog. */
    fun decodeCatalog(bytes: ByteArray): Map<Long, List<Move>> {
        require(bytes.size >= MAGIC.length + 4) { "solution catalog is too short to contain a header" }
        val magic = String(bytes, 0, MAGIC.length, Charsets.US_ASCII)
        require(magic == MAGIC) { "solution catalog magic was '$magic', expected '$MAGIC'" }

        var offset = MAGIC.length
        val entries = readInt(bytes, offset).also { offset += 4 }
        val solutions = HashMap<Long, List<Move>>(entries)
        repeat(entries) {
            val seed = readLong(bytes, offset).also { offset += 8 }
            val moveCount = readShort(bytes, offset).also { offset += 2 }
            val moves = ArrayList<Move>(moveCount)
            repeat(moveCount) {
                moves += decodeMove(readShort(bytes, offset))
                offset += 2
            }
            solutions[seed] = moves
        }
        return solutions
    }

    const val MAGIC = "KSOL"

    /** Matches the generator's own winning-line cap, so a move count always fits the two bytes it is written into. */
    const val MAX_MOVES = 500

    private fun writeInt(out: ByteArrayOutputStream, value: Int) {
        for (shift in 0 until 32 step 8) out.write((value shr shift) and 0xFF)
    }

    private fun writeShort(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    private fun writeLong(out: ByteArrayOutputStream, value: Long) {
        for (shift in 0 until 64 step 8) out.write(((value shr shift) and 0xFF).toInt())
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int {
        require(offset + 4 <= bytes.size) { "truncated solution catalog reading an int at $offset" }
        var value = 0
        for (i in 0 until 4) value = value or ((bytes[offset + i].toInt() and 0xFF) shl (i * 8))
        return value
    }

    private fun readShort(bytes: ByteArray, offset: Int): Int {
        require(offset + 2 <= bytes.size) { "truncated solution catalog reading a short at $offset" }
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readLong(bytes: ByteArray, offset: Int): Long {
        require(offset + 8 <= bytes.size) { "truncated solution catalog reading a long at $offset" }
        var value = 0L
        for (i in 0 until 8) value = value or ((bytes[offset + i].toLong() and 0xFF) shl (i * 8))
        return value
    }
}
