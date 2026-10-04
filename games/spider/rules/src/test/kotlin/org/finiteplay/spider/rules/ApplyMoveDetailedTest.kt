package org.finiteplay.spider.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.spider.layout.GameStatus
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.layout.TableauCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

private fun boardOf(columns: List<List<Card>>, banked: Int): SpiderState {
    val tableau = (0 until TABLEAU_COLUMNS).map { i ->
        columns.getOrElse(i) { emptyList() }.map { TableauCard(it, faceUp = true) }
    }
    return SpiderState(
        tableau = tableau,
        stock = emptyList(),
        banked = Suit.entries.associateWith { if (it == Suit.SPADES) banked else 0 },
        suitCount = SuitCount.ONE,
        seed = 0L,
        versions = VERSIONS,
        moveCount = 0,
        status = GameStatus.IN_PROGRESS,
    )
}

private fun spades(vararg ranks: Rank) = ranks.map { Card(Suit.SPADES, it) }

/**
 * [applyMoveDetailed] exists so an animation can know exactly which cards a move banked, in the
 * order the tableau held them, without diffing states or duplicating the reducer's own logic.
 */
class ApplyMoveDetailedTest {
    @Test
    fun `a move that banks nothing reports no banked runs`() {
        val state = boardOf(listOf(spades(Rank.KING), spades(Rank.QUEEN)), banked = 0)
        val outcome = applyMoveDetailed(state, Move.TableauToTableau(fromColumn = 1, fromIndex = 0, toColumn = 0))
        assertEquals(emptyList<BankedRun>(), outcome.bankedRuns)
    }

    @Test
    fun `completing a run reports it king-first, ace-last, from the column it banked in`() {
        // Column 0 holds King down to Two; column 1 holds the Ace alone. Moving the Ace onto the
        // Two completes the run in column 0.
        val descending = (Rank.KING.value downTo Rank.TWO.value)
            .map { Card(Suit.SPADES, Rank.entries.first { r -> r.value == it }) }
        val state = boardOf(listOf(descending, spades(Rank.ACE)), banked = 7)

        val outcome = applyMoveDetailed(state, Move.TableauToTableau(fromColumn = 1, fromIndex = 0, toColumn = 0))

        assertEquals(1, outcome.bankedRuns.size)
        val run = outcome.bankedRuns.single()
        assertEquals(Suit.SPADES, run.suit)
        assertEquals(0, run.column)
        assertEquals(Rank.KING, run.cards.first().rank)
        assertEquals(Rank.ACE, run.cards.last().rank)
        assertEquals(13, run.cards.size)
        assertEquals(GameStatus.WON, outcome.state.status)
    }

    @Test
    fun `a row deal can report more than one banked run at once`() {
        // Two columns each hold King down to Two and need only an Ace to complete — the dealt row
        // supplies it to both at once.
        val run = (Rank.KING.value downTo Rank.TWO.value)
            .map { Card(Suit.SPADES, Rank.entries.first { r -> r.value == it }) }
        val columns = List(TABLEAU_COLUMNS) { i ->
            when (i) {
                0, 5 -> run
                else -> listOf(Card(Suit.SPADES, Rank.KING)) // never completes; just fills the row deal
            }
        }
        val tableau = columns.map { col -> col.map { TableauCard(it, faceUp = true) } }
        val state = SpiderState(
            tableau = tableau,
            // An Ace completes columns 0 and 5; the rest are irrelevant filler that must not
            // itself complete anything.
            stock = (0 until TABLEAU_COLUMNS).map { i ->
                when (i) {
                    0, 5 -> Card(Suit.SPADES, Rank.ACE)
                    else -> Card(Suit.SPADES, Rank.KING)
                }
            },
            banked = Suit.entries.associateWith { if (it == Suit.SPADES) 6 else 0 },
            suitCount = SuitCount.ONE,
            seed = 0L,
            versions = VERSIONS,
            moveCount = 0,
            status = GameStatus.IN_PROGRESS,
        )

        val outcome = applyMoveDetailed(state, Move.DealRow)

        assertEquals(setOf(0, 5), outcome.bankedRuns.map { it.column }.toSet())
        assertTrue(outcome.bankedRuns.all { it.suit == Suit.SPADES && it.cards.size == 13 })
    }
}
