package org.finiteplay.holdem.opponents

import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The hand-built half of the preflop tables: how wide each spot's range is, in basis points of all
 * 1,326 combinations (1,500 is the top 15% of hands, by the committed ranking in [PreflopTables]).
 * Arrays are indexed by the seats still to act behind the player — 0 the big blind, 1 the small
 * blind, 2 the button, up to 5 for the first to act at a full table — so a short-handed table needs
 * no table of its own. Profiles scale them (`Profile.looseness`).
 */
internal object PreflopRanges {
    /** First in: raise or limp. */
    val open = intArrayOf(0, 5000, 4200, 2800, 1900, 1500)

    /** Raise over limpers. */
    val iso = intArrayOf(2200, 2000, 2600, 2000, 1600, 1300)

    /** Re-raise a single raise for value. */
    val threeBet = intArrayOf(800, 800, 700, 550, 450, 400)

    /** Continue, by calling or re-raising, against a single raise at the pot odds of a 2.5 big blind open. */
    val callRaise = intArrayOf(2400, 1100, 1500, 1100, 850, 750)

    /** Re-raise against a re-raise. */
    val fourBet = intArrayOf(300, 300, 300, 300, 300, 300)

    /** Continue against a re-raise. */
    val call3Bet = intArrayOf(700, 600, 650, 600, 550, 500)

    /** At 15 big blinds, how wide the first to act pushes, by seats behind; narrower for fewer chips, wider for more. */
    val pushAt15 = intArrayOf(0, 4500, 3500, 2500, 1800, 1400)

    /** The pot odds, as the share the call must win, each call-off row is built for. */
    val needed = doubleArrayOf(0.28, 0.35, 0.42, 0.50, 0.60)

    /** How wide the pusher a call-off row assumes pushes: a seat two to act behind, at the row's stack. */
    const val ASSUMED_PUSHER_BEHIND = 3

    const val MAX_PUSH_STACK = 15

    fun pushWidth(stackBb: Int, behind: Int): Int =
        (pushAt15[behind.coerceIn(1, 5)] * (MAX_PUSH_STACK.toDouble() / stackBb).pow(0.85)).roundToInt().coerceAtMost(10_000)

    fun neededBucket(needed: Double): Int {
        for (i in this.needed.indices) if (needed <= this.needed[i]) return i
        return this.needed.size - 1
    }
}

/** What a seat decides preflop: the spot, as the policy sees it. */
internal class PreCtx(
    val effBb: Double,
    val nLive: Int,
    val behind: Int,
    val raises: Int,
    val limpers: Int,
    val toCall: Int,
    val pot: Int,
    val stack: Int,
    val streetBet: Int,
    val currentBet: Int,
    val raiserBehind: Int,
) {
    val unopened get() = raises == 0 && limpers == 0
    val raiseFraction get() = if (unopened) 2.0 / 3.0 else 1.0

    fun target(f: Double): Int = currentBet + (f * (pot + toCall)).roundToInt()

    /** A raise that would put in this much of the stack is a shove. */
    fun commits(f: Double): Boolean = target(f) >= 0.4 * (streetBet + stack)
}

internal const val P_FOLD = 0
internal const val P_CALL = 1
internal const val P_RAISE = 2
internal const val P_ALLIN = 3

private fun freq(c: Int, widthBp: Double): Double {
    val lo = PreflopTables.classLow[c]
    val hi = PreflopTables.classHigh[c]
    return ((widthBp - lo) / (hi - lo)).coerceIn(0.0, 1.0)
}

/**
 * The preflop policy for starting-hand class [c]: the probabilities of folding, calling (checking),
 * raising to the menu's size, and going all in, into [out]. At an effective stack of 15 big blinds or
 * less it is push-or-fold and nothing else.
 */
