package org.finiteplay.holdem.rules

import kotlin.random.Random

/** A random choice among the legal actions, biased toward the rare ones so a soak reaches them. */
object RandomPlay {
    fun choose(legal: LegalActions, random: Random): Action {
        val options = ArrayList<Action>()
        if (legal.canFold) repeat(2) { options += Action.Fold }
        if (legal.canCheck) repeat(4) { options += Action.Check }
        if (legal.canCall) repeat(4) { options += Action.Call }
        legal.bet?.let { range ->
            options += Action.Bet(range.first)
            options += Action.Bet(range.random(random))
            options += Action.Bet(range.last)
            options += Action.Bet(((range.first + range.last) / 2))
        }
        legal.raise?.let { range ->
            options += Action.Raise(range.first)
            options += Action.Raise(range.random(random))
            options += Action.Raise(range.last)
            options += Action.Raise(((range.first + range.last) / 2))
        }
        if (legal.allInTo != null) repeat(2) { options += Action.AllIn }
        return options.random(random)
    }

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
