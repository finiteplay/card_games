package org.finiteplay.holdem.rules

import org.finiteplay.cards.Card
import org.finiteplay.holdem.rules.Fx.step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** `SeatView` is the only type the opponents see: nothing hidden may be readable from it. */
class SeatViewTest {
    /** The same hand with every card a seat cannot see replaced: the others' hole cards and the whole deck order. */
    private fun scrambled(state: HoldemState, seat: Int, random: Random): HoldemState {
        val others = Card.CANONICAL_DECK.filter { it !in state.holeCards[seat] }.shuffled(random)
        val holes = state.holeCards.mapIndexed { i, cards ->
            if (i == seat || cards.isEmpty()) cards else listOf(others[2 * i], others[2 * i + 1])
        }
        return state.copy(holeCards = holes, deck = Card.CANONICAL_DECK.shuffled(random))
    }

    @Test
    fun `states differing only in what a seat cannot see give that seat the same view`() {
        val random = Random(5)
        repeat(200) { n ->
            val state = dealHand(Tournament.start(n.toLong()))
            for (seat in 0 until 6) {
                val other = scrambled(state, seat, random)
                assertNotEquals(state, other)
                assertEquals("hand $n seat $seat", state.seatView(seat), other.seatView(seat))
            }
        }
    }

    @Test
    fun `views stay equal as actions are taken, through a hand that ends without a showdown`() {
        val random = Random(6)
        repeat(100) { n ->
            var a = dealHand(Tournament.start(1_000L + n))
            var b = scrambled(a, 0, random)
            // Preflop deals no card, so what the seat sees changes only by the actions: fold the hand around.
            while (a.phase == Phase.BETTING && a.street == Street.PREFLOP) {
                val legal = legalActions(a)!!
                val action = if (legal.canFold) Action.Fold else Action.Check
                a = a.step(legal.seat, action)
                b = b.step(legal.seat, action)
                if (a.street == Street.PREFLOP) assertEquals(a.seatView(0), b.seatView(0))
            }
            assertEquals(Phase.HAND_OVER, a.phase)
            assertFalse(a.result!!.showdown)
            assertEquals(a.seatView(0), b.seatView(0))
            assertTrue(a.seatView(0).revealed.isEmpty())
        }
    }

    @Test
    fun `a view holds the seat's own cards and nothing of anyone else's`() {
        val state = dealHand(Tournament.start(3L))
        for (seat in 0 until 6) {
            val view = state.seatView(seat)
            assertEquals(state.holeCards[seat], view.holeCards)
            assertTrue(view.revealed.isEmpty())
            assertTrue(view.board.isEmpty())
        }
    }

    @Test
    fun `no field of a view can carry the deck or a seed`() {
        val fields = SeatView::class.java.declaredFields.map { it.name }
        assertTrue(fields.none { it.contains("deck", ignoreCase = true) || it.contains("seed", ignoreCase = true) })
        // The card-carrying fields are exactly the seat's own cards, the board, and what a showdown revealed.
        val cardFields = SeatView::class.java.declaredFields.filter { f ->
            f.genericType.toString().contains("Card")
        }.map { it.name }.toSet()
        assertEquals(setOf("holeCards", "board", "revealed"), cardFields)
        // Nor can a nested type: pots, legal actions and history are chips and seats only.
        for (type in listOf(Pot::class.java, LegalActions::class.java, HistoryEntry::class.java, Blinds::class.java)) {
            assertTrue(type.declaredFields.none { it.genericType.toString().contains("Card") })
        }
    }

    @Test
    fun `the legal actions are present only on the seat's own turn`() {
        val state = dealHand(Tournament.start(9L))
        for (seat in 0 until 6) {
            val view = state.seatView(seat)
            if (seat == state.toAct) assertEquals(legalActions(state), view.legal) else assertNull(view.legal)
            assertEquals(state.toCall(seat), view.toCall)
            assertEquals(seat == state.toAct, view.isMyTurn)
        }
    }

    @Test
    fun `the view carries the public table`() {
        val state = dealHand(Tournament.start(9L))
        val view = state.seatView(2)
        assertEquals(state.stacks, view.stacks)
        assertEquals(state.pot, view.pot)
        assertEquals(30, view.pot)
        assertEquals(state.pots, view.pots)
        assertEquals(state.blinds, view.blinds)
        assertEquals(state.button, view.button)
        assertEquals(state.smallBlindSeat, view.smallBlindSeat)
        assertEquals(state.bigBlindSeat, view.bigBlindSeat)
        assertEquals(state.street, view.street)
        assertEquals(List(6) { true }, view.inTournament)
        val acted = state.step(state.toAct, Action.Raise(60))
        assertEquals(listOf(HistoryEntry(Street.PREFLOP, state.toAct, Action.Raise(60), 60, 60, false)), acted.seatView(2).history)
    }

    @Test
    fun `hands turned over at a showdown are revealed, and only then`() {
        val shown = ReferenceHands.wheel()
        val view = shown.seatView(0)
        assertEquals(setOf(0, 1), view.revealed.keys)
        assertEquals(shown.holeCards[1], view.revealed.getValue(1))

        val folded = ReferenceHands.bigBlindOption()
        assertTrue(folded.seatView(0).revealed.isEmpty())
        assertFalse(folded.result!!.showdown)
    }
}
