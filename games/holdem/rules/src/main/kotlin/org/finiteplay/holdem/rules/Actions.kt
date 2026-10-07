package org.finiteplay.holdem.rules

/**
 * What a seat may do (`RULES.md` "Legal Actions"). Amounts are street totals: [Bet.amount] is the
 * chips the seat will have in front of it on this street, and [Raise.total] is the raise-to level.
 * Call and all-in carry no amount; the state resolves them.
 */
sealed interface Action {
    data object Fold : Action
    data object Check : Action
    data object Call : Action
    data class Bet(val amount: Int) : Action
    data class Raise(val total: Int) : Action
    data object AllIn : Action
}

/** One entry of a hand's move log: every seat's action, the opponents' as well as the player's. */
data class SeatAction(val seat: Int, val action: Action)

/**
 * An action as it happened, with what the state resolved it to: [chips] is what it added from the
 * stack, [streetTotal] the seat's total in front of it afterwards, and [allIn] whether it left the
 * seat with nothing. Public information: it is what every seat at a real table saw.
 */
data class HistoryEntry(
    val street: Street,
    val seat: Int,
    val action: Action,
    val chips: Int,
    val streetTotal: Int,
    val allIn: Boolean,
)

/**
 * What the seat to act may do, and nothing else (`RULES.md` "Legal Actions"). An action absent here
 * is never offered rather than offered and refused.
 *
 * [toCall] is what matching the bet costs and may exceed [stack]; [callAmount] is what a Call
 * actually puts in (the whole stack when that is less, a call-all-in) and is null when Call is not
 * offered. [bet] and [raise] are the legal street totals, null when not offered; where the stack is
 * below the minimum they are null and only [allInTo] remains. [allInTo] is the seat's street total
 * after going all in, null unless Bet or Raise is offered.
 */
data class LegalActions(
    val seat: Int,
    val stack: Int,
    val toCall: Int,
    val canFold: Boolean,
    val canCheck: Boolean,
    val callAmount: Int?,
    val bet: IntRange?,
    val raise: IntRange?,
    val allInTo: Int?,
) {
    val canCall: Boolean get() = callAmount != null

    fun allows(action: Action): Boolean = when (action) {
        Action.Fold -> canFold
        Action.Check -> canCheck
        Action.Call -> canCall
        is Action.Bet -> bet?.contains(action.amount) == true
        is Action.Raise -> raise?.contains(action.total) == true
        Action.AllIn -> allInTo != null
    }
}
