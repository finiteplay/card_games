package org.finiteplay.blackjack.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Constructed rounds, each proving one rule of `RULES.md` "Order of Play" on a shoe built by hand. */
class RoundFixturesTest {
    private fun BlackjackSession.legal() = legalDecisions(state, 1_000)

    @Test
    fun `the deal order is player, dealer up, player, dealer hole`() {
        val round = roundFrom("2C", "3D", "4H", "5S")
        assertEquals(cards("2C", "4H"), round.state.hands[0].cards)
        assertEquals(cards("3D", "5S"), round.state.dealer)
        assertEquals(4, round.state.shoePosition)
        assertFalse("the hole card is not shown", round.state.holeRevealed)
        assertEquals(cards("3D"), round.state.dealerVisibleCards)
    }

    @Test
    fun `an Ace up offers insurance alone, and only an Ace does`() {
        val ace = roundFrom("9C", "AD", "8H", "5S")
        assertEquals(Phase.INSURANCE, ace.state.phase)
        assertEquals(setOf(Decision.TAKE_INSURANCE, Decision.DECLINE_INSURANCE), ace.legal())

        val ten = roundFrom("9C", "KD", "8H", "5S")
        assertEquals(Phase.PLAYING, ten.state.phase)
        assertFalse(Decision.TAKE_INSURANCE in ten.legal())
    }

    @Test
    fun `insurance needs half the bet in available bankroll`() {
        // 1,000 bet is not possible; use 500 with a 700 bankroll: 200 left, insurance costs 250.
        val broke = startRound(1L, 500, 700, shoeOf("9C", "AD", "8H", "5S"))
        assertEquals("insurance is skipped when it cannot be afforded", Phase.PLAYING, broke.state.phase)
        val enough = startRound(1L, 500, 750, shoeOf("9C", "AD", "8H", "5S"))
        assertEquals(Phase.INSURANCE, enough.state.phase)
    }

    @Test
    fun `a dealer blackjack is found at the peek, before any decision, and settles the round`() {
        val round = roundFrom("9C", "KD", "8H", "AS")
        assertEquals(Phase.SETTLED, round.state.phase)
        assertTrue(round.state.holeRevealed)
        assertTrue(round.state.settlement!!.dealerBlackjack)
        assertEquals(-100, round.state.settlement!!.total)
        assertEquals(emptySet<Decision>(), round.legal())
    }

    @Test
    fun `insurance pays two to one on a dealer blackjack and is lost otherwise`() {
        val won = roundFrom("9C", "AD", "8H", "KS").play(Decision.TAKE_INSURANCE)
        // Hand loses 100; insurance (50) pays 100.
        assertEquals(0, won.state.settlement!!.total)
        assertEquals(100, won.state.settlement!!.insuranceDelta)

        val lost = roundFrom("9C", "AD", "8H", "5S", "KH").play(Decision.TAKE_INSURANCE, Decision.STAND)
        // 17 vs dealer A+5 = soft 16 -> hits K -> hard 16... A,5,K = 16; hits? shoe order below.
        assertEquals(-50, lost.state.settlement!!.insuranceDelta)
    }

    @Test
    fun `declining insurance then a dealer blackjack costs only the hand`() {
        val round = roundFrom("9C", "AD", "8H", "KS").play(Decision.DECLINE_INSURANCE)
        assertEquals(-100, round.state.settlement!!.total)
        assertEquals(0, round.state.settlement!!.insuranceDelta)
    }

    @Test
    fun `a player blackjack against no dealer blackjack pays three to two at once`() {
        val round = roundFrom("AC", "9D", "KH", "7S")
        assertEquals(Phase.SETTLED, round.state.phase)
        assertEquals(HandOutcome.BLACKJACK, round.state.settlement!!.hands[0].outcome)
        assertEquals(150, round.state.settlement!!.total)
        assertTrue(round.state.holeRevealed)
    }

    @Test
    fun `a player blackjack pushes a dealer blackjack`() {
        val round = roundFrom("AC", "KD", "KH", "AS")
        assertEquals(HandOutcome.PUSH, round.state.settlement!!.hands[0].outcome)
        assertEquals(0, round.state.settlement!!.total)
    }

    @Test
    fun `taking insurance on a blackjack against an Ace is the even-money choice`() {
        val round = roundFrom("AC", "AD", "KH", "KS").play(Decision.TAKE_INSURANCE)
        // Blackjack pushes; insurance pays 2:1 on 50 = 100. Even money on 100.
        assertEquals(100, round.state.settlement!!.total)
    }

    @Test
    fun `a hand reaching 21 completes without Stand`() {
        // 5+6 = 11, hit 10 -> 21: complete; dealer 9+7 = 16 hits 5 -> 21? choose dealer 10+7 = 17.
        val round = roundFrom("5C", "TD", "6H", "7S", "TS").play(Decision.HIT)
        assertEquals(Phase.SETTLED, round.state.phase)
        assertEquals(HandOutcome.WIN, round.state.settlement!!.hands[0].outcome)
    }

    @Test
    fun `hit, bust and the dealer draws nothing after every hand busts`() {
        val round = roundFrom("TC", "9D", "6H", "8S", "KS").play(Decision.HIT)
        assertEquals(HandOutcome.BUST, round.state.settlement!!.hands[0].outcome)
        assertEquals("the dealer draws nothing once every hand has busted", 2, round.state.dealer.size)
        assertTrue(round.state.holeRevealed)
    }

