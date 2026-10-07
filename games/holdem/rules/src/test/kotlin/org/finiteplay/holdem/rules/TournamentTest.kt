package org.finiteplay.holdem.rules

import org.finiteplay.holdem.rules.Fx.step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The tournament around the hands: blinds, button, places, the end, leaving, and the session. */
class TournamentTest {
    @Test
    fun `the blind schedule is the table in RULES, then doubles`() {
        val expected = listOf(
            10 to 20, 15 to 30, 25 to 50, 50 to 100, 75 to 150, 100 to 200, 150 to 300,
            200 to 400, 300 to 600, 400 to 800, 600 to 1_200, 800 to 1_600, 1_000 to 2_000,
            2_000 to 4_000, 4_000 to 8_000, 8_000 to 16_000,
        )
        for ((i, pair) in expected.withIndex()) {
            val blinds = BlindSchedule.blinds(i + 1)
            assertEquals("level ${i + 1}", Blinds(i + 1, pair.first, pair.second), blinds)
        }
    }

    @Test
    fun `the level rises every ten hands from the first`() {
        assertEquals(List(10) { 1 }, (1..10).map(BlindSchedule::levelOf))
        assertEquals(2, BlindSchedule.levelOf(11))
        assertEquals(2, BlindSchedule.levelOf(20))
        assertEquals(3, BlindSchedule.levelOf(21))
        assertEquals(14, BlindSchedule.levelOf(131))
        assertEquals(Blinds(14, 2_000, 4_000), BlindSchedule.blindsForHand(131))
        assertEquals(10, BlindSchedule.handsLeftAtLevel(1))
        assertEquals(1, BlindSchedule.handsLeftAtLevel(10))
        assertEquals(10, BlindSchedule.handsLeftAtLevel(11))
    }

    @Test
    fun `an extreme level does not overflow`() {
        assertTrue(BlindSchedule.blinds(10_000).big > 0)
    }

    private fun foldAround(state: HoldemState): HoldemState {
        var s = state
        while (s.phase == Phase.BETTING) {
            val legal = legalActions(s)!!
            s = s.step(legal.seat, if (legal.canFold) Action.Fold else Action.Check)
        }
        return s
    }

    @Test
    fun `the blinds rise on the eleventh hand and are posted at the new level`() {
        val tenth = dealHand(Fx.tournament(List(6) { 1_500 }, button = 0, handNumber = 10))
        assertEquals(20, tenth.streetBets[tenth.bigBlindSeat])
        assertEquals(10, tenth.streetBets[tenth.smallBlindSeat])
        val eleventh = nextHand(foldAround(tenth))!!
        assertEquals(11, eleventh.handNumber)
        assertEquals(Blinds(2, 15, 30), eleventh.blinds)
        assertEquals(15, eleventh.streetBets[eleventh.smallBlindSeat])
        assertEquals(30, eleventh.streetBets[eleventh.bigBlindSeat])
    }

