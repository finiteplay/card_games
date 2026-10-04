package org.finiteplay.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Path each store in this package is backed by, inside the given [directory].
 *
 * Callers own the directory: real usage points this at an app-private directory (e.g.
 * `context.filesDir`); tests point it at a per-test temp directory, which is what keeps
 * every store in this package plain-JVM-testable without a real Android `Context`.
 */
fun preferencesFileAt(directory: File, name: String): File = File(directory, "$name.preferences_pb")

/**
 * One [DataStore] per absolute file path, for the lifetime of the process. DataStore
 * itself requires this — constructing a second instance over a file an existing one
 * still has open throws `IllegalStateException: There are multiple DataStores active
 * for the same file` the moment either is used. Real usage never repeats a (directory,
 * name) pair long enough to matter (`rememberGameViewModelFactory` builds each store
 * once per `GameViewModel`, itself cached by `ViewModelProvider` for as long as its
 * owning Activity's task lives), but `MainActivityTest` launches a fresh `MainActivity`
 * — and so a fresh `GameViewModel` and fresh stores — per `@Test` method without
 * restarting the process between them, which hits this exact conflict without the
 * cache: the previous test's store is still technically "active" (nothing ever
 * cancels its scope) when the next one is constructed over the same real file.
 */
private val activeDataStores = ConcurrentHashMap<String, DataStore<Preferences>>()

/**
 * Builds a [Preferences] DataStore backed by its own file (see [preferencesFileAt]).
 * Every store in this package gets a distinct file so a corrupt or truncated write to
 * one can never take another down with it (`docs/PLATFORM.md`, "Persistence").
 *
 * A file that fails to parse (a torn write, garbage bytes) is replaced with empty
 * preferences rather than thrown from every future read and write against it: without
 * this, a corrupt file would also break the *next* save, since DataStore's `edit`
 * reads the current value before applying an update. Each store layers its own
 * missing-key/wrong-format-version checks on top of this to detect "was corrupt" and
 * react per `docs/PLATFORM.md` ("Persistence").
 */
fun preferencesDataStoreAt(directory: File, name: String): DataStore<Preferences> {
    val file = preferencesFileAt(directory, name)
    return activeDataStores.computeIfAbsent(file.absolutePath) {
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { file },
        )
    }
}
