package org.finiteplay.blackjack.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** A random legal-action soak (`EXECUTION_PLAN.md` B2 gate) over many seeds, bets and bankrolls. */
class SoakTest {
    @Test
    fun `random legal play preserves every invariant`() {
        val random = Random(20261003)
        var settledRounds = 0
        var splitRounds = 0
        var doubleRounds = 0
        var insuranceRounds = 0

        repeat(6_000) { round ->
            val bankroll = listOf(10, 20, 50, 100, 150, 300, 700, 1_000, 5_000).random(random)
            val maxBet = Chips.maxBetFor(bankroll)
            val bet = (Chips.MIN_BET..maxBet step Chips.BET_STEP).toList().random(random)
            val seed = random.nextLong()
            var session = startRound(seed, bet, bankroll)

            fun check(state: BlackjackState) {
                // No card is dealt twice: every card in play was taken from the shoe at its own index.
                val inPlay = state.dealer.size + state.hands.sumOf { it.cards.size }
                assertEquals("round $round: cards in play match the shoe position", state.shoePosition, inPlay)
                assertTrue("round $round: available bankroll went negative", availableBankroll(state, bankroll) >= 0)
                assertTrue("round $round: more than four hands", state.hands.size <= MAX_HANDS)
                for (hand in state.hands) {
                    if (hand.splitAces) assertTrue("round $round: a split Ace took a second card", hand.cards.size <= 2)
                }
                if (state.phase != Phase.SETTLED) {
                    assertEquals("round $round: the dealer drew before every hand was complete", 2, state.dealer.size)
                }
            }

            check(session.state)
            var steps = 0
            while (session.state.phase != Phase.SETTLED) {
                check(steps++ < 80)
                val legal = legalDecisions(session.state, bankroll).toList()
                assertTrue("round $round: a live round offers a decision", legal.isNotEmpty())
                // Biased toward the rare decisions so the soak actually reaches splits and doubles.
                val choice = when {
                    Decision.SPLIT in legal && random.nextInt(10) < 7 -> Decision.SPLIT
                    Decision.DOUBLE in legal && random.nextInt(10) < 4 -> Decision.DOUBLE
                    else -> legal.random(random)
                }
                session = session.decide(choice, bankroll)!!
                check(session.state)
            }

            val state = session.state
            val settlement = state.settlement!!
            settledRounds++
            if (state.hands.size > 1) splitRounds++
            if (state.hands.any { it.doubled }) doubleRounds++
            if (state.insuranceStake > 0) insuranceRounds++

            // Chips are conserved: the bankroll change is exactly the hands plus the insurance.
            assertEquals(
                "round $round: settlement total",
                settlement.hands.sumOf { it.delta } + settlement.insuranceDelta,
                settlement.total,
            )
            if (state.hands.all { it.busted } && !settlement.dealerBlackjack) {
                assertEquals("round $round: the dealer drew after every hand busted", 2, state.dealer.size)
            }
            assertTrue("round $round: no decision once settled", legalDecisions(state, bankroll).isEmpty())

            // Replaying the log from the seed reproduces the round exactly.
            assertEquals("round $round: replay", session, replayRound(seed, bet, bankroll, session.log))
        }

        // The soak is only worth something if it reached the interesting rounds.
        assertEquals(6_000, settledRounds)
        assertTrue("splits reached: $splitRounds", splitRounds > 100)
        assertTrue("doubles reached: $doubleRounds", doubleRounds > 100)
        assertTrue("insurance reached: $insuranceRounds", insuranceRounds > 50)
    }
}
