package org.finiteplay.klondike.session

import org.finiteplay.cards.Suit
import org.finiteplay.klondike.deal.MoveOpcode
import org.finiteplay.klondike.rules.Move
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Binary encoding for the append-only [LogEntry] log — the active-game save format
 * (`docs/games/klondike/DESIGN.md`, "Persistence and Privacy"). Uses the frozen [MoveOpcode] IDs so this
 * stays the same primitive encoding the solver certificates use. [MoveOpcode] has no slot
 * for [LogEntry.SetAutomaticMoves] (it is a session-level event, not a certificate move),
 * so this codec reserves [OPCODE_SET_AUTOMATIC_MOVES] locally rather than editing the
 * frozen enum. [MoveOpcode.FLIP] is never emitted here: flips are derived by the
 * reducer during replay, not recorded as a log entry.
 *
 * Lives beside [LogEntry] rather than in `:app`'s storage layer, matching
 * `solution/SolutionCodec`: it is the serialized form of a `:rules` type, defined in terms
 * of `:rules` opcodes, and keeping it here lets offline tooling decode a pulled save
 * without a second, drifting copy of the format.
 *
 * Decoding never throws a caller into an inconsistent state on malformed input — every
 * failure surfaces as [IOException] or a standard parsing exception, which `ActiveGameStore`
 * treats as a corrupt save.
 */
object LogEntryCodec {
    private const val OPCODE_SET_AUTOMATIC_MOVES: Byte = 10

    fun encode(log: List<LogEntry>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(log.size)
            log.forEach { entry -> writeEntry(out, entry) }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): List<LogEntry> {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val count = input.readInt()
            require(count >= 0) { "negative log entry count: $count" }
            return List(count) { readEntry(input) }
        }
    }

    private fun writeEntry(out: DataOutputStream, entry: LogEntry) {
        when (entry) {
            is LogEntry.PlayerMove -> writeMove(out, entry.move)
            LogEntry.Undo -> out.writeByte(MoveOpcode.UNDO.id.toInt())
            is LogEntry.SetAutomaticMoves -> {
                out.writeByte(OPCODE_SET_AUTOMATIC_MOVES.toInt())
                out.writeBoolean(entry.enabled)
            }
            LogEntry.AutoFinish -> out.writeByte(MoveOpcode.AUTO_FINISH.id.toInt())
        }
    }

    private fun writeMove(out: DataOutputStream, move: Move) {
        when (move) {
            Move.Draw -> out.writeByte(MoveOpcode.DRAW.id.toInt())
            Move.Recycle -> out.writeByte(MoveOpcode.RECYCLE.id.toInt())
            is Move.TableauToTableau -> {
                out.writeByte(MoveOpcode.TABLEAU_TO_TABLEAU.id.toInt())
                out.writeByte(move.fromColumn)
                out.writeByte(move.fromIndex)
                out.writeByte(move.toColumn)
            }
            is Move.TableauToFoundation -> {
                out.writeByte(MoveOpcode.TABLEAU_TO_FOUNDATION.id.toInt())
                out.writeByte(move.fromColumn)
            }
            is Move.WasteToTableau -> {
                out.writeByte(MoveOpcode.WASTE_TO_TABLEAU.id.toInt())
                out.writeByte(move.toColumn)
            }
            Move.WasteToFoundation -> out.writeByte(MoveOpcode.WASTE_TO_FOUNDATION.id.toInt())
            is Move.FoundationToTableau -> {
                out.writeByte(MoveOpcode.FOUNDATION_TO_TABLEAU.id.toInt())
                out.writeByte(move.suit.ordinal)
                out.writeByte(move.toColumn)
            }
        }
    }

    private fun readEntry(input: DataInputStream): LogEntry {
        val opcodeId = input.readByte()
        if (opcodeId == OPCODE_SET_AUTOMATIC_MOVES) {
            return LogEntry.SetAutomaticMoves(input.readBoolean())
        }
        return when (val opcode = MoveOpcode.fromId(opcodeId)) {
            MoveOpcode.UNDO -> LogEntry.Undo
            MoveOpcode.AUTO_FINISH -> LogEntry.AutoFinish
            MoveOpcode.DRAW -> LogEntry.PlayerMove(Move.Draw)
            MoveOpcode.RECYCLE -> LogEntry.PlayerMove(Move.Recycle)
            MoveOpcode.TABLEAU_TO_TABLEAU -> LogEntry.PlayerMove(
                Move.TableauToTableau(
                    fromColumn = input.readByte().toInt(),
                    fromIndex = input.readByte().toInt(),
                    toColumn = input.readByte().toInt(),
                ),
            )
            MoveOpcode.TABLEAU_TO_FOUNDATION -> LogEntry.PlayerMove(
                Move.TableauToFoundation(fromColumn = input.readByte().toInt()),
            )
            MoveOpcode.WASTE_TO_TABLEAU -> LogEntry.PlayerMove(
                Move.WasteToTableau(toColumn = input.readByte().toInt()),
            )
            MoveOpcode.WASTE_TO_FOUNDATION -> LogEntry.PlayerMove(Move.WasteToFoundation)
            MoveOpcode.FOUNDATION_TO_TABLEAU -> LogEntry.PlayerMove(
                Move.FoundationToTableau(
                    suit = suitFromOrdinal(input.readByte()),
                    toColumn = input.readByte().toInt(),
                ),
            )
            MoveOpcode.FLIP -> throw IOException("opcode $opcode is not valid in a committed log")
        }
    }

    private fun suitFromOrdinal(ordinal: Byte): Suit {
        val suits = Suit.entries
        if (ordinal.toInt() !in suits.indices) throw IOException("invalid suit ordinal: $ordinal")
        return suits[ordinal.toInt()]
    }
}
