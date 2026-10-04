package org.finiteplay.klondike.storage

import kotlinx.coroutines.test.runTest
import org.finiteplay.solitaire.catalog.catalog.CatalogTraversalState
import org.finiteplay.solitaire.catalog.catalog.DealTraversal
import org.finiteplay.klondike.board.DrawMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration.Companion.milliseconds

/**
 * Crash-injection-style coverage (`docs/games/klondike/EXECUTION_PLAN.md`, S1 gate): reasons about "what
 * if the process died right after step N" at each ordering point between the
 * active-game store, the history store, and the traversal store. These are logic tests
 * against this module's own serialization and write boundaries, not an OS-level crash
 * simulation — each "crash" point is modeled by simply not making the next call (a
 * coroutine that never resumes looks identical, from disk, to one that was never
 * launched), then continuing by constructing a brand-new store instance over the same
 * directory, so every "resume" step reads only what was durably written, not anything
 * held in a previous instance's memory.
 */
class CrossStoreOrderingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `a crash between the active-game write and the history upsert leaves history absent, active game intact`() = runTest {
        val session = playRealisticSession(seed = 21L)
        val dir = tempFolder.newFolder()
        activeGameStore(dir).save(session, initialAutomaticMovesEnabled = true, elapsed = 1_000L.milliseconds, gameId = "game-1")

        // Simulated crash: the history upsert that should follow a win never runs.
        val restoredGame = activeGameStore(dir).load()
        check(restoredGame is ActiveGameLoadResult.Restored)
        assertEquals(session, restoredGame.session)
        assertEquals(emptyList<HistoryRecord>(), historyStore(dir).all())

        // Execution resumes: the history upsert now runs, and repeating it (as a retry
        // after an ambiguous crash might) stays idempotent.
        val result = HistoryRecord(
            gameId = "game-21",
            resultId = "result-21",
            outcome = Outcome.WIN,
            elapsedMillis = 1_000L,
            moveCount = session.state.moveCount,
            timestampMillis = 1L,
        )
        val history = historyStore(dir)
        history.upsert(result)
        history.upsert(result)

        assertEquals(listOf(result), historyStore(dir).all())
    }

    @Test
    fun `a crash between the history upsert and the traversal advance leaves traversal unchanged, history intact`() = runTest {
        val dir = tempFolder.newFolder()
        val initialTraversal = catalogTraversalStore(dir).loadOrStartNew(catalogVersion = 1, recordCount = 10)
        val result = HistoryRecord(
            gameId = "game-9",
            resultId = "result-9",
            outcome = Outcome.WIN,
            elapsedMillis = 5_000L,
            moveCount = 40,
            timestampMillis = 2L,
        )
        historyStore(dir).upsert(result)

        // Simulated crash: the traversal advance that should follow a new-game selection
        // never runs.
        val traversalAfterCrash = catalogTraversalStore(dir).load(currentCatalogVersion = 1, currentRecordCount = 10)
        assertEquals(TraversalLoadResult.Present(initialTraversal), traversalAfterCrash)
        assertEquals(listOf(result), historyStore(dir).all())

        // Execution resumes: the traversal advances normally afterward.
        val advanced = DealTraversal.advance(initialTraversal, recordCount = 10)
        catalogTraversalStore(dir).save(advanced, recordCount = 10)

        val finalTraversal = catalogTraversalStore(dir).load(currentCatalogVersion = 1, currentRecordCount = 10)
        assertEquals(TraversalLoadResult.Present(advanced), finalTraversal)
    }

    @Test
    fun `a torn write to the active-game file is recovered without touching settings, traversal, or history`() = runTest {
        val dir = tempFolder.newFolder()

        // Populate the other three stores first, with values distinct from any default.
        val settings = settingsStore(dir)
        settings.setAutomaticMovesEnabled(false)
        settings.setAnimationsEnabled(true)
        val traversalState = CatalogTraversalState(catalogVersion = 1, nextPosition = 5)
        catalogTraversalStore(dir).save(traversalState, recordCount = 10)
        val historyRecord = HistoryRecord(
            gameId = "game-1",
            resultId = "result-1",
            outcome = Outcome.LOSS,
            elapsedMillis = 250L,
            moveCount = 12,
            timestampMillis = 3L,
        )
        historyStore(dir).upsert(historyRecord)

        // A torn write to the active-game file: present, non-empty, but not a valid
        // Preferences payload.
        FakeDataStores.corrupt(dir, "active_game")

        val recovery = activeGameStore(dir).load()
        assertEquals(ActiveGameLoadResult.Recovered, recovery)

        // The other three stores are completely unaffected.
        assertEquals(
            Settings(
                automaticMovesEnabled = false,
                animationsEnabled = true,
                hintShowsWinningMove = true,
                hintTimeout = Settings.DEFAULT.hintTimeout,
                restReminderInterval = Settings.DEFAULT.restReminderInterval,
                handedness = Handedness.RIGHT,
                soundEnabled = false,
                drawMode = DrawMode.ONE,
                difficulty = Settings.DEFAULT.difficulty,
                languageTag = Settings.DEFAULT.languageTag,
                themeMode = Settings.DEFAULT.themeMode,
            ),
            settingsStore(dir).current(),
        )
        assertEquals(
            TraversalLoadResult.Present(traversalState),
            catalogTraversalStore(dir).load(currentCatalogVersion = 1, currentRecordCount = 10),
        )
        assertEquals(listOf(historyRecord), historyStore(dir).all())
    }

    @Test
    fun `writes to other stores keep working while the active-game store holds corrupt data`() = runTest {
        val dir = tempFolder.newFolder()
        FakeDataStores.corrupt(dir, "active_game")

        // Neither of these touches the active-game file, so both succeed even though it
        // is currently unreadable.
        settingsStore(dir).setAnimationsEnabled(true)
        val record = HistoryRecord(
            gameId = "game-x",
            resultId = "result-x",
            outcome = Outcome.WIN,
            elapsedMillis = 10L,
            moveCount = 1,
            timestampMillis = 4L,
        )
        historyStore(dir).upsert(record)

        assertTrue(settingsStore(dir).current().animationsEnabled)
        assertEquals(listOf(record), historyStore(dir).all())
    }
}
