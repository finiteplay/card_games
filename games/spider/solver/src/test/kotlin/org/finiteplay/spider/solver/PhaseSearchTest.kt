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
import org.finiteplay.spider.layout.dealGame
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.applyMove
import org.finiteplay.spider.rules.isLegal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PhaseSearch] proves nothing on its own — its lines are trusted only after a replay — so what
 * these pin is that what it returns really replays to a win on the real reducer, that it wins the
 * boards it exists for, and that it gives the time back when it cannot.
 */
class PhaseSearchTest {
    private val versions = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1)

    private fun assertWins(start: SpiderState, line: List<Move>?) {
        assertNotNull("expected a line", line)
        var state = start
        for (move in line!!) {
            assertTrue("illegal move $move", isLegal(state, move))
            state = applyMove(state, move)
        }
        assertTrue("line must replay to a real win", state.isWon)
    }

    @Test
    fun `wins a certified two-suit opening the beam-and-playout search cannot`() {
        // TWO catalog deal 3: SpiderSolver at the hint budget fails it from the opening.
        val start = dealGame(10052L, versions, SuitCount.TWO)
        assertWins(start, PhaseSearch().certify(start, maxMillis = 30_000))
    }

    @Test
    fun `wins a two-suit board a player reached off the shipped line`() {
        val start = HintVerdictTest.offLineBoard(versions)
        assertWins(start, PhaseSearch().certify(start, maxMillis = 30_000))
    }

    @Test
    fun `wins a one-suit opening`() {
        val start = dealGame(1L, versions, SuitCount.ONE)
        assertWins(start, PhaseSearch().certify(start, maxMillis = 30_000))
    }

    @Test
    fun `gives up at once on a board with no move, instead of widening until the deadline`() {
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
        val started = System.nanoTime()
        assertNull(PhaseSearch().certify(dead, maxMillis = 10_000))
        assertTrue("took ${(System.nanoTime() - started) / 1_000_000}ms", System.nanoTime() - started < 2_000_000_000L)
    }

    @Test
    fun `stops near its deadline`() {
        // A four-suit opening: far beyond what a few hundred milliseconds can win.
        val start = dealGame(1L, versions, SuitCount.FOUR)
        val started = System.nanoTime()
        PhaseSearch().certify(start, maxMillis = 300)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("ran ${elapsedMs}ms on a 300ms budget", elapsedMs < 1_500)
    }

    @Test
    fun `the deal key ignores column order but not suits`() {
        val board = FastBoard.from(dealGame(5L, versions, SuitCount.TWO))
        val key = dealKeyOf(board)

        val swapped = board.copy()
        val first = swapped.cards[0].copyOf(); val firstLen = swapped.len[0]; val firstDown = swapped.faceDown[0]
        System.arraycopy(swapped.cards[7], 0, swapped.cards[0], 0, swapped.len[7])
        swapped.len[0] = swapped.len[7]; swapped.faceDown[0] = swapped.faceDown[7]
        System.arraycopy(first, 0, swapped.cards[7], 0, firstLen)
        swapped.len[7] = firstLen; swapped.faceDown[7] = firstDown
        swapped.markAllColumnsDirty()
        assertEquals(key, dealKeyOf(swapped))

        val resuited = board.copy()
        val top = resuited.len[0] - 1
        val card = resuited.cards[0][top].toInt()
        resuited.cards[0][top] = ((card + FastBoard.RANKS) % (2 * FastBoard.RANKS)).toByte()
        resuited.markAllColumnsDirty()
        assertNotEquals(key, dealKeyOf(resuited))
    }
}
