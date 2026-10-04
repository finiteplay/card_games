package org.finiteplay.blackjack.rules

import org.finiteplay.cards.Card

/**
 * A stable 64-bit content hash of [state], for pinning a reference round's exact behaviour across
 * engine changes (`EXECUTION_PLAN.md` "RF — Rules Freeze"). Every input is a card id, an ordinal or
 * a plain number — never a JVM identity hash — so the value is the same across processes,
 * platforms and Kotlin/JVM versions.
 *
 * It covers the whole round: the versions, the bet, how far the shoe has been dealt, both sides'
 * cards, every hand's stake and flags, the insurance, the phase and the settlement. It is never
 * used for play and is not part of the saved format, which is a seed, a bet and a log.
 */
fun canonicalStateHash(state: BlackjackState): Long {
    var h = -3750763034362895579L // FNV-1a 64-bit offset basis
    val prime = 1099511628211L

    fun mixLong(v: Long) {
        h = h xor v
        h *= prime
    }
    fun mixInt(v: Int) = mixLong(v.toLong())
    fun mixCard(card: Card) = mixInt(card.id)

    mixLong(state.seed)
    mixInt(state.rulesVersion)
    mixInt(state.shuffleVersion)
    mixInt(state.bet)
    mixInt(state.shoePosition)

    mixInt(1000 + state.dealer.size)
    for (card in state.dealer) mixCard(card)
    mixInt(if (state.holeRevealed) 1 else 0)

    mixInt(2000 + state.hands.size)
    for (hand in state.hands) {
        mixInt(3000 + hand.cards.size)
        for (card in hand.cards) mixCard(card)
        mixInt(hand.bet)
        mixInt((if (hand.doubled) 1 else 0) + (if (hand.stood) 2 else 0) + (if (hand.fromSplit) 4 else 0) + (if (hand.splitAces) 8 else 0))
    }
    mixInt(state.activeHand)
    mixInt(state.insuranceStake)
    mixInt(state.phase.ordinal)

    val settlement = state.settlement
    mixInt(if (settlement == null) 0 else 1)
    if (settlement != null) {
        mixInt(if (settlement.dealerBlackjack) 1 else 0)
        mixInt(settlement.insuranceDelta)
        for (result in settlement.hands) {
            mixInt(result.outcome.ordinal)
            mixInt(result.stake)
            mixInt(result.delta)
        }
    }
    return h
}
