package org.finiteplay.klondike.session

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.GameStateFixtures
import org.finiteplay.klondike.board.GameStateFixtures.faceUp
import org.finiteplay.klondike.board.GameStateFixtures.withFoundation
import org.finiteplay.klondike.board.GameStateFixtures.withTableauColumn
import org.finiteplay.klondike.board.GameStatus
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.klondike.rules.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionTest {

    private val versions = GameStateFixtures.TEST_VERSIONS

    /** A start state with a hand-built board so tests don't depend on a real deal. */
    private fun sessionWith(state: GameState) = GameSession.of(state)

    @Test
    fun `undo restores the board but keeps counted moves, then adds one`() {
        // One player move (Waste to Tableau, +1) followed by two automatic transfers
        // (+2) reaches move count 3; undo must land on 4, per the EXECUTION_PLAN example.
        val movedCard = Card(Suit.SPADES, Rank.SIX)
        val state = GameStateFixtures.emptyBoard(versions = versions)
            .withTableauColumn(0, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.CLUBS, Rank.ACE))))
            .withTableauColumn(2, listOf(faceUp(Card(Suit.HEARTS, Rank.ACE))))
            .copy(waste = listOf(movedCard))
        var session = sessionWith(state)

        session = session.commitMove(Move.WasteToTableau(toColumn = 0))

        assertEquals(3, session.state.moveCount)
        assertEquals(1, session.undoStack.size)

        session = session.undo()

        assertEquals(4, session.state.moveCount)
        assertEquals(listOf(movedCard), session.state.waste)
        assertEquals(Card(Suit.HEARTS, Rank.SEVEN), session.state.tableau[0].last().card)
        // The automatic transfers are reverted along with the player move.
        assertEquals(0, session.state.foundations.getValue(Suit.CLUBS))
        assertEquals(0, session.state.foundations.getValue(Suit.HEARTS))
    }

    @Test
    fun `undo is unavailable once the win is recorded`() {
        val state = GameStateFixtures.emptyBoard(versions = versions).copy(status = GameStatus.WON)
        val session = GameSession.of(state, undoStack = listOf(state))

        assertFalse(session.canUndo)
        assertEquals(session, session.undo())
    }

    @Test
    fun `start deals the raw board untouched, regardless of the automatic-moves setting`() {
        // Automation never runs before the player's first action, even when the
        // setting starts enabled: the first cascade (if any) happens as part of the
        // player's own first move (`docs/games/klondike/DESIGN.md` "Automatic Foundation Moves").
        val raw = dealGame(seed = 7L, versions = versions)
        val enabled = GameSession.start(seed = 7L, versions = versions, automaticMovesEnabled = true)
        val disabled = GameSession.start(seed = 7L, versions = versions, automaticMovesEnabled = false)

        assertEquals(raw, enabled.state)
        assertEquals(raw, disabled.state)
        assertTrue(enabled.undoStack.isEmpty())
        assertTrue(enabled.log.isEmpty())
        assertFalse(enabled.hasPlayerActed)
        assertFalse(enabled.canUndo)
    }

    @Test
    fun `a player move marks the game as played`() {
        val state = GameStateFixtures.emptyBoard(versions = versions).copy(waste = listOf(Card(Suit.CLUBS, Rank.ACE)))
        var session = sessionWith(state)

        assertFalse(session.hasPlayerActed)
        session = session.commitMove(Move.WasteToFoundation)
        assertTrue(session.hasPlayerActed)
    }

    @Test
    fun `replaying the log reproduces an identical session, including undo and an automation toggle`() {
        val movedCard = Card(Suit.SPADES, Rank.SIX)
        fun freshState() = GameStateFixtures.emptyBoard(versions = versions)
            .withTableauColumn(0, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.CLUBS, Rank.ACE))))
            .copy(waste = listOf(movedCard, Card(Suit.HEARTS, Rank.ACE)))

        var original = sessionWith(freshState())
        original = original.commitMove(Move.WasteToTableau(toColumn = 0))
        original = original.undo()
        original = original.withAutomaticMoves(false)
        original = original.commitMove(Move.WasteToTableau(toColumn = 0))

        var replayed = sessionWith(freshState())
        for (entry in original.log) {
            replayed = when (entry) {
                is LogEntry.PlayerMove -> replayed.commitMove(entry.move)
                is LogEntry.Undo -> replayed.undo()
                is LogEntry.SetAutomaticMoves -> replayed.withAutomaticMoves(entry.enabled)
                is LogEntry.AutoFinish -> replayed.commitAutoFinish()
            }
        }

        assertEquals(original.state, replayed.state)
        assertEquals(original.undoStack, replayed.undoStack)
        assertEquals(original.log, replayed.log)
        assertEquals(original.automaticMovesEnabled, replayed.automaticMovesEnabled)
    }

    @Test
    fun `replaySession from a real deal reproduces the same session as manual replay`() {
        val log = listOf(LogEntry.SetAutomaticMoves(false))
        val direct = GameSession.start(42L, versions, automaticMovesEnabled = true).withAutomaticMoves(false)
        val replayed = replaySession(42L, versions, initialAutomaticMovesEnabled = true, log)

        assertEquals(direct.state, replayed.state)
        assertEquals(direct.log, replayed.log)
    }

    @Test
    fun `commitAutoFinish clears the undo stack and records one log entry`() {
        var state = GameStateFixtures.emptyBoard(versions = versions)
        for (suit in Suit.entries) state = state.withFoundation(suit = suit, topRankValue = Rank.QUEEN.value)
        state = state.withTableauColumn(0, listOf(faceUp(Card(Suit.CLUBS, Rank.KING))))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.DIAMONDS, Rank.KING))))
            .withTableauColumn(2, listOf(faceUp(Card(Suit.HEARTS, Rank.KING))))
            .withTableauColumn(3, listOf(faceUp(Card(Suit.SPADES, Rank.KING))))
        var session = GameSession.of(state, undoStack = listOf(state))

        session = session.commitAutoFinish()

        assertEquals(GameStatus.WON, session.state.status)
        assertTrue(session.undoStack.isEmpty())
        assertEquals(listOf(LogEntry.AutoFinish), session.log)
        assertFalse(session.canUndo)
    }

    @Test
    fun `hint cursor advances and wraps, and resets on a committed move`() {
        val state = GameStateFixtures.emptyBoard(versions = versions)
            .withTableauColumn(3, listOf(faceUp(Card(Suit.CLUBS, Rank.ACE))))
            .copy(waste = listOf(Card(Suit.HEARTS, Rank.ACE), Card(Suit.CLUBS, Rank.TWO)))
        var session = sessionWith(state)

        val first = session.currentHint()
        session = session.nextHint()
        val second = session.currentHint()
        session = session.nextHint()
        val wrapped = session.currentHint()

        assertEquals(first, wrapped)
        assertTrue(first != second)

        session = session.commitMove(requireNotNull(first))
        assertEquals(0, session.hintCursor)
    }
}
