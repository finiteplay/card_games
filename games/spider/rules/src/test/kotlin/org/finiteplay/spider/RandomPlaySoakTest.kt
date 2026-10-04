package org.finiteplay.spider

import kotlin.random.Random
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.dealGame
import org.finiteplay.spider.rules.applyMove
import org.finiteplay.spider.rules.invariantViolations
import org.finiteplay.spider.rules.isStuck
import org.finiteplay.spider.rules.legalMoves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cheapest proof the reducer is sound: play thousands of random games and assert every
 * invariant after every move. A rule can be wrong in a way no hand-written board reaches — a
 * card duplicated only when a row deal completes two sequences at once, say — and this is what
 * finds it.
 */
class RandomPlaySoakTest {

    private val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    @Test
    fun `random play never breaks an invariant`() {
        for (seed in 1L..120L) {
            val suitCount = SuitCount.entries[(seed % 3).toInt()]
            var state = dealGame(seed, versions, suitCount)
            val random = Random(seed)

            repeat(400) {
                val moves = legalMoves(state)
                if (moves.isEmpty()) return@repeat
                val next = applyMove(state, moves[random.nextInt(moves.size)])

                val violations = invariantViolations(next)
                assertEquals("seed $seed at move ${next.moveCount}: $violations", emptyList<String>(), violations)
                assertEquals("a move must count once", state.moveCount + 1, next.moveCount)
                state = next
            }
        }
    }

    @Test
    fun `a deal always offers a move, and stuck means no move exists`() {
        for (seed in 1L..40L) {
            val state = dealGame(seed, versions)

            assertTrue("seed $seed deals a layout with nothing to do", legalMoves(state).isNotEmpty())
            assertTrue(!isStuck(state))
        }
    }

    @Test
    fun `the stock empties in exactly five row deals`() {
        // Playing nothing but row deals is legal as long as no column is empty, and a fresh
        // deal has none — so this reaches the end of the stock without any judgement.
        var state = dealGame(seed = 11L, versions = versions)
        var deals = 0
        while (true) {
            val move = legalMoves(state).filterIsInstance<org.finiteplay.spider.rules.Move.DealRow>().firstOrNull()
                ?: break
            state = applyMove(state, move)
            deals++
        }

        assertEquals(5, deals)
        assertTrue(state.stock.isEmpty())
        assertEquals(0, state.rowDealsRemaining)
        assertEquals(emptyList<String>(), invariantViolations(state))
    }
}
