package org.finiteplay.holdem.rules

import org.finiteplay.core.session.Session

/**
 * A hand on `core/session`'s [Session]: the state and the log of every seat's actions since the
 * hand was dealt, the opponents' as well as the player's (`DESIGN.md` "Architecture").
 *
 * Only the commit-and-log half is used. The undo stack is never written — [act] builds the next
 * session directly rather than through `commit`, which would push the prior state — so `canUndo`
 * is false for the life of a hand and no undo is reachable (`DESIGN.md` "What Hold'em is not").
 *
 * The log is per hand, as the saved hand is (`DESIGN.md` "Persistence"): [nextHand] starts a new
 * one, and a hand is restored from [Tournament] (its state when dealt) plus its log.
 */
typealias HoldemSession = Session<HoldemState, SeatAction>

/** A new tournament on [seed]: stacks set, the first button drawn, hand 1 dealt with its blinds posted. */
fun startTournament(seed: Long): HoldemSession = sessionOf(dealHand(Tournament.start(seed)))

/** A session on a hand test fixtures built; the contract's only entry points are [startTournament] and [replayHand]. */
internal fun sessionOf(state: HoldemState): HoldemSession = Session.start(state)

/** Applies [action] by [seat] and logs it, or returns null when it is not currently legal. */
fun HoldemSession.act(seat: Int, action: Action): HoldemSession? {
    val next = apply(state, seat, action) ?: return null
    return copy(state = next, log = log + SeatAction(seat, action))
}

/** The next hand, dealt, with an empty log; null unless this hand is over and the tournament is not. */
fun HoldemSession.nextHand(): HoldemSession? = nextHand(state)?.let(::sessionOf)

/** The player leaves the tournament (`RULES.md` "Leaving"); not logged, since a left tournament is never resumed. */
fun HoldemSession.leave(): HoldemSession? = leave(state)?.let { copy(state = it) }

/**
 * Rebuilds a hand from the tournament as it stood when it was dealt and every seat's actions since:
 * the only way a saved hand is restored. Null when any entry is illegal at its point, which a caller
 * treats as a corrupt save.
 */
fun replayHand(tournament: Tournament, log: List<SeatAction>): HoldemSession? {
    if (tournament.alive.size < 2) return null
    var session = sessionOf(dealHand(tournament))
    for (entry in log) session = session.act(entry.seat, entry.action) ?: return null
    return session
}

/**
 * Replays a whole tournament from its seed: one log per hand, each hand dealt as [nextHand] deals
 * it. Null when any entry is illegal or a hand's log does not end it where the next begins.
 */
fun replayTournament(seed: Long, hands: List<List<SeatAction>>): HoldemSession? {
    var session = startTournament(seed)
    for ((i, log) in hands.withIndex()) {
        if (i > 0) session = session.nextHand() ?: return null
        for (entry in log) session = session.act(entry.seat, entry.action) ?: return null
    }
    return session
}
