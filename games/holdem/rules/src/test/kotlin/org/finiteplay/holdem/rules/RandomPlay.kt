package org.finiteplay.holdem.rules

import kotlin.random.Random

/**
 * A random choice among the legal actions. Passive more often than not, with the big commitments
 * (the maximum bet, all in) rare, so tournaments last long enough to reach every street and every
 * kind of pot.
 */
object RandomPlay {
    /** How often, one in this many, a decision may be all in or a maximum bet: a calm table or a wild one. */
    enum class Style(val allInOneIn: Int, val maxOneIn: Int) { CALM(20, 10), WILD(4, 3) }

    fun choose(legal: LegalActions, random: Random, style: Style = Style.CALM): Action {
        val options = ArrayList<Action>()
        if (legal.canFold) repeat(3) { options += Action.Fold }
        if (legal.canCheck) repeat(8) { options += Action.Check }
        if (legal.canCall) repeat(6) { options += Action.Call }
        legal.bet?.let { range ->
            options += Action.Bet(range.first)
            options += Action.Bet(small(range, random))
            options += Action.Bet(small(range, random))
            if (random.nextInt(style.maxOneIn) == 0) options += Action.Bet(range.last)
        }
        legal.raise?.let { range ->
            options += Action.Raise(range.first)
            options += Action.Raise(small(range, random))
            options += Action.Raise(small(range, random))
            if (random.nextInt(style.maxOneIn) == 0) options += Action.Raise(range.last)
        }
        if (legal.allInTo != null && (options.isEmpty() || random.nextInt(20) == 0)) options += Action.AllIn
        return options.random(random)
    }

    /** A size near the minimum, so most bets are modest. */
    private fun small(range: IntRange, random: Random): Int = range.first + random.nextInt(minOf(range.last - range.first + 1, 150))

    /** Plays the hand in [session] to its end with random legal actions. */
    fun finishHand(session: HoldemSession, random: Random): HoldemSession {
        var s = session
        while (s.state.phase == Phase.BETTING) {
            val legal = legalActions(s.state)!!
            s = s.act(legal.seat, choose(legal, random))!!
        }
        return s
    }
}
