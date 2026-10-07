package org.finiteplay.holdem.opponents

import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.HoldemState
import org.finiteplay.holdem.rules.Phase
import org.finiteplay.holdem.rules.SeatView
import org.finiteplay.holdem.rules.apply
import org.finiteplay.holdem.rules.nextHand
import org.finiteplay.holdem.rules.seatView
import org.finiteplay.holdem.rules.startTournament

/** Headless seeded tournaments with any mix of policies, one per seat (`EXECUTION_PLAN.md` H7). */
object TournamentRunner {
    /** Called with each view and the action taken, before it is applied; for tests that watch decisions. */
    fun interface Observer {
        fun decided(view: SeatView, action: Action)
    }

    /**
     * Plays the tournament on [seed] to its end, or until [watch] is out, and returns each seat's
     * finishing place (null for a seat still in when play stopped). Every action is checked against
     * the legal actions the view offered; an illegal one throws.
     */
    fun play(seed: Long, policies: List<OpponentPolicy>, watch: Int? = null, observer: Observer? = null): List<Int?> {
        require(policies.size == 6)
        var state: HoldemState = startTournament(seed).state
        while (state.phase != Phase.TOURNAMENT_OVER) {
            if (watch != null && state.places[watch] != null) break
            if (state.phase == Phase.HAND_OVER) {
                state = nextHand(state)!!
                continue
            }
            val seat = state.toAct
            val view = state.seatView(seat)
            val action = policies[seat].decide(view, decisionSalt(state.tournament.handSeed, seat, state.history.size))
            check(view.legal!!.allows(action)) { "seed $seed hand ${state.handNumber} seat $seat: $action is not legal in ${view.legal}" }
            observer?.decided(view, action)
            state = apply(state, seat, action) ?: error("apply refused $action")
        }
        return state.places
    }
}
