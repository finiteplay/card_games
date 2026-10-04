package org.finiteplay.klondike.storage

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import org.finiteplay.klondike.board.DrawMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration.Companion.milliseconds

class ActiveGameStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `a fresh directory with no prior save reports Missing`() = runTest {
        assertEquals(ActiveGameLoadResult.Missing, activeGameStore(tempFolder.newFolder()).load())
    }

    @Test
    fun `save then a fresh reader restores an identical session via replay`() = runTest {
        val session = playRealisticSession(seed = 4242L)
        val elapsed = 91_234L.milliseconds
        val dir = tempFolder.newFolder()

        activeGameStore(dir).save(session, initialAutomaticMovesEnabled = true, elapsed = elapsed, gameId = "game-1")

        // A brand-new store instance, sharing no in-memory state with the writer above,
        // simulates a process restart.
        val restored = activeGameStore(dir).load()

        check(restored is ActiveGameLoadResult.Restored)
        assertEquals(session.state, restored.session.state)
        assertEquals(session.undoStack, restored.session.undoStack)
        assertEquals(session.log, restored.session.log)
        assertEquals(session.automaticMovesEnabled, restored.session.automaticMovesEnabled)
        assertEquals(session, restored.session)
        assertEquals(elapsed, restored.elapsed)
        assertTrue(restored.initialAutomaticMovesEnabled)
        assertEquals("game-1", restored.gameId)
    }

    @Test
    fun `a save with automation initially disabled replays with the same initial flag`() = runTest {
        val session = playRealisticSession(seed = 99L, initialAutomaticMovesEnabled = false)
        val dir = tempFolder.newFolder()
        activeGameStore(dir).save(session, initialAutomaticMovesEnabled = false, elapsed = 0L.milliseconds, gameId = "game-1")

        val restored = activeGameStore(dir).load()

        check(restored is ActiveGameLoadResult.Restored)
        assertEquals(false, restored.initialAutomaticMovesEnabled)
        assertEquals(session, restored.session)
    }

    @Test
    fun `a draw-three save restores with the same draw mode`() = runTest {
        val session = playRealisticSession(seed = 55L, drawMode = DrawMode.THREE)
        val dir = tempFolder.newFolder()
        activeGameStore(dir).save(session, initialAutomaticMovesEnabled = true, elapsed = 0L.milliseconds, gameId = "game-1")

        val restored = activeGameStore(dir).load()

        check(restored is ActiveGameLoadResult.Restored)
        assertEquals(DrawMode.THREE, restored.session.state.drawMode)
        assertEquals(session, restored.session)
    }

    @Test
    fun `a byte-garbage file that never parses is discarded rather than thrown`() = runTest {
        val dir = tempFolder.newFolder()
        FakeDataStores.corrupt(dir, "active_game")

        assertEquals(ActiveGameLoadResult.Recovered, activeGameStore(dir).load())
    }

    @Test
    fun `an unsupported format version is discarded`() = runTest {
        val session = playRealisticSession(seed = 7L)
        val dir = tempFolder.newFolder()
        activeGameStore(dir).save(session, initialAutomaticMovesEnabled = true, elapsed = 0L.milliseconds, gameId = "game-1")

        // Directly corrupt the persisted format version, bypassing ActiveGameStore's own
        // API, the way a future incompatible build might leave an old save behind.
        val raw = FakeDataStores.create(dir, "active_game")
        raw.edit { it[intPreferencesKey("format_version")] = 999 }

        assertEquals(ActiveGameLoadResult.Recovered, activeGameStore(dir).load())
    }

    @Test
    fun `a save missing a required field is discarded`() = runTest {
        val session = playRealisticSession(seed = 11L)
        val dir = tempFolder.newFolder()
        activeGameStore(dir).save(session, initialAutomaticMovesEnabled = true, elapsed = 0L.milliseconds, gameId = "game-1")

        val raw = FakeDataStores.create(dir, "active_game")
        raw.edit { it.remove(stringPreferencesKey("log")) }

        assertEquals(ActiveGameLoadResult.Recovered, activeGameStore(dir).load())
    }

    @Test
    fun `a genuine pre-draw-mode v1 save is discarded through the same Recovery path, not thrown or misread`() = runTest {
        val session = playRealisticSession(seed = 13L)
        val dir = tempFolder.newFolder()
        activeGameStore(dir).save(session, initialAutomaticMovesEnabled = true, elapsed = 0L.milliseconds, gameId = "game-1")

        // Reproduces exactly what a save written by the app before draw-three support
        // shipped looks like: format_version 1, every other v2 field present, but no
        // draw_mode key at all.
        val raw = FakeDataStores.create(dir, "active_game")
        raw.edit {
            it[intPreferencesKey("format_version")] = 1
            it.remove(stringPreferencesKey("draw_mode"))
        }

        assertEquals(ActiveGameLoadResult.Recovered, activeGameStore(dir).load())
    }

    @Test
    fun `after a corrupt save is discarded, the next save succeeds normally`() = runTest {
        val dir = tempFolder.newFolder()
        FakeDataStores.corrupt(dir, "active_game")

        val store = activeGameStore(dir)
        assertEquals(ActiveGameLoadResult.Recovered, store.load())

        val session = playRealisticSession(seed = 5L)
        store.save(session, initialAutomaticMovesEnabled = true, elapsed = 500L.milliseconds, gameId = "game-1")

        val restored = activeGameStore(dir).load()
        check(restored is ActiveGameLoadResult.Restored)
        assertEquals(session, restored.session)
    }

    @Test
    fun `clear discards the save so a later load reports Missing`() = runTest {
        val session = playRealisticSession(seed = 3L)
        val dir = tempFolder.newFolder()
        val store = activeGameStore(dir)
        store.save(session, initialAutomaticMovesEnabled = true, elapsed = 0L.milliseconds, gameId = "game-1")

        store.clear()

        assertEquals(ActiveGameLoadResult.Missing, activeGameStore(dir).load())
    }
}
