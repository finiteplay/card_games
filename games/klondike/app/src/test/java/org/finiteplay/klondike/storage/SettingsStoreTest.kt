package org.finiteplay.klondike.storage

import kotlinx.coroutines.test.runTest
import org.finiteplay.core.ui.layout.HintTimeout
import org.finiteplay.core.ui.layout.RestReminderInterval
import org.finiteplay.klondike.board.DrawMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `defaults are automatic moves on, animations on, right-handed, sound off, and draw-one`() = runTest {
        val settings = settingsStore(tempFolder.newFolder()).current()
        assertEquals(
            Settings(
                automaticMovesEnabled = true,
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
            settings,
        )
    }

    @Test
    fun `difficulty defaults to Trivial, so a first-ever game is winnable on sight`() = runTest {
        val settings = settingsStore(tempFolder.newFolder()).current()
        assertEquals(DifficultyPreference.TRIVIAL, settings.difficulty)
    }

    @Test
    fun `settings survive a simulated restart`() = runTest {
        val dir = tempFolder.newFolder()
        val store = settingsStore(dir)
        store.setAutomaticMovesEnabled(false)
        store.setAnimationsEnabled(false)
        store.setHandedness(Handedness.LEFT)
        store.setSoundEnabled(false)
        store.setDrawMode(DrawMode.THREE)

        val restored = settingsStore(dir).current()

        assertEquals(
            Settings(
                automaticMovesEnabled = false,
                animationsEnabled = false,
                hintShowsWinningMove = true,
                hintTimeout = Settings.DEFAULT.hintTimeout,
                restReminderInterval = Settings.DEFAULT.restReminderInterval,
                handedness = Handedness.LEFT,
                soundEnabled = false,
                drawMode = DrawMode.THREE,
                difficulty = Settings.DEFAULT.difficulty,
                languageTag = Settings.DEFAULT.languageTag,
                themeMode = Settings.DEFAULT.themeMode,
            ),
            restored,
        )
    }

    @Test
    fun `each setting can change independently of the others`() = runTest {
        val store = settingsStore(tempFolder.newFolder())
        store.setAnimationsEnabled(false)

        assertEquals(
            Settings(
                automaticMovesEnabled = true,
                animationsEnabled = false,
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
            store.current(),
        )
    }

    @Test
    fun `hint-shows-winning-move survives a simulated restart`() = runTest {
        val dir = tempFolder.newFolder()
        settingsStore(dir).setHintShowsWinningMove(false)

        assertEquals(false, settingsStore(dir).current().hintShowsWinningMove)
    }

    @Test
    fun `hint timeout defaults to 5 seconds and survives a simulated restart`() = runTest {
        val dir = tempFolder.newFolder()
        assertEquals(HintTimeout.FIVE, settingsStore(dir).current().hintTimeout)

        settingsStore(dir).setHintTimeout(HintTimeout.TWELVE)

        assertEquals(HintTimeout.TWELVE, settingsStore(dir).current().hintTimeout)
    }

    @Test
    fun `rest reminder interval defaults to 60 minutes and survives a simulated restart`() = runTest {
        val dir = tempFolder.newFolder()
        assertEquals(RestReminderInterval.SIXTY, settingsStore(dir).current().restReminderInterval)

        settingsStore(dir).setRestReminderInterval(RestReminderInterval.NEVER)

        assertEquals(RestReminderInterval.NEVER, settingsStore(dir).current().restReminderInterval)
    }

    @Test
    fun `a garbage settings file falls back to defaults instead of throwing`() = runTest {
        val dir = tempFolder.newFolder()
        FakeDataStores.corrupt(dir, "settings")

        assertEquals(Settings.DEFAULT, settingsStore(dir).current())
    }

    @Test
    fun `an unrecognized stored handedness name falls back to the default rather than crashing`() = runTest {
        val dir = tempFolder.newFolder()
        val store = settingsStore(dir)
        store.setHandedness(Handedness.LEFT)
        // Simulate a downgrade after a future enum entry was persisted, or any other
        // corruption limited to this one key: the raw preference no longer maps to a
        // known Handedness value.
        FakeDataStores.setRawStringPreference(dir, "settings", "handedness", "AMBIDEXTROUS")

        assertEquals(Handedness.RIGHT, settingsStore(dir).current().handedness)
    }

    @Test
    fun `an unrecognized stored draw mode name falls back to the default rather than crashing`() = runTest {
        val dir = tempFolder.newFolder()
        val store = settingsStore(dir)
        store.setDrawMode(DrawMode.THREE)
        FakeDataStores.setRawStringPreference(dir, "settings", "draw_mode", "SEVEN")

        assertEquals(DrawMode.ONE, settingsStore(dir).current().drawMode)
    }
}
