package org.finiteplay.holdem.storage

import kotlinx.coroutines.test.runTest
import org.finiteplay.holdem.rules.Contract
import org.finiteplay.holdem.rules.HoldemSession
import org.finiteplay.holdem.rules.Phase
import org.finiteplay.holdem.rules.act
import org.finiteplay.holdem.rules.leave
import org.finiteplay.holdem.rules.legalActions
import org.finiteplay.holdem.rules.nextHand
import org.finiteplay.holdem.rules.startTournament
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The game as the view model will drive it, written in the order `DESIGN.md` "Persistence" fixes,
 * with every store write counted so a kill can be placed before any of them.
 */
private class Harness(
    dir: File,
    crashAt: Int?,
    private val seed: Long,
    private val choose: Chooser,
    private val leaveAtHand: Int? = null,
) {
    val faults = FaultyDataStores(crashAt)
    val storage = HoldemGameStorage(
        HoldemTournamentStore(dir, faults::create),
        HoldemHandStore(dir, faults::create),
        gameIds = { "game" },
    )
    var plan: RestorePlan? = null

    /** Plays until the tournament has been counted: its statistics then, or null when killed first. */
    suspend fun run(): HoldemStatistics? = try {
        play()
    } catch (e: Crash) {
        null
    }

    private suspend fun play(): HoldemStatistics {
        val restored = storage.restore().also { plan = it }
        var stored = restored.stored
        var session: HoldemSession
        when (restored) {
            is RestorePlan.NewTournament -> {
                if (stored.statistics.tournamentsPlayed >= 1) return stored.statistics
                stored = stored.started(seed)
                storage.beginTournament(stored)
                session = startTournament(seed)
                if (session.state.phase == Phase.BETTING) storage.saveHand(session)
            }
            is RestorePlan.ResumeHand -> {
                session = restored.session
                if (!restored.saved && session.state.phase == Phase.BETTING) storage.saveHand(session)
            }
        }
        while (true) {
            val state = session.state
            if (state.phase == Phase.BETTING) {
                if (leaveAtHand == state.handNumber && state.toAct == PLAYER_SEAT) {
                    stored = storage.leave(stored, session.leave()!!.state)
                    return stored.statistics
                }
                val next = session.act(legalActions(state)!!.seat, choose(state))!!
                session = next
                if (next.state.phase == Phase.BETTING) storage.saveHand(next)
                continue
            }
            stored = storage.settleHand(stored, state)
            val chips = (stored.tournament?.stacks ?: state.stacks).sum()
            assertEquals("every chip accounted for", Contract.TOTAL_CHIPS, chips)
            if (stored.tournament == null) return stored.statistics
            session = session.nextHand()!!
            storage.newHand()
            if (session.state.phase == Phase.BETTING) storage.saveHand(session)
        }
    }
}

class PersistenceFaultTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val choose = randomChooser()

    private fun clean(seed: Long, leaveAtHand: Int? = null): Pair<HoldemStatistics, List<String>> {
        val harness = Harness(folder.newFolder(), null, seed, choose, leaveAtHand)
        var stats: HoldemStatistics? = null
        kotlinx.coroutines.runBlocking { stats = harness.run() }
        return stats!! to harness.faults.writes
    }

    /** Kills the first run at write [k], restarts over the same disk, and plays on to the end. */
    private fun killedAt(seed: Long, k: Int, leaveAtHand: Int? = null): Pair<HoldemStatistics, RestorePlan> {
        val dir = folder.newFolder()
        kotlinx.coroutines.runBlocking {
            assertNull("the run should have been killed at write $k", Harness(dir, k, seed, choose, leaveAtHand).run())
        }
        val restart = Harness(dir, null, seed, choose, leaveAtHand)
        var stats: HoldemStatistics? = null
        kotlinx.coroutines.runBlocking { stats = restart.run() }
        return stats!! to restart.plan!!
    }

    @Test
    fun `a clean run counts one tournament and every hand of it`() {
        val (stats, writes) = clean(SEEDS[0])
        assertEquals(1, stats.tournamentsPlayed)
        assertTrue(stats.handsPlayed >= 1)
        assertTrue(writes.size > 10)
    }

    @Test
    fun `a kill before a hand's first save deals the hand again from its start`() {
        val (expected, writes) = clean(SEEDS[0])
        // Write 1 is the tournament, write 2 the first hand's first save.
        assertEquals(listOf("tournament", "active_game"), writes.take(2))
        val (stats, plan) = killedAt(SEEDS[0], 2)
        assertEquals(expected, stats)
        plan as RestorePlan.ResumeHand
        assertEquals(false, plan.saved)
        assertEquals(false, plan.handRecovered)
        assertEquals(emptyList<Any>(), plan.session.log)
        assertEquals(1, plan.session.state.handNumber)
    }

    @Test
    fun `a kill between an action and its save restores the hand as last saved`() {
        val (expected, writes) = clean(SEEDS[0])
        // Write 3 is the save after the hand's first action.
        assertEquals("active_game", writes[2])
        val (stats, plan) = killedAt(SEEDS[0], 3)
        assertEquals(expected, stats)
        plan as RestorePlan.ResumeHand
        assertEquals(true, plan.saved)
        assertEquals(emptyList<Any>(), plan.session.log)
    }

    @Test
    fun `a kill between a hand's end write and clearing it leaves the hand counted once`() {
        val (expected, writes) = clean(SEEDS[0])
        val settle = writes.indices.first { it > 0 && writes[it] == "tournament" }
        assertTrue(settle > 0)
        // The write after the settlement is the clearing of the hand save.
        assertEquals("active_game", writes[settle + 1])
        val (stats, plan) = killedAt(SEEDS[0], settle + 2)
        assertEquals(expected, stats)
        if (plan is RestorePlan.ResumeHand) {
            assertEquals(true, plan.clearHandSave)
            assertEquals(2, plan.session.state.handNumber)
        }
    }

    @Test
    fun `a kill at any store write of a tournament restores to the same tournament and the same statistics`() {
        for (seed in SEEDS) {
            val (expected, writes) = clean(seed)
            for (k in 1..writes.size) {
                val (stats, _) = killedAt(seed, k)
                assertEquals("seed $seed killed at write $k of ${writes.size}", expected, stats)
            }
        }
    }

    @Test
    fun `a kill at any store write around leaving counts the tournament once, in the place left`() {
        val seed = SEEDS[1]
        val (expected, writes) = clean(seed, leaveAtHand = 3)
        assertEquals(1, expected.tournamentsPlayed)
        for (k in 1..writes.size) {
            val (stats, _) = killedAt(seed, k, leaveAtHand = 3)
            assertEquals("seed $seed killed at write $k of ${writes.size}", expected, stats)
        }
    }

    @Test
    fun `a kill after the tournament's end write restarts with a new tournament and the result counted once`() {
        val (expected, writes) = clean(SEEDS[0])
        val last = writes.lastIndexOf("tournament")
        // The write after the end write clears the hand; the kill lands there.
        assertEquals("active_game", writes[last + 1])
        val dir = folder.newFolder()
        kotlinx.coroutines.runBlocking { Harness(dir, last + 2, SEEDS[0], choose).run() }
        val storage = HoldemGameStorage(HoldemTournamentStore(dir, FaultyDataStores()::create), HoldemHandStore(dir, FaultyDataStores()::create))
        val plan = kotlinx.coroutines.runBlocking { storage.restore() }
        plan as RestorePlan.NewTournament
        assertEquals(true, plan.clearHandSave)
        assertEquals(expected, plan.stored.statistics)
        // And the stale hand is gone.
        assertEquals(HandLoad.Missing, kotlinx.coroutines.runBlocking { storage.hands.load() })
    }

    @Test
    fun `a failed write propagates and the earlier state is what is on disk`() = runTest {
        val dir = folder.newFolder()
        val faults = FaultyDataStores(crashAt = 2)
        val storage = HoldemGameStorage(HoldemTournamentStore(dir, faults::create), HoldemHandStore(dir, faults::create))
        val stored = StoredTournament().started(SEEDS[0])
        storage.beginTournament(stored)
        val session = startTournament(SEEDS[0])
        var failed = false
        try {
            storage.saveHand(session)
        } catch (e: Crash) {
            failed = true
        }
        assertTrue(failed)
        assertEquals(HandLoad.Missing, storage.hands.load())
        assertEquals(TournamentLoad.Loaded(stored), storage.tournaments.load())
        assertNotNull(session)
    }

    private companion object {
        val SEEDS = listOf(11L, 23L, 37L, 41L)
    }
}
