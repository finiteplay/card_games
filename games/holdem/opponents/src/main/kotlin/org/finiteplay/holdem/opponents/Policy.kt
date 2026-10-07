package org.finiteplay.holdem.opponents

import org.finiteplay.cards.SplitMix64
import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.LegalActions
import org.finiteplay.holdem.rules.SeatView
import org.finiteplay.holdem.rules.Street

/**
 * The salt for one decision, from the hand's seed, the seat and the action's index in the hand's log
 * (`DESIGN.md` "The policy"). The caller knows the seed; the policy never does.
 */
fun decisionSalt(handSeed: Long, seat: Int, actionIndex: Int): Long = mix(mix(handSeed, seat.toLong()), actionIndex.toLong())

/** The one policy, played with a [Profile]'s parameters (`DESIGN.md` "The policy"). */
class ProfilePolicy(val profile: Profile) : OpponentPolicy {
    override fun decide(view: SeatView, salt: Long): Action = Brain.decide(view, salt, profile, Brain.DECISION_SAMPLES, mostLikely = false).action
}

internal class Decision(val action: Action, val equity: Double)

internal object Brain {
    /** Fixed, never time-bounded: the same view must give the same answer on any device. */
    const val DECISION_SAMPLES = 600
    const val HINT_SAMPLES = 3000

    private const val MC_DOMAIN = 0x4D43L

    fun decide(view: SeatView, salt: Long, profile: Profile, samples: Int, mostLikely: Boolean): Decision {
        val legal = checkNotNull(view.legal) { "seat ${view.seat} is not to act" }
        val sit = Situation(view)
        val rng = SplitMix64(mix(mix(salt, view.seat.toLong()), view.history.size.toLong()))
        val mc = SplitMix64(mix(rng.nextULong().toLong(), MC_DOMAIN))
        return if (view.street == Street.PREFLOP) preflop(sit, legal, profile, rng, mc, samples, mostLikely) else postflop(sit, legal, profile, rng, mc, samples, mostLikely)
    }

    /** The share of the pot the seat wins on average against the hands the actions so far make likely. */
    fun equity(sit: Situation, samples: Int, mc: SplitMix64): Double {
        val view = sit.view
        val ranges = estimateRanges(sit, Profiles.strongest)
        return Equity.estimate(view.holeCards[0].id, view.holeCards[1].id, sit.boardIds, ranges, samples, mc)
    }

    private fun pick(p: DoubleArray, rng: SplitMix64, mostLikely: Boolean): Int {
        var total = 0.0
        for (v in p) total += v
        if (total <= 0.0) return -1
        if (mostLikely) {
            var best = 0
            for (i in p.indices) if (p[i] > p[best]) best = i
            return best
        }
        var u = rng.nextDouble() * total
        for (i in p.indices) {
            u -= p[i]
            if (u < 0) return i
        }
        return p.indices.last { p[it] > 0 }
    }

    private fun preflop(sit: Situation, legal: LegalActions, profile: Profile, rng: SplitMix64, mc: SplitMix64, samples: Int, mostLikely: Boolean): Decision {
        val view = sit.view
        val c = HandClasses.of(view.holeCards[0].id, view.holeCards[1].id)
        val x = PreCtx(
            effBb = sit.effectiveBb(sit.me),
            nLive = sit.live,
            behind = sit.behind[sit.me],
            raises = sit.bets,
            limpers = sit.limpers,
            toCall = view.toCall,
            pot = view.pot,
            stack = legal.stack,
            streetBet = view.streetBets[sit.me],
            currentBet = view.currentBet,
            raiserBehind = sit.lastRaiserBehind,
        )
        val p = DoubleArray(4)
        preflopProbs(c, x, profile, p)
        if (legal.raise == null) {
            p[P_ALLIN] += p[P_RAISE]
            p[P_RAISE] = 0.0
        }
        if (legal.allInTo == null) {
            p[P_CALL] += p[P_ALLIN]
            p[P_ALLIN] = 0.0
        }
        val action = when (pick(p, rng, mostLikely)) {
            P_RAISE -> Action.Raise(x.target(x.raiseFraction).coerceIn(legal.raise!!))
            P_ALLIN -> Action.AllIn
            P_FOLD -> if (legal.canFold) Action.Fold else Action.Check
            else -> passive(legal)
        }
        return Decision(action, Double.NaN)
    }

    private fun passive(legal: LegalActions): Action = when {
        legal.canCheck -> Action.Check
        legal.canCall -> Action.Call
        else -> Action.Fold
    }

    private fun postflop(sit: Situation, legal: LegalActions, profile: Profile, rng: SplitMix64, mc: SplitMix64, samples: Int, mostLikely: Boolean): Decision {
        val view = sit.view
        val h0 = view.holeCards[0].id
        val h1 = view.holeCards[1].id
        val count = sit.boardIds.size
        val ranges = estimateRanges(sit, Profiles.strongest)
        val live = sit.opponents.size
        val equity = Equity.estimate(h0, h1, sit.boardIds, ranges, samples, mc)
        val outs = drawOuts(h0, h1, sit.boardIds, count)
        val x = PostCtx(
            street = view.street,
            toCall = view.toCall,
            pot = view.pot,
            currentBet = view.currentBet,
            streetBet = view.streetBets[sit.me],
            stack = legal.stack,
            opponents = live,
            wet = wetness(sit.boardIds, count),
            paired = isPaired(sit.boardIds, count),
        )
        val p = DoubleArray(Q_SIZES)
        postflopProbs(equity, outs, x, profile, p)
        val canSize = if (x.facing) legal.raise != null else legal.bet != null
        for (q in Q_SMALL..Q_POT) if (!canSize) {
            p[Q_ALLIN] += p[q]
            p[q] = 0.0
        }
        if (legal.allInTo == null) {
            p[Q_CALL] += p[Q_ALLIN]
            p[Q_ALLIN] = 0.0
        }
        if (!legal.canFold) {
            p[Q_CALL] += p[Q_FOLD]
            p[Q_FOLD] = 0.0
        }
        val q = pick(p, rng, mostLikely)
        val action = when (q) {
            Q_FOLD -> Action.Fold
            Q_SMALL, Q_MEDIUM, Q_POT -> {
                val total = x.target(SIZE_FRACTION[q])
                if (x.commits(SIZE_FRACTION[q]) && legal.allInTo != null) Action.AllIn
                else if (x.facing) Action.Raise(total.coerceIn(legal.raise!!)) else Action.Bet(total.coerceIn(legal.bet!!))
            }
            Q_ALLIN -> Action.AllIn
            else -> passive(legal)
        }
        return Decision(action, equity)
    }
}
