package org.finiteplay.holdem.testing

import org.finiteplay.holdem.opponents.OpponentPolicy
import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.HandResult
import org.finiteplay.holdem.rules.HoldemSession
import org.finiteplay.holdem.rules.HoldemState
import org.finiteplay.holdem.rules.LegalActions
import org.finiteplay.holdem.rules.Phase
import org.finiteplay.holdem.rules.SeatView
import org.finiteplay.holdem.rules.act
import org.finiteplay.holdem.rules.nextHand
import org.finiteplay.holdem.rules.seatView
import org.finiteplay.holdem.rules.startTournament
import org.finiteplay.holdem.ui.Preset
import org.finiteplay.holdem.ui.Sizing

/**
 * Shared by the unit tests and the instrumented tests (`src/scenarios`, added to both source sets):
 * what a scripted seat does, so a fixed tournament seed plays out the same headless and through the
 * screen. A seat's [Script] is a function of its own view alone, so the same one drives an opponent
 * through `OpponentPolicy` and the player through the buttons.
 */
enum class Move { FOLD, CHECK, CALL, ALL_IN, CHECK_OR_FOLD, CHECK_OR_CALL, MIN_RAISE, BET_DEFAULT, BET_HALF_POT, BET_POT }

fun interface Script {
    fun choose(view: SeatView): Move
}

/** The sizing moves are the player's: they go through the sizing row, which an opponent's view cannot build. */
val Move.usesSizing: Boolean get() = this == Move.BET_DEFAULT || this == Move.BET_HALF_POT || this == Move.BET_POT

/** The action [move] makes when [legal] is what the seat may do; [sizing] is the row for the sizing moves. */
fun resolve(move: Move, legal: LegalActions, sizing: Sizing?): Action {
    val checkOrCall = if (legal.canCheck) Action.Check else Action.Call
    return when (move) {
        Move.FOLD -> if (legal.canFold) Action.Fold else Action.Check
        Move.CHECK -> if (legal.canCheck) Action.Check else Action.Call
        Move.CALL -> if (legal.canCall) Action.Call else Action.Check
        Move.CHECK_OR_FOLD -> if (legal.canCheck) Action.Check else Action.Fold
        Move.CHECK_OR_CALL -> checkOrCall
        Move.ALL_IN -> if (legal.allInTo != null) Action.AllIn else checkOrCall
        Move.MIN_RAISE -> when {
            legal.raise != null -> Action.Raise(legal.raise!!.first)
            legal.bet != null -> Action.Bet(legal.bet!!.first)
            else -> checkOrCall
        }
        Move.BET_DEFAULT, Move.BET_HALF_POT, Move.BET_POT -> when {
            sizing == null -> checkOrCall
            move == Move.BET_HALF_POT -> sizing.preset(Preset.HALF_POT).action()
            move == Move.BET_POT -> sizing.preset(Preset.POT).action()
            else -> sizing.action()
        }
    }
}

/** An opponent that plays [script]; its moves may not use the sizing row. */
fun scriptedPolicy(script: Script) = OpponentPolicy { view, _ ->
    val move = script.choose(view)
    require(!move.usesSizing) { "an opponent cannot size a bet" }
    resolve(move, checkNotNull(view.legal), null)
}

/** What a seat does in [state], player or opponent, exactly as the screen and the policies do. */
fun scriptedAction(state: HoldemState, seat: Int, script: Script): Action {
    val view = state.seatView(seat)
    val legal = checkNotNull(view.legal)
    return resolve(script.choose(view), legal, Sizing.of(state, legal))
}

/** One hand of a headless run: where it ended and what the stacks were then. */
data class HandRecord(val state: HoldemState) {
    val result: HandResult get() = checkNotNull(state.result)
    val stacks: List<Int> get() = state.stacks
}

/** A tournament played headless under scripts. */
data class Run(val hands: List<HandRecord>, val session: HoldemSession) {
    val last: HoldemState get() = session.state
}

/**
 * Plays [seed] under [player] and [opponents] (by seat, 1 to 5) until the tournament is over for the
 * player or [maxHands] hands are done, whichever is first.
 */
fun runHeadless(seed: Long, player: Script, opponents: (Int) -> Script, maxHands: Int): Run {
    var session = startTournament(seed)
    val hands = ArrayList<HandRecord>()
    while (true) {
        val state = session.state
        when {
            state.phase == Phase.BETTING -> {
                val seat = state.toAct
                val action = scriptedAction(state, seat, if (seat == 0) player else opponents(seat))
                session = checkNotNull(session.act(seat, action)) { "$action was not legal for seat $seat" }
            }
            else -> {
                hands += HandRecord(state)
                if (state.phase == Phase.TOURNAMENT_OVER || state.playerOut || hands.size >= maxHands) return Run(hands, session)
                session = checkNotNull(session.nextHand())
            }
        }
    }
}
