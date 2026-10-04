package org.finiteplay.freecell.tools.catalog

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.finiteplay.freecell.rules.Move
import org.finiteplay.freecell.session.FreeCellLogCodec
import org.finiteplay.freecell.session.FreeCellLogEntry

/**
 * Flat, seed-indexed encoding for every certified deal's winning line
 * (`docs/games/freecell/DEALS.md` "Generation"), the certificate-storage analogue of the active
 * game save format.
 *
 * Reuses [FreeCellLogCodec] directly rather than inventing a second move alphabet: a certificate
 * is exactly a committed-move log with no undos, which is what that codec already writes, wrapping
 * each [Move] as a [FreeCellLogEntry.PlayerMove] and unwrapping the same way on the way back out.
 */
object FreeCellSolutionCodec {
    private const val MAGIC = 0x4652434C // ASCII "FRCL"

    fun encode(solutions: Map<Long, List<Move>>): ByteArray {
        val out = ByteArrayOutputStream()
        val data = DataOutputStream(out)
        data.writeInt(MAGIC)
        data.writeInt(solutions.size)
        for ((seed, line) in solutions.toSortedMap()) {
            val payload = FreeCellLogCodec.encode(line.map { FreeCellLogEntry.PlayerMove(it) })
            data.writeLong(seed)
            data.writeInt(payload.size)
            data.write(payload)
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): Map<Long, List<Move>> {
        val data = DataInputStream(bytes.inputStream())
        require(data.readInt() == MAGIC) { "not a FreeCell solutions blob" }
        val count = data.readInt()
        val result = LinkedHashMap<Long, List<Move>>(count)
        repeat(count) {
            val seed = data.readLong()
            val payload = ByteArray(data.readInt())
            data.readFully(payload)
            result[seed] = FreeCellLogCodec.decode(payload).map { (it as FreeCellLogEntry.PlayerMove).move }
        }
        return result
    }
}
