package org.finiteplay.holdem.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.SeatAction
import org.finiteplay.holdem.rules.legalActions
import org.finiteplay.holdem.rules.seatView
import org.finiteplay.holdem.testing.HandRecord
import org.finiteplay.holdem.testing.Move
import org.finiteplay.holdem.testing.Scenario
import org.finiteplay.holdem.testing.Scenarios
import org.finiteplay.holdem.testing.resolve
import org.finiteplay.holdem.testing.scriptedPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `EXECUTION_PLAN.md` H3's gate on a real emulator: the committed fixed-seed tournaments
 * (`Scenarios`) played through the real screen, every hand's chips asserted to the chip against a
 * headless run of the same scripts, and the buttons on screen on each of the player's turns exactly
 * the engine's legal actions. Opponents are the scripted policies, injected through the view model's
 * constructor; the player's moves are taps.
 */
abstract class ScenarioPlayTest(private val landscape: Boolean) {
    @get:org.junit.Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun unlockOrientation() {
        composeRule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    private lateinit var vm: HoldemViewModel

    private fun rotate() {
        val wanted = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        composeRule.activityRule.scenario.onActivity { it.requestedOrientation = wanted }
        val orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        composeRule.waitUntil(15_000) {
            var current = -1
            composeRule.activityRule.scenario.onActivity { current = it.resources.configuration.orientation }
            current == orientation
        }
        composeRule.waitForIdle()
    }

    private fun show(scenario: Scenario) {
        rotate()
        vm = HoldemViewModel(
            seedSource = { scenario.seed },
            policyFor = { seat -> scriptedPolicy(scenario.opponents(seat)) },
            initialSettings = TableSettings(animationsEnabled = false, soundEnabled = false),
        )
        composeRule.setContent { FinitePlayTheme(darkTheme = true) { GameScreen(vm) } }
    }

    private fun present(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = unmerged(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun tap(tag: String) {
        node(tag).performClick()
        composeRule.waitForIdle()
    }

    /** Seat and pot children are inside one merged node for accessibility, so they are read unmerged; the bar is read as a screen reader would. */
    private fun unmerged(tag: String) = tag.startsWith("seat_") || tag.startsWith("pot")

    private fun node(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = unmerged(tag))

    private fun money(amount: Int) = "%,d".format(amount)

    private val over get() = vm.table.stage == Stage.HAND_OVER || vm.table.stage == Stage.TOURNAMENT_OVER

    /** What the bar shows on the player's turn is the engine's legal set, with the amounts in the labels. */
    private fun assertButtonsAreLegalActions() {
        val state = vm.session.state
        val legal = checkNotNull(legalActions(state))
        assertEquals(0, legal.seat)
        val expected = buildSet {
            if (legal.canFold) add("action_fold")
            if (legal.canCheck) add("action_check")
            if (legal.canCall) add("action_call")
            if (legal.bet != null) add("action_bet")
            if (legal.raise != null) add("action_raise")
            if (legal.allInTo != null) add("action_all_in")
        }
        val all = listOf("action_fold", "action_check", "action_call", "action_bet", "action_raise", "action_all_in")
        for (tag in all) assertEquals("$tag with ${state.history.size} actions in", tag in expected, present(tag))
        assertTrue(present("action_settings"))
        assertFalse(present("action_next_hand"))
        legal.callAmount?.let { if (legal.toCall <= legal.stack) node("action_call").assertTextContains(money(it), substring = true) }
        if (legal.allInTo != null) node("action_all_in").assertTextContains(money(legal.stack), substring = true)
        assertEquals("the sizing row is up exactly when Bet or Raise is", legal.bet != null || legal.raise != null, present("sizing_amount"))
    }

    private fun playersMove(scenario: Scenario): Pair<Move, Action> {
        val state = vm.session.state
        val legal = checkNotNull(legalActions(state))
        val move = scenario.player.choose(state.seatView(0))
        return move to resolve(move, legal, org.finiteplay.holdem.ui.Sizing.of(state, legal))
    }

    private fun takeTurn(scenario: Scenario) {
        assertButtonsAreLegalActions()
        val (move, action) = playersMove(scenario)
        val before = vm.session.log.size
        when (action) {
            Action.Fold -> tap("action_fold")
            Action.Check -> tap("action_check")
            Action.Call -> tap("action_call")
            Action.AllIn -> tap("action_all_in")
            is Action.Bet, is Action.Raise -> {
                if (move == Move.BET_HALF_POT) tap("sizing_half_pot")
                if (move == Move.BET_POT) tap("sizing_pot")
                val amount = if (action is Action.Bet) action.amount else (action as Action.Raise).total
                node("sizing_amount").assertTextEquals(money(amount))
                tap(if (action is Action.Bet) "action_bet" else "action_raise")
            }
        }
        composeRule.waitUntil(10_000) { vm.session.log.size > before }
        assertEquals(SeatAction(0, action), vm.session.log[before])
    }

    private class Seen {
        var sideBanner = false
        var mainBanner = false
    }

    /** Plays until the hand is over, tapping through the player's turns and watching what is shown on the way. */
    private fun playHand(scenario: Scenario, seen: Seen) {
        var guard = 0
        while (!over && guard++ < 200) {
            composeRule.waitUntil(60_000) {
                val banner = vm.table.banner
                if (banner?.kind == PotKind.SIDE && present("pot_banner")) seen.sideBanner = true
                if (banner?.kind == PotKind.MAIN && present("pot_banner")) seen.mainBanner = true
                vm.table.stage == Stage.YOUR_TURN || over
            }
            if (vm.table.stage == Stage.YOUR_TURN) takeTurn(scenario)
        }
        assertTrue("the hand ended", over)
    }

    private fun assertHand(record: HandRecord) {
        val state = record.state
        assertEquals("engine and view model agree", state.stacks, vm.session.state.stacks)
        assertEquals(9_000, vm.session.state.stacks.sum())
        composeRule.waitForIdle()
        for (seat in 1..5) {
            val place = state.places[seat]
            val expected = if (place != null) "Out · ${listOf("1st", "2nd", "3rd", "4th", "5th", "6th")[place - 1]}" else money(state.stacks[seat])
            node("seat_${seat}_stack").assertTextEquals(expected)
        }
        if (state.stacks[0] > 0) node("seat_0_stack").assertTextEquals("You · ${money(state.stacks[0])}")
        assertEquals(record.state.stacks, vm.table.seats.map { it.stack })
        val shouldMark = state.contesting(0)
        assertEquals("result mark", shouldMark, present("result_mark"))
        assertEquals(0, vm.table.pot)
    }

    private fun play(scenario: Scenario) {
        show(scenario)
        val run = scenario.run()
        val seen = Seen()
        for ((index, record) in run.hands.withIndex()) {
            assertEquals(index + 1, vm.table.handNumber)
            playHand(scenario, seen)
            assertHand(record)
            if (index < run.hands.lastIndex) {
                assertEquals(Stage.HAND_OVER, vm.table.stage)
                assertTrue(present("action_next_hand"))
                assertFalse(present("action_fold"))
                tap("action_next_hand")
                composeRule.waitUntil(10_000) { vm.table.handNumber == index + 2 }
            }
        }
        finish(scenario, run.hands.last(), seen)
    }

    private fun finish(scenario: Scenario, last: HandRecord, seen: Seen) {
        val state = last.state
        when (scenario.name) {
            "allInWithSidePot" -> {
                assertTrue("a side pot banner was shown", seen.sideBanner)
                assertTrue("the main pot was paid last", seen.mainBanner)
            }
            "splitPot" -> {
                val award = state.result!!.awards.first { it.winners.size >= 2 && 0 in it.winners }
                val share = award.shares[award.winners.indexOf(0)]
                node("seat_0_award").assertTextContains("+" + money(share), substring = true)
            }
            "fold" -> assertFalse(present("result_mark"))
            "checkDown" -> {
                node("result_mark").assertIsDisplayed()
                for (seat in state.result!!.values.keys) assertTrue(present(if (seat == 0) "seat_0_hand" else "seat_${seat}_hand"))
            }
            "eliminated" -> {
                composeRule.waitUntil(10_000) { present("end_dialog") }
                val place = state.places[0]!!
                node("end_title").assertTextEquals("You finished ${listOf("1st", "2nd", "3rd", "4th", "5th", "6th")[place - 1]}")
                tap("end_close")
                assertFalse(present("end_dialog"))
                node("action_new_tournament").assertIsDisplayed()
                assertFalse(present("action_fold"))
            }
            "tournamentWon" -> {
                composeRule.waitUntil(10_000) { present("end_dialog") }
                node("end_title").assertTextEquals("You won the tournament")
                assertEquals(9_000, vm.session.state.stacks[0])
                tap("end_new_tournament")
                composeRule.waitUntil(10_000) { vm.table.handNumber == 1 && vm.table.stage != Stage.TOURNAMENT_OVER }
                assertFalse(present("end_dialog"))
            }
        }
    }

    @Test
    fun thePotLineNamesItsPotsWhileSidePotsExist() {
        rotate()
        val dealt = TablePresenter.dealt(Scenarios.allInWithSidePot.run().hands[0].state)
        val model = dealt.copy(pot = 3_456, potCount = 3)
        composeRule.setContent {
            FinitePlayTheme(darkTheme = true) { TableView(model, SeededRoster.profiles(1L), landscape = landscape, animate = false) }
        }
        node("pot_amount").assertTextEquals("Pot 3,456")
        node("pot_count").assertTextEquals("3 pots")
    }

    @Test fun aFold() = play(Scenarios.fold)
    @Test fun aCheckDownToShowdown() = play(Scenarios.checkDown)
    @Test fun aBetAndACall() = play(Scenarios.betAndCall)
    @Test fun aRaiseAndAReRaise() = play(Scenarios.raiseAndReraise)
    @Test fun anAllInWithASidePot() = play(Scenarios.allInWithSidePot)
    @Test fun aSplitPot() = play(Scenarios.splitPot)
    @Test fun thePlayerEliminatedWithTheirPlaceShown() = play(Scenarios.eliminated)
    @Test fun aTournamentWon() = play(Scenarios.tournamentWon)
}

class PortraitScenarioPlayTest : ScenarioPlayTest(landscape = false)

class LandscapeScenarioPlayTest : ScenarioPlayTest(landscape = true)
