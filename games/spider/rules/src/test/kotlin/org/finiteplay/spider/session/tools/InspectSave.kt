package org.finiteplay.spider.session.tools

import java.io.File
import java.util.Base64
import org.finiteplay.cards.Rank
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.session.decodeSpiderLog
import org.finiteplay.spider.session.replaySpiderSession

/**
 * Dev tool: decode a Spider `active_game.preferences_pb` pulled off a device and print the
 * board it reconstructs to. Not part of the app or its tests — run via the `inspectSave`
 * Gradle task, which a device pull script feeds. See `games/spider/tools/inspect-save.ps1`.
 *
 * Reads the AndroidX Preferences DataStore wire format directly (varint tag/length,
 * string/int32/int64 values) rather than depending on the datastore-preferences runtime,
 * since this module is plain JVM and the format is small and stable.
 */
fun main(args: Array<String>) {
    val path = args.getOrNull(0)
        ?: error("usage: inspectSave <path to active_game.preferences_pb>")
    val prefs = parsePreferenceMap(File(path).readBytes())

    val seed = prefs["seed"] as Long
    val versions = GameVersions(
        catalogVersion = prefs["catalog_version"] as Int,
        rulesVersion = prefs["rules_version"] as Int,
        shuffleVersion = prefs["shuffle_version"] as Int,
    )
    val suitCount = SuitCount.valueOf(prefs["suit_count"] as String)
    val logBytes = Base64.getDecoder().decode(prefs["log"] as String)
    val log = decodeSpiderLog(logBytes)

    val session = replaySpiderSession(seed, versions, log, suitCount)
    printBoard(session.state)
}

private fun printBoard(state: SpiderState) {
    println(
        "moveCount=${state.moveCount} status=${state.status} " +
            "rowDealsRemaining=${state.rowDealsRemaining} banked=${state.banked}",
    )
    state.tableau.forEachIndexed { index, column ->
        val cards = column.joinToString(" ") { tableauCard ->
            val hidden = if (tableauCard.faceUp) "" else "*"
            "$hidden${rankLabel(tableauCard.card.rank)}${tableauCard.card.suit.symbol}"
        }
        println("col ${index + 1} (${column.size}): $cards")
    }
}

private fun rankLabel(rank: Rank): String = when (rank) {
    Rank.ACE -> "A"
    Rank.JACK -> "J"
    Rank.QUEEN -> "Q"
    Rank.KING -> "K"
    else -> rank.value.toString()
}

// --- Minimal AndroidX Preferences DataStore (.preferences_pb) reader ---
// Wire format: top-level message is a repeated field 1 of {key: string field 1, value:
// message field 2}. A value message is a oneof: bool=1, float=2, int32=3, int64=4,
// string=5, string_set=6, double=7, bytes=8. Only the cases this save uses are decoded;
// anything else throws rather than silently mis-reading.

private fun parsePreferenceMap(bytes: ByteArray): Map<String, Any> {
    val out = mutableMapOf<String, Any>()
    var i = 0
    while (i < bytes.size) {
        val (tag, afterTag) = readVarint(bytes, i)
        i = afterTag
        require((tag and 7L) == 2L) { "expected a length-delimited map entry, got wire type ${tag and 7}" }
        val (length, afterLen) = readVarint(bytes, i)
        i = afterLen
        val entry = bytes.copyOfRange(i, i + length.toInt())
        i += length.toInt()

        var j = 0
        var key: String? = null
        var value: Any? = null
        while (j < entry.size) {
            val (fieldTag, afterFieldTag) = readVarint(entry, j)
            j = afterFieldTag
            val (fieldLen, afterFieldLen) = readVarint(entry, j)
            j = afterFieldLen
            val fieldBytes = entry.copyOfRange(j, j + fieldLen.toInt())
            j += fieldLen.toInt()
            when (fieldTag ushr 3) {
                1L -> key = fieldBytes.toString(Charsets.UTF_8)
                2L -> value = parseValue(fieldBytes)
                else -> error("unexpected map entry field ${fieldTag ushr 3}")
            }
        }
        out[requireNotNull(key) { "map entry missing a key" }] =
            requireNotNull(value) { "map entry '$key' missing a value" }
    }
    return out
}

private fun parseValue(bytes: ByteArray): Any {
    var i = 0
    var result: Any? = null
    while (i < bytes.size) {
        val (tag, afterTag) = readVarint(bytes, i)
        i = afterTag
        val fieldNo = tag ushr 3
        when ((tag and 7L).toInt()) {
            0 -> { // varint: bool=1, int32=3, int64=4
                val (v, after) = readVarint(bytes, i)
                i = after
                result = if (fieldNo == 3L) v.toInt() else v
            }
            2 -> { // length-delimited: string=5, bytes=8, string_set=6 (message)
                val (len, after) = readVarint(bytes, i)
                i = after
                val chunk = bytes.copyOfRange(i, i + len.toInt())
                i += len.toInt()
                result = if (fieldNo == 5L) chunk.toString(Charsets.UTF_8) else chunk
            }
            5 -> i += 4 // float, unused by this save
            1 -> i += 8 // double, unused by this save
            else -> error("unsupported wire type in Value for field $fieldNo")
        }
    }
    return requireNotNull(result) { "empty Value message" }
}

/** Returns the decoded value and the index just past it. */
private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
    var result = 0L
    var shift = 0
    var i = start
    while (true) {
        val b = bytes[i].toInt() and 0xFF
        i++
        result = result or ((b and 0x7F).toLong() shl shift)
        if (b and 0x80 == 0) break
        shift += 7
    }
    return result to i
}
