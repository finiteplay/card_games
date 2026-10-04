package org.finiteplay.klondike.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File
import org.finiteplay.core.storage.FakeDataStores as SharedFakeDataStores

/**
 * `core:storage`'s test fixture, re-exported under this package so the call sites throughout these
 * tests are unchanged. The substitute itself is shared because the DataStore limitations that
 * force it — the Windows rename failure, and DataStore refusing two instances over one file, which
 * is what any restart simulation does — are not this game's problem (see the shared class for the
 * full reasoning).
 */
object FakeDataStores {
    fun create(directory: File, name: String): DataStore<Preferences> =
        SharedFakeDataStores.create(directory, name)

    fun corrupt(directory: File, name: String) = SharedFakeDataStores.corrupt(directory, name)

    fun setRawStringPreference(directory: File, name: String, preferenceKey: String, value: String) =
        SharedFakeDataStores.setRawStringPreference(directory, name, preferenceKey, value)
}
