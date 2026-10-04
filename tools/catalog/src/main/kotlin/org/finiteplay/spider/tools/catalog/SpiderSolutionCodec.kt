package org.finiteplay.spider.tools.catalog

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.session.SpiderLogEntry
import org.finiteplay.spider.session.encodeSpiderLog
import org.finiteplay.spider.session.decodeSpiderLog

/**
 * Flat, seed-indexed encoding for every certified deal's winning line
 * (`docs/games/spider/DEALS.md` "Shipped Solutions").
 *
 * Not seekable like Klondike's `CompactSolutionCodec`: Spider's own log alphabet already packs a
 * whole move into one to four bytes (`SpiderLogCodec`), and twenty thousand short lines decode into
 * memory in a single pass cheaply enough that an index avoiding that pass is not worth a second
 * format. Reuses [encodeSpiderLog]/[decodeSpiderLog] directly — a certificate is exactly a
 * committed-move log with no undos, which is what that codec already writes.
 */
object SpiderSolutionCodec {
    private const val MAGIC = 0x53504C31 // ASCII "SPL1"

    fun encode(solutions: Map<Long, List<Move>>): ByteArray {
        val out = ByteArrayOutputStream()
        val data = DataOutputStream(out)
        data.writeInt(MAGIC)
        data.writeInt(solutions.size)
        for ((seed, line) in solutions.toSortedMap()) {
            val payload = encodeSpiderLog(line.map { SpiderLogEntry.PlayerMove(it) })
            data.writeLong(seed)
            data.writeInt(payload.size)
            data.write(payload)
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): Map<Long, List<Move>> {
        val data = DataInputStream(bytes.inputStream())
        require(data.readInt() == MAGIC) { "not a Spider solutions blob" }
        val count = data.readInt()
        val result = LinkedHashMap<Long, List<Move>>(count)
        repeat(count) {
            val seed = data.readLong()
            val payload = ByteArray(data.readInt())
            data.readFully(payload)
            result[seed] = decodeSpiderLog(payload).map { (it as SpiderLogEntry.PlayerMove).move }
        }
        return result
    }
}
