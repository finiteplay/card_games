package org.finiteplay.holdem.rules

/**
 * `RULES.md` "Legal Actions" written out a second time, straight from the text and from raw numbers
 * rather than from [HoldemState], so the engine is held to the document and not to itself.
 */
object LegalOracle {
    fun expected(
        seat: Int,
        stack: Int,
        streetBet: Int,
        currentBet: Int,
        minRaiseIncrement: Int,
        bigBlind: Int,
        bettingOpen: Boolean,
    ): LegalActions {
        val c = maxOf(currentBet - streetBet, 0)
        val betMade = currentBet > 0
        val total = streetBet + stack
        val bet: IntRange? = if (!betMade && stack >= bigBlind) bigBlind..total else null
        val raiseAllowed = betMade && stack > c && bettingOpen
        val minRaise = currentBet + minRaiseIncrement
        val raise: IntRange? = if (raiseAllowed && total >= minRaise) minRaise..total else null
        val allIn: Int? = when {
            !betMade && stack > 0 -> total
            raiseAllowed -> total
            else -> null
        }
        return LegalActions(
            seat = seat,
            stack = stack,
            toCall = c,
            canFold = c > 0,
            canCheck = c == 0,
            callAmount = if (c > 0) minOf(c, stack) else null,
            bet = bet,
            raise = raise,
            allInTo = allIn,
        )
    }

    /** The oracle for the seat to act in [state]; the short all-in rule is read from the seat's last response. */
    fun expected(state: HoldemState): LegalActions? {
        if (state.phase != Phase.BETTING || state.toAct < 0) return null
        val seat = state.toAct
        val acted = state.lastActionBet[seat]
        val open = acted < 0 || state.currentBet - acted >= state.raiseIncrement
        return expected(seat, state.stacks[seat], state.streetBets[seat], state.currentBet, state.raiseIncrement, state.blinds.big, open)
    }
}
