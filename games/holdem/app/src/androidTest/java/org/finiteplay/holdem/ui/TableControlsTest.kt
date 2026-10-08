package org.finiteplay.holdem.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.holdem.testing.Scenarios
import org.finiteplay.holdem.testing.scriptedPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The bet slider and the live hand name: what a player sees and touches while a hand is on (`UI_SPEC.md` "The sizing row", "Seats"). */
class TableControlsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var vm: HoldemViewModel

    @Before
    fun show() {
        val scenario = Scenarios.checkDown
        vm = HoldemViewModel(
            seedSource = { scenario.seed },
            policyFor = { seat -> scriptedPolicy(scenario.opponents(seat)) },
            initialSettings = TableSettings(animationsEnabled = false, soundEnabled = false),
        )
        composeRule.setContent { FinitePlayTheme(darkTheme = true) { GameScreen(vm) } }
        composeRule.waitUntil(30_000) { vm.table.stage == Stage.YOUR_TURN && present("sizing_slider") }
    }

    private fun present(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun money(amount: Int) = "%,d".format(amount)

    @Test
    fun theSliderSetsTheAmountFromTheSmallestLegalToAllIn() {
        val row = checkNotNull(vm.sizing).row
        composeRule.onNodeWithTag("sizing_slider").performSemanticsAction(SemanticsActions.SetProgress) { it(row.max.toFloat()) }
        composeRule.waitForIdle()
        assertEquals(row.max, vm.sizing!!.amount)
        composeRule.onNodeWithTag("sizing_amount").assertTextEquals(money(row.max))

        composeRule.onNodeWithTag("sizing_slider").performSemanticsAction(SemanticsActions.SetProgress) { it(row.min.toFloat()) }
        composeRule.waitForIdle()
        assertEquals(row.min, vm.sizing!!.amount)
    }

    @Test
    fun draggingAcrossTheSliderReachesBothEnds() {
        val row = checkNotNull(vm.sizing).row
        composeRule.onNodeWithTag("sizing_slider").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertEquals(row.max, vm.sizing!!.amount)
        composeRule.onNodeWithTag("sizing_slider").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals(row.min, vm.sizing!!.amount)
    }

    @Test
    fun theAmountIsAlwaysLegalWhateverTheSliderIsGiven() {
        val row = checkNotNull(vm.sizing).row
        composeRule.onNodeWithTag("sizing_slider").performSemanticsAction(SemanticsActions.SetProgress) { it((row.max + 5_000).toFloat()) }
        composeRule.waitForIdle()
        assertTrue(vm.sizing!!.amount in row.min..row.max)
    }

    @Test
    fun thePlayerSeesTheirHandNamedOnceTheFlopIsDown() {
        composeRule.waitUntil(60_000) {
            if (vm.table.stage == Stage.YOUR_TURN && vm.table.board.size < 3) {
                val tag = if (present("action_check")) "action_check" else "action_call"
                composeRule.onNodeWithTag(tag).performClick()
            }
            vm.table.board.size >= 3 && vm.table.stage == Stage.YOUR_TURN && present("seat_0_hand")
        }
        assertTrue(present("seat_0_hand"))
    }

    @Test
    fun whenTheHandIsOverEachWinnerSaysWonAndAPlayerWhoLostChipsSaysLostInRed() {
        composeRule.waitUntil(90_000) {
            if (vm.table.stage == Stage.YOUR_TURN) {
                val tag = if (present("action_check")) "action_check" else "action_call"
                composeRule.onNodeWithTag(tag).performClick()
            }
            vm.table.stage == Stage.HAND_OVER
        }
        composeRule.waitForIdle()
        for ((seat, model) in vm.table.seats.withIndex()) {
            val net = checkNotNull(model.net)
            val tag = if (seat == 0) (if (net > 0) "seat_0_won" else "seat_0_lost") else "seat_${seat}_won"
            val shown = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            val expected = if (seat == 0) net != 0 else net > 0
            assertEquals("seat $seat net $net", expected, shown)
        }
    }
}
