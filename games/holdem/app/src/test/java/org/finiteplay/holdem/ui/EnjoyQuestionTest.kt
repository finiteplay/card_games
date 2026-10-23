package org.finiteplay.holdem.ui

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.finiteplay.core.session.ENJOY_MIN_GOOD_RESULTS
import org.finiteplay.core.storage.EngagementStore
import org.finiteplay.core.storage.FakeDataStores
import org.finiteplay.core.storage.seedVeteran
import org.finiteplay.core.ui.feedback.EnjoyController
import org.finiteplay.holdem.testing.Scenario
import org.finiteplay.holdem.testing.Scenarios
import org.finiteplay.holdem.testing.scriptedAction
import org.finiteplay.holdem.testing.scriptedPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Hold'em's part of the "enjoying this game?" question (`docs/PLATFORM.md` "Feedback and rating"): a tournament finished
 * first or second is a good result, and the question opens when the player has dealt with the end dialog. Leaving a
 * tournament is not a good result.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EnjoyQuestionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var scheduler: TestCoroutineScheduler
    private val day = TimeUnit.DAYS.toMillis(1)
    private var now = 1_000_000_000_000L
    private lateinit var store: EngagementStore
    private lateinit var enjoy: EnjoyController

    @Before
    fun setUp() {
        scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        store = EngagementStore(folder.newFolder(), dataStoreFactory = FakeDataStores::create)
        // A player one good result short of the question: ten sessions over several days, two good results so far.
        runBlocking {
            now = store.seedVeteran(now, ENJOY_MIN_GOOD_RESULTS - 1)
        }
        enjoy = EnjoyController(store, CoroutineScope(UnconfinedTestDispatcher(scheduler)), clock = { now })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(scenario: Scenario) = HoldemViewModel(
        seedSource = { scenario.seed },
        policyFor = { seat -> scriptedPolicy(scenario.opponents(seat)) },
        initialSettings = TableSettings(animationsEnabled = false),
        enjoy = enjoy,
    )

    private fun HoldemViewModel.playToTheEnd(scenario: Scenario) {
        var guard = 0
        while (endDialog == null && guard++ < 2_000) {
            scheduler.advanceTimeBy(20)
            scheduler.runCurrent()
            if (table.stage == Stage.YOUR_TURN) act(scriptedAction(session.state, 0, scenario.player))
        }
        assertNotNull(endDialog)
    }

    @Test
    fun `winning a tournament is a good result, and the question waits for the end dialog to be closed`() {
        val scenario = Scenarios.tournamentWon
        val vm = viewModel(scenario)

        vm.playToTheEnd(scenario)

        assertEquals(ENJOY_MIN_GOOD_RESULTS, runBlocking { store.current().goodResults })
        assertFalse("not over the end dialog", enjoy.visible)

        vm.dismissEndDialog()
        scheduler.runCurrent()

        assertTrue(enjoy.visible)
    }

    @Test
    fun `choosing New Tournament from the end screen also opens the question`() {
        val scenario = Scenarios.tournamentWon
        val vm = viewModel(scenario)
        vm.playToTheEnd(scenario)

        vm.newTournament()
        scheduler.runCurrent()

        assertTrue(enjoy.visible)
    }

    @Test
    fun `leaving a tournament is not a good result`() {
        val vm = viewModel(Scenarios.fold)
        repeat(60) {
            scheduler.advanceTimeBy(50)
            scheduler.runCurrent()
        }

        vm.requestLeave()
        vm.confirmLeave()
        vm.dismissEndDialog()
        scheduler.runCurrent()

        assertEquals(ENJOY_MIN_GOOD_RESULTS - 1, runBlocking { store.current().goodResults })
        assertFalse(enjoy.visible)
    }
}
