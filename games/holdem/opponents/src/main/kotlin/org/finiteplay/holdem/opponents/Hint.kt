package org.finiteplay.holdem.opponents

import org.finiteplay.cards.SplitMix64
import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.LegalActions
import org.finiteplay.holdem.rules.SeatView
import org.finiteplay.holdem.rules.Street
import kotlin.math.roundToInt

/**
 * Why the hint suggests what it does, in the terms a coach would use. The app turns each into a
 * sentence in the player's language; the engine only decides which lesson the spot teaches.
 */
enum class HintReason {
    /** Preflop, a top hand: raise to build the pot while ahead. */
    PREMIUM_RAISE,

    /** Preflop, a good hand from late position: raising takes the initiative and can win the blinds. */
    LATE_POSITION_RAISE,

    /** Preflop, a good hand for the spot: raising takes the initiative. */
    PLAYABLE_RAISE,

    /** Preflop, worth a flop at this price but not a raise. */
    CALL_TO_SEE_FLOP,

    /** Preflop in the big blind with nothing to call: the flop is free. */
    FREE_FLOP,

    /** Preflop, a weak hand: folding costs nothing. */
    FOLD_WEAK,

    /** Preflop, facing a raise with a hand not strong enough for the price. */
    FOLD_TO_RAISE,

    /** Short stack, a hand strong enough to push all-in. */
    PUSH_SHORT_STACK,

    /** Short stack, a hand too weak to risk everything on. */
    FOLD_SHORT_STACK,

    /** Postflop, probably ahead and first to bet: bet so weaker hands pay. */
    VALUE_BET,

    /** Postflop, a strong hand facing a bet: raise to put more chips in while ahead. */
    VALUE_RAISE,

    /** Postflop, a strong draw: betting can win now and the hand can still improve. */
    SEMI_BLUFF,

    /** Postflop, the call wins more often than its price requires. */
    CALL_GOOD_PRICE,

    /** Postflop, a drawing hand whose price is justified by the cards still to come. */
    CALL_DRAW,

    /** Postflop, the hand wins less often than the call's price requires. */
    FOLD_BAD_PRICE,

    /** Postflop, nothing to call and not much to bet with: a free card. */
    FREE_CARD,

    /** Postflop, a middling hand: check to keep the pot small. */
    POT_CONTROL,
}

/**
 * The Hint (`DESIGN.md` "Hint"): the player's [equity] (the share of the pot their hand wins on
 * average against the hands the actions so far make likely), the [potOdds] (what a call costs against
 * what the pot would then hold, 0 when nothing is owed), and the [suggested] action, which is always
 * legal for the view. [reason] is the lesson of the spot; [outs] counts the cards that complete a
 * flush or straight draw (0 off the flop and turn), [stackBb] is the effective stack in big blinds,
 * and [threats] what the opponents may already hold against the player (null before the flop).
 */
data class HintResult(
    val equity: Double,
    val potOdds: Double,
    val suggested: Action,
    val reason: HintReason = HintReason.POT_CONTROL,
    val outs: Int = 0,
    val stackBb: Int = 0,
    val threats: Threats? = null,
)

/**
 * The hint for the seat to act in [view], from that view alone, with a fixed sample count and a seed
 * derived from the view: asking twice gives the same answer. The suggestion is what the strongest
 * profile would play in the seat, preferring its most likely action to a drawn one, then held to a
 * coach's rules: never fold when the price is right, bet a strong hand rather than hide it, raise a
 * monster that faces a bet.
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
    val potOdds = if (owed == 0) 0.0 else owed.toDouble() / (view.pot + owed)
    val sit = Situation(view)
    val stackBb = sit.effectiveBb(sit.me).toInt()
    val board = IntArray(view.board.size) { view.board[it].id }
    val outs = if (view.street == Street.PREFLOP) 0 else drawOuts(view.holeCards[0].id, view.holeCards[1].id, board, board.size)
    val action = if (view.street == Street.PREFLOP) decision.action else coach(view, legal, decision.action, equity, potOdds)
    val reason = if (view.street == Street.PREFLOP) preflopReason(view, sit, action, stackBb) else postflopReason(view, action, equity, potOdds, outs)
    val threats = threatsOf(sit, Threats.SAMPLES, SplitMix64(mix(seed, 0x7448L)))
    return HintResult(equity, potOdds, action, reason, outs, stackBb, threats)
}

/** Equity above the price by this much makes a call profitable enough that folding would be a mistake. */
private const val CALL_MARGIN = 0.01

