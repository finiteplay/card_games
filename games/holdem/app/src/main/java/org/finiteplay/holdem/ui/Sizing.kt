package org.finiteplay.holdem.ui

import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.HoldemState
import org.finiteplay.holdem.rules.LegalActions
import org.finiteplay.holdem.rules.Street

enum class Preset { HALF_POT, POT }

/**
 * The sizing row's state (`UI_SPEC.md` "The sizing row"): the legal range with its presets, and the
 * [amount] now chosen — always inside the range, so the action it makes is always legal.
 */
data class Sizing(val row: SizingRow, val amount: Int, val raise: Boolean, val step: Int) {
    val canStepDown: Boolean get() = amount > row.min
    val canStepUp: Boolean get() = amount < row.max

    fun stepped(direction: Int): Sizing = copy(amount = (amount + direction * step).coerceIn(row.min, row.max))

    fun preset(preset: Preset): Sizing = copy(amount = if (preset == Preset.HALF_POT) row.halfPot else row.pot)

    /** The bet, or the raise to the street total, the row now reads. */
    fun action(): Action = if (raise) Action.Raise(amount) else Action.Bet(amount)

    companion object {
        /** The row for [seat]'s decision in [state], opened at its default; null when neither Bet nor Raise is offered. */
        fun of(state: HoldemState, legal: LegalActions): Sizing? {
            val range = legal.raise ?: legal.bet ?: return null
            val raise = legal.raise != null
            val big = state.blinds.big
            val street = when {
                state.street != Street.PREFLOP -> SizingStreet.Postflop
                state.currentBet <= big -> SizingStreet.PreflopUnraised
                else -> SizingStreet.PreflopRaised
            }
            val row = SizingRow.of(
                pot = state.pot,
                toCall = legal.toCall,
                minTotal = range.first,
                stack = legal.stack,
                bigBlind = big,
                street = street,
                isRaise = raise,
                committed = state.streetBets[legal.seat],
            )
            return Sizing(row, row.defaultAmount, raise, big)
        }
    }
}
