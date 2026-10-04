package org.finiteplay.freecell.session

import org.finiteplay.freecell.rules.Move
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Binary encoding for the append-only [FreeCellLogEntry] log — the active-game save format
 * (`docs/PLATFORM.md` "Persistence"). No solver or catalog exists yet
 * (`docs/games/freecell/EXECUTION_PLAN.md` F5), so these opcodes are not yet also a certificate
 * alphabet the way Klondike's `MoveOpcode` is — but they are frozen the same way regardless: once
 * a save is written under an opcode, that opcode cannot change meaning without a format-version
 * bump, whether or not anything else reads it yet.
 *
 * Lives beside [FreeCellLogEntry] rather than in `:app`'s storage layer, matching Klondike's own
 * `LogEntryCodec`: it is the serialized form of a `:rules` type, defined in terms of `:rules`
 * moves, and keeping it here lets a future solver or offline tool decode a pulled save without a
 * second, drifting copy of the format.
 *
 * Decoding never throws a caller into an inconsistent state on malformed input — every failure
 * surfaces as [IOException] or a standard parsing exception, which the app's active-game store
 * treats as a corrupt save.
 */
object FreeCellLogCodec {
    private const val OPCODE_TABLEAU_TO_TABLEAU: Byte = 1
    private const val OPCODE_TABLEAU_TO_FREE_CELL: Byte = 2
    private const val OPCODE_TABLEAU_TO_FOUNDATION: Byte = 3
    private const val OPCODE_FREE_CELL_TO_TABLEAU: Byte = 4
    private const val OPCODE_FREE_CELL_TO_FOUNDATION: Byte = 5
    private const val OPCODE_UNDO: Byte = 6
    private const val OPCODE_SET_AUTOMATIC_MOVES: Byte = 7
    private const val OPCODE_AUTO_FINISH: Byte = 8

    fun encode(log: List<FreeCellLogEntry>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(log.size)
            log.forEach { entry -> writeEntry(out, entry) }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): List<FreeCellLogEntry> {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val count = input.readInt()
            require(count >= 0) { "negative log entry count: $count" }
            return List(count) { readEntry(input) }
        }
    }

    private fun writeEntry(out: DataOutputStream, entry: FreeCellLogEntry) {
        when (entry) {
            is FreeCellLogEntry.PlayerMove -> writeMove(out, entry.move)
            FreeCellLogEntry.Undo -> out.writeByte(OPCODE_UNDO.toInt())
            is FreeCellLogEntry.SetAutomaticMoves -> {
                out.writeByte(OPCODE_SET_AUTOMATIC_MOVES.toInt())
                out.writeBoolean(entry.enabled)
            }
            FreeCellLogEntry.AutoFinish -> out.writeByte(OPCODE_AUTO_FINISH.toInt())
        }
    }

    private fun writeMove(out: DataOutputStream, move: Move) {
        when (move) {
            is Move.TableauToTableau -> {
                out.writeByte(OPCODE_TABLEAU_TO_TABLEAU.toInt())
                out.writeByte(move.fromColumn)
                out.writeByte(move.fromIndex)
                out.writeByte(move.toColumn)
            }
            is Move.TableauToFreeCell -> {
                out.writeByte(OPCODE_TABLEAU_TO_FREE_CELL.toInt())
                out.writeByte(move.fromColumn)
                out.writeByte(move.cell)
            }
            is Move.TableauToFoundation -> {
                out.writeByte(OPCODE_TABLEAU_TO_FOUNDATION.toInt())
                out.writeByte(move.fromColumn)
            }
            is Move.FreeCellToTableau -> {
                out.writeByte(OPCODE_FREE_CELL_TO_TABLEAU.toInt())
                out.writeByte(move.cell)
                out.writeByte(move.toColumn)
            }
            is Move.FreeCellToFoundation -> {
                out.writeByte(OPCODE_FREE_CELL_TO_FOUNDATION.toInt())
                out.writeByte(move.cell)
            }
        }
    }

    private fun readEntry(input: DataInputStream): FreeCellLogEntry {
        val opcode = input.readByte()
        return when (opcode) {
            OPCODE_TABLEAU_TO_TABLEAU -> FreeCellLogEntry.PlayerMove(
                Move.TableauToTableau(
                    fromColumn = input.readByte().toInt(),
                    fromIndex = input.readByte().toInt(),
                    toColumn = input.readByte().toInt(),
                ),
            )
            OPCODE_TABLEAU_TO_FREE_CELL -> FreeCellLogEntry.PlayerMove(
                Move.TableauToFreeCell(fromColumn = input.readByte().toInt(), cell = input.readByte().toInt()),
            )
            OPCODE_TABLEAU_TO_FOUNDATION -> FreeCellLogEntry.PlayerMove(
                Move.TableauToFoundation(fromColumn = input.readByte().toInt()),
            )
            OPCODE_FREE_CELL_TO_TABLEAU -> FreeCellLogEntry.PlayerMove(
                Move.FreeCellToTableau(cell = input.readByte().toInt(), toColumn = input.readByte().toInt()),
            )
            OPCODE_FREE_CELL_TO_FOUNDATION -> FreeCellLogEntry.PlayerMove(
                Move.FreeCellToFoundation(cell = input.readByte().toInt()),
            )
            OPCODE_UNDO -> FreeCellLogEntry.Undo
            OPCODE_SET_AUTOMATIC_MOVES -> FreeCellLogEntry.SetAutomaticMoves(input.readBoolean())
            OPCODE_AUTO_FINISH -> FreeCellLogEntry.AutoFinish
            else -> throw IOException("unknown opcode $opcode")
        }
    }
}
