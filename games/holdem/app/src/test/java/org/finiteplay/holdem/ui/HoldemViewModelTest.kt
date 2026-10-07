package org.finiteplay.holdem.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.finiteplay.core.ui.sound.SoundEffect
import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.Contract
import org.finiteplay.holdem.rules.Phase
import org.finiteplay.holdem.rules.legalActions
import org.finiteplay.holdem.testing.Scenario
import org.finiteplay.holdem.testing.Scenarios
import org.finiteplay.holdem.testing.scriptedAction
import org.finiteplay.holdem.testing.scriptedPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The view model's pacing and ordering, on a virtual clock (`EXECUTION_PLAN.md` H3). */
@OptIn(ExperimentalCoroutinesApi::class)
class HoldemViewModelTest {
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

    private fun viewModel(scenario: Scenario, animations: Boolean = true): HoldemViewModel =
        HoldemViewModel(
            seedSource = { scenario.seed },
            policyFor = { seat -> scriptedPolicy(scenario.opponents(seat)) },
            initialSettings = TableSettings(animationsEnabled = animations),
        )

    private fun HoldemViewModel.advance(ms: Long) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
    }

    /** What the player has been shown is never ahead of what the session holds. */
    private fun HoldemViewModel.assertNotAhead() {
        val state = session.state
        assertEquals(state.board.take(table.board.size), table.board)
        assertTrue(table.seats.count { it.lastAction != null } <= state.history.size)
        for ((seat, shown) in table.seats.withIndex()) {
            if (seat != 0 && shown.cards != null) assertTrue(state.result?.showdown == true)
        }
        if (table.stage == Stage.YOUR_TURN) {
            assertEquals(0, state.toAct)
            assertEquals(legalActions(state), legal)
        } else {
            assertNull(legal)
        }
    }

    @Test
    fun `opponents act one at a time after a pause, never ahead of the session`() {
        val vm = viewModel(Scenarios.fold)
        val shownActions = ArrayList<Int>()
        var last = -1
        repeat(200) {
            vm.advance(50)
            vm.assertNotAhead()
            val count = vm.table.seats.count { it.lastAction != null }
            if (count != last) shownActions += count
            last = count
            if (vm.table.stage == Stage.YOUR_TURN) return@repeat
        }
        assertEquals(Stage.YOUR_TURN, vm.table.stage)
        // Each action appeared on its own, one more than the last.
        assertEquals(shownActions.sorted(), shownActions)
        assertTrue(shownActions.zipWithNext().all { (a, b) -> b - a <= 1 })
    }

    @Test
    fun `the player is untouchable until it is their turn`() {
        val vm = viewModel(Scenarios.fold)
        assertTrue(vm.busy)
        val before = vm.session
        vm.act(Action.Fold)
        assertEquals(before, vm.session)
    }

    @Test
    fun `an opponent's thinking is longer for a large decision and shorter under skip animations`() {
        val pacing = Pacing(true)
        assertEquals(800, pacing.thinkMs(false))
        assertEquals(1_400, pacing.thinkMs(true))
        assertEquals(250, Pacing(false).thinkMs(true))
        assertEquals(3_000, pacing.nextHandMs)
        assertEquals(1_500, Pacing(false).nextHandMs)
    }

    @Test
    fun `skip animations still shows every action on its own`() {
        val vm = viewModel(Scenarios.checkDown, animations = false)
        val seen = HashSet<Int>()
        repeat(400) {
            vm.advance(20)
            seen += vm.table.seats.count { it.lastAction != null }
            if (vm.table.stage == Stage.YOUR_TURN) {
                val action = scriptedAction(vm.session.state, 0, Scenarios.checkDown.player)
                vm.act(action)
            }
        }
        // 0, 1, 2, ... up to the last opponent's: no frame skipped several actions at once.
        assertTrue(seen.containsAll((0..seen.max()).toList()))
    }

    @Test
    fun `a hand is played through, paid one pot at a time, and the next deals itself after the pause`() {
        val scenario = Scenarios.checkDown
        val vm = viewModel(scenario, animations = false)
        val banners = ArrayList<PotBanner>()
        var guard = 0
        while (vm.table.stage != Stage.HAND_OVER && guard++ < 2_000) {
            vm.advance(20)
            vm.assertNotAhead()
            vm.table.banner?.let { if (banners.lastOrNull() != it) banners += it }
            if (vm.table.stage == Stage.YOUR_TURN) vm.act(scriptedAction(vm.session.state, 0, scenario.player))
        }
        assertEquals(Stage.HAND_OVER, vm.table.stage)
        assertEquals(Contract.TOTAL_CHIPS, vm.table.seats.sumOf { it.stack })
        assertEquals(vm.session.state.stacks, vm.table.seats.map { it.stack })
        assertTrue(banners.isNotEmpty())
        assertEquals(1, vm.table.handNumber)
        vm.advance(1_600)
        assertEquals(2, vm.table.handNumber)
    }

    @Test
    fun `next hand pressed early deals at once`() {
        val scenario = Scenarios.checkDown
        val vm = viewModel(scenario, animations = false)
        var guard = 0
        while (vm.table.stage != Stage.HAND_OVER && guard++ < 2_000) {
            vm.advance(20)
            if (vm.table.stage == Stage.YOUR_TURN) vm.act(scriptedAction(vm.session.state, 0, scenario.player))
        }
        vm.nextHand()
        vm.advance(0)
        assertEquals(2, vm.table.handNumber)
        assertEquals(Phase.BETTING, vm.session.state.phase)
    }

    @Test
    fun `cues follow the actions and the deal`() {
        val vm = viewModel(Scenarios.fold, animations = false)
        val cues = ArrayList<SoundEffect>()
        vm.soundSink = { cues += it }
        vm.newTournament()
        repeat(100) { vm.advance(20) }
        assertEquals(listOf(SoundEffect.SHUFFLE, SoundEffect.DEAL), cues.take(2))
        assertTrue(cues.any { it == SoundEffect.CHIP })
    }

    @Test
    fun `leaving ends the tournament at once in the place a knockout now would take`() {
        val vm = viewModel(Scenarios.fold)
        repeat(60) { vm.advance(50) }
        assertFalse(vm.leaveConfirmVisible)
        vm.requestLeave()
        assertTrue(vm.leaveConfirmVisible)
        vm.dismissLeave()
        assertFalse(vm.leaveConfirmVisible)
        vm.requestLeave()
        vm.confirmLeave()
        assertEquals(Stage.TOURNAMENT_OVER, vm.table.stage)
        assertEquals(6, vm.endDialog!!.place)
        assertFalse(vm.busy)
        vm.advance(10_000)
        assertEquals(Stage.TOURNAMENT_OVER, vm.table.stage)
        vm.dismissEndDialog()
        assertNull(vm.endDialog)
    }

    @Test
    fun `a tournament won ends with the first place and the hands it took`() {
        val scenario = Scenarios.tournamentWon
        val vm = viewModel(scenario, animations = false)
        var guard = 0
        while (vm.endDialog == null && guard++ < 2_000) {
            vm.advance(20)
            if (vm.table.stage == Stage.YOUR_TURN) vm.act(scriptedAction(vm.session.state, 0, scenario.player))
        }
        val end = assertNotNull(vm.endDialog)
        assertEquals(1, end.place)
        assertEquals(1, end.hands)
        assertTrue(end.largestPot > 0)
    }

    @Test
    fun `new tournament starts over from a new seed with the table dealt`() {
        var seed = 1L
        val vm = HoldemViewModel(seedSource = { seed })
        val first = vm.session.state.tournament.seed
        seed = 99L
        vm.newTournament()
        assertEquals(99L, vm.session.state.tournament.seed)
        assertEquals(1, vm.table.handNumber)
        assertEquals(1L, first)
        assertEquals(5, vm.profiles.size)
    }

    @Test
    fun `the salt depends on the hand, the seat and the action index`() {
        val salts = setOf(decisionSalt(1, 1, 0), decisionSalt(2, 1, 0), decisionSalt(1, 2, 0), decisionSalt(1, 1, 1))
        assertEquals(4, salts.size)
        assertEquals(decisionSalt(5, 3, 7), decisionSalt(5, 3, 7))
    }

    private fun <T : Any> assertNotNull(value: T?): T {
        org.junit.Assert.assertNotNull(value)
        return value!!
    }
}
