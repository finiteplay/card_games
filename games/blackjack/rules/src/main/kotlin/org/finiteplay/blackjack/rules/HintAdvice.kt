package org.finiteplay.blackjack.rules

import org.finiteplay.cards.Rank

/**
 * Why the Hint recommends what it does, in the words of a coach. The app turns each into a sentence;
 * the rules module only says which lesson the spot teaches (`DESIGN.md` "Hint").
 */
enum class BlackjackHintReason {
    /** A made hand (hard 17 or more, soft 18 or more): hitting risks a bust for little gain. */
    STAND_STRONG,

    /** A weak total against a dealer card that often busts: let the dealer take the risk. */
    STAND_DEALER_WEAK,

    /** A weak total against a dealer card that often makes a hand: standing usually loses. */
    HIT_DEALER_STRONG,

    /** A hard 11 or less: no card can bust the hand. */
    HIT_CANNOT_BUST,

    /** A soft total: the next card cannot bust it. */
    HIT_SOFT,

    /** A strong spot to put more chips on the table for one card. */
    DOUBLE,

    SPLIT_ACES,
    SPLIT_EIGHTS,

    /** Any other pair worth splitting against this dealer card. */
    SPLIT_PAIR,

    /** Insurance loses chips over time. */
    DECLINE_INSURANCE,
}

/**
 * The odds that make the recommendation make sense, for the active hand against the dealer's up
 * card: how often the dealer busts, how often a hit would bust the player (0 for a soft hand), and
 * how a stand would end now. Computed for an endless deck with the table's dealer rules, so they are
 * close, not exact, for the six-deck shoe.
 */
data class HintOdds(
    val dealerBust: Double,
    val yourBust: Double,
    val standWin: Double,
    val standPush: Double,
    val standLose: Double,
)

/** The recommendation with its lesson: what [BasicStrategy.recommend] says, why, and the odds behind it. */
data class BlackjackAdvice(
    val decision: Decision,
    val reason: BlackjackHintReason,
    val dealerUp: Rank,
    /** The active hand's total, and whether it is soft. */
    val total: Int,
    val soft: Boolean,
    val odds: HintOdds,
)

/** How the dealer's hand ends from an up card, by the table rules (`RULES.md` "Dealer Play"). */
object DealerOdds {
    /** Outcomes: 17, 18, 19, 20, 21, then bust. */
    private const val BUST = 5

    private fun cardWeight(value: Int): Double = if (value == 10) 4.0 / 13.0 else 1.0 / 13.0

    private val memo = HashMap<Int, DoubleArray>()

    private fun finish(hardSum: Int, hasAce: Boolean): DoubleArray {
        val key = hardSum * 2 + if (hasAce) 1 else 0
        memo[key]?.let { return it }
        val out = DoubleArray(6)
        val total = if (hasAce && hardSum + 10 <= 21) hardSum + 10 else hardSum
        when {
            hardSum > 21 -> out[BUST] = 1.0
            total >= 17 -> out[total - 17] = 1.0
            else -> for (v in 1..10) {
                val next = finish(hardSum + v, hasAce || v == 1)
                for (i in 0 until 6) out[i] += cardWeight(v) * next[i]
            }
        }
        memo[key] = out
        return out
    }

    /**
     * The chance the dealer ends on 17, 18, 19, 20, 21 or busts (six entries), given the up card and
     * that the hole card is not blackjack (the dealer has already peeked).
     */
    fun distribution(up: Rank): DoubleArray {
        val upValue = hardValue(up)
        val out = DoubleArray(6)
        var weight = 0.0
        for (v in 1..10) {
            if (up == Rank.ACE && v == 10) continue
            if (isTenValue(up) && v == 1) continue
            val w = cardWeight(v)
            weight += w
            val next = finish(upValue + v, up == Rank.ACE || v == 1)
            for (i in 0 until 6) out[i] += w * next[i]
        }
        for (i in 0 until 6) out[i] /= weight
        return out
    }

    fun bustChance(up: Rank): Double = distribution(up)[BUST]

    /** How a stand on [total] (21 or less) ends against [up]: the chances to win, push and lose. */
    fun standOutcome(total: Int, up: Rank): Triple<Double, Double, Double> {
        val d = distribution(up)
        var win = d[BUST]
        var push = 0.0
        for (final in 17..21) {
            when {
                final < total -> win += d[final - 17]
                final == total -> push += d[final - 17]
            }
        }
        return Triple(win, push, 1.0 - win - push)
    }

    /** The chance a hit busts a hard [total]: 0 up to 11, rising to 1 at 21. */
    fun bustOnHit(total: Int): Double {
        var bust = 0.0
        for (v in 1..10) if (total + v > 21) bust += cardWeight(v)
        return bust
    }
}

/**
 * [BasicStrategy.recommend] with its lesson and odds, or null when nothing is offered. The reason is
 * about what is actually recommended: a double that falls back to a hit is taught as a hit.
 */
fun BasicStrategy.advise(state: BlackjackState, bankroll: Int): BlackjackAdvice? {
    val decision = recommend(state, bankroll) ?: return null
    val up = state.dealerUpCard.rank
    val hand = state.hands[state.activeHand]
    val value = hand.value
    val total = value.total.coerceAtMost(21)
    val (win, push, lose) = DealerOdds.standOutcome(total, up)
    val odds = HintOdds(
        dealerBust = DealerOdds.bustChance(up),
        yourBust = if (value.soft) 0.0 else DealerOdds.bustOnHit(value.total),
        standWin = win,
        standPush = push,
        standLose = lose,
    )
    val weakDealer = hardValue(up) in 2..6 && up != Rank.ACE
    val reason = when (decision) {
        Decision.DECLINE_INSURANCE, Decision.TAKE_INSURANCE -> BlackjackHintReason.DECLINE_INSURANCE
        Decision.DOUBLE -> BlackjackHintReason.DOUBLE
        Decision.SPLIT -> when (hand.cards[0].rank) {
            Rank.ACE -> BlackjackHintReason.SPLIT_ACES
            Rank.EIGHT -> BlackjackHintReason.SPLIT_EIGHTS
            else -> BlackjackHintReason.SPLIT_PAIR
        }
        Decision.HIT -> when {
            value.soft -> BlackjackHintReason.HIT_SOFT
            value.total <= 11 -> BlackjackHintReason.HIT_CANNOT_BUST
            else -> BlackjackHintReason.HIT_DEALER_STRONG
        }
        Decision.STAND -> if (value.total >= 17 || value.soft && value.total >= 18 || !weakDealer) BlackjackHintReason.STAND_STRONG else BlackjackHintReason.STAND_DEALER_WEAK
    }
    return BlackjackAdvice(decision, reason, up, value.total, value.soft, odds)
}
