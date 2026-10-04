package org.finiteplay.freecell.solver

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.freecell.layout.FREE_CELLS
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.GameStatus
import org.finiteplay.freecell.layout.GameVersions
import org.finiteplay.freecell.layout.TABLEAU_COLUMNS
import org.finiteplay.freecell.layout.dealGame
import org.finiteplay.freecell.rules.Move
import org.finiteplay.freecell.rules.applyMove
import org.finiteplay.freecell.rules.isLegal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolverTest {
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

    private fun replayAndAssertWon(start: FreeCellState, certificate: List<Move>) {
        var state = start
        for (move in certificate) {
            check(isLegal(state, move)) { "certificate step $move illegal against $state" }
            state = applyMove(state, move)
        }
        assertTrue("certificate did not reach a won state", state.isWon)
    }

    @Test
    fun `solve finds a winning line when the last suit is already exposed in playable order`() {
        // Three foundations already complete; Hearts needs exactly four more, all in one column
        // in peel order (King at the bottom, Ten on top) with nothing else on the board at all.
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index ->
                if (index == 0) {
                    listOf(
                        Card(Suit.HEARTS, Rank.KING),
                        Card(Suit.HEARTS, Rank.QUEEN),
                        Card(Suit.HEARTS, Rank.JACK),
                        Card(Suit.HEARTS, Rank.TEN),
                    )
                } else {
                    emptyList()
                }
            },
            foundations = mapOf(Suit.CLUBS to 13, Suit.DIAMONDS to 13, Suit.SPADES to 13, Suit.HEARTS to 9),
        )

        val outcome = DepthFirstSolver.solve(state)

        check(outcome is SolveOutcome.Solved) { "expected a solution, got $outcome" }
        assertTrue(outcome.certificate.size <= 4)
        replayAndAssertWon(state, outcome.certificate)
    }

    @Test
    fun `solve reports Unsolved for a genuinely dead position`() {
        // Every tableau column holds one black card, no two of which can ever stack on each
        // other (building requires the opposite colour), every free cell is full of another
        // black card for the same reason, and nothing is foundation-eligible (foundations empty,
        // no card is an ace) — no tableau move, no free-cell move, no foundation move, ever.
        val state = fixture(
            tableau = listOf(
                listOf(Card(Suit.CLUBS, Rank.TWO)),
                listOf(Card(Suit.CLUBS, Rank.THREE)),
                listOf(Card(Suit.CLUBS, Rank.FOUR)),
                listOf(Card(Suit.CLUBS, Rank.FIVE)),
                listOf(Card(Suit.CLUBS, Rank.SIX)),
                listOf(Card(Suit.CLUBS, Rank.SEVEN)),
                listOf(Card(Suit.CLUBS, Rank.EIGHT)),
                listOf(Card(Suit.CLUBS, Rank.NINE)),
            ),
            freeCells = listOf(
                Card(Suit.SPADES, Rank.TWO),
                Card(Suit.SPADES, Rank.THREE),
                Card(Suit.SPADES, Rank.FOUR),
                Card(Suit.SPADES, Rank.FIVE),
            ),
        )

        val outcome = DepthFirstSolver.solve(state)

        assertTrue("expected Unsolved, got $outcome", outcome is SolveOutcome.Unsolved)
    }

    @Test
    fun `solve is deterministic across repeated calls on the same board`() {
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index ->
                if (index == 0) {
                    listOf(
                        Card(Suit.HEARTS, Rank.KING),
                        Card(Suit.HEARTS, Rank.QUEEN),
                        Card(Suit.HEARTS, Rank.JACK),
                        Card(Suit.HEARTS, Rank.TEN),
                    )
                } else {
                    emptyList()
                }
            },
            foundations = mapOf(Suit.CLUBS to 13, Suit.DIAMONDS to 13, Suit.SPADES to 13, Suit.HEARTS to 9),
        )

        val first = DepthFirstSolver.solve(state)
        val second = DepthFirstSolver.solve(state)

        check(first is SolveOutcome.Solved && second is SolveOutcome.Solved)
        assertEquals(first.certificate, second.certificate)
    }

    @Test
    fun `a root previousMove deprioritizes its exact inverse so hint-to-hint doesn't oscillate`() {
        // Column 1 holds a red Five blocking a bankable Queen of clubs underneath it; the Five
        // itself can never bank (hearts is already complete) so it must be parked in an empty
        // column first. Columns 0 and 3 are both empty and equally viable parking spots —
        // genuinely symmetric, both lead to the same win (expose the Queen, bank it, then the
        // King). Column 2's King of clubs only becomes bankable once the Queen does.
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index ->
                when (index) {
                    1 -> listOf(Card(Suit.CLUBS, Rank.QUEEN), Card(Suit.HEARTS, Rank.FIVE))
                    2 -> listOf(Card(Suit.CLUBS, Rank.KING))
                    else -> emptyList()
                }
            },
            foundations = mapOf(Suit.CLUBS to 11, Suit.DIAMONDS to 13, Suit.HEARTS to 13, Suit.SPADES to 13),
        )
        // Hypothetically "the Five just arrived in column 1 from column 0" — its exact reversal
        // is moving it from column 1 straight back to column 0.
        val previousMove = Move.TableauToTableau(fromColumn = 0, fromIndex = 0, toColumn = 1)
        val inverse = Move.TableauToTableau(fromColumn = 1, fromIndex = 1, toColumn = 0)
        assertTrue("fixture must actually offer the inverse as a legal candidate", isLegal(state, inverse))

        val outcome = DepthFirstSolver.solve(state, rootPreviousMove = previousMove)

        check(outcome is SolveOutcome.Solved) { "expected a solution, got $outcome" }
        assertTrue("the very first move must not be the flagged inverse", outcome.certificate.first() != inverse)
        replayAndAssertWon(state, outcome.certificate)
    }

    /**
     * The one real-world check this suite needs beyond hand-built fixtures: whatever the solver
     * reports for a handful of real deals must be either an outright win or an honest failure to
     * find one, never a certificate that turns out illegal against the real reducer — the "never
     * validate itself" rule `docs/games/freecell/DEALS.md` "Generation" states for the real
     * generation pipeline, exercised here at a much smaller scale.
     */
    @Test
    fun `every certificate found for a handful of real deals replay-validates through the real reducer`() {
        for (seed in 1L..8L) {
            val state = dealGame(seed, versions)
            val outcome = solveOnLargeStack(state, SolverLimits(maxNodes = 500_000L, maxDurationMs = 5_000L))
            if (outcome is SolveOutcome.Solved) {
                replayAndAssertWon(state, outcome.certificate)
            }
        }
    }
}
