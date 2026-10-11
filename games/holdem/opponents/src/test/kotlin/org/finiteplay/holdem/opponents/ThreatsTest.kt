package org.finiteplay.holdem.opponents

import org.finiteplay.cards.Card
import org.finiteplay.holdem.rules.HandCategory
import org.finiteplay.holdem.rules.Street
import org.finiteplay.holdem.rules.seatView
import org.finiteplay.holdem.rules.startTournament
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The odds the hint states about what the opponents may hold (`DESIGN.md` "Hint"). */
class ThreatsTest {
    private fun card(c: String): Card = Card.CANONICAL_DECK.first { it.suit.name[0] == c[1] && "A23456789TJQK"[it.rank.value - 1] == c[0] }

    private fun hintFor(hole: String, board: String): HintResult {
        val state = startTournament(3L).state
        val cards = board.split(' ').filter { it.isNotEmpty() }.map(::card)
        val street = when (cards.size) { 0 -> Street.PREFLOP; 3 -> Street.FLOP; 4 -> Street.TURN; else -> Street.RIVER }
        val view = state.seatView(state.toAct).copy(holeCards = hole.split(' ').map(::card), board = cards, street = street)
        return hint(view)
    }

    @Test
    fun `nothing is said before the flop`() {
        assertNull(hintFor("AS KS", "").threats)
    }

    @Test
    fun `three hearts on the board make a flush a real danger to a hand without one`() {
        val t = hintFor("2C 3D", "AH KH 9H").threats!!
        assertTrue("beats you ${t.beatsYou}", t.beatsYou > 0.5)
        val flush = t.best.firstOrNull { it.first == HandCategory.FLUSH }
        assertNotNull("a flush is possible on this board: ${t.best}", flush)
        assertTrue("flush ${flush!!.second}", flush.second in 0.02..0.5)
    }

    @Test
    fun `holding the best possible hand leaves nothing to fear`() {
        val t = hintFor("AH KH", "QH JH TH").threats!!
        assertEquals(0.0, t.beatsYou, 0.0)
        assertTrue(t.best.isEmpty())
    }

    @Test
    fun `a dry board offers few ways to be beaten but not none`() {
        val t = hintFor("2C 3D", "KS 7D 2H").threats!!
        assertTrue(t.best.none { it.first == HandCategory.FLUSH })
        assertTrue(t.best.size <= Threats.MAX_SHOWN)
        assertTrue(t.best.all { it.second >= Threats.MIN_SHOWN })
    }

    @Test
    fun `asking twice gives the same odds`() {
        assertEquals(hintFor("QS QD", "QH 7C 2D"), hintFor("QS QD", "QH 7C 2D"))
    }
}
