package org.finiteplay.spider.solver

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
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The interactive hint budget turns the full-legal DFS off, so the only search left that could end
 * in an empty tree is the beam — which never looks at most of each board's moves. Running out of
 * beam is therefore not a proof, and must not surface as "No solution" on a board that is winnable.
 */
class HintVerdictTest {
    private val versions = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1)

    @Test
    fun `running out of beam on a winnable board is not a proof of no solution`() {
        val outcome = SpiderSolver(HINT_SOLVER_LIMITS).solve(offLineBoard(versions))
        assertFalse("winnable board reported ${outcome.result}", outcome.provedUnsolvable)
    }

    @Test
    fun `a board with no legal move at all is still proved unsolvable`() {
        val aces = (0 until TABLEAU_COLUMNS).map { listOf(TableauCard(Card(Suit.SPADES, Rank.ACE), faceUp = true)) }
        val dead = SpiderState(
            seed = 0L,
            versions = versions,
            suitCount = SuitCount.TWO,
            tableau = aces,
            stock = emptyList(),
            banked = Suit.entries.associateWith { 0 },
            status = GameStatus.IN_PROGRESS,
        )
        assertEquals(SolveResult.EXHAUSTED, SpiderSolver(HINT_SOLVER_LIMITS).solve(dead).result)
    }

    companion object {
        /**
         * TWO catalog deal 1 (seed 10046), part-way along its shipped line and then six plausible
         * moves off it: all five rows dealt, two suits banked. Winnable — a 67-move line was found and
         * replayed from it — yet the hint budget's beam ran dry on it after ~9,000 nodes, and that
         * used to come back as a proof of no solution.
         */
        private val offLineText = """
            ~EIGHT:SPADES ~KING:HEARTS ~FOUR:SPADES TEN:SPADES NINE:HEARTS EIGHT:HEARTS EIGHT:HEARTS ACE:SPADES SEVEN:HEARTS SIX:HEARTS FIVE:HEARTS FOUR:HEARTS THREE:HEARTS TWO:HEARTS ACE:HEARTS
            TWO:HEARTS SIX:HEARTS FIVE:HEARTS FOUR:HEARTS
            KING:HEARTS KING:SPADES THREE:SPADES THREE:SPADES NINE:SPADES EIGHT:SPADES SEVEN:SPADES SIX:SPADES FIVE:SPADES NINE:SPADES
            ~TWO:SPADES ~SEVEN:HEARTS ~KING:SPADES SEVEN:HEARTS
            ~FOUR:SPADES KING:SPADES QUEEN:SPADES TEN:SPADES QUEEN:SPADES JACK:SPADES TEN:HEARTS NINE:HEARTS
            ~FOUR:HEARTS KING:HEARTS
            ~ACE:HEARTS ~SEVEN:SPADES SEVEN:SPADES SIX:SPADES FIVE:SPADES FOUR:SPADES THREE:SPADES TWO:SPADES ACE:SPADES EIGHT:SPADES ACE:SPADES QUEEN:HEARTS JACK:HEARTS TEN:HEARTS NINE:SPADES EIGHT:HEARTS SIX:HEARTS
            ~QUEEN:SPADES ~THREE:HEARTS NINE:HEARTS QUEEN:HEARTS JACK:SPADES
            ~TWO:SPADES ~JACK:HEARTS ~JACK:SPADES TWO:HEARTS ACE:HEARTS SIX:SPADES FIVE:SPADES FIVE:HEARTS TEN:HEARTS TEN:SPADES
            ~THREE:HEARTS ~JACK:HEARTS QUEEN:HEARTS
        """.trimIndent()

        fun offLineBoard(versions: GameVersions): SpiderState = SpiderState(
            seed = 10046L,
            versions = versions,
            suitCount = SuitCount.TWO,
            tableau = offLineText.lines().map { line ->
                line.trim().split(' ').map { token ->
                    val (rank, suit) = token.removePrefix("~").split(':')
                    TableauCard(Card(Suit.valueOf(suit), Rank.valueOf(rank)), faceUp = !token.startsWith("~"))
                }
            },
            stock = emptyList(),
            banked = Suit.entries.associateWith { if (it == Suit.SPADES || it == Suit.HEARTS) 1 else 0 },
            status = GameStatus.IN_PROGRESS,
        )
    }
}
