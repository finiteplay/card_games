package org.finiteplay.freecell.debug

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.rules.Move
import org.finiteplay.freecell.session.FreeCellLogEntry
import org.finiteplay.freecell.session.FreeCellSession
import org.finiteplay.freecell.solver.HintOutcome
import org.finiteplay.freecell.ui.game.MoveRecorder
import java.io.File
import java.util.concurrent.Executors

/**
 * Debug-only transcript of every game, one append-only text file per game id, so a real game can be pulled and
 * replayed: `adb exec-out run-as org.finiteplay.freecell ls files/debug_moves`. Each file starts
 * with the seed and versions, so the log alone reproduces every board; the boards are written too, for reading.
 * Never networked; absent from release by source-set separation (`src/release`).
 */
fun debugMoveRecorder(filesDir: File): MoveRecorder? = FileMoveRecorder(File(filesDir, "debug_moves"))

private class FileMoveRecorder(private val directory: File) : MoveRecorder {
    private val writer = Executors.newSingleThreadExecutor()

    override fun onCommitted(gameId: String, session: FreeCellSession, shownHint: Move?) = append(gameId, session, loggedAlready = (session.log.size - 1).coerceAtLeast(0)) {
        val index = session.log.lastIndex
        if (index < 0) return@append board(session.state)
        val entry = session.log[index]
        val followed = when {
            entry !is FreeCellLogEntry.PlayerMove -> ""
            shownHint == null -> " [no hint shown]"
            shownHint == entry.move -> " [followed hint]"
            else -> " [hint was ${describe(shownHint)}]"
        }
        "#$index ${describe(entry)}$followed moves=${session.state.moveCount}\n" + board(session.state)
    }

    override fun onHint(gameId: String, session: FreeCellSession, outcome: HintOutcome) = append(gameId, session, loggedAlready = session.log.size) {
        val result = if (outcome is HintOutcome.Guidance) describe(outcome.move) else outcome::class.simpleName
        "  hint -> $result (${outcome.nodes} nodes, ${outcome.elapsedMs} ms)\n"
    }

    private fun append(gameId: String, session: FreeCellSession, loggedAlready: Int, text: () -> String) {
        val body = text()
        writer.execute {
            directory.mkdirs()
            val file = File(directory, "$gameId.txt")
            if (!file.exists()) file.writeText(header(session, loggedAlready))
            file.appendText(body)
        }
    }

    /** Written once per game; a game first seen mid-way (restored from a save) gets its earlier log back-filled. */
    private fun header(session: FreeCellSession, loggedAlready: Int) = buildString {
        val state = session.state
        appendLine("seed=${state.seed} catalog=${state.versions.catalogVersion} rules=${state.versions.rulesVersion} shuffle=${state.versions.shuffleVersion}")
        session.log.take(loggedAlready).forEachIndexed { index, entry -> appendLine("#$index ${describe(entry)} [before recording]") }
    }

    private fun describe(entry: FreeCellLogEntry): String = when (entry) {
        is FreeCellLogEntry.PlayerMove -> describe(entry.move)
        FreeCellLogEntry.Undo -> "undo"
        is FreeCellLogEntry.SetAutomaticMoves -> "automatic moves ${if (entry.enabled) "on" else "off"}"
        FreeCellLogEntry.AutoFinish -> "automatic finish"
    }

    private fun describe(move: Move): String = when (move) {
        is Move.TableauToTableau -> "col${move.fromColumn}[${move.fromIndex}] -> col${move.toColumn}"
        is Move.TableauToFreeCell -> "col${move.fromColumn} -> cell${move.cell}"
        is Move.TableauToFoundation -> "col${move.fromColumn} -> foundation"
        is Move.FreeCellToTableau -> "cell${move.cell} -> col${move.toColumn}"
        is Move.FreeCellToFoundation -> "cell${move.cell} -> foundation"
    }

    private fun board(state: FreeCellState) = buildString {
        append("  cells: ").appendLine(state.freeCells.joinToString(" ") { it?.let(::card) ?: "--" })
        append("  foundations: ").appendLine(state.foundations.entries.joinToString(" ") { "${it.key.name.first()}${it.value}" })
        state.tableau.forEachIndexed { index, column -> appendLine("  col$index: ${column.joinToString(" ", transform = ::card)}") }
    }

    private fun card(card: Card): String {
        val rank = when (card.rank) {
            Rank.ACE -> "A"
            Rank.TEN -> "T"
            Rank.JACK -> "J"
            Rank.QUEEN -> "Q"
            Rank.KING -> "K"
            else -> card.rank.value.toString()
        }
        return rank + card.suit.name.first()
    }
}
