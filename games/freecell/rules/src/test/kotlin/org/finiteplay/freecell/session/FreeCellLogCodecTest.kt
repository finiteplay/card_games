package org.finiteplay.freecell.session

import org.finiteplay.freecell.layout.GameVersions
import org.finiteplay.freecell.rules.Move
import org.junit.Assert.assertEquals
import org.junit.Test

class FreeCellLogCodecTest {

    private val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    @Test
    fun `every entry kind round-trips through encode and decode`() {
        val log = listOf(
            FreeCellLogEntry.PlayerMove(Move.TableauToTableau(fromColumn = 3, fromIndex = 5, toColumn = 7)),
            FreeCellLogEntry.PlayerMove(Move.TableauToFreeCell(fromColumn = 2, cell = 3)),
            FreeCellLogEntry.PlayerMove(Move.TableauToFoundation(fromColumn = 0)),
            FreeCellLogEntry.PlayerMove(Move.FreeCellToTableau(cell = 1, toColumn = 6)),
            FreeCellLogEntry.PlayerMove(Move.FreeCellToFoundation(cell = 2)),
            FreeCellLogEntry.Undo,
            FreeCellLogEntry.SetAutomaticMoves(false),
            FreeCellLogEntry.SetAutomaticMoves(true),
            FreeCellLogEntry.AutoFinish,
        )

        val decoded = FreeCellLogCodec.decode(FreeCellLogCodec.encode(log))

        assertEquals(log, decoded)
    }

    @Test
    fun `an empty log round-trips to an empty log`() {
        assertEquals(emptyList<FreeCellLogEntry>(), FreeCellLogCodec.decode(FreeCellLogCodec.encode(emptyList())))
    }

    @Test
    fun `decoding a real played session's log reproduces the identical session`() {
        var session = FreeCellSession.start(seed = 5L, versions = versions, automaticMovesEnabled = true)
        val state = session.state
        val column = state.tableau.indexOfFirst { it.isNotEmpty() }
        session = session.commitMove(Move.TableauToFreeCell(column, 0))
        session = session.undo()
        session = session.withAutomaticMoves(false)

        val decodedLog = FreeCellLogCodec.decode(FreeCellLogCodec.encode(session.log))
        val replayed = replayFreeCellSession(seed = 5L, versions = versions, initialAutomaticMovesEnabled = true, log = decodedLog)

        assertEquals(session.state, replayed.state)
        assertEquals(session.automaticMovesEnabled, replayed.automaticMovesEnabled)
    }

    @Test(expected = Exception::class)
    fun `an unknown opcode is reported as corrupt, not silently ignored`() {
        FreeCellLogCodec.decode(byteArrayOf(0, 0, 0, 1, 99))
    }
}
