package org.finiteplay.holdem.rules

import org.finiteplay.holdem.rules.Fx.checkDown
import org.finiteplay.holdem.rules.Fx.run
import org.finiteplay.holdem.rules.Fx.step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules a soak cannot reach reliably, each on a stacked deck (`EXECUTION_PLAN.md` H2 gate). */
class RoundFixturesTest {
    @Test
    fun `a wheel is the lowest straight and still beats a pair`() {
        val s = ReferenceHands.wheel()
        assertEquals(Phase.HAND_OVER, s.phase)
        val result = s.result!!
        assertTrue(result.showdown)
        assertEquals(listOf(1), result.awards.single().winners)
        assertEquals(HandCategory.STRAIGHT, categoryOf(result.values.getValue(1)))
        assertEquals(HandCategory.PAIR, categoryOf(result.values.getValue(0)))
        assertEquals(listOf(4_480, 4_520, 0, 0, 0, 0), s.stacks)
    }

    @Test
    fun `a board that plays for everyone splits evenly`() {
        val s = ReferenceHands.boardPlays()
        val award = s.result!!.awards.single()
        assertEquals(60, award.pot.amount)
        assertEquals(listOf(20, 20, 20), award.shares)
        assertEquals(3, award.winners.size)
        assertEquals(setOf(HandCategory.STRAIGHT_FLUSH), s.result!!.values.values.map(::categoryOf).toSet())
        assertEquals(listOf(3_000, 3_000, 3_000, 0, 0, 0), s.stacks)
    }

    @Test
    fun `a three-way split with an odd chip gives it to the first seat left of the button`() {
        val s = ReferenceHands.oddChipSplit()
        val award = s.result!!.awards.single()
        assertEquals(70, award.pot.amount)
        assertEquals(listOf(2, 3, 0), award.winners)
        assertEquals(listOf(24, 23, 23), award.shares)
        assertEquals(listOf(2_253, 2_240, 2_254, 2_253, 0, 0), s.stacks)
        assertEquals(Contract.TOTAL_CHIPS, s.stacks.sum())
    }

    @Test
    fun `a short all-in does not reopen the betting for those who already acted`() {
        val seen = HashMap<String, HoldemState>()
        val s = ReferenceHands.shortAllIn { label, state -> seen[label] = state }

        val flop = seen.getValue("flop")
        assertEquals(Street.FLOP, flop.street)
        assertEquals(1, flop.toAct)
        assertEquals(400, flop.pot)
        assertEquals(20..2_800, legalActions(flop)!!.bet)

        val facing = legalActions(seen.getValue("facing the bet, short stack to act"))!!
        assertEquals(3, facing.seat)
        assertEquals(100, facing.callAmount)
        assertNull("150 is below the minimum raise to 200", facing.raise)
        assertEquals(150, facing.allInTo)

        val afterShort = seen.getValue("after the short all-in, seat 0 has not acted")
        assertEquals(150, afterShort.currentBet)
        assertEquals("a raise of 50 is not a full raise", 100, afterShort.raiseIncrement)
        val unacted = legalActions(afterShort)!!
        assertEquals(0, unacted.seat)
        assertEquals("a player who has not yet acted may raise as normal", 250..2_800, unacted.raise)

        for (label in listOf("after the short all-in, seat 1 has acted", "after the short all-in, seat 2 has acted")) {
            val closed = legalActions(seen.getValue(label))!!
            assertEquals(50, closed.callAmount)
            assertTrue(closed.canFold)
            assertNull("$label: may not raise", closed.raise)
            assertNull("$label: all in is not offered either", closed.allInTo)
            assertNull(apply(seen.getValue(label), closed.seat, Action.Raise(250)))
            assertNull(apply(seen.getValue(label), closed.seat, Action.AllIn))
        }

        assertEquals(listOf(2_650, 2_650, 2_700, 1_000, 0, 0), s.stacks)
        assertEquals(listOf(3), s.result!!.awards.single().winners)
    }

    @Test
    fun `short all-ins that add up to a full raise reopen the betting`() {
        // Seat 0 responded to a bet of 100; two short all-ins then took it to 210, a rise of 110
        // against a minimum raise of 100.
        val base = dealHand(Tournament.start(3L)).copy(toAct = 0, currentBet = 210, raiseIncrement = 100)
        val one = base.copy(lastActionBet = base.lastActionBet.toMutableList().also { it[0] = 100 })
        assertNotNull(legalActions(one)!!.raise)
        val two = base.copy(currentBet = 160, lastActionBet = base.lastActionBet.toMutableList().also { it[0] = 100 })
        assertNull("one short all-in alone does not", legalActions(two)!!.raise)
    }

