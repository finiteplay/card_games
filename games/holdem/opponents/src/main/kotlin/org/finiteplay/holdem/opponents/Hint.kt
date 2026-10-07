package org.finiteplay.holdem.opponents

import org.finiteplay.cards.SplitMix64
import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.SeatView

/**
 * The Hint (`DESIGN.md` "Hint"): the player's [equity] (the share of the pot their hand wins on
 * average against the hands the actions so far make likely), the [potOdds] (what a call costs against
 * what the pot would then hold, 0 when nothing is owed), and the [suggested] action, which is always
 * legal for the view.
 */
data class HintResult(val equity: Double, val potOdds: Double, val suggested: Action)

/**
 * The hint for the seat to act in [view], from that view alone, with a fixed sample count and a seed
 * derived from the view: asking twice gives the same answer. The suggestion is what the strongest
 * profile would play in the seat, preferring its most likely action to a drawn one.
 */
fun hint(view: SeatView): HintResult {
    val legal = checkNotNull(view.legal) { "seat ${view.seat} is not to act" }
    val seed = viewSeed(view)
    val decision = Brain.decide(view, seed, Profiles.strongest, Brain.HINT_SAMPLES, mostLikely = true)
    val equity = if (decision.equity.isNaN()) {
        Brain.equity(Situation(view), Brain.HINT_SAMPLES, SplitMix64(mix(seed, 0x484EL)))
    } else {
        decision.equity
    }
    val owed = minOf(view.toCall, legal.stack)
    return HintResult(equity, if (owed == 0) 0.0 else owed.toDouble() / (view.pot + owed), decision.action)
}

/** A stable number for a view: from the cards, the stacks and the actions, never from object identity. */
internal fun viewSeed(view: SeatView): Long {
    var h = mix(view.seat.toLong(), view.handNumber.toLong())
    for (c in view.holeCards) h = mix(h, c.id.toLong())
    h = mix(h, 100L)
    for (c in view.board) h = mix(h, c.id.toLong())
    h = mix(h, 101L)
    for (s in view.stacks) h = mix(h, s.toLong())
    h = mix(h, view.button.toLong())
    for (e in view.history) {
        h = mix(h, e.seat.toLong() * 8 + e.street.ordinal)
        h = mix(h, e.chips.toLong())
        h = mix(h, e.streetTotal.toLong())
    }
    return h
}
