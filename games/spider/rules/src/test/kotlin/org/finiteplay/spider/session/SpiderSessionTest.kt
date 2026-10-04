package org.finiteplay.spider.session

import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.legalMoves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SpiderSessionTest {

    private val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    private fun freshDeal(seed: Long = 1L, suitCount: SuitCount = SuitCount.FOUR) =
        SpiderSession.start(seed, versions, suitCount)

    @Test
    fun `a fresh session has nothing to undo and nothing logged`() {
        val session = freshDeal()

        assertFalse(session.canUndo)
        assertEquals(emptyList<SpiderState>(), session.undoStack)
        assertEquals(emptyList<SpiderLogEntry>(), session.log)
    }

    @Test
    fun `start deals the requested suit count, not always four`() {
        assertEquals(SuitCount.ONE, freshDeal(suitCount = SuitCount.ONE).state.suitCount)
        assertEquals(SuitCount.TWO, freshDeal(suitCount = SuitCount.TWO).state.suitCount)
    }

    @Test
    fun `committing a legal move advances the board and logs it once`() {
        val session = freshDeal()
        val move = legalMoves(session.state).first()

        val committed = session.commitMove(move)

        assertEquals(1, committed.state.moveCount)
        assertTrue(committed.canUndo)
        assertEquals(listOf<SpiderLogEntry>(SpiderLogEntry.PlayerMove(move)), committed.log)
    }

    @Test
    fun `committing an illegal move throws, exactly as applyMove does`() {
        val session = freshDeal()
        // Column 0 onto itself is never legal, whatever the board looks like.
        val illegal = Move.TableauToTableau(fromColumn = 0, fromIndex = 0, toColumn = 0)

        assertThrows(IllegalArgumentException::class.java) { session.commitMove(illegal) }
    }

    @Test
    fun `undo restores the board but keeps the counted moves, then adds one`() {
        var session = freshDeal()
        repeat(3) { session = session.commitMove(legalMoves(session.state).first()) }
        assertEquals(3, session.state.moveCount)
        val boardBeforeUndo = session.undoStack.last()

        val undone = session.undo()

        assertEquals(boardBeforeUndo.tableau, undone.state.tableau)
        assertEquals(boardBeforeUndo.stock, undone.state.stock)
        assertEquals(4, undone.state.moveCount)
    }

    @Test
    fun `undo pops exactly one board and logs itself`() {
        var session = freshDeal()
        session = session.commitMove(legalMoves(session.state).first())
        session = session.commitMove(legalMoves(session.state).first())

        val undone = session.undo()

        assertEquals(1, undone.undoStack.size)
        assertEquals(3, undone.log.size)
        assertEquals(SpiderLogEntry.Undo, undone.log.last())
    }

    @Test
    fun `undo on a fresh session is a no-op`() {
        val session = freshDeal()

        assertEquals(session, session.undo())
    }

    @Test
    fun `replaying the log reproduces an identical session`() {
        var original = freshDeal(seed = 7L, suitCount = SuitCount.TWO)
        repeat(5) { original = original.commitMove(legalMoves(original.state).first()) }
        original = original.undo()
        repeat(2) { original = original.commitMove(legalMoves(original.state).first()) }

        val replayed = replaySpiderSession(seed = 7L, versions = versions, log = original.log, suitCount = SuitCount.TWO)

        assertEquals(original, replayed)
    }

    @Test
    fun `replaying an empty log yields the dealt session`() {
        val dealt = freshDeal(seed = 3L)

        assertEquals(dealt, replaySpiderSession(seed = 3L, versions = versions, log = emptyList()))
    }
}
