package org.finiteplay.freecell.session

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.core.session.commit
import org.finiteplay.freecell.layout.FREE_CELLS
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.GameStatus
import org.finiteplay.freecell.layout.GameVersions
import org.finiteplay.freecell.layout.TABLEAU_COLUMNS
import org.finiteplay.freecell.rules.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FreeCellSessionTest {

    private val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    private fun stateWith(
        tableau: List<List<Card>> = List(TABLEAU_COLUMNS) { emptyList() },
        freeCells: List<Card?> = List(FREE_CELLS) { null },
        foundations: Map<Suit, Int> = Suit.entries.associateWith { 0 },
    ): FreeCellState = FreeCellState(
        seed = 0,
        versions = versions,
        tableau = tableau,
        freeCells = freeCells,
        foundations = foundations,
        status = GameStatus.IN_PROGRESS,
    )

    @Test
    fun `a fresh session deals the raw board with automation not yet run, even when enabled`() {
        // RULES.md "Automatic Foundation Moves": never before the player's first action. A deal
        // whose very first card is a safe ace would otherwise be auto-banked before the player
        // did anything, which start() must never do.
        val session = FreeCellSession.start(seed = 3L, versions = versions, automaticMovesEnabled = true)
        assertFalse(session.hasPlayerActed)
        // The raw deal is exactly dealGame's own output — nothing has run automation on it.
        assertEquals(0, session.state.foundations.values.sum())
    }

    @Test
    fun `a player move that triggers no cascade is one undo step that adds one move`() {
        val session = FreeCellSession.of(
            state = stateWith(
                tableau = List(TABLEAU_COLUMNS) { index -> if (index == 0) listOf(Card(Suit.SPADES, Rank.KING)) else emptyList() },
            ),
            automaticMovesEnabled = true,
        )

        val afterMove = session.commitMove(Move.TableauToTableau(0, 0, 1))
        assertEquals(1, afterMove.undoStack.size)
        assertEquals(1, afterMove.state.moveCount)

        val undone = afterMove.undo()
        assertEquals(session.state, undone.state.copy(moveCount = session.state.moveCount))
        assertEquals(2, undone.state.moveCount) // 1 counted, plus one for the undo itself
        assertFalse(undone.canUndo)
    }

    @Test
    fun `a player move whose cascade fires is still one undo step`() {
        // Parking an ace in a free cell makes it immediately safe, so this one player move
        // actually commits two transfers (the park, then the automatic bank) — undo must still
        // step back over the whole thing as one transaction.
        val session = FreeCellSession.of(
            state = stateWith(
                tableau = List(TABLEAU_COLUMNS) { index -> if (index == 0) listOf(Card(Suit.CLUBS, Rank.ACE)) else emptyList() },
            ),
            automaticMovesEnabled = true,
        )

        val afterMove = session.commitMove(Move.TableauToFreeCell(0, 0))
        assertEquals(1, afterMove.undoStack.size)
        assertEquals(2, afterMove.state.moveCount) // the park, plus the cascaded bank
        assertEquals(1, afterMove.state.foundations.getValue(Suit.CLUBS))

        val undone = afterMove.undo()
        assertEquals(session.state, undone.state.copy(moveCount = session.state.moveCount))
        assertEquals(3, undone.state.moveCount) // 2 counted, plus one for the undo itself
    }

    @Test
    fun `undo restores the board but not the counted moves, then adds one`() {
        // An ace commits (1 move) and immediately cascades a safe two behind it (1 more move) —
        // undo must restore the pre-transaction board while keeping both counted, then add one.
        val session = FreeCellSession.of(
            state = stateWith(
                tableau = listOf(
                    listOf(Card(Suit.CLUBS, Rank.ACE)),
                    listOf(Card(Suit.CLUBS, Rank.TWO)),
                    emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
                ),
            ),
            automaticMovesEnabled = true,
        )

        val afterMove = session.commitMove(Move.TableauToFoundation(0))
        assertEquals(2, afterMove.state.moveCount) // the player's move, plus one cascaded transfer
        assertEquals(2, afterMove.state.foundations.getValue(Suit.CLUBS))

        val undone = afterMove.undo()
        assertEquals(session.state.tableau, undone.state.tableau)
        assertEquals(session.state.foundations, undone.state.foundations)
        assertEquals(3, undone.state.moveCount) // 2 counted, plus one for the undo itself
    }

    @Test
    fun `disabled automation leaves a safe card exactly where the player put it`() {
        val session = FreeCellSession.of(
            state = stateWith(
                tableau = listOf(
                    listOf(Card(Suit.CLUBS, Rank.ACE)),
                    emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
                ),
            ),
            automaticMovesEnabled = false,
        )

        val next = session.commitMove(Move.TableauToFreeCell(0, 0))

        assertEquals(0, next.state.foundations.getValue(Suit.CLUBS))
        assertEquals(Card(Suit.CLUBS, Rank.ACE), next.state.freeCells[0])
    }

    @Test
    fun `undo does nothing on a session with nothing to undo`() {
        val session = FreeCellSession.start(seed = 1L, versions = versions, automaticMovesEnabled = true)
        assertEquals(session, session.undo())
    }

    @Test
    fun `replaying a log reproduces an identical session`() {
        val session = FreeCellSession.start(seed = 9L, versions = versions, automaticMovesEnabled = true)
            .commitMove(Move.TableauToFreeCell(0, 0))
            .let { it.commitMove(Move.TableauToFreeCell(1, 1)) }
            .undo()

        val replayed = replayFreeCellSession(seed = 9L, versions = versions, initialAutomaticMovesEnabled = true, log = session.log)

        assertEquals(session.state, replayed.state)
        assertEquals(session.undoStack, replayed.undoStack)
    }

    @Test
    fun `a won session cannot be undone even though boards remain on the stack`() {
        val session = FreeCellSession.of(
            state = stateWith(foundations = Suit.entries.associateWith { 12 }),
            undoStack = listOf(stateWith()),
        )
        val won = session.copy(core = session.core.commit(session.state.copy(status = GameStatus.WON), FreeCellLogEntry.AutoFinish))
        assertFalse(won.canUndo)
    }
}
