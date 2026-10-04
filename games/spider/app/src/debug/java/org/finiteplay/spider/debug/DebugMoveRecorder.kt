package org.finiteplay.spider.debug

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.session.SpiderLogEntry
import org.finiteplay.spider.session.SpiderSession
import org.finiteplay.spider.solver.HintOutcome
import org.finiteplay.spider.ui.game.MoveRecorder
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * Debug-only transcript of every game — moves, undos, row deals, and every hint asked for — one
 * append-only text file per game id, so real play can be pulled and analysed
 * (`games/spider/tools/pull-debug-moves.sh`). The header names the seed, suit count and versions,
 * so the log alone reproduces every board; the board is written after each entry too, as the player
 * saw it (face-down cards hidden), for reading. Never networked; absent from release by source-set
 * separation (`src/release`). Mirrors FreeCell's own debug recorder.
 */
fun debugMoveRecorder(filesDir: File): MoveRecorder? = FileMoveRecorder(File(filesDir, "debug_moves"))

private class FileMoveRecorder(private val directory: File) : MoveRecorder {
    /** All bookkeeping and IO happens here, in order, so nothing below needs a lock. */
    private val writer = Executors.newSingleThreadExecutor()

    private class Recorded(val seed: Long, var entries: Int)

    private val recorded = HashMap<String, Recorded>()

    override fun onCommitted(gameId: String, session: SpiderSession, shownHint: Move?) {
        val at = LocalTime.now()
        writer.execute { record(gameId, session, shownHint, at) }
    }

    override fun onHint(gameId: String, session: SpiderSession, outcome: HintOutcome, elapsedMs: Long) {
        val at = LocalTime.now()
        writer.execute {
            record(gameId, session, null, at)
            val result = when (outcome) {
                is HintOutcome.Guidance -> describe(outcome.move)
                HintOutcome.NoSolution -> "no solution"
                HintOutcome.Inconclusive -> "inconclusive"
            }
            File(directory, "$gameId.txt").appendText("  ${TIME.format(at)} hint -> $result ($elapsedMs ms)\n")
        }
    }

    /**
     * Appends whatever of [session]'s log is not yet in [gameId]'s file. A game id first seen in
     * this process (one restored from a save) picks up from what its file already holds; one whose
     * file holds a different deal — New Game names the new game before its deal lands — starts over.
     */
    private fun record(gameId: String, session: SpiderSession, shownHint: Move?, at: LocalTime) {
        directory.mkdirs()
        val file = File(directory, "$gameId.txt")
        val state = session.state
        var known = recorded[gameId] ?: readExisting(file)
        if (known != null && (known.seed != state.seed || known.entries > session.log.size)) known = null
        if (known == null) {
            file.writeText(header(state))
            known = Recorded(state.seed, 0)
            if (session.log.isEmpty()) file.appendText(board(state))
        }
        recorded[gameId] = known
        val log = session.log
        if (known.entries >= log.size) return
        val text = StringBuilder()
        for (index in known.entries until log.size) {
            val entry = log[index]
            if (index < log.lastIndex) {
                text.append("#$index ${describe(entry)} [before recording]\n")
                continue
            }
            val followed = when {
                entry !is SpiderLogEntry.PlayerMove -> ""
                shownHint == null -> ""
                shownHint == entry.move -> " [followed hint]"
                else -> " [hint was ${describe(shownHint)}]"
            }
            text.append("#$index ${TIME.format(at)} ${describe(entry)}$followed moves=${state.moveCount}\n")
            text.append(board(state))
            if (state.isWon) text.append("WON\n")
        }
        known.entries = log.size
        file.appendText(text.toString())
    }

    private fun readExisting(file: File): Recorded? {
        if (!file.exists()) return null
        val lines = file.readLines()
        val seed = lines.firstOrNull()?.substringAfter("seed=", "")?.substringBefore(' ')?.toLongOrNull() ?: return null
        return Recorded(seed, lines.count { it.startsWith("#") })
    }

    private fun header(state: SpiderState) =
        "seed=${state.seed} suits=${state.suitCount} catalog=${state.versions.catalogVersion} " +
            "rules=${state.versions.rulesVersion} shuffle=${state.versions.shuffleVersion} " +
            "started=${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}\n"

    private fun describe(entry: SpiderLogEntry): String = when (entry) {
        is SpiderLogEntry.PlayerMove -> describe(entry.move)
        SpiderLogEntry.Undo -> "undo"
    }

    private fun describe(move: Move): String = when (move) {
        is Move.TableauToTableau -> "col${move.fromColumn}[${move.fromIndex}] -> col${move.toColumn}"
        Move.DealRow -> "deal row"
    }

    /** The board as the player sees it: one `-` per face-down card, then the face-up cards. */
    private fun board(state: SpiderState) = buildString {
        appendLine("  stock=${state.stock.size} banked=${state.sequencesBanked}")
        state.tableau.forEachIndexed { index, column ->
            append("  col$index: ")
            append("-".repeat(column.count { !it.faceUp }))
            column.filter { it.faceUp }.forEach { append(' ').append(card(it.card)) }
            appendLine()
        }
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

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    }
}
