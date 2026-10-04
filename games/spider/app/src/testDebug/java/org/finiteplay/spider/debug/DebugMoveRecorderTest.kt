package org.finiteplay.spider.debug

import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.isLegal
import org.finiteplay.spider.session.SpiderSession
import org.finiteplay.spider.session.commitMove
import org.finiteplay.spider.session.undo
import org.finiteplay.spider.solver.HintOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DebugMoveRecorderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val versions = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1)

    private fun firstLegalMove(session: SpiderSession): Move =
        (0 until 10).flatMap { from -> (0 until 10).map { to -> from to to } }
            .map { (from, to) -> Move.TableauToTableau(from, session.state.tableau[from].lastIndex, to) }
            .first { isLegal(session.state, it) }

    /** The recorder writes on its own thread; wait for [lines] entries rather than guessing a delay. */
    private fun entriesOf(file: File, lines: Int): List<String> {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (file.exists()) {
                val entries = file.readLines().filter { it.startsWith("#") || it.contains("hint ->") }
                if (entries.size >= lines) return entries
            }
            Thread.sleep(10)
        }
        return if (file.exists()) file.readLines().filter { it.startsWith("#") || it.contains("hint ->") } else emptyList()
    }

    @Test
    fun `records moves, undos and hints, and a restored game carries on in the same file`() {
        val files = folder.root
        val recorder = debugMoveRecorder(files)!!
        var session = SpiderSession.start(seed = 10046L, versions = versions, suitCount = SuitCount.TWO)
        recorder.onCommitted("game", session, null)
        val move = firstLegalMove(session)
        session = session.commitMove(move)
        recorder.onCommitted("game", session, move)
        recorder.onHint("game", session, HintOutcome.Inconclusive, 42)
        session = session.undo()
        recorder.onCommitted("game", session, null)

        val file = File(files, "debug_moves/game.txt")
        val first = entriesOf(file, 3)
        assertTrue(file.readLines().first().startsWith("seed=10046 suits=TWO"))
        assertTrue(first[0], first[0].startsWith("#0 ") && first[0].contains("[followed hint]"))
        assertTrue(first[1], first[1].contains("hint -> inconclusive (42 ms)"))
        assertTrue(first[2], first[2].startsWith("#1 ") && first[2].contains("undo"))

        // A new process: a fresh recorder sees the restored game and must not repeat its entries.
        val restored = debugMoveRecorder(files)!!
        restored.onCommitted("game", session, null)
        session = session.commitMove(DealRow)
        restored.onCommitted("game", session, null)
        val after = entriesOf(file, 4)
        assertEquals(4, after.size)
        assertTrue(after[3], after[3].startsWith("#2 ") && after[3].contains("deal row"))
    }

    @Test
    fun `a game id reused for a different deal starts its file over`() {
        val recorder = debugMoveRecorder(folder.root)!!
        var old = SpiderSession.start(seed = 1L, versions = versions, suitCount = SuitCount.TWO)
        old = old.commitMove(firstLegalMove(old))
        recorder.onCommitted("next", old, null)
        recorder.onCommitted("next", SpiderSession.start(seed = 2L, versions = versions, suitCount = SuitCount.TWO), null)

        val file = File(folder.root, "debug_moves/next.txt")
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && !(file.exists() && file.readLines().first().startsWith("seed=2 "))) Thread.sleep(10)
        assertTrue(file.readLines().first().startsWith("seed=2 "))
        assertTrue(file.readLines().none { it.startsWith("#") })
    }

    private companion object {
        val DealRow = Move.DealRow
    }
}
