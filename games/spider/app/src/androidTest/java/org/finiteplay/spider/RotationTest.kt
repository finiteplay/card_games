package org.finiteplay.spider

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.finiteplay.spider.storage.SpiderActiveGameStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * S4's rotation coverage: the real activity, the real `SpiderViewModel` factory, and the real
 * `ViewModelStoreOwner` a config change hands back the same instance from — the property
 * `ActivityScenario.recreate()` exercises the same way a physical rotation would, without a real
 * device orientation change actually being necessary for it (both trigger the identical
 * Activity-recreation lifecycle a config change causes).
 *
 * Runs against the real save location (`MainActivity`'s own wiring, `context.filesDir`), unlike
 * every other Spider instrumented test, which constructs its own `SpiderViewModel` directly — this
 * is the one test that has to exercise that wiring to mean anything. The test itself does not
 * depend on whatever save already existed when the activity launched — it only compares the board
 * to itself before and after recreating — and clears the real active-game save afterward, so it
 * leaves nothing behind for a later manual install or test run to trip over.
 */
class RotationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // The rule launches the real activity, restoring from whatever save already exists, before
    // any @Before here would run — so this cannot give the test a clean starting board, only
    // avoid leaving one behind. The test itself does not depend on the starting state; it only
    // compares the board to itself before and after recreating.
    @After
    fun clearWhatThisTestLeftBehind() = runBlocking {
        SpiderActiveGameStore(composeRule.activity.filesDir).clear()
    }

    @Test
    fun theBoardSurvivesAConfigurationChange() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("stock").performClick()
        composeRule.waitForIdle()

        val beforeStatus = statusText()
        assertTrue("fixture must show a nonzero move count before recreating", beforeStatus.contains("1"))
        val beforeElapsed = elapsedSecondsIn(beforeStatus)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        val afterStatus = statusText()
        // Compared with the ticking elapsed-time suffix stripped out: the timer runs exactly
        // while the game is foreground and unfinished (`docs/games/spider/RULES.md`), a config
        // change is still foreground, so a `recreate()` slow enough to cross a one-second
        // boundary correctly advances it - that is real time passing, not lost state, and an
        // exact string comparison here was asserting a timing coincidence rather than survival.
        assertEquals(
            "the board shown after a config change must be the exact one before it, aside from the ticking timer",
            stripElapsedTime(beforeStatus),
            stripElapsedTime(afterStatus),
        )
        val afterElapsed = elapsedSecondsIn(afterStatus)
        assertTrue(
            "elapsed time must not go backward or jump across a config change: before=$beforeElapsed after=$afterElapsed",
            afterElapsed in beforeElapsed..(beforeElapsed + 5),
        )
    }

    private fun statusText(): String =
        composeRule.onNodeWithTag("status_row").fetchSemanticsNode().config[SemanticsProperties.Text]
            .joinToString(separator = "") { it.text }

    private val timePattern = Regex("""Time:\s*(\d+):(\d+)""")

    private fun elapsedSecondsIn(status: String): Int {
        val match = timePattern.find(status) ?: error("no \"Time: M:SS\" found in status text: $status")
        val (minutes, seconds) = match.destructured
        return minutes.toInt() * 60 + seconds.toInt()
    }

    private fun stripElapsedTime(status: String): String = timePattern.replace(status, "Time:")
}
