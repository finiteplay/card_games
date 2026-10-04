package org.finiteplay.spider.game

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.spider.layout.GameStatus
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.layout.TableauCard
import org.finiteplay.spider.rules.findAutoFinish
import org.finiteplay.spider.rules.isAutoFinishAvailable
import org.junit.Assert.assertTrue
import org.junit.Test

private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

/**
 * The automatic finish is the only heavy computation the game screen runs on the main thread, and
 * it runs after every committed move. This measures its worst case: thirteen cards that cannot be
 * assembled, which is the position the search has to exhaust rather than solve.
 */
class AutoFinishCostTest {

    private fun boardOf(columns: List<List<Card>>): SpiderState = SpiderState(
        tableau = (0 until TABLEAU_COLUMNS).map { i ->
            columns.getOrElse(i) { emptyList() }.map { TableauCard(it, faceUp = true) }
        },
        stock = emptyList(),
        banked = Suit.entries.associateWith { if (it == Suit.SPADES) 7 else 0 },
        suitCount = SuitCount.ONE,
        seed = 0L,
        versions = VERSIONS,
        moveCount = 0,
        status = GameStatus.IN_PROGRESS,
    )

    @Test(timeout = 120_000)
    fun theUnsolvableEndgameIsNotSlowEnoughToFreezeTheInterface() {
        // Thirteen spades spread one per column, plus a small run: legal moves abound and none of
        // them ever completes the sequence, so the search cannot stop early.
        val ranks = listOf(
            Rank.KING, Rank.QUEEN, Rank.JACK, Rank.TEN, Rank.NINE, Rank.EIGHT,
            Rank.SEVEN, Rank.SIX, Rank.FIVE, Rank.FOUR,
        )
        val columns = ranks.map { listOf(Card(Suit.SPADES, it)) } +
            listOf(listOf(Card(Suit.SPADES, Rank.THREE), Card(Suit.SPADES, Rank.TWO), Card(Suit.SPADES, Rank.ACE)))
        val state = boardOf(columns)

        assertTrue("fixture must actually trigger the finish", isAutoFinishAvailable(state))

        // The fastest of a few runs: a wall-clock budget measured once also measures whatever else
        // the machine was doing (a parallel Gradle build, JIT warm-up), and that is what made this
        // fail once under load while passing alone.
        var moves: List<*>? = null
        val millis = (1..3).minOf {
            val started = System.nanoTime()
            moves = findAutoFinish(state)
            (System.nanoTime() - started) / 1_000_000
        }

        println("findAutoFinish on an unsolvable 13-card endgame: ${millis}ms, result=${moves?.size}")
        // The search no longer runs on the main thread, but its cost still bounds how long a
        // finish takes to appear, and an unbounded one was what froze the game. This is the
        // measured budget, not an aspiration: at the old 200,000-node ceiling this same position
        // took 767ms here.
        assertTrue("auto-finish took ${millis}ms on an unsolvable endgame", millis < 250)
    }
}