    @Test
    fun `the big blind may check or raise when action reaches it unraised`() {
        val seen = HashMap<String, HoldemState>()
        val s = ReferenceHands.bigBlindOption { label, state -> seen[label] = state }

        val option = seen.getValue("the big blind's option")
        assertEquals(Street.PREFLOP, option.street)
        val legal = legalActions(option)!!
        assertEquals(2, legal.seat)
        assertTrue(legal.canCheck)
        assertFalse("nothing to call, so no fold", legal.canFold)
        assertNull(legal.bet)
        assertEquals(40..3_000, legal.raise)

        val raised = seen.getValue("after the option raise")
        assertEquals(0, raised.toAct)
        assertEquals(40, raised.toCall(0))

        assertEquals(Phase.HAND_OVER, s.phase)
        assertFalse(s.result!!.showdown)
        assertTrue("no cards are shown", s.result!!.values.isEmpty())
        assertEquals(40, s.returned[2])
        assertEquals(listOf(2_980, 2_980, 3_040, 0, 0, 0), s.stacks)
    }

    @Test
    fun `checking the option ends preflop`() {
        val t = Fx.tournament(listOf(3_000, 3_000, 3_000, 0, 0, 0), button = 0)
        val s = Fx.hand(t, mapOf(0 to "2c 3d", 1 to "4h 5d", 2 to "6c 7d"), "Ts 8s 9h Kd 2h")
            .run(0 to Action.Call, 1 to Action.Call)
        assertEquals("the round is not over before the big blind has acted", Street.PREFLOP, s.street)
        val flop = s.step(2, Action.Check)
        assertEquals(Street.FLOP, flop.street)
        assertEquals(3, flop.board.size)
        assertEquals(1, flop.toAct)
    }

    @Test
    fun `heads-up the button posts the small blind, acts first before the flop and last after`() {
        val seen = HashMap<String, HoldemState>()
        val s = ReferenceHands.headsUp { label, state -> seen[label] = state }

        val dealt = seen.getValue("dealt")
        assertEquals(1, dealt.smallBlindSeat)
        assertEquals(0, dealt.bigBlindSeat)
        assertEquals(listOf(4_480, 4_490), dealt.stacks.take(2))
        assertEquals(listOf(20, 10), dealt.streetBets.take(2))
        assertEquals("the first card goes to the big blind", listOf(dealt.deck[0], dealt.deck[2]), dealt.holeCards[0])
        assertEquals(listOf(dealt.deck[1], dealt.deck[3]), dealt.holeCards[1])
        assertEquals(1, dealt.toAct)
        assertEquals(10, legalActions(dealt)!!.toCall)

        assertEquals(Street.FLOP, seen.getValue("flop").street)
        assertEquals(0, seen.getValue("flop").toAct)
        assertEquals(listOf(4_520, 4_480, 0, 0, 0, 0), s.stacks)
    }

    @Test
    fun `two knockouts in one hand with equal starting stacks share the higher place`() {
        val s = ReferenceHands.knockouts(listOf(5_000, 1_500, 1_500, 1_000, 0, 0))
        assertEquals(Phase.HAND_OVER, s.phase)
        assertEquals(listOf(8_000, 0, 0, 1_000, 0, 0), s.stacks)
        assertEquals(listOf(1, 2), s.result!!.eliminated)
        assertEquals(listOf(null, 3, 3, null, 6, 5), s.places)
        assertEquals(3_500, s.returned[0])
        assertEquals(Contract.TOTAL_CHIPS, s.stacks.sum())
    }

    @Test
    fun `two knockouts in one hand with unequal starting stacks are ordered by those stacks`() {
        val s = ReferenceHands.knockouts(listOf(4_500, 1_500, 2_000, 1_000, 0, 0))
        assertEquals(listOf(8_000, 0, 0, 1_000, 0, 0), s.stacks)
        // Seat 2 started with more chips than seat 1, so it finishes higher, whatever their seats.
        assertEquals(listOf(null, 4, 3, null, 6, 5), s.places)
        assertEquals(listOf(4_500, 1_000), s.result!!.awards.map { it.pot.amount })
        assertEquals(2_500, s.returned[0])
        assertEquals(Contract.TOTAL_CHIPS, s.stacks.sum())
    }