/** Past this equity a hand is a value hand: bet or raise it, do not hide it. */
private const val VALUE_EQUITY = 0.70
private const val MONSTER_EQUITY = 0.80
private const val STRONG_EQUITY = 0.60
private const val MIDDLING_EQUITY = 0.45

/** The top of the starting-hand ranking: about the best 4% of the 1,326 combinations, in basis points. */
private const val PREMIUM_BP = 450

private fun coach(view: SeatView, legal: LegalActions, base: Action, equity: Double, potOdds: Double): Action {
    if (view.toCall > 0) {
        if (base == Action.Fold && legal.canCall && equity >= potOdds + CALL_MARGIN) return Action.Call
        if (base == Action.Call && equity >= MONSTER_EQUITY) valueRaise(view, legal)?.let { return it }
    } else if (base == Action.Check && equity >= VALUE_EQUITY) {
        valueBet(view, legal)?.let { return it }
    }
    return base
}

private fun valueBet(view: SeatView, legal: LegalActions): Action? {
    val range = legal.bet ?: return null
    val target = (view.pot * 2.0 / 3.0).roundToInt().coerceIn(range)
    return if (legal.allInTo != null && target >= 0.55 * (view.streetBets[view.seat] + legal.stack)) Action.AllIn else Action.Bet(target)
}

private fun valueRaise(view: SeatView, legal: LegalActions): Action? {
    val range = legal.raise ?: return null
    val target = (view.currentBet + (view.pot + view.toCall) * 2.0 / 3.0).roundToInt().coerceIn(range)
    return if (legal.allInTo != null && target >= 0.55 * (view.streetBets[view.seat] + legal.stack)) Action.AllIn else Action.Raise(target)
}

private fun isAggressive(a: Action) = a is Action.Bet || a is Action.Raise || a == Action.AllIn

private fun preflopReason(view: SeatView, sit: Situation, action: Action, stackBb: Int): HintReason {
    val short = stackBb <= PreflopRanges.MAX_PUSH_STACK
    val c = HandClasses.of(view.holeCards[0].id, view.holeCards[1].id)
    val top = PreflopTables.classHigh[c]
    val facingRaise = view.toCall > view.blinds.big
    val late = sit.behind[sit.me] in 2..3
    return when {
        isAggressive(action) -> when {
            short -> HintReason.PUSH_SHORT_STACK
            top <= PREMIUM_BP || facingRaise -> HintReason.PREMIUM_RAISE
            late -> HintReason.LATE_POSITION_RAISE
            else -> HintReason.PLAYABLE_RAISE
        }
        short && action == Action.Call && facingRaise -> HintReason.PUSH_SHORT_STACK
        action == Action.Fold -> when {
            short -> HintReason.FOLD_SHORT_STACK
            facingRaise -> HintReason.FOLD_TO_RAISE
            else -> HintReason.FOLD_WEAK
        }
        view.toCall == 0 -> HintReason.FREE_FLOP
        else -> HintReason.CALL_TO_SEE_FLOP
    }
}

private fun postflopReason(view: SeatView, action: Action, equity: Double, potOdds: Double, outs: Int): HintReason = when {
    isAggressive(action) -> when {
        equity >= STRONG_EQUITY -> if (view.toCall > 0) HintReason.VALUE_RAISE else HintReason.VALUE_BET
        outs > 0 -> HintReason.SEMI_BLUFF
        else -> if (view.toCall > 0) HintReason.VALUE_RAISE else HintReason.VALUE_BET
    }
    action == Action.Fold -> HintReason.FOLD_BAD_PRICE
    action == Action.Call -> if (equity >= potOdds) HintReason.CALL_GOOD_PRICE else HintReason.CALL_DRAW
    equity >= MIDDLING_EQUITY -> HintReason.POT_CONTROL
    else -> HintReason.FREE_CARD
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
