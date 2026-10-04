package org.finiteplay.spider.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.spider.storage.SpiderActiveGameStore
import org.finiteplay.spider.storage.SpiderSettingsStore
import org.finiteplay.spider.storage.SpiderTraversalStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * S4's restoration coverage: a played game, persisted through the real stores exactly as
 * `MainActivity` wires them, comes back identically to a fresh [SpiderViewModel] pointed at the
 * same files — the on-device counterpart to `SpiderActiveGameStoreTest`'s store-level round trip.
 *
 * A unique subdirectory of the instrumentation target's own files dir, not the app's real save
 * location: this must never read or clobber whatever save the app under test already has.
 */
class RestorationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var directory: File

    private fun viewModel(): SpiderViewModel = SpiderViewModel(
        store = SpiderActiveGameStore(directory),
        settingsStore = SpiderSettingsStore(directory),
        traversalStore = SpiderTraversalStore(directory),
    )

    @Test
    fun aPlayedGamePersistsAndRestoresIdenticallyToANewViewModel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(context.filesDir, "restoration_test_${System.nanoTime()}").apply { mkdirs() }

        val first = viewModel()
        // A test-owned snapshot state holding *which* view model is composed, so the second
        // instance can be swapped in by recomposition — a real ComposeTestRule permits only one
        // `setContent` call per activity, unlike Klondike's launched-and-relaunched-process own
        // restoration proof, which this on-device counterpart cannot reproduce without a second
        // process.
        var current by mutableStateOf(first)
        composeRule.setContent { FinitePlayTheme { GameScreen(viewModel = current) } }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("stock").performClick()
        composeRule.waitForIdle()
        val playedTableau = first.session.state.tableau
        val playedSeed = first.session.state.seed
        val playedMoveCount = first.session.state.moveCount
        val playedDealNumber = first.dealNumber
        assertTrue("fixture must have actually made a move", playedMoveCount > 0)

        val second = viewModel()
        current = second
        composeRule.waitForIdle()

        assertEquals("restore must reproduce the exact board", playedTableau, second.session.state.tableau)
        assertEquals(playedSeed, second.session.state.seed)
        assertEquals(playedMoveCount, second.session.state.moveCount)
        assertEquals(playedDealNumber, second.dealNumber)
        assertEquals(false, second.recoveryNoticeVisible)
    }
}
