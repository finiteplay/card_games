package org.finiteplay.blackjack.rules

/**
 * The Hint: conventional total-dependent basic strategy for exactly the rules in `RULES.md` "Table
 * Rules", as a fixed table (`DESIGN.md` "Hint"). A lookup, not a search, and a derived artifact of
 * the rules: `BasicStrategyTest` regenerates it offline and fails if any cell differs, so changing a
 * rule means regenerating it.
 *
 * Each row is one letter per dealer up card, in the order 2 3 4 5 6 7 8 9 10 A:
 * `H` hit, `S` stand, `D` double else hit, `d` double else stand, `P` split.
 *
 * It never names an action that is not offered: [recommend] resolves every letter against the legal
 * decisions, falling back as `DESIGN.md` describes — a double entry to hit or stand, a split entry
 * to the pair's total (an Ace pair as soft 12).
 */
object BasicStrategy {
    /** Hard totals 5 through 21; any lower total reads as 5. */
    val HARD: Map<Int, String> = mapOf(
        5 to "HHHHHHHHHH",
        6 to "HHHHHHHHHH",
        7 to "HHHHHHHHHH",
        8 to "HHHHHHHHHH",
        9 to "HDDDDHHHHH",
        10 to "DDDDDDDDHH",
        11 to "DDDDDDDDDH",
        12 to "HHHSSHHHHH",
        13 to "SSSSSHHHHH",
        14 to "SSSSSHHHHH",
        15 to "SSSSSHHHHH",
        16 to "SSSSSHHHHH",
        17 to "SSSSSSSSSS",
        18 to "SSSSSSSSSS",
        19 to "SSSSSSSSSS",
        20 to "SSSSSSSSSS",
        21 to "SSSSSSSSSS",
    )

    /** Soft totals 12 (an Ace pair that cannot split) through 21. */
    val SOFT: Map<Int, String> = mapOf(
        12 to "HHHHDHHHHH",
        13 to "HHHDDHHHHH",
        14 to "HHHDDHHHHH",
        15 to "HHDDDHHHHH",
        16 to "HHDDDHHHHH",
        17 to "HDDDDHHHHH",
        18 to "SddddSSHHH",
        19 to "SSSSSSSSSS",
        20 to "SSSSSSSSSS",
        21 to "SSSSSSSSSS",
    )

    /** Pairs by card value, Ace as 1 and every ten-value card as 10. */
    val PAIRS: Map<Int, String> = mapOf(
        1 to "PPPPPPPPPP",
        2 to "PPPPPPHHHH",
        3 to "PPPPPPHHHH",
        4 to "HHHPPHHHHH",
        5 to "DDDDDDDDHH",
        6 to "PPPPPHHHHH",
        7 to "PPPPPPHHHH",
        8 to "PPPPPPPPPP",
        9 to "PPPPPSPPSS",
        10 to "SSSSSSSSSS",
    )

    /** Column of the dealer's up card: 2..10 are 0..8 and an Ace is 9. */
    private fun column(upValue: Int): Int = if (upValue == 1) 9 else upValue - 2

    /**
     * What basic strategy says to do now, always one of [legalDecisions]; null when nothing is
     * offered. Insurance is always declined: it pays only when the hole card is a ten-value card,
     * which with a fresh six-deck shoe is always less likely than the one in three at which it would
     * break even.
     */
    fun recommend(state: BlackjackState, bankroll: Int): Decision? {
        val legal = legalDecisions(state, bankroll)
        if (legal.isEmpty()) return null
        if (state.phase == Phase.INSURANCE) return Decision.DECLINE_INSURANCE

        val hand = state.hands[state.activeHand]
        val col = column(hardValue(state.dealerUpCard.rank))
        val value = hand.value

        if (hand.isPair && Decision.SPLIT in legal) {
            val letter = PAIRS.getValue(hardValue(hand.cards[0].rank))[col]
            if (letter == 'P') return Decision.SPLIT
        }
        // A pair that is not split is played as its total: an Ace pair as soft 12, any other as hard.
        val letter = if (value.soft && value.total < 21) SOFT.getValue(value.total)[col]
        else HARD.getValue(value.total.coerceIn(5, 21))[col]

        val wanted = when (letter) {
            'H' -> Decision.HIT
            'S' -> Decision.STAND
            'D' -> if (Decision.DOUBLE in legal) Decision.DOUBLE else Decision.HIT
            'd' -> if (Decision.DOUBLE in legal) Decision.DOUBLE else Decision.STAND
            else -> Decision.STAND
        }
        return if (wanted in legal) wanted else Decision.STAND
    }
}
