package org.finiteplay.blackjack.rules

/**
 * The chip economy (`docs/games/blackjack/RULES.md` "Betting"). Chips are play money: nothing here
 * can be bought, wagered for, or exchanged.
 */
object Chips {
    const val STARTING_BANKROLL = 1_000
    const val MIN_BET = 10
    const val MAX_BET = 500
    const val BET_STEP = 10

    /**
     * The largest bet a bankroll covers, in whole steps and never above [MAX_BET]. Below
     * [MIN_BET] a round cannot be dealt at all, and this returns less than [MIN_BET] to say so.
     */
    fun maxBetFor(bankroll: Int): Int = minOf(MAX_BET, bankroll / BET_STEP * BET_STEP)

    /** Whether [bankroll] can no longer cover a table minimum, so Deal is replaced by the reset. */
    fun needsReset(bankroll: Int): Boolean = bankroll < MIN_BET

    /** [bet] if the bankroll covers it, else the largest bet it does cover — settlement's adjustment. */
    fun betAfterSettlement(selected: Int, bankroll: Int): Int =
        if (needsReset(bankroll)) MIN_BET else selected.coerceIn(MIN_BET, maxBetFor(bankroll))

    /** One step up or down, clamped to what the bankroll covers. */
    fun steppedBet(selected: Int, direction: Int, bankroll: Int): Int =
        (selected + direction * BET_STEP).coerceIn(MIN_BET, maxOf(MIN_BET, maxBetFor(bankroll)))

    /** A bankroll reset: back to the starting stake, with the bet at the table minimum. */
    fun reset(): Int = STARTING_BANKROLL
}
