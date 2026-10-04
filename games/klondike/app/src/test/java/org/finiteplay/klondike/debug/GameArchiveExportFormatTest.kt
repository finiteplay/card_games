package org.finiteplay.klondike.debug

import org.finiteplay.cards.Suit
import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.session.LogEntry
import org.finiteplay.klondike.storage.ArchivedGame
import org.finiteplay.klondike.storage.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The export is the only way the archive leaves the device, so a token that silently
 * collided with another would corrupt the analysis it exists to serve.
 */
class GameArchiveExportFormatTest {

    private fun archived(moves: List<LogEntry>) = ArchivedGame(
        gameId = "g1",
        seed = 42L,
        versions = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1),
        drawMode = DrawMode.ONE,
        initialAutomaticMovesEnabled = true,
        outcome = Outcome.WIN,
        elapsedMillis = 12_345L,
        moveCount = 3,
        timestampMillis = 1_700_000_000_000L,
        countedForStatistics = true,
        moves = moves,
    )

    @Test
    fun `every entry type gets a distinct token`() {
        val entries = listOf(
            LogEntry.PlayerMove(Move.Draw),
            LogEntry.PlayerMove(Move.Recycle),
            LogEntry.PlayerMove(Move.WasteToFoundation),
            LogEntry.PlayerMove(Move.TableauToTableau(fromColumn = 2, fromIndex = 5, toColumn = 4)),
            LogEntry.PlayerMove(Move.TableauToFoundation(fromColumn = 3)),
            LogEntry.PlayerMove(Move.WasteToTableau(toColumn = 6)),
            LogEntry.PlayerMove(Move.FoundationToTableau(suit = Suit.HEARTS, toColumn = 1)),
            LogEntry.Undo,
            LogEntry.AutoFinish,
            LogEntry.SetAutomaticMoves(enabled = false),
        )

        val moveLine = formatGameArchive(listOf(archived(entries))).lines().first { it.startsWith("D ") }
        val tokens = moveLine.split(" ")

        assertEquals(entries.size, tokens.size)
        assertEquals(entries.size, tokens.toSet().size)
        assertEquals(listOf("D", "R", "WF", "T2.5>4", "TF3", "W6", "FH>1", "U", "!", "A0"), tokens)
    }

    @Test
    fun `the header names every column of the game line, in order`() {
        val lines = formatGameArchive(listOf(archived(listOf(LogEntry.PlayerMove(Move.Draw))))).lines()
        val header = lines.first().removePrefix("# ").split(" ")
        val gameLine = lines.first { it.startsWith("g1 ") }.split(" ")

        assertEquals(header.size, gameLine.size)
        assertEquals("g1 42 ONE WIN 12345 3 1700000000000 true true", gameLine.joinToString(" "))
    }

    /** A long game exports every move on its one line — the export must not summarise. */
    @Test
    fun `a long move log exports in full`() {
        val text = formatGameArchive(listOf(archived(List(437) { LogEntry.PlayerMove(Move.Draw) })))

        val moveLine = text.lines().first { it.startsWith("D ") }
        assertEquals(437, moveLine.split(" ").size)
    }

    @Test
    fun `an empty archive still exports its header`() {
        val text = formatGameArchive(emptyList())

        assertTrue(text.startsWith("# gameId"))
        assertEquals(2, text.trim().lines().size)
    }
}