    @Test
    fun `a big blind that cannot cover posts what it has, all in, and is called for the whole blind`() {
        val t = Fx.tournament(listOf(3_000, 3_000, 15, 2_985, 0, 0), button = 0)
        val dealt = dealHand(t)
        assertEquals(15, dealt.streetBets[2])
        assertTrue(dealt.allIn[2])
        assertEquals(0, dealt.stacks[2])
        assertEquals(20, dealt.currentBet)
        assertEquals(3, dealt.toAct)
        assertEquals(20, legalActions(dealt)!!.toCall)

        val s = ReferenceHands.shortBigBlind()
        assertEquals(listOf(60, 15), s.result!!.awards.map { it.pot.amount })
        assertEquals(listOf(listOf(0, 1, 2, 3), listOf(0, 1, 3)), s.result!!.awards.map { it.pot.eligible })
        assertEquals(listOf(2), s.result!!.awards[0].winners)
        assertEquals(listOf(1), s.result!!.awards[1].winners)
        assertEquals(listOf(2_980, 2_995, 60, 2_965, 0, 0), s.stacks)
        assertEquals("not eliminated: it won the main pot", null, s.places[2])
    }

    @Test
    fun `a short big blind that everyone folds to wins the blinds`() {
        val s = ReferenceHands.shortBigBlindFoldedTo()
        assertEquals(listOf(3_000, 2_990, 25, 2_985, 0, 0), s.stacks)
        assertFalse(s.result!!.showdown)
        assertEquals(Contract.TOTAL_CHIPS, s.stacks.sum())
    }

    @Test
    fun `blinds that cannot be covered leave the one seat that can bet a call or a fold, then the board runs out`() {
        val t = Fx.tournament(listOf(8_985, 5, 10, 0, 0, 0), button = 0)
        val dealt = Fx.hand(t, mapOf(0 to "Ah Ad", 1 to "Kh Kd", 2 to "Qc Qd"), "2c 3d 7s 9h Js")
        assertEquals(listOf(0, 5, 10), dealt.streetBets.take(3))
        assertEquals(0, dealt.toAct)
        val legal = legalActions(dealt)!!
        assertEquals(20, legal.toCall)
        assertTrue(legal.canFold)

        val over = dealt.step(0, Action.Call)
        assertEquals("no betting between the cards: nothing is left to act", Phase.TOURNAMENT_OVER, over.phase)
        assertEquals(5, over.board.size)
        assertEquals(10, over.returned[0])
        assertEquals(listOf(15, 10), over.result!!.awards.map { it.pot.amount })
        assertEquals(listOf(listOf(0, 1, 2), listOf(0, 2)), over.result!!.awards.map { it.pot.eligible })
        assertEquals(listOf(9_000, 0, 0, 0, 0, 0), over.stacks)
        // The tournament ends: the winner is first and the larger starting stack finishes higher.
        assertEquals(listOf(1, 3, 2, 6, 5, 4), over.places)
        assertNull(nextHand(over))
    }

    @Test
    fun `when only one seat has chips and it has matched the blind there is no betting at all`() {
        val t = Fx.tournament(listOf(5, 8_995, 0, 0, 0, 0), button = 0)
        val s = Fx.hand(t, mapOf(0 to "Ah Ad", 1 to "2c 3d"), "Ts 8s 9h Kd 4h")
        assertEquals(Phase.HAND_OVER, s.phase)
        assertEquals(5, s.board.size)
        assertTrue(s.result!!.showdown)
        assertEquals(15, s.returned[1])
        assertEquals(listOf(10, 8_990, 0, 0, 0, 0), s.stacks)
    }

    @Test
    fun `no one to act means no legal actions`() {
        val s = ReferenceHands.wheel()
        assertNull(legalActions(s))
        assertNull(apply(s, 0, Action.Check))
    }

    @Test
    fun `categories in the fixtures come from the evaluator`() {
        val s = ReferenceHands.headsUp()
        assertEquals(HandCategory.STRAIGHT, categoryOf(s.result!!.values.getValue(0)))
    }

    @Test
    fun `the whole fixture set keeps the table's chips`() {
        for (name in ReferenceHands.names) {
            val s = ReferenceHands.build(name)
            assertEquals(name, Contract.TOTAL_CHIPS, s.stacks.sum() + if (s.phase == Phase.BETTING) s.contributions.sum() else 0)
        }
    }
}
