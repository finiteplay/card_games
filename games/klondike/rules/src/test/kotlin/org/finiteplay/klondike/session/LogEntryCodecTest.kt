package org.finiteplay.klondike.session

import org.finiteplay.cards.Suit
import org.finiteplay.klondike.rules.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class LogEntryCodecTest {

    private val everyEntryKind = listOf(
        LogEntry.PlayerMove(Move.Draw),
        LogEntry.PlayerMove(Move.Recycle),
        LogEntry.PlayerMove(Move.TableauToTableau(fromColumn = 3, fromIndex = 5, toColumn = 6)),
        LogEntry.PlayerMove(Move.TableauToFoundation(fromColumn = 2)),
        LogEntry.PlayerMove(Move.WasteToTableau(toColumn = 0)),
        LogEntry.PlayerMove(Move.WasteToFoundation),
        LogEntry.PlayerMove(Move.FoundationToTableau(suit = Suit.HEARTS, toColumn = 4)),
        LogEntry.Undo,
        LogEntry.SetAutomaticMoves(enabled = false),
        LogEntry.SetAutomaticMoves(enabled = true),
        LogEntry.AutoFinish,
    )

    @Test
    fun `round-trips every log entry kind`() {
        val decoded = LogEntryCodec.decode(LogEntryCodec.encode(everyEntryKind))
        assertEquals(everyEntryKind, decoded)
    }

    @Test
    fun `round-trips an empty log`() {
        assertEquals(emptyList<LogEntry>(), LogEntryCodec.decode(LogEntryCodec.encode(emptyList())))
    }

    @Test
    fun `round-trips a long realistic log`() {
        val log = (1..500).map {
            if (it % 7 == 0) LogEntry.Undo else LogEntry.PlayerMove(Move.Draw)
        }
        assertEquals(log, LogEntryCodec.decode(LogEntryCodec.encode(log)))
    }

    @Test
    fun `decoding a truncated stream throws rather than returning a partial log`() {
        val truncated = LogEntryCodec.encode(everyEntryKind).copyOf(3)
        assertThrows(IOException::class.java) { LogEntryCodec.decode(truncated) }
    }

    @Test
    fun `decoding an unknown opcode throws`() {
        val garbage = byteArrayOf(0, 0, 0, 1, 99)
        assertThrows(IllegalArgumentException::class.java) { LogEntryCodec.decode(garbage) }
    }

    @Test
    fun `decoding a negative declared count throws`() {
        val garbage = byteArrayOf(-1, -1, -1, -1)
        assertThrows(IllegalArgumentException::class.java) { LogEntryCodec.decode(garbage) }
    }

    @Test
    fun `decoding a certificate-only FLIP opcode throws, since it is never a committed log entry`() {
        val bytesWithFlipOpcode = byteArrayOf(0, 0, 0, 1, 2)
        assertThrows(IOException::class.java) { LogEntryCodec.decode(bytesWithFlipOpcode) }
    }
}
