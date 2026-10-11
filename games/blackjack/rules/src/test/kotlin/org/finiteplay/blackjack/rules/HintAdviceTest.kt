package org.finiteplay.blackjack.rules

import org.finiteplay.cards.Rank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Hint's lessons and the odds it states (`DESIGN.md` "Hint"). */
class HintAdviceTest {
    private fun advice(player: String, dealerUp: String, bankroll: Int = 1_000, phase: Phase = Phase.PLAYING): BlackjackAdvice {
        val state = stateWith(listOf(PlayerHand(cards(*player.split(' ').toTypedArray()), 100)), dealer = cards(dealerUp, "5H"), phase = phase)
        return BasicStrategy.advise(state, bankroll)!!
    }

    @Test
    fun `the dealer's outcome chances sum to one and bust most often on a weak card`() {
        for (rank in Rank.entries) assertEquals(1.0, DealerOdds.distribution(rank).sum(), 1e-9)
        for (rank in listOf(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX)) assertTrue("$rank", DealerOdds.bustChance(rank) in 0.33..0.46)
        for (rank in listOf(Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.KING)) assertTrue("$rank", DealerOdds.bustChance(rank) in 0.19..0.28)
        assertTrue(DealerOdds.bustChance(Rank.ACE) in 0.09..0.20)
        assertTrue(DealerOdds.bustChance(Rank.SIX) > DealerOdds.bustChance(Rank.SEVEN))
        // The table's own published figure, to the point: a six busts about 42% of the time.
        assertEquals(0.42, DealerOdds.bustChance(Rank.SIX), 0.015)
    }

    @Test
    fun `a stand ends in a win, a push or a loss`() {
        for (total in 4..21) for (rank in Rank.entries) {
            val (w, p, l) = DealerOdds.standOutcome(total, rank)
            assertEquals(1.0, w + p + l, 1e-9)
            assertTrue(w >= 0 && p >= 0 && l >= -1e-12)
        }
        // Sixteen against a ten wins only when the dealer busts.
        val (win, push, _) = DealerOdds.standOutcome(16, Rank.TEN)
        assertEquals(DealerOdds.bustChance(Rank.TEN), win, 1e-9)
        assertEquals(0.0, push, 0.0)
    }

    @Test
    fun `a hit busts a hard hand by how many cards are too big`() {
        assertEquals(0.0, DealerOdds.bustOnHit(11), 1e-12)
        assertEquals(4.0 / 13.0, DealerOdds.bustOnHit(12), 1e-12)
        assertEquals(8.0 / 13.0, DealerOdds.bustOnHit(16), 1e-12)
        assertEquals(1.0, DealerOdds.bustOnHit(21), 1e-12)
    }

    @Test
    fun `each recommendation teaches its own lesson`() {
        assertEquals(BlackjackHintReason.HIT_DEALER_STRONG, advice("TC 6H", "TS").reason)
        assertEquals(BlackjackHintReason.STAND_DEALER_WEAK, advice("TC 2H", "6S").reason)
        assertEquals(BlackjackHintReason.STAND_STRONG, advice("TC 9H", "TS").reason)
        assertEquals(BlackjackHintReason.DOUBLE, advice("5C 6H", "6S").reason)
        assertEquals(BlackjackHintReason.HIT_CANNOT_BUST, advice("5C 6H", "6S", bankroll = 150).reason)
        assertEquals(BlackjackHintReason.HIT_SOFT, advice("AC 6H", "9S").reason)
        assertEquals(BlackjackHintReason.SPLIT_ACES, advice("AC AH", "TS").reason)
        assertEquals(BlackjackHintReason.SPLIT_EIGHTS, advice("8C 8H", "TS").reason)
        assertEquals(BlackjackHintReason.SPLIT_PAIR, advice("7C 7H", "5S").reason)
        assertEquals(BlackjackHintReason.DECLINE_INSURANCE, advice("TC 9H", "AS", phase = Phase.INSURANCE).reason)
    }

    @Test
    fun `the lesson always matches the decision it explains`() {
        val random = kotlin.random.Random(5)
        repeat(1_500) {
            var session = startRound(random.nextLong(), 100, 1_000)
            while (session.state.phase != Phase.SETTLED) {
                val a = BasicStrategy.advise(session.state, 1_000)!!
                assertEquals(BasicStrategy.recommend(session.state, 1_000), a.decision)
                val ok = when (a.decision) {
                    Decision.HIT -> a.reason in setOf(BlackjackHintReason.HIT_SOFT, BlackjackHintReason.HIT_CANNOT_BUST, BlackjackHintReason.HIT_DEALER_STRONG)
                    Decision.STAND -> a.reason in setOf(BlackjackHintReason.STAND_STRONG, BlackjackHintReason.STAND_DEALER_WEAK)
                    Decision.DOUBLE -> a.reason == BlackjackHintReason.DOUBLE
                    Decision.SPLIT -> a.reason in setOf(BlackjackHintReason.SPLIT_ACES, BlackjackHintReason.SPLIT_EIGHTS, BlackjackHintReason.SPLIT_PAIR)
                    else -> a.reason == BlackjackHintReason.DECLINE_INSURANCE
                }
                assertTrue("${a.decision} explained as ${a.reason}", ok)
                if (a.reason == BlackjackHintReason.HIT_CANNOT_BUST) assertEquals(0.0, a.odds.yourBust, 0.0)
                if (a.reason == BlackjackHintReason.HIT_SOFT) assertEquals(0.0, a.odds.yourBust, 0.0)
                session = session.decide(legalDecisions(session.state, 1_000).random(random), 1_000)!!
            }
        }
    }
}
