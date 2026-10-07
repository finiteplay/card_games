package org.finiteplay.holdem.opponents

import org.finiteplay.holdem.rules.Street
import kotlin.math.exp
import kotlin.math.roundToInt

internal const val Q_FOLD = 0
internal const val Q_CALL = 1
internal const val Q_SMALL = 2
internal const val Q_MEDIUM = 3
internal const val Q_POT = 4
internal const val Q_ALLIN = 5
internal const val Q_SIZES = 6

/** The sizing menu, as fractions of the pot (`DESIGN.md` "The policy"): a third, two thirds, the pot; the last is all in. */
internal val SIZE_FRACTION = doubleArrayOf(0.0, 0.0, 1.0 / 3.0, 2.0 / 3.0, 1.0)

/** A postflop spot: what the actor faced, as the policy sees it. */
internal class PostCtx(
    val street: Street,
    val toCall: Int,
    val pot: Int,
    val currentBet: Int,
    val streetBet: Int,
    val stack: Int,
    val opponents: Int,
    val wet: Double,
    val paired: Boolean,
) {
    val facing get() = toCall > 0

    /** The street total a bet or raise of [f] of the pot makes. */
    fun target(f: Double): Int = if (facing) currentBet + (f * (pot + toCall)).roundToInt() else (f * pot).roundToInt()

    /** A bet that would put in this much of what the seat has is all in. */
    fun commits(f: Double): Boolean = target(f) >= 0.55 * (streetBet + stack)
}

private fun sig(x: Double): Double = 1.0 / (1.0 + exp(-x))

/**
 * The postflop policy: from [e], the hand's equity against the field it faces, and [outs], its cards to
 * come, the probabilities of each action in the menu — fold, check or call, a third pot, two thirds,
 * the pot, all in — into [out]. One function serves the decision and the range model, so a hand is
 * weighted by how likely the policy itself is to play it the way it was played.
 */
internal fun postflopProbs(e: Double, outs: Int, x: PostCtx, p: Profile, out: DoubleArray) {
    out.fill(0.0)
    val a = p.aggression
    val n = x.opponents.coerceAtLeast(1)
    val value = 1.0 / (n + 1) + 0.16
    val strong = value + 0.14
    val river = x.street == Street.RIVER
    val drawy = if (river) 0.0 else minOf(1.0, outs / 15.0)
    val streetBluff = when (x.street) {
        Street.FLOP -> 0.9
        Street.TURN -> 0.7
        else -> 0.6
    }
    val airness = sig((0.42 - e) / 0.08)
    val bluffScale = p.bluff * streetBluff * (1.3 - 0.6 * x.wet) * (if (x.paired) 1.15 else 1.0) * airness
    val strongShare = sig((e - strong) / 0.04)

    if (!x.facing) {
        val pValue = sig((e - (value - 0.10 * (a - 0.5))) / 0.045) * (0.45 + 0.55 * a)
        val rest = 1 - pValue
        val pSemi = rest * drawy * (0.15 + 0.7 * a)
        val pBluff = (rest - pSemi) * bluffScale / n
        val bet = minOf(0.97, pValue + pSemi + pBluff)
        val scale = if (pValue + pSemi + pBluff > 0) bet / (pValue + pSemi + pBluff) else 0.0
        val wet = x.wet
        val s = DoubleArray(3)
        val mild = doubleArrayOf(0.40 - 0.25 * wet, 0.45, 0.15 + 0.25 * wet)
        val big = doubleArrayOf(0.10, 0.40, 0.50)
        val semi = doubleArrayOf(0.20, 0.55, 0.25)
        val air = doubleArrayOf(0.45, 0.45, 0.10)
        for (i in 0..2) {
            s[i] = scale * (pValue * ((1 - strongShare) * mild[i] + strongShare * big[i]) + pSemi * semi[i] + pBluff * air[i])
        }
        out[Q_CALL] = 1 - bet
        for (i in 0..2) {
            val q = Q_SMALL + i
            if (x.commits(SIZE_FRACTION[q])) out[Q_ALLIN] += s[i] else out[q] += s[i]
        }
        return
    }

    val needed = x.toCall.toDouble() / (x.pot + x.toCall)
    val callThr = needed + 0.03 + p.foldToPressure * (0.03 + 0.25 * needed)
    val cont = sig((e - callThr) / 0.045)
    val strongRaise = sig((e - (strong - 0.06 * (a - 0.5))) / 0.04) * (0.25 + 0.65 * a)
    val semiRaise = drawy * (0.10 + 0.45 * a) * (1 - cont * 0.5)
    val bluffRaise = p.bluff * 0.10 * airness * streetBluff
    val raise = minOf(0.9, strongRaise + semiRaise + bluffRaise)
    val call = maxOf(0.0, cont - raise)
    out[Q_FOLD] = maxOf(0.0, 1 - call - raise)
    out[Q_CALL] = call
    val allShare = 0.2 + 0.3 * strongShare
    val mediumShare = (1 - allShare) * 0.6
    val potShare = 1 - allShare - mediumShare
    out[Q_MEDIUM] = if (x.commits(SIZE_FRACTION[Q_MEDIUM])) 0.0 else raise * mediumShare
    out[Q_POT] = if (x.commits(SIZE_FRACTION[Q_POT])) 0.0 else raise * potShare
    out[Q_ALLIN] = raise - out[Q_MEDIUM] - out[Q_POT]
}
