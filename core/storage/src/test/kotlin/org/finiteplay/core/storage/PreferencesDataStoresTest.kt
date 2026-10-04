package org.finiteplay.core.storage

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * DataStore itself requires one instance per file for the process lifetime -
 * constructing a second over a file the first still has open throws
 * `IllegalStateException: There are multiple DataStores active for the same file` the
 * moment either is used. `MainActivityTest` hit exactly this: each `@Test` method
 * launches a fresh `MainActivity` (and so a fresh `GameViewModel` and fresh stores)
 * without restarting the process between them.
 */
class PreferencesDataStoresTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `the same directory and name return the identical instance`() {
        val dir = tempFolder.newFolder()

        val first = preferencesDataStoreAt(dir, "settings")
        val second = preferencesDataStoreAt(dir, "settings")

        assertSame(first, second)
    }

    @Test
    fun `different names in the same directory return different instances`() {
        val dir = tempFolder.newFolder()

        val settings = preferencesDataStoreAt(dir, "settings")
        val activeGame = preferencesDataStoreAt(dir, "active_game")

        assertNotSame(settings, activeGame)
    }

    @Test
    fun `the same name in different directories returns different instances`() {
        val first = preferencesDataStoreAt(tempFolder.newFolder(), "settings")
        val second = preferencesDataStoreAt(tempFolder.newFolder(), "settings")

        assertNotSame(first, second)
    }

    @Test
    fun `the cached instance still reads back what was written through it`() = runTest {
        val dir = tempFolder.newFolder()
        val key = booleanPreferencesKey("flag")

        preferencesDataStoreAt(dir, "settings").edit { it[key] = true }
        val reread = preferencesDataStoreAt(dir, "settings").data.first()

        assertEquals(true, reread[key])
    }
}
