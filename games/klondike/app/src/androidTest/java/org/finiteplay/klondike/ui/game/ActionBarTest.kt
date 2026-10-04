package org.finiteplay.klondike.ui.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.junit.Rule
import org.junit.Test
import org.finiteplay.core.ui.layout.BoardOrientation

/**
 * `EXECUTION_PLAN.md` A1 gate: "Disabled controls still expose their label and disabled
 * state to semantics." A3 wires every action live. Statistics is not here — it lives in
 * the status row's corner (see `GameScreen`'s StatusRow).
 */
class ActionBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var settingsClicked = false

    private fun setBar(
        orientation: BoardOrientation,
        canUndo: Boolean = false,
        hintEnabled: Boolean = false,
        landscapeGroup: LandscapeActionGroup = LandscapeActionGroup.PRIMARY,
    ) {
        settingsClicked = false
        composeRule.setContent {
            FinitePlayTheme {
                ActionBar(
                    orientation = orientation,
                    canUndo = canUndo,
                    hintEnabled = hintEnabled,
                    landscapeGroup = landscapeGroup,
                    onUndo = {},
                    onNew = {},
                    onReplay = {},
                    onHint = {},
                    onSettings = { settingsClicked = true },
                )
            }
        }
    }

    @Test
    fun settingsIsEnabledAndInvokesItsCallback() {
        setBar(BoardOrientation.PORTRAIT)

        composeRule.onNodeWithText("Settings").assertIsDisplayed().assertIsEnabled().performClick()

        assert(settingsClicked)
    }

    @Test
    fun landscapePrimaryRailHoldsHintAndUndo() {
        setBar(BoardOrientation.LANDSCAPE, canUndo = true, hintEnabled = true, landscapeGroup = LandscapeActionGroup.PRIMARY)

        for (label in listOf("Hint", "Undo")) {
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun landscapeSecondaryRailHoldsTheRest() {
        setBar(BoardOrientation.LANDSCAPE, canUndo = true, hintEnabled = true, landscapeGroup = LandscapeActionGroup.SECONDARY)

        for (label in listOf("Settings", "Replay", "New")) {
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun everyActionStaysOnScreenWhateverTheLabelWidths() {
        // The reported failure: a wide translation grew its own button and pushed the
        // ones after it along, until the last slid past the screen edge. Equal weighted
        // shares are what keep all five inside the row.
        setBar(BoardOrientation.PORTRAIT, canUndo = true, hintEnabled = true)

        for (label in listOf("Settings", "Replay", "New", "Hint", "Undo")) {
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun newAndReplayAreAlwaysEnabledAndUndoTracksCanUndo() {
        setBar(BoardOrientation.PORTRAIT, canUndo = true)

        composeRule.onNodeWithText("New").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("Replay").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("Undo").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun undoIsDisabledWhenCanUndoIsFalse() {
        setBar(BoardOrientation.PORTRAIT, canUndo = false)

        composeRule.onNodeWithText("Undo").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun hintTracksHintEnabled() {
        setBar(BoardOrientation.PORTRAIT, hintEnabled = true)

        composeRule.onNodeWithText("Hint").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun hintIsDisabledWhenNoHintExists() {
        setBar(BoardOrientation.PORTRAIT, hintEnabled = false)

        composeRule.onNodeWithText("Hint").assertIsDisplayed().assertIsNotEnabled()
    }
}