internal fun preflopProbs(c: Int, x: PreCtx, p: Profile, out: DoubleArray) {
    out.fill(0.0)
    if (x.effBb <= PreflopRanges.MAX_PUSH_STACK) {
        pushOrFold(c, x, p, out)
        return
    }
    val b = x.behind.coerceIn(0, 5)
    val l = p.looseness
    val a = p.aggression
    val canCheck = x.toCall == 0
    var call: Double
    var raise: Double
    when {
        x.unopened -> {
            if (canCheck) {
                call = 1.0
                raise = 0.0
            } else {
                val openWidth = PreflopRanges.open[b] * l
                val openF = freq(c, openWidth)
                val raiseShare = 0.5 + 0.5 * a
                raise = openF * raiseShare
                var limp = openF * (1 - raiseShare) + (freq(c, openWidth * (1 + 0.6 * (1 - a))) - openF) * (1 - a)
                if (b == 1) limp = maxOf(limp, freq(c, maxOf(PreflopRanges.open[b], 6500) * l) - raise)
                call = minOf(1 - raise, limp)
            }
        }
        x.raises == 0 -> {
            val isoF = freq(c, PreflopRanges.iso[b] * l)
            raise = isoF * (0.35 + 0.65 * a)
            call = if (canCheck) 1 - raise else minOf(1 - raise, maxOf(0.0, freq(c, maxOf(PreflopRanges.iso[b], PreflopRanges.open[b] * 7 / 10) * l) - raise))
        }
        else -> {
            val tc = minOf(x.toCall, x.stack).toDouble()
            val needed = (tc / (x.pot + tc)).coerceAtLeast(0.05)
            val odds = (0.38 / needed).pow(1.2).coerceIn(0.25, 2.0)
            val rf = if (x.raiserBehind < 0) 1.0 else sqrt((if (x.raiserBehind == 0) 2000 else PreflopRanges.open[x.raiserBehind]) / 2800.0).coerceIn(0.7, 1.4)
            val fear = 1 - 0.35 * p.foldToPressure
            if (x.raises == 1) {
                val threeBet = freq(c, PreflopRanges.threeBet[b] * l * rf)
                val callWidth = PreflopRanges.callRaise[b] * l * rf * odds * fear
                val cont = freq(c, maxOf(callWidth, PreflopRanges.threeBet[b] * l * rf))
                val valueRaise = threeBet * (0.55 + 0.45 * a)
                val bluff = (freq(c, callWidth * 1.4) - freq(c, callWidth)).coerceAtLeast(0.0) * p.bluff * 0.7
                raise = minOf(1.0, valueRaise + bluff)
                call = minOf(1 - raise, maxOf(0.0, cont - valueRaise))
            } else {
                val fourBet = freq(c, PreflopRanges.fourBet[b] * l)
                val callWidth = PreflopRanges.call3Bet[b] * l * rf * odds * fear
                val cont = freq(c, maxOf(callWidth, PreflopRanges.fourBet[b] * l))
                raise = fourBet * (0.7 + 0.3 * a)
                call = minOf(1 - raise, maxOf(0.0, cont - fourBet))
            }
        }
    }
    var allIn = 0.0
    if (raise > 0 && x.commits(x.raiseFraction)) {
        allIn = raise
        raise = 0.0
    }
    out[P_FOLD] = maxOf(0.0, 1 - call - raise - allIn)
    out[P_CALL] = call
    out[P_RAISE] = raise
    out[P_ALLIN] = allIn
}

private fun pushOrFold(c: Int, x: PreCtx, p: Profile, out: DoubleArray) {
    val idx = ceil(x.effBb / p.looseness * (if (x.nLive == 2) 0.6 else 1.0)).toInt().coerceIn(1, PreflopRanges.MAX_PUSH_STACK)
    if (x.raises == 0) {
        when {
            PreflopTables.pushSet(idx, x.behind.coerceAtLeast(1))[c] -> out[P_ALLIN] = 1.0
            x.toCall == 0 -> out[P_CALL] = 1.0
            else -> out[P_FOLD] = 1.0
        }
        return
    }
    val tc = minOf(x.toCall, x.stack).toDouble()
    val needed = if (tc == 0.0) 0.0 else tc / (x.pot + tc)
    when {
        PreflopTables.callPushSet(idx, PreflopRanges.neededBucket(needed))[c] ->
            if (x.toCall >= x.stack || x.toCall == 0) out[P_CALL] = 1.0 else out[P_ALLIN] = 1.0
        x.toCall == 0 -> out[P_CALL] = 1.0
        else -> out[P_FOLD] = 1.0
    }
}
