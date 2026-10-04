package org.finiteplay.klondike.storage

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.session.LogEntryCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pins the **on-disk shape** of an active-game save, written by hand rather than by the store.
 *
 * Every other test in this package round-trips through `ActiveGameStore`, which proves the store
 * agrees with itself and nothing more — rename a key in both directions and they all still pass
 * while every save on every installed device becomes unreadable. This one writes the exact keys
 * and types a shipped build wrote and asserts the current code restores from them, so the format
 * is pinned independently of whatever the store looks like today.
 *
 * A failure here is not a test to update. It means a shipped save no longer loads, and the fix is
 * either to keep the old keys or to bump `CURRENT_FORMAT_VERSION` and handle the old shape as a
 * recognised generation — never to edit the constants below to match the new code.
 */
class ActiveGameFormatCompatibilityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // Spelled out rather than referenced from the store, deliberately: reusing the store's own
    // key objects would make this test agree with any rename, which is the failure it exists to
    // catch.
    private val formatVersionKey = intPreferencesKey("format_version")
    private val gameIdKey = stringPreferencesKey("game_id")
    private val seedKey = longPreferencesKey("seed")
    private val catalogVersionKey = intPreferencesKey("catalog_version")
    private val rulesVersionKey = intPreferencesKey("rules_version")
    private val shuffleVersionKey = intPreferencesKey("shuffle_version")
    private val initialAutomaticMovesKey = booleanPreferencesKey("initial_automatic_moves_enabled")
    private val drawModeKey = stringPreferencesKey("draw_mode")
    private val elapsedMillisKey = longPreferencesKey("elapsed_millis")
    private val logKey = stringPreferencesKey("log")

    @Test
    fun `a save written in the shipped v2 layout still restores`() = runTest {
        val session = playRealisticSession(seed = 4242L)
        val dir = tempFolder.newFolder()

        // Written directly into the preferences file the store reads, bypassing the store.
        FakeDataStores.create(dir, "active_game").edit { prefs ->
            prefs[formatVersionKey] = 2
            prefs[gameIdKey] = "game-compat"
            prefs[seedKey] = session.state.seed
            prefs[catalogVersionKey] = session.state.versions.catalogVersion
            prefs[rulesVersionKey] = session.state.versions.rulesVersion
            prefs[shuffleVersionKey] = session.state.versions.shuffleVersion
            prefs[initialAutomaticMovesKey] = true
            prefs[drawModeKey] = DrawMode.ONE.name
            prefs[elapsedMillisKey] = 91_234L
            prefs[logKey] = Base64.getEncoder().encodeToString(LogEntryCodec.encode(session.log))
        }

        val restored = activeGameStore(dir).load()

        check(restored is ActiveGameLoadResult.Restored) { "expected Restored, got $restored" }
        assertEquals(session, restored.session)
        assertEquals(91_234L.milliseconds, restored.elapsed)
        assertEquals("game-compat", restored.gameId)
        assertTrue(restored.initialAutomaticMovesEnabled)
    }

    @Test
    fun `the shipped format version is 2, and changing it is a decision not an accident`() {
        // A bump is legitimate; a bump nobody noticed is not. This fails loudly so the change
        // arrives with a decision about what happens to saves written under the old number.
        assertEquals(2, ActiveGameStore.CURRENT_FORMAT_VERSION)
    }

    @Test
    fun `a save from an older format version is discarded rather than misread`() = runTest {
        val dir = tempFolder.newFolder()

        FakeDataStores.create(dir, "active_game").edit { prefs ->
            prefs[formatVersionKey] = 1
            prefs[gameIdKey] = "game-old"
            prefs[seedKey] = 1L
        }

        assertEquals(ActiveGameLoadResult.Recovered, activeGameStore(dir).load())
    }
}
