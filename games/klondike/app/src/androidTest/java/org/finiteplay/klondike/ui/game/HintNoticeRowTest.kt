package org.finiteplay.klondike.ui.game

import org.finiteplay.core.ui.R as CoreR
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import org.finiteplay.klondike.R
import org.junit.Rule
import org.junit.Test

/**
 * `docs/games/klondike/UI_SPEC.md` "Hint": the No solution / Inconclusive notice is "a brief
 * dismissible notice". The Inconclusive message is the longest of the three hint
 * strings, long enough on a real phone width that a `Row` without a weighted `Text`
 * lets the message consume the whole row and pushes the Dismiss button outside the
 * `Surface`'s clipped bounds - reachable in code (`GameViewModel.dismissHint`) but
 * never actually visible to the player.
 */
class HintNoticeRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dismissButtonStaysOnScreenForTheLongInconclusiveMessage() {
        composeRule.setContent {
            HintNoticeRow(hintState = HintUiState.Inconclusive(), onDismiss = {})
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithText(context.getString(CoreR.string.action_dismiss)).assertIsDisplayed()
    }
}
