package org.finiteplay.holdem.storage

import org.finiteplay.holdem.rules.HoldemSession
import org.finiteplay.holdem.rules.HoldemState
import org.finiteplay.holdem.rules.Phase

/**
 * The two stores and the order they are written in (`docs/games/holdem/DESIGN.md` "Persistence"),
 * so the view model cannot get the order wrong:
 *
 * - [restore] before any table is shown.
 * - [beginTournament] writes the tournament, with the statistics, before its first hand is dealt.
 * - [saveHand] after every action and before its result is displayed; also the hand's first
 *   save, with an empty log, before its cards are.
 * - [settleHand] / [leave] when a hand ends or the player leaves: the one whole write of the
 *   tournament and statistics, and only then the hand save is cleared. A kill between the two
 *   leaves a save whose number is no longer the tournament's current hand, which [restore] drops.
 *
 * A write that throws propagates and nothing after it happens, so what is displayed is never ahead
 * of what is on disk.
 */
class HoldemGameStorage(
    val tournaments: HoldemTournamentStore,
    val hands: HoldemHandStore,
    private val gameIds: () -> String = { java.util.UUID.randomUUID().toString() },
) {
    private var gameId: String = gameIds()

    /** Reads both stores and decides what to restore; clears a hand save [planRestore] says is unusable. */
    suspend fun restore(): RestorePlan {
        val tournamentLoad = tournaments.load()
        val handLoad = hands.load()
        val plan = planRestore(tournamentLoad, handLoad)
        if (handLoad is HandLoad.Restored) gameId = handLoad.hand.gameId
        if (plan.clearHandSave) hands.clear()
        return plan
    }

    /** Writes [stored] with its new tournament (`StoredTournament.started`) before the first hand is dealt from it. */
    suspend fun beginTournament(stored: StoredTournament) {
        tournaments.save(stored)
    }

    /** Saves [session]'s hand as it stands. A hand that is over is settled, not saved. */
    suspend fun saveHand(session: HoldemSession) {
        val state = session.state
        require(state.phase == Phase.BETTING) { "a hand that is over is settled, not saved" }
        hands.save(SavedHand(gameId, state.handNumber, state.tournament.handSeed, session.log))
    }

    /** Starts the next hand's save under a fresh game id; call before [saveHand] of its first state. */
    fun newHand() {
        gameId = gameIds()
    }

    /** The whole write for a finished hand, then the hand save is cleared. Returns what was stored. */
    suspend fun settleHand(stored: StoredTournament, finished: HoldemState): StoredTournament {
        val next = org.finiteplay.holdem.storage.settleHand(stored, finished)
        tournaments.save(next)
        hands.clear()
        return next
    }

    /** The whole write for a player who leaves, then the hand save is cleared. */
    suspend fun leave(stored: StoredTournament, left: HoldemState): StoredTournament {
        val next = settleTournament(stored, left)
        tournaments.save(next)
        hands.clear()
        return next
    }

    /** Statistics changes outside a hand's end (a hint used, a reset): the tournament is written as it is. */
    suspend fun saveStatistics(stored: StoredTournament, statistics: HoldemStatistics): StoredTournament {
        val next = stored.copy(statistics = statistics)
        tournaments.save(next)
        return next
    }
}