    @Test
    fun `a new tournament has equal stacks, a drawn button, and blinds posted`() {
        for (seed in listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L)) {
            val s = startTournament(seed).state
            assertEquals(firstButton(seed), s.button)
            assertEquals(1, s.handNumber)
            assertEquals(Contract.TOTAL_CHIPS, s.stacks.sum() + s.contributions.sum())
            assertEquals(List(6) { null }, s.places)
            assertEquals(Phase.BETTING, s.phase)
            assertEquals((s.button + 1) % 6, s.smallBlindSeat)
            assertEquals((s.button + 2) % 6, s.bigBlindSeat)
            assertEquals((s.button + 3) % 6, s.toAct)
        }
        assertEquals(6, (1L..200L).map { firstButton(it) }.toSet().size)
    }

    @Test
    fun `the button moves to the next seat still in the tournament`() {
        val first = ReferenceHands.knockouts(listOf(5_000, 1_500, 1_500, 1_000, 0, 0))
        val next = nextHand(first)!!
        // Button 0; seats 1 and 2 are out, so it moves to 3, and heads-up it posts the small blind.
        assertEquals(3, next.button)
        assertEquals(2, next.handNumber)
        assertEquals(3, next.smallBlindSeat)
        assertEquals(0, next.bigBlindSeat)
        assertEquals(3, next.toAct)
        assertEquals(listOf(null, 3, 3, null, 6, 5), next.places)
        assertEquals(listOf(8_000, 0, 0, 1_000, 0, 0), next.stacks.mapIndexed { i, c -> c + next.contributions[i] })
        assertTrue("an eliminated seat is not dealt in", listOf(1, 2, 4, 5).all { next.holeCards[it].isEmpty() })

        val ordinary = dealHand(Tournament.start(11L))
        val after = nextHand(foldAround(ordinary))!!
        assertEquals((ordinary.button + 1) % 6, after.button)
    }

    @Test
    fun `blinds and button skip an empty seat and no dead button is applied`() {
        // Seat 1 is out. Button 5: the small blind is seat 0, the big blind seat 2.
        val t = Fx.tournament(listOf(1_800, 0, 1_800, 1_800, 1_800, 1_800), button = 5)
        val s = dealHand(t)
        assertEquals(0, s.smallBlindSeat)
        assertEquals(2, s.bigBlindSeat)
        // The button moves to the next live seat, 0, so seat 0 goes from small blind to button and
        // seat 2 from big blind to small blind, with no skipped blind to make up.
        val next = nextHand(foldAround(s))!!
        assertEquals(0, next.button)
        assertEquals(2, next.smallBlindSeat)
        assertEquals(3, next.bigBlindSeat)
    }

    @Test
    fun `the tournament ends when one seat holds every chip, with places for all`() {
        var s = nextHand(ReferenceHands.knockouts(listOf(4_500, 1_500, 2_000, 1_000, 0, 0)))!!
        assertEquals(listOf(0, 3), s.seatsInHand)
        s = s.step(3, Action.AllIn).step(0, Action.Call)
        assertEquals(Phase.TOURNAMENT_OVER, s.phase)
        assertEquals(Contract.TOTAL_CHIPS, s.stacks.sum())
        assertEquals((1..6).toList(), s.places.map { it!! }.sorted())
        val winner = s.stacks.indexOfFirst { it == 9_000 }
        assertEquals(1, s.places[winner])
        assertEquals(2, s.places[if (winner == 0) 3 else 0])
        assertEquals(listOf(4, 3, 6, 5), listOf(s.places[1], s.places[2], s.places[4], s.places[5]))
        assertNull(nextHand(s))
        assertNull(legalActions(s))
        assertNull(apply(s, 0, Action.Check))
    }

    @Test
    fun `leaving finishes in the place of a player knocked out now`() {
        val fresh = dealHand(Tournament.start(5L))
        val left = leave(fresh)!!
        assertEquals(Phase.TOURNAMENT_OVER, left.phase)
        assertEquals(6, left.places[0])
        assertEquals(List(5) { null }, left.places.drop(1))
        assertTrue(left.playerOut)
        assertNull("left, so nothing to resume", leave(left))
        assertNull(legalActions(left))
        assertNull(nextHand(left))

        val four = dealHand(Fx.tournament(listOf(2_250, 2_250, 2_250, 2_250, 0, 0), button = 1))
        assertEquals(4, leave(four)!!.places[0])
        val betting = four.step(four.toAct, Action.Fold)
        assertEquals("mid-hand is the same place", 4, leave(betting)!!.places[0])

        val between = foldAround(four)
        assertEquals(Phase.HAND_OVER, between.phase)
        assertEquals(4, leave(between)!!.places[0])
    }

    @Test
    fun `a knocked-out player cannot leave and the engine runs on without them`() {
        val out = dealHand(Fx.tournament(listOf(0, 4_500, 4_500, 0, 0, 0), button = 1))
        assertTrue(out.playerOut)
        assertNull(leave(out))
        assertNotNull(legalActions(out))
    }

    @Test
    fun `the session logs every seat and offers no undo`() {
        val start = startTournament(1L)
        assertTrue(start.log.isEmpty())
        assertFalse(start.canUndo)
        val legal = legalActions(start.state)!!
        val after = start.act(legal.seat, Action.Fold)!!
        assertEquals(listOf(SeatAction(legal.seat, Action.Fold)), after.log)
        assertFalse(after.canUndo)
        assertTrue(after.undoStack.isEmpty())
        assertNull("not that seat's turn", after.act(legal.seat, Action.Fold))
        assertNull("an amount outside the range", start.act(legal.seat, Action.Raise(1)))
    }

    @Test
    fun `replaying a hand's log from its tournament reproduces the hand, and rejects a bad entry`() {
        val random = Random(8)
        var session = startTournament(77L)
        repeat(12) {
            val t = session.state.tournament
            val played = RandomPlay.finishHand(session, random)
            assertEquals(played, replayHand(t, played.log))
            if (played.log.isNotEmpty()) {
                // The wrong seat, an action that is not offered, and one past the hand's end.
                val first = played.log.first()
                assertNull(replayHand(t, listOf(first.copy(seat = (first.seat + 1) % 6)) + played.log.drop(1)))
                assertNull(replayHand(t, played.log + SeatAction(0, Action.Check)))
                assertNull(replayHand(t, listOf(first.copy(action = Action.Raise(1))) + played.log.drop(1)))
            }
            session = played.nextHand() ?: return@repeat
        }
    }

    @Test
    fun `replaying a whole tournament from its seed reproduces it`() {
        val random = Random(21)
        var session = startTournament(2026L)
        val logs = ArrayList<List<SeatAction>>()
        while (true) {
            session = RandomPlay.finishHand(session, random)
            logs += session.log
            session = session.nextHand() ?: break
            if (logs.size > 1_000) error("the tournament did not end")
        }
        val replayed = replayTournament(2026L, logs)
        assertEquals(session, replayed)
    }
}
