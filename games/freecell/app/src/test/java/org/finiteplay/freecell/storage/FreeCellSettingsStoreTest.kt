package org.finiteplay.freecell.storage

import kotlinx.coroutines.test.runTest
import org.finiteplay.core.storage.FakeDataStores
import org.finiteplay.core.ui.layout.Handedness
import org.finiteplay.core.ui.layout.HintTimeout
import org.finiteplay.core.ui.layout.RestReminderInterval
import org.finiteplay.core.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FreeCellSettingsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun storeIn(dir: java.io.File) = FreeCellSettingsStore(dir, dataStoreFactory = FakeDataStores::create)

    @Test
    fun `defaults are automatic moves on, animations on, right-handed, and sound off`() = runTest {
        assertEquals(FreeCellSettings.DEFAULT, storeIn(folder.newFolder()).current())
    }

    @Test
    fun `settings survive a simulated restart`() = runTest {
        val dir = folder.newFolder()
        val store = storeIn(dir)
        store.setAutomaticMovesEnabled(false)
        store.setAnimationsEnabled(false)
        store.setHandedness(Handedness.LEFT)
        store.setSoundEnabled(true)
        store.setThemeMode(ThemeMode.DARK)

        val restored = storeIn(dir).current()

        assertEquals(
            FreeCellSettings(
                automaticMovesEnabled = false,
                animationsEnabled = false,
                hintShowsWinningMove = true,
                hintTimeout = FreeCellSettings.DEFAULT.hintTimeout,
                restReminderInterval = FreeCellSettings.DEFAULT.restReminderInterval,
                handedness = Handedness.LEFT,
                soundEnabled = true,
                languageTag = FreeCellSettings.DEFAULT.languageTag,
                themeMode = ThemeMode.DARK,
            ),
            restored,
        )
    }

    @Test
    fun `hint timeout defaults to 5 seconds and survives a simulated restart`() = runTest {
        val dir = folder.newFolder()
        assertEquals(HintTimeout.FIVE, storeIn(dir).current().hintTimeout)

        storeIn(dir).setHintTimeout(HintTimeout.TWELVE)

        assertEquals(HintTimeout.TWELVE, storeIn(dir).current().hintTimeout)
    }

    @Test
    fun `rest reminder interval defaults to 60 minutes and survives a simulated restart`() = runTest {
        val dir = folder.newFolder()
        assertEquals(RestReminderInterval.SIXTY, storeIn(dir).current().restReminderInterval)

        storeIn(dir).setRestReminderInterval(RestReminderInterval.NEVER)

        assertEquals(RestReminderInterval.NEVER, storeIn(dir).current().restReminderInterval)
    }

    @Test
    fun `each setting can change independently of the others`() = runTest {
        val store = storeIn(folder.newFolder())
        store.setAnimationsEnabled(false)

        assertEquals(FreeCellSettings.DEFAULT.copy(animationsEnabled = false), store.current())
    }

    @Test
    fun `hint-shows-winning-move survives a simulated restart`() = runTest {
        val dir = folder.newFolder()
        storeIn(dir).setHintShowsWinningMove(false)

        assertEquals(false, storeIn(dir).current().hintShowsWinningMove)
    }

    @Test
    fun `a garbage settings file falls back to defaults instead of throwing`() = runTest {
        val dir = folder.newFolder()
        FakeDataStores.corrupt(dir, "settings")

        assertEquals(FreeCellSettings.DEFAULT, storeIn(dir).current())
    }

    @Test
    fun `an unrecognized stored handedness name falls back to the default rather than crashing`() = runTest {
        val dir = folder.newFolder()
        storeIn(dir).setHandedness(Handedness.LEFT)
        // Simulate a downgrade after a future enum entry was persisted, or any other corruption
        // limited to this one key: the raw preference no longer maps to a known Handedness value.
        FakeDataStores.setRawStringPreference(dir, "settings", "handedness", "AMBIDEXTROUS")

        assertEquals(Handedness.RIGHT, storeIn(dir).current().handedness)
    }

    @Test
    fun `an unrecognized stored theme mode name falls back to the default rather than crashing`() = runTest {
        val dir = folder.newFolder()
        storeIn(dir).setThemeMode(ThemeMode.DARK)
        FakeDataStores.setRawStringPreference(dir, "settings", "theme_mode", "NEON")

        assertEquals(FreeCellSettings.DEFAULT.themeMode, storeIn(dir).current().themeMode)
    }
}
