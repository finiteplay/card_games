package org.finiteplay.freecell.solver

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.freecell.layout.FREE_CELLS
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.GameStatus
import org.finiteplay.freecell.layout.GameVersions
import org.finiteplay.freecell.layout.TABLEAU_COLUMNS
import org.finiteplay.freecell.rules.Move
import org.finiteplay.freecell.rules.applyMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [HintEngine]'s certificate cache is what makes following hint after hint walk one continuous
 * plan, mirroring Klondike's own stateful `HintEngine` (`docs/games/freecell/DESIGN.md` "Hint").
 */
class HintEngineTest {
    private val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    private fun fixture(
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

    /**
     * A red Five blocks a bankable Queen of clubs; parking the Five, then banking the Queen, then
     * the King, is the only three-move win from here — long enough to exercise the cache across
     * more than one follow-up hint.
     */
    private fun threeMoveFixture() = fixture(
        tableau = List(TABLEAU_COLUMNS) { index ->
            when (index) {
                1 -> listOf(Card(Suit.CLUBS, Rank.QUEEN), Card(Suit.HEARTS, Rank.FIVE))
                2 -> listOf(Card(Suit.CLUBS, Rank.KING))
                else -> emptyList()
            }
        },
        foundations = mapOf(Suit.CLUBS to 11, Suit.DIAMONDS to 13, Suit.HEARTS to 13, Suit.SPADES to 13),
    )

    @Test
    fun `a followed hint resolves the next step from the cache with no search`() {
        val engine = HintEngine()
        var state = threeMoveFixture()

        val first = engine.hint(state)
        check(first is HintOutcome.Guidance) { "expected Guidance, got $first" }
        assertTrue("the first hint of a game must actually search", first.nodes > 0)
        state = applyMove(state, first.move)

        val second = engine.hint(state)
        check(second is HintOutcome.Guidance) { "expected Guidance, got $second" }
        assertEquals("a followed hint must resolve from the cache, not a fresh search", 0L, second.nodes)
        state = applyMove(state, second.move)

        val third = engine.hint(state)
        check(third is HintOutcome.Guidance) { "expected Guidance, got $third" }
        assertEquals("the third step must still come from the same cached line", 0L, third.nodes)
        state = applyMove(state, third.move)

        assertTrue("the cached line must actually reach a win", state.isWon)
    }

    @Test
    fun `deviating from the cached line forces a fresh search`() {
        val engine = HintEngine()
        var state = threeMoveFixture()

        val first = engine.hint(state)
        check(first is HintOutcome.Guidance)

        // Ignore the hint and park the Five in a different empty column instead — still a step
        // toward the same eventual win, but not the exact board the cached line expects next.
        val hintedDestination = (first.move as? Move.TableauToTableau)?.toColumn
        val fiveMove = Move.TableauToTableau(
            fromColumn = 1,
            fromIndex = 1,
            toColumn = (0 until TABLEAU_COLUMNS).first { it != 1 && it != 2 && it != hintedDestination },
        )
        state = applyMove(state, fiveMove)

        val afterDeviation = engine.hint(state)
        check(afterDeviation is HintOutcome.Guidance) { "expected Guidance, got $afterDeviation" }
        assertTrue("a board the cached line never visits must trigger a real search", afterDeviation.nodes > 0)
    }

    @Test
    fun `reset clears the cache even when the board would still match it`() {
        val engine = HintEngine()
        val state = threeMoveFixture()

        val first = engine.hint(state)
        check(first is HintOutcome.Guidance)

        engine.reset()

        val second = engine.hint(state)
        check(second is HintOutcome.Guidance) { "expected Guidance, got $second" }
        assertTrue("a reset engine must search again even for a board it has already solved", second.nodes > 0)
    }

    @Test
    fun `an undo back onto the cached line resolves from the cache again`() {
        val engine = HintEngine()
        val start = threeMoveFixture()

        val first = engine.hint(start)
        check(first is HintOutcome.Guidance)
        val afterFirst = applyMove(start, first.move)

        val second = engine.hint(afterFirst)
        check(second is HintOutcome.Guidance)
        assertEquals(0L, second.nodes)
        applyMove(afterFirst, second.move) // the player continues past it, moving on

        // An undo restores the exact prior board — still on the cached line, just back at an
        // earlier position on it, not necessarily the line's own start.
        val afterUndo = engine.hint(afterFirst)
        check(afterUndo is HintOutcome.Guidance) { "expected Guidance, got $afterUndo" }
        assertEquals("landing back on an earlier cached position must not force a re-search", 0L, afterUndo.nodes)
        assertEquals(second.move, afterUndo.move)
    }

    @Test
    fun `a primed known solution resolves the first hint from the cache with no search`() {
        val engine = HintEngine()
        val start = threeMoveFixture()

        // The exact three-move win `threeMoveFixture`'s own doc describes, as if it had been read
        // from a shipped `solutions.bin` instead of found by a live search.
        var state = start
        val knownSolution = mutableListOf<Move>()
        while (!state.isWon) {
            val move = engine.hint(state).let { (it as HintOutcome.Guidance).move }
            knownSolution += move
            state = applyMove(state, move)
        }
        engine.reset()

        engine.primeWithKnownSolution(start, knownSolution)
        val first = engine.hint(start)
        check(first is HintOutcome.Guidance) { "expected Guidance, got $first" }
        assertEquals("a primed solution must resolve with no search at all", 0L, first.nodes)
        assertEquals(knownSolution.first(), first.move)
    }

    @Test
    fun `priming with a solution that does not replay is ignored, not trusted`() {
        val engine = HintEngine()
        val start = threeMoveFixture()

        engine.primeWithKnownSolution(start, listOf(Move.FreeCellToFoundation(cell = 0))) // empty cell: illegal

        val first = engine.hint(start)
        check(first is HintOutcome.Guidance) { "expected Guidance, got $first" }
        assertTrue("an untrustworthy primed line must fall back to a real search", first.nodes > 0)
    }

    @Test
    fun `exactStateHash distinguishes two boards canonicalSearchHash treats as equivalent`() {
        // Column 0 and column 1 swapped is the same state for the search's own symmetry-collapsed
        // purposes, but a cached move naming an actual column index must not treat them alike.
        val a = fixture(
            tableau = List(TABLEAU_COLUMNS) { index ->
                when (index) {
                    0 -> listOf(Card(Suit.CLUBS, Rank.TWO))
                    1 -> listOf(Card(Suit.HEARTS, Rank.THREE))
                    else -> emptyList()
                }
            },
        )
        val b = fixture(
            tableau = List(TABLEAU_COLUMNS) { index ->
                when (index) {
                    0 -> listOf(Card(Suit.HEARTS, Rank.THREE))
                    1 -> listOf(Card(Suit.CLUBS, Rank.TWO))
                    else -> emptyList()
                }
            },
        )
        assertEquals(canonicalSearchHash(a), canonicalSearchHash(b))
        assertNotEquals(exactStateHash(a), exactStateHash(b))
    }
}
