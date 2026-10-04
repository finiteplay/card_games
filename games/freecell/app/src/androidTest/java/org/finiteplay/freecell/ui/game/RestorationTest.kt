package org.finiteplay.freecell.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.freecell.rules.Move
import org.finiteplay.freecell.storage.FreeCellActiveGameLoadResult
import org.finiteplay.freecell.storage.FreeCellActiveGameStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * F3a's restoration coverage: a played game, persisted through the real store exactly as
 * `MainActivity` wires it, comes back identically to a fresh [FreeCellViewModel] pointed at the
 * same file — the on-device counterpart to `FreeCellActiveGameStoreTest`'s store-level round trip.
 *
 * A unique subdirectory of the instrumentation target's own files dir, not the app's real save
 * location: this must never read or clobber whatever save the app under test already has.
 */
class RestorationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var directory: File

    private fun viewModel(): FreeCellViewModel = FreeCellViewModel(store = FreeCellActiveGameStore(directory))

    @Test
    fun aPlayedGamePersistsAndRestoresIdenticallyToANewViewModel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(context.filesDir, "restoration_test_${System.nanoTime()}").apply { mkdirs() }

        val first = viewModel()
        // A test-owned snapshot state holding *which* view model is composed, so the second
        // instance can be swapped in by recomposition — a real ComposeTestRule permits only one
        // `setContent` call per activity, the same technique Spider's own `RestorationTest` uses.
        var current by mutableStateOf(first)
        composeRule.setContent { FinitePlayTheme { GameScreen(viewModel = current) } }
        composeRule.waitForIdle()

        // Parking the top card of the first non-empty column into a free cell is legal for any
        // seed at all — every free cell starts empty — which is what this test actually needs;
        // whether a *tap* resolves to a move at all is `GameScreenTest`'s own concern, and
        // depending on it here would make this test's reliability depend on the random deal.
        val state = first.session.state
        val column = state.tableau.indexOfFirst { it.isNotEmpty() }
        assertTrue("fixture must find at least one non-empty column", first.dragMove(Move.TableauToFreeCell(column, 0)))
        composeRule.waitForIdle()

        val playedTableau = first.session.state.tableau
        val playedFreeCells = first.session.state.freeCells
        val playedFoundations = first.session.state.foundations
        val playedSeed = first.session.state.seed
        val playedMoveCount = first.session.state.moveCount
        assertTrue("fixture must have actually made a move", playedMoveCount > 0)

        // `scheduleSave` fires a coroutine on `first`'s own view-model scope, which is not tied
        // to Compose's recomposition clock — `waitForIdle` alone does not guarantee it has landed
        // yet, and a `second` constructed too early would read a still-missing save and deal a
        // fresh, unrelated hand instead of restoring this one. Poll the real store directly
        // rather than adding an artificial delay: the save is what this test is actually waiting
        // for, not a fixed amount of time.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val loaded = runBlocking { FreeCellActiveGameStore(directory).load() }
            loaded is FreeCellActiveGameLoadResult.Restored && loaded.session.state.moveCount == playedMoveCount
        }

        val second = viewModel()
        current = second
        composeRule.waitForIdle()

        assertEquals("restore must reproduce the exact tableau", playedTableau, second.session.state.tableau)
        assertEquals("restore must reproduce the exact free cells", playedFreeCells, second.session.state.freeCells)
        assertEquals("restore must reproduce the exact foundations", playedFoundations, second.session.state.foundations)
        assertEquals(playedSeed, second.session.state.seed)
        assertEquals(playedMoveCount, second.session.state.moveCount)
        assertEquals(false, second.recoveryNoticeVisible)
        assertEquals(true, second.session.canUndo)
    }
}
