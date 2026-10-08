package org.finiteplay.holdem.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width as modifierWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import org.finiteplay.core.ui.layout.BoardOrientation
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.holdem.rules.LegalActions
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Settings stays at its edge and the actions at the other as the set of buttons changes (`UI_SPEC.md` "Action Bar"). */
class ActionBarAnchorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun showAlone(mirrored: Boolean) {
        composeRule.setContent {
            FinitePlayTheme(darkTheme = true) {
                Box(Modifier.modifierWidth(360.dp)) {
                    ActionBar(
                        orientation = BoardOrientation.PORTRAIT,
                        mirrored = mirrored,
                        bar = BarState(Stage.OPPONENT_TURN, legal = null, sizing = null, busy = true),
                        onAction = {},
                        onNextHand = {},
                        onNewTournament = {},
                        onSettings = {},
                    )
                }
            }
        }
    }

    @Test
    fun aLoneSettingsButtonStaysAtTheLeftEdgeAtItsOwnWidth() {
        showAlone(mirrored = false)
        val bounds = composeRule.onNodeWithTag("action_settings").getBoundsInRoot()
        assertTrue("at the left edge, not centred: ${bounds.left}", bounds.left < 16.dp)
        assertTrue("one sixth of the bar, not all of it: ${bounds.width}", bounds.width < 70.dp)
    }

    @Test
    fun leftHandedPlayConsistentlyMirrorsItToTheRightEdge() {
        showAlone(mirrored = true)
        val bounds = composeRule.onNodeWithTag("action_settings").getBoundsInRoot()
        assertTrue("at the right edge: ${bounds.right}", bounds.right > 344.dp)
        assertTrue("one sixth of the bar: ${bounds.width}", bounds.width < 70.dp)
    }

    @Test
    fun theActionsStayAtTheRightEdgeWhateverTheirNumber() {
        val legal = LegalActions(seat = 0, stack = 1_500, toCall = 0, canFold = false, canCheck = true, callAmount = null, bet = 20..1_500, raise = null, allInTo = 1_500)
        composeRule.setContent {
            FinitePlayTheme(darkTheme = true) {
                Box(Modifier.modifierWidth(360.dp)) {
                    ActionBar(
                        orientation = BoardOrientation.PORTRAIT,
                        mirrored = false,
                        bar = BarState(Stage.YOUR_TURN, legal = legal, sizing = null, busy = false),
                        onAction = {},
                        onNextHand = {},
                        onNewTournament = {},
                        onSettings = {},
                    )
                }
            }
        }
        val settings = composeRule.onNodeWithTag("action_settings").getBoundsInRoot()
        val allIn = composeRule.onNodeWithTag("action_all_in").getBoundsInRoot()
        val hint = composeRule.onNodeWithTag("action_hint").getBoundsInRoot()
        val bet = composeRule.onNodeWithTag("action_bet").getBoundsInRoot()
        val check = composeRule.onNodeWithTag("action_check").getBoundsInRoot()
        assertTrue("left to right: All in, Bet, Check, then Hint", allIn.right <= bet.left + 1.dp && bet.right <= check.left + 1.dp && check.right <= hint.left + 1.dp)
        assertTrue("Settings at the left edge: ${settings.left}", settings.left < 16.dp)
        assertTrue("Hint at the right edge: ${hint.right}", hint.right > 344.dp)
        assertTrue("every button the width of one of six: ${allIn.width}", allIn.width < 70.dp)
        assertTrue("the gap is between Settings and the actions", allIn.left - settings.right > 40.dp)
    }
}
