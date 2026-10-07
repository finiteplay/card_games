package org.finiteplay.holdem.ui

/** Where in the hand the sizing row opens, for [SizingRow.defaultAmount]. */
enum class SizingStreet { PreflopUnraised, PreflopRaised, Postflop }

/**
 * The sizing row's range, presets and default, `docs/games/holdem/UI_SPEC.md` "The sizing row". Amounts
 * are the bet, or the raise's total on the street, in whole chips; [max] is the whole stack, which is
 * all in.
 */
data class SizingRow(
    val min: Int,
    val max: Int,
    val halfPot: Int,
    val pot: Int,
    val defaultAmount: Int,
) {
    companion object {
        /**
         * @param pot every chip committed to the hand so far, this street's bets included.
         * @param toCall chips needed to call; 0 for a bet.
         * @param minTotal the smallest legal bet or raise total.
         * @param stack the player's remaining chips.
         * @param bigBlind the current big blind.
         * @param committed chips this player already has in on this street; 0 for a bet.
         */
        fun of(
            pot: Int,
            toCall: Int,
            minTotal: Int,
            stack: Int,
            bigBlind: Int,
            street: SizingStreet,
            isRaise: Boolean,
            committed: Int = 0,
        ): SizingRow {
            val max = committed + stack
            val min = minOf(minTotal, max)
            fun clamp(amount: Int) = amount.coerceIn(min, max)
            val betLevel = committed + toCall
            val potAfterCall = pot + toCall
            val half = if (isRaise) betLevel + potAfterCall / 2 else pot / 2
            val full = if (isRaise) betLevel + potAfterCall else pot
            val default = when (street) {
                SizingStreet.PreflopUnraised -> 3 * bigBlind
                SizingStreet.PreflopRaised -> 3 * betLevel
                SizingStreet.Postflop -> half
            }
            return SizingRow(min, max, clamp(half), clamp(full), clamp(default))
        }
    }
}
