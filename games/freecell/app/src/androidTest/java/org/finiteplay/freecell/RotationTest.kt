package org.finiteplay.freecell

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.finiteplay.freecell.storage.FreeCellActiveGameStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * F2's rotation coverage: the real activity, the real `FreeCellViewModel` factory, and the real
 * `ViewModelStoreOwner` a config change hands back the same instance from — the property
 * `ActivityScenario.recreate()` exercises the same way a physical rotation would, without a real
 * device orientation change actually being necessary for it, mirroring Spider's own
 * `RotationTest`.
 *
 * Runs against the real save location (`MainActivity`'s own wiring, `context.filesDir`), unlike
 * every other FreeCell instrumented test, which constructs its own `FreeCellViewModel`
 * directly — this is the one test that has to exercise that wiring to mean anything. The test
 * itself does not depend on whatever save already existed when the activity launched — it only
 * compares the board to itself before and after recreating — and clears the real active-game
 * save afterward, so it leaves nothing behind for a later manual install or test run to trip
 * over.
 */
class RotationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun clearWhatThisTestLeftBehind() = runBlocking {
        FreeCellActiveGameStore(composeRule.activity.filesDir).clear()
    }

    @Test
    fun theBoardSurvivesAConfigurationChange() {
        // `waitForIdle` alone is not enough: the opening deal is decided only once the bundled
        // catalog and any active-game save have both been read (`FreeCellViewModel`'s own
        // `isLoading`), an async read this hops off the main thread for, and a slower device can
        // still be mid-read after Compose itself reports nothing pending.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("action_new").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("action_new").performClick()
        composeRule.waitForIdle()

        val beforeStatus = statusText()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        assertEquals("the board shown after a config change must be the exact one before it", beforeStatus, statusText())
        assertTrue("sanity: the status row must actually show something", beforeStatus.isNotBlank())
    }

    private fun statusText(): String =
        composeRule.onNodeWithTag("status_row").fetchSemanticsNode().config[SemanticsProperties.Text]
            .joinToString(separator = "") { it.text }
}
