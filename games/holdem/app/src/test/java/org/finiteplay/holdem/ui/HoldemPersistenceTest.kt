package org.finiteplay.holdem.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.finiteplay.core.storage.FakeDataStores
import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.legalActions
import org.finiteplay.holdem.storage.HoldemGameStorage
import org.finiteplay.holdem.storage.HoldemHandStore
import org.finiteplay.holdem.storage.HoldemSettingsStore
import org.finiteplay.holdem.storage.HoldemTournamentStore
import org.finiteplay.holdem.testing.Scenario
import org.finiteplay.holdem.testing.Scenarios
import org.finiteplay.holdem.testing.scriptedPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The view model on the stores (`EXECUTION_PLAN.md` H4/H5/H7 wiring): restart, settle once, hint. */
@OptIn(ExperimentalCoroutinesApi::class)
class HoldemPersistenceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var scheduler: TestCoroutineScheduler

    @Before
    fun setUp() {
        scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(scenario: Scenario): HoldemViewModel {
        val dir = folder.root
        return HoldemViewModel(
            seedSource = { scenario.seed },
            policyFor = { seat -> scriptedPolicy(scenario.opponents(seat)) },
            initialSettings = TableSettings(animationsEnabled = false),
            storage = HoldemGameStorage(HoldemTournamentStore(dir, FakeDataStores::create), HoldemHandStore(dir, FakeDataStores::create)),
            settingsStore = HoldemSettingsStore(dir, FakeDataStores::create),
        )
    }

    private fun HoldemViewModel.until(stage: Stage) {
        repeat(1_000) {
            if (!isLoading && table.stage == stage) return
            scheduler.advanceTimeBy(50)
            scheduler.runCurrent()
            Thread.sleep(3) // the stores finish on real I/O threads, which a virtual clock does not wait for
        }
        error("never reached $stage; at ${table.stage}")
    }

    private fun HoldemViewModel.untilLeft(stage: Stage) {
        repeat(1_000) {
            if (table.stage != stage) return
            scheduler.advanceTimeBy(50)
            scheduler.runCurrent()
            Thread.sleep(3)
        }
        error("never left $stage")
    }

    @Test
    fun `a restart mid-hand restores the same hand, the same table and the same turn`() {
        val first = vm(Scenarios.checkDown)
        first.until(Stage.YOUR_TURN)
        first.act(Action.Call.takeIf { first.legal!!.canCall } ?: Action.Check)
        first.untilLeft(Stage.YOUR_TURN)
        first.until(Stage.YOUR_TURN)

        val second = vm(Scenarios.checkDown)
        second.until(Stage.YOUR_TURN)

        assertEquals(first.session.log, second.session.log)
        assertEquals(first.table.board, second.table.board)
        assertEquals(first.table.pot, second.table.pot)
        assertEquals(first.table.seats.map { it.stack }, second.table.seats.map { it.stack })
        assertEquals(first.legal, second.legal)
    }

    @Test
    fun `a hand is counted once, however often the game is restarted after it`() {
        val first = vm(Scenarios.fold)
        first.until(Stage.YOUR_TURN)
        first.act(Action.Fold)
        first.until(Stage.HAND_OVER)
        assertEquals(1, first.statistics.handsPlayed)

        repeat(3) {
            val again = vm(Scenarios.fold)
            repeat(100) {
                scheduler.advanceTimeBy(5)
                scheduler.runCurrent()
                Thread.sleep(3)
            }
            assertTrue(!again.isLoading)
            assertEquals(1, again.statistics.handsPlayed)
            assertEquals("the next hand is the one dealt", 2, again.session.state.handNumber)
        }
    }

    @Test
    fun `hint suggests a legal action, is counted once and survives a restart`() {
        val first = vm(Scenarios.checkDown)
        first.until(Stage.YOUR_TURN)
        first.requestHint()
        val shown = first.hintShown!!
        val legal = legalActions(first.session.state)!!
        assertTrue(legal.allows(shown.suggested))
        assertEquals(1, first.statistics.hintsUsed)

        first.requestHint()
        assertNull(first.hintShown)
        assertEquals("hiding it does not count again", 1, first.statistics.hintsUsed)

        scheduler.advanceTimeBy(100)
        scheduler.runCurrent()
        assertEquals(1, vm(Scenarios.checkDown).also { it.until(Stage.YOUR_TURN) }.statistics.hintsUsed)
    }

    @Test
    fun `settings written to the store come back after a restart`() {
        val first = vm(Scenarios.fold)
        first.until(Stage.YOUR_TURN)
        first.setSoundEnabled(true)
        first.setHandedness(org.finiteplay.core.ui.layout.Handedness.LEFT)
        scheduler.advanceTimeBy(100)
        scheduler.runCurrent()

        val second = vm(Scenarios.fold)
        second.until(Stage.YOUR_TURN)
        assertTrue(second.settings.soundEnabled)
        assertEquals(org.finiteplay.core.ui.layout.Handedness.LEFT, second.settings.handedness)
    }
}
