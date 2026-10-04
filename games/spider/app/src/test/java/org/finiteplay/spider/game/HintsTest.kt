package org.finiteplay.spider.game

import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.dealGame
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.applyMove
import org.finiteplay.spider.rules.legalMoves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HintsTest {
    private val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    /** A board reached by play, so the positions tested are ones a game actually produces. */
    private fun playedBoard(seed: Long, moves: Int) =
        generateSequence(dealGame(seed = seed, versions = versions)) { state ->
            legalMoves(state).filterIsInstance<Move.TableauToTableau>().firstOrNull()
                ?.let { applyMove(state, it) }
        }.take(moves + 1).last()

    @Test
    fun `every hinted card really has a legal move`() {
        val state = playedBoard(seed = 5L, moves = 12)
        val hints = hintedCards(state)
        for (hint in hints) {
            assertTrue(
                "hinted ${hint.column}/${hint.index} must have somewhere to go",
                tapDestinations(state, hint.column, hint.index).isNotEmpty(),
            )
        }
    }

    @Test
    fun `every movable sub-stack in a column is hinted, not just the largest`() {
        val state = playedBoard(seed = 5L, moves = 12)
        val hints = hintedCards(state)
        for (column in state.tableau.indices) {
            val movable = state.tableau[column].indices
                .filter { tapDestinations(state, column, it).isNotEmpty() }
                .toSet()
            assertEquals(
                "column $column should hint every lift point that has a destination",
                movable,
                hints.filter { it.column == column }.map { it.index }.toSet(),
            )
        }
    }

    @Test
    fun `a card with nowhere to go is never hinted`() {
        val state = playedBoard(seed = 5L, moves = 12)
        val hinted = hintedCards(state)
        for (column in state.tableau.indices) {
            for (index in state.tableau[column].indices) {
                if (tapDestinations(state, column, index).isEmpty()) {
                    assertTrue(
                        "$column/$index has no destination and must not be hinted",
                        HintedCard(column, index) !in hinted,
                    )
                }
            }
        }
    }

    @Test
    fun `no column with a move is left unhinted`() {
        val state = playedBoard(seed = 5L, moves = 12)
        val hinted = hintedCards(state).map { it.column }.toSet()
        val columnsWithMoves = legalMoves(state).filterIsInstance<Move.TableauToTableau>()
            .map { it.fromColumn }.toSet()
        assertEquals(columnsWithMoves, hinted)
    }

    @Test
    fun `a won game hints nothing`() {
        val state = playedBoard(seed = 5L, moves = 4)
        val won = state.copy(status = org.finiteplay.spider.layout.GameStatus.WON)
        assertTrue(hintedCards(won).isEmpty())
    }
}
