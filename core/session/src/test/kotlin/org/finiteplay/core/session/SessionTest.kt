package org.finiteplay.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the bookkeeping every game's session inherits, against a board small enough that the
 * rule under test is the only thing on screen.
 *
 * [Board] is a move count and a label because those are the two things the rules below actually
 * constrain: undo has to carry the count forward while restoring the label, and nothing else
 * here cares what a board is.
 */
class SessionTest {

    private data class Board(val label: String, val moveCount: Int)

    private sealed interface Entry {
        data class Play(val label: String) : Entry
        data object Undo : Entry
        data class Setting(val on: Boolean) : Entry
        data object Finish : Entry
    }

    private fun start(label: String = "dealt") = Session.start<Board, Entry>(Board(label, 0))

    /** The rule of `docs/PLATFORM.md`: restore the board, keep the count, add one. */
    private fun undoRestore(current: Board): (Board) -> Board =
        { prior -> prior.copy(moveCount = current.moveCount + 1) }

    private fun Session<Board, Entry>.play(label: String): Session<Board, Entry> =
        commit(Board(label, state.moveCount + 1), Entry.Play(label))

    @Test
    fun `a fresh session has nothing to undo and nothing logged`() {
        val session = start()

        assertFalse(session.canUndo)
        assertEquals(emptyList<Board>(), session.undoStack)
        assertEquals(emptyList<Entry>(), session.log)
    }

    @Test
    fun `committing advances the board, stacks the old one, and logs once`() {
        val session = start().play("a")

        assertEquals(Board("a", 1), session.state)
        assertEquals(listOf(Board("dealt", 0)), session.undoStack)
        assertEquals(listOf<Entry>(Entry.Play("a")), session.log)
        assertTrue(session.canUndo)
    }

    @Test
    fun `undo restores the board but keeps the counted moves, then adds one`() {
        // The rule this module exists to stop a second game from reimplementing wrongly:
        // three moves counted, undo returns the two-move board still counting four.
        val played = start().play("a").play("b").play("c")
        assertEquals(3, played.state.moveCount)

        val undone = played.undo(Entry.Undo, undoRestore(played.state))

        assertEquals("b", undone.state.label)
        assertEquals(4, undone.state.moveCount)
    }

    @Test
    fun `undo pops exactly one board and logs itself`() {
        val played = start().play("a").play("b")

        val undone = played.undo(Entry.Undo, undoRestore(played.state))

        assertEquals(listOf(Board("dealt", 0)), undone.undoStack)
        assertEquals(listOf(Entry.Play("a"), Entry.Play("b"), Entry.Undo), undone.log)
    }

    @Test
    fun `undo on a fresh session changes nothing at all`() {
        val session = start()

        val undone = session.undo(Entry.Undo) { error("restore must not be called") }

        assertEquals(session, undone)
    }

    @Test
    fun `undo is unlimited and walks the stack back to the deal`() {
        var session = start().play("a").play("b").play("c")

        while (session.canUndo) session = session.undo(Entry.Undo, undoRestore(session.state))

        assertEquals("dealt", session.state.label)
        assertFalse(session.canUndo)
    }

    @Test
    fun `recording an event logs it without touching the board or the stack`() {
        val played = start().play("a")

        val recorded = played.record(Entry.Setting(on = false))

        assertEquals(played.state, recorded.state)
        assertEquals(played.undoStack, recorded.undoStack)
        assertEquals(listOf(Entry.Play("a"), Entry.Setting(false)), recorded.log)
    }

    @Test
    fun `finishing advances the board and clears the undo stack`() {
        val played = start().play("a").play("b")

        val finished = played.finish(Board("won", 9), Entry.Finish)

        assertEquals(Board("won", 9), finished.state)
        assertFalse(finished.canUndo)
        assertEquals(listOf(Entry.Play("a"), Entry.Play("b"), Entry.Finish), finished.log)
    }

    @Test
    fun `replaying a log reproduces the session it came from`() {
        val original = start()
            .play("a")
            .play("b")
            .record(Entry.Setting(on = false))
            .let { it.undo(Entry.Undo, undoRestore(it.state)) }
            .play("c")

        val replayed = original.log.fold(start(), ::step)

        assertEquals(original, replayed)
    }

    @Test
    fun `replaying a replayed log is identical again`() {
        val original = start().play("a").let { it.undo(Entry.Undo, undoRestore(it.state)) }.play("b")

        val once = original.log.fold(start(), ::step)
        val twice = once.log.fold(start(), ::step)

        assertEquals(once, twice)
    }

    @Test
    fun `replaying an empty log yields the dealt session`() {
        assertEquals(start(), emptyList<Entry>().fold(start(), ::step))
    }

    /** A game's own dispatch over its entries — the part of replay that cannot be shared. */
    private fun step(session: Session<Board, Entry>, entry: Entry): Session<Board, Entry> = when (entry) {
        is Entry.Play -> session.play(entry.label)
        Entry.Undo -> session.undo(Entry.Undo, undoRestore(session.state))
        is Entry.Setting -> session.record(entry)
        Entry.Finish -> session.finish(Board("won", 9), entry)
    }
}