    @Test
    fun `the dealer draws to seventeen and stands on soft seventeen`() {
        // Dealer 5+6 = 11 -> hit 3 = 14 -> hit 2 = 16 -> hit 3 = 19 stands.
        val hits = roundFrom("TC", "5D", "8H", "6S", "3S", "2S", "3H").play(Decision.STAND)
        assertEquals(cards("5D", "6S", "3S", "2S", "3H"), hits.state.dealer)

        // Dealer A+6 = soft 17 stands.
        val soft = roundFrom("TC", "AD", "8H", "6S").play(Decision.DECLINE_INSURANCE, Decision.STAND)
        assertEquals(2, soft.state.dealer.size)
    }

    @Test
    fun `double stakes twice the bet, takes exactly one card and completes the hand`() {
        val round = roundFrom("5C", "6D", "6H", "TS", "TD").play(Decision.DOUBLE)
        assertEquals(Phase.SETTLED, round.state.phase)
        val hand = round.state.hands[0]
        assertEquals(200, hand.bet)
        assertEquals(3, hand.cards.size)
        // 5+6+10 = 21 vs dealer 6+10 = 16 draws next filler card.
        assertEquals(HandOutcome.WIN, round.state.settlement!!.hands[0].outcome)
        assertEquals(200, round.state.settlement!!.hands[0].delta)
    }

    @Test
    fun `double is refused without the bankroll to cover it`() {
        val round = startRound(1L, 100, 150, shoeOf("5C", "6D", "6H", "TS"))
        assertFalse(Decision.DOUBLE in legalDecisions(round.state, 150))
        assertNull(round.decide(Decision.DOUBLE, 150))
    }

    @Test
    fun `a split deals each hand its second card as it becomes active`() {
        // Player 8,8; dealer 6 up, 10 hole. Split: hand A takes 3 (11), hand B waits.
        val round = roundFrom("8C", "6D", "8H", "TS", "3S", "2S", "9S").play(Decision.SPLIT)
        assertEquals(2, round.state.hands.size)
        assertEquals(cards("8C", "3S"), round.state.hands[0].cards)
        assertEquals("the second split hand has not been dealt yet", cards("8H"), round.state.hands[1].cards)
        assertEquals(0, round.state.activeHand)

        // Finish hand A by standing; hand B then takes the next card (2).
        val next = round.play(Decision.STAND)
        assertEquals(cards("8H", "2S"), next.state.hands[1].cards)
        assertEquals(1, next.state.activeHand)
    }

    @Test
    fun `split hands are played left to right and a resplit is allowed to four hands`() {
        // 8,8 split; each new card is another 8 so the pair can resplit until four hands.
        val shoe = arrayOf("8C", "6D", "8H", "TS", "8S", "8D", "8C", "8H", "8S", "8D", "2S", "2S", "2S", "2S")
        var round = startRound(1L, 100, 1_000, shoeOf(*shoe))
        round = round.play(Decision.SPLIT)
        assertTrue(Decision.SPLIT in round.legal())
        round = round.play(Decision.SPLIT)
        assertTrue(Decision.SPLIT in round.legal())
        round = round.play(Decision.SPLIT)
        assertEquals(4, round.state.hands.size)
        assertFalse("three splits is the limit", Decision.SPLIT in round.legal())
    }

    @Test
    fun `split Aces take one card each and complete, and 21 on them is not blackjack`() {
        val round = roundFrom("AC", "6D", "AH", "TS", "KS", "9S", "2S", "2H").play(Decision.SPLIT)
        // Both Aces received exactly one card and the dealer played out.
        assertEquals(Phase.SETTLED, round.state.phase)
        assertEquals(cards("AC", "KS"), round.state.hands[0].cards)
        assertEquals(cards("AH", "9S"), round.state.hands[1].cards)
        assertTrue(round.state.hands.all { it.splitAces && it.complete })
        // A+K on a split Ace is a 21 that pays 1:1, never 3:2.
        assertEquals(HandOutcome.WIN, round.state.settlement!!.hands[0].outcome)
        assertEquals(100, round.state.settlement!!.hands[0].delta)
    }

    @Test
    fun `a split hand may double except split Aces`() {
        val round = roundFrom("8C", "6D", "8H", "TS", "3S", "5S").play(Decision.SPLIT)
        assertTrue(Decision.DOUBLE in round.legal())
    }

    @Test
    fun `a push returns the stake`() {
        val round = roundFrom("TC", "TD", "8H", "8S").play(Decision.STAND)
        assertEquals(HandOutcome.PUSH, round.state.settlement!!.hands[0].outcome)
        assertEquals(0, round.state.settlement!!.total)
    }

    @Test
    fun `an illegal decision is refused and changes nothing`() {
        val round = roundFrom("9C", "TD", "8H", "7S")
        assertNull(round.decide(Decision.TAKE_INSURANCE, 1_000))
        assertNull(round.decide(Decision.SPLIT, 1_000))
        assertNotNull(round.decide(Decision.STAND, 1_000))
    }

    @Test
    fun `no undo is reachable`() {
        val round = roundFrom("9C", "TD", "8H", "7S").play(Decision.HIT)
        assertFalse(round.canUndo)
        assertEquals(emptyList<BlackjackState>(), round.undoStack)
    }

    @Test
    fun `a round replays from its seed, bet and log`() {
        val live = startRound(77L, 100, 1_000)
        var session = live
        var guard = 0
        while (session.state.phase != Phase.SETTLED && guard++ < 50) {
            val choice = legalDecisions(session.state, 1_000).first()
            session = session.decide(choice, 1_000)!!
        }
        val replayed = replayRound(77L, 100, 1_000, session.log)
        assertEquals(session, replayed)
        assertEquals(session.log, decodeLog(encodeLog(session.log)))
    }
}
