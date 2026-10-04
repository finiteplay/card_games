package org.finiteplay.spider.session

import org.finiteplay.spider.rules.Move

/**
 * On-disk encoding for the committed-transaction log (`docs/PLATFORM.md` "Persistence").
 *
 * Spider's own alphabet, deliberately not shared: `docs/ARCHITECTURE.md` keeps move-log encoding
 * with the game, because the opcodes are also what a solution certificate would be written in and
 * must be frozen per game rather than tracked against another game's changes.
 *
 * Two bytes of alphabet against Klondike's four. A [Move.TableauToTableau] carries three small
 * integers — every one of them bounded by the ten columns and the twenty-card column ceiling — so
 * each fits a byte with room to spare, and a whole entry is four bytes. [SpiderLogEntry.Undo]
 * carries nothing and is one.
 *
 * [OPCODE_*] values are on disk on every installed device. Changing what one means makes every
 * save written before the change decode into a different game, silently; that is a format change
 * and needs [FORMAT_VERSION] bumped rather than a redefinition.
 */
internal object SpiderLogCodec {
    const val OPCODE_TABLEAU_TO_TABLEAU: Byte = 1
    const val OPCODE_DEAL_ROW: Byte = 2
    const val OPCODE_UNDO: Byte = 3
}

/**
 * The version this codec writes. Bumped whenever the alphabet or an entry's layout changes, which
 * makes older saves take the recovery path instead of decoding into the wrong board.
 */
const val SPIDER_LOG_FORMAT_VERSION = 1

/** Encodes [log] to bytes. Inverse of [decodeSpiderLog]. */
fun encodeSpiderLog(log: List<SpiderLogEntry>): ByteArray {
    val out = ArrayList<Byte>(log.size * 4)
    for (entry in log) {
        when (entry) {
            is SpiderLogEntry.PlayerMove -> when (val move = entry.move) {
                is Move.TableauToTableau -> {
                    out += SpiderLogCodec.OPCODE_TABLEAU_TO_TABLEAU
                    out += move.fromColumn.toByte()
                    out += move.fromIndex.toByte()
                    out += move.toColumn.toByte()
                }
                is Move.DealRow -> out += SpiderLogCodec.OPCODE_DEAL_ROW
            }
            is SpiderLogEntry.Undo -> out += SpiderLogCodec.OPCODE_UNDO
        }
    }
    return out.toByteArray()
}

/**
 * Decodes [bytes] back into log entries.
 *
 * Throws [IllegalArgumentException] on an unknown opcode or a truncated entry rather than
 * returning what it managed to read. A partial log replays into a board that is not the one that
 * was saved, and handing that back as a restored game is worse than reporting the save unreadable
 * — the store's caller turns the exception into the recovery notice a player is owed.
 */
fun decodeSpiderLog(bytes: ByteArray): List<SpiderLogEntry> {
    val log = ArrayList<SpiderLogEntry>()
    var i = 0
    while (i < bytes.size) {
        when (bytes[i]) {
            SpiderLogCodec.OPCODE_TABLEAU_TO_TABLEAU -> {
                require(i + 3 < bytes.size) { "truncated tableau move at byte $i" }
                log += SpiderLogEntry.PlayerMove(
                    Move.TableauToTableau(
                        fromColumn = bytes[i + 1].toInt(),
                        fromIndex = bytes[i + 2].toInt(),
                        toColumn = bytes[i + 3].toInt(),
                    ),
                )
                i += 4
            }
            SpiderLogCodec.OPCODE_DEAL_ROW -> {
                log += SpiderLogEntry.PlayerMove(Move.DealRow)
                i += 1
            }
            SpiderLogCodec.OPCODE_UNDO -> {
                log += SpiderLogEntry.Undo
                i += 1
            }
            else -> throw IllegalArgumentException("unknown opcode ${bytes[i]} at byte $i")
        }
    }
    return log
}
