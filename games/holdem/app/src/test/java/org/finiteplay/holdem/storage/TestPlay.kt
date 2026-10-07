package org.finiteplay.holdem.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import org.finiteplay.core.storage.FakeDataStores
import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.HoldemSession
import org.finiteplay.holdem.rules.HoldemState
import org.finiteplay.holdem.rules.LegalActions
import org.finiteplay.holdem.rules.Phase
import org.finiteplay.holdem.rules.act
import org.finiteplay.holdem.rules.legalActions
import java.io.File
import kotlin.random.Random

/** A decision as a pure function of the state, so a restarted game decides what the first run would have. */
typealias Chooser = (HoldemState) -> Action

/** Random legal play, calm or wild, seeded by the hand, the action's index and the seat. */
fun randomChooser(wild: Boolean = false): Chooser = { state ->
    val legal = legalActions(state)!!
    val random = Random(state.tournament.handSeed * 31 + state.history.size * 7919L + legal.seat)
    randomAction(legal, random, wild)
}

fun randomAction(legal: LegalActions, random: Random, wild: Boolean): Action {
    val options = ArrayList<Action>()
    if (legal.canFold) repeat(3) { options += Action.Fold }
    if (legal.canCheck) repeat(8) { options += Action.Check }
    if (legal.canCall) repeat(6) { options += Action.Call }
    legal.bet?.let { range ->
        options += Action.Bet(range.first)
        options += Action.Bet(range.first + random.nextInt(minOf(range.last - range.first + 1, 150)))
        if (random.nextInt(if (wild) 3 else 10) == 0) options += Action.Bet(range.last)
    }
    legal.raise?.let { range ->
        options += Action.Raise(range.first)
        options += Action.Raise(range.first + random.nextInt(minOf(range.last - range.first + 1, 150)))
        if (random.nextInt(if (wild) 3 else 10) == 0) options += Action.Raise(range.last)
    }
    if (legal.allInTo != null && (options.isEmpty() || random.nextInt(if (wild) 4 else 20) == 0)) options += Action.AllIn
    return options.random(random)
}

/** Plays the hand in [session] to its end with [choose], every seat. */
fun playOut(session: HoldemSession, choose: Chooser): HoldemSession {
    var s = session
    while (s.state.phase == Phase.BETTING) {
        s = s.act(legalActions(s.state)!!.seat, choose(s.state))!!
    }
    return s
}

/** Thrown in place of a write that never reached the disk: the process was killed there. */
class Crash : RuntimeException("killed at a store write")

/**
 * The store seam of a fault injection: wraps [FakeDataStores] and, when [crashAt] is the number of
 * the write about to land, throws [Crash] instead of landing it. [writes] names every write that was
 * attempted, by store.
 */
class FaultyDataStores(private val crashAt: Int? = null) {
    val writes = mutableListOf<String>()

    fun create(directory: File, name: String): DataStore<Preferences> {
        val inner = FakeDataStores.create(directory, name)
        return object : DataStore<Preferences> {
            override val data = inner.data
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                writes += name
                if (crashAt == writes.size) throw Crash()
                return inner.updateData(transform)
            }
        }
    }
}
