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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Settings stays where it is as the bar's other buttons come and go (`UI_SPEC.md` "Action Bar"). */
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
}
