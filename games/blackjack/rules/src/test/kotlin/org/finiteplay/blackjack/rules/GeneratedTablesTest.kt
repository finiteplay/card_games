package org.finiteplay.blackjack.rules

import org.finiteplay.cards.Card
import org.junit.Assert.assertEquals
import org.junit.Test

/** Generated tables rather than hand-written lists (`EXECUTION_PLAN.md` B2 gate). */
class GeneratedTablesTest {

    /** The number of cases the legal-decision matrix generates; a change here is a change to the matrix. */
    private val legalMatrixCases = 67

    /** The number of cases the settlement table generates. */
    private val settlementCases = 154

    @Test
    fun `legal decisions across phase, hand size, pair, split Ace, split count and affordability`() {
        var cases = 0
        val bet = 100

        for (insuranceCovered in listOf(true, false)) {
            // The insurance phase offers its two decisions whatever else is true of the round.
            val state = stateWith(listOf(PlayerHand(cards("9C", "8H"), bet)), phase = Phase.INSURANCE)
            assertEquals(setOf(Decision.TAKE_INSURANCE, Decision.DECLINE_INSURANCE), legalDecisions(state, if (insuranceCovered) 1_000 else 100))
            cases++
        }
        assertEquals(emptySet<Decision>(), legalDecisions(stateWith(listOf(PlayerHand(cards("9C", "8H"), bet)), phase = Phase.SETTLED), 1_000))
        cases++

        for (size in listOf(2, 3)) {
            for (pair in listOf(true, false)) {
                for (splitAces in listOf(true, false)) {
                    for (splitsMade in 0..3) {
                        for (covered in listOf(true, false)) {
                            val handCards = when {
                                size == 2 && pair -> if (splitAces) cards("AC", "AH") else cards("8C", "8H")
                                size == 2 -> if (splitAces) cards("AC", "9H") else cards("8C", "9H")
                                // A three-card hand is never a pair; keep it under 21 so Hit is in play.
                                else -> if (splitAces) cards("AC", "2H", "3D") else cards("2C", "3H", "4D")
                            }
                            // Splits made: that many extra hands already on the table, each already complete.
                            val others = List(splitsMade) { PlayerHand(cards("TC", "TH"), bet, stood = true) }
                            val active = PlayerHand(handCards, bet, fromSplit = splitAces, splitAces = splitAces)
                            val hands = listOf(active) + others
                            val staked = bet * hands.size
                            // Covered: room for one more bet. Not covered: exactly the stakes on the table.
                            val bankroll = if (covered) staked + bet else staked
                            val state = stateWith(hands)

                            // The spec, restated independently of the implementation.
                            val expected = buildSet {
                                add(Decision.STAND)
                                if (size == 2 || size == 3) add(Decision.HIT)
                                if (size == 2 && !splitAces && covered) add(Decision.DOUBLE)
                                if (size == 2 && pair && splitsMade < 3 && covered) add(Decision.SPLIT)
                            }
                            // A split Ace that has two cards is complete and is never the active hand in play, but
                            // the engine's answer for an active hand is still pure: it must match the spec above.
                            assertEquals("size=$size pair=$pair aces=$splitAces splits=$splitsMade covered=$covered", expected, legalDecisions(state, bankroll))
                            cases++
                        }
                    }
                }
            }
        }
        assertEquals(legalMatrixCases, cases)
    }

    private val dealerHands: List<Pair<String, List<Card>>> = listOf(
        "blackjack" to cards("AS", "KH"),
        "bust" to cards("TS", "6H", "KD"),
        "17" to cards("TS", "7H"),
        "18" to cards("TS", "8H"),
        "19" to cards("TS", "9H"),
        "20" to cards("TS", "KH"),
        "21" to cards("7S", "7H", "7D"),
    )

    private val playerHands: List<Pair<String, List<Card>>> = listOf(
        "blackjack" to cards("AC", "QH"),
        "bust" to cards("TC", "6H", "KS"),
        "17" to cards("TC", "7H"),
        "18" to cards("TC", "8H"),
        "20" to cards("TC", "KH"),
        "21" to cards("7C", "7H", "7D"),
    )

    @Test
    fun `settlement across player outcome, dealer outcome, doubled and insurance`() {
        var cases = 0
        val bet = 100
        for ((playerName, playerCards) in playerHands) {
            for ((dealerName, dealerCards) in dealerHands) {
                for (doubled in listOf(false, true)) {
                    for (insured in listOf(false, true)) {
                        // A natural cannot be doubled: a two-card blackjack settles before any decision.
                        if (playerName == "blackjack" && doubled) continue
                        val stake = if (doubled) bet * 2 else bet
                        val insurance = if (insured) bet / 2 else 0
                        val hand = PlayerHand(playerCards, stake, doubled = doubled)
                        val dealerBlackjack = dealerName == "blackjack"
                        val state = stateWith(listOf(hand), dealer = dealerCards, insuranceStake = insurance)
                        val settled = settle(state, dealerBlackjack).settlement!!

                        val playerBlackjack = playerName == "blackjack"
                        val dealerTotal = handValue(dealerCards)
                        val playerTotal = handValue(playerCards)
                        val expectedHand = when {
                            dealerBlackjack -> if (playerBlackjack) 0 else -stake
                            playerBlackjack -> stake * 3 / 2
                            playerTotal.busted -> -stake
                            dealerTotal.busted -> stake
                            playerTotal.total > dealerTotal.total -> stake
                            playerTotal.total < dealerTotal.total -> -stake
                            else -> 0
                        }
                        val expectedInsurance = when {
                            !insured -> 0
                            dealerBlackjack -> insurance * 2
                            else -> -insurance
                        }
                        val label = "$playerName vs $dealerName doubled=$doubled insured=$insured"
                        assertEquals(label, expectedHand, settled.hands[0].delta)
                        assertEquals(label, expectedInsurance, settled.insuranceDelta)
                        assertEquals(label, expectedHand + expectedInsurance, settled.total)
                        cases++
                    }
                }
            }
        }
        assertEquals(settlementCases, cases)
    }

    @Test
    fun `several hands settle on their own stakes in one total`() {
        val hands = listOf(
            PlayerHand(cards("TC", "KH"), 100, stood = true, fromSplit = true),
            PlayerHand(cards("TC", "7H"), 200, doubled = true, fromSplit = true),
            PlayerHand(cards("TC", "6H", "KS"), 100, fromSplit = true),
        )
        val settled = settle(stateWith(hands, dealer = cards("TS", "8H")), dealerBlackjack = false).settlement!!
        assertEquals(listOf(100, -200, -100), settled.hands.map { it.delta })
        assertEquals(-200, settled.total)
    }
}
