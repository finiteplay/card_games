package org.finiteplay.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * An in-memory [DataStore]`<Preferences>` substitute for JVM unit tests, injected via each store
 * class's `dataStoreFactory` constructor parameter (production leaves that parameter at its
 * default, [preferencesDataStoreAt], and never sees this class).
 *
 * On at least one Windows development environment, the real file-backed DataStore fails
 * deterministically on a second write to the same file — `IOException: Unable to rename
 * ...preferences_pb.tmp ... This likely means that there are multiple instances of DataStore for
 * this file` — even from a single store instance issuing two sequential `edit` calls, and even
 * against the newest available `1.3.0` alpha that specifically advertises a fix for a
 * temp-file-rename bug. This is a long-documented, environment-specific limitation independent of
 * this module's code (a bare `java.nio.file.Files.move` loop with no DataStore involved does not
 * reproduce it; real Android runtime usage and Linux CI are unaffected; the Android community's
 * own accepted workaround for exactly this failure is the same one used here — see the
 * `nowinandroid` project's `InMemoryDataStore` fix for the identical symptom).
 *
 * Constructing two real DataStores over one file is also rejected outright by DataStore itself,
 * which is what a test simulating a restart naturally does — a second store instance reading what
 * the first wrote. That alone makes a substitute necessary regardless of platform.
 *
 * This fake keeps unit tests exercising the real `Preferences`/`MutablePreferences` API and each
 * store's own read/write/corruption logic, without touching the OS filesystem's rename path at
 * all. Content is keyed by absolute canonical path in a process-wide map, so a fresh [create] call
 * for the same (directory, name) sees whatever a previous instance left behind — no shared Kotlin
 * object state, only "durable" content, which is exactly what a fresh-reader-after-restart test
 * needs.
 */
object FakeDataStores {

    private val content = ConcurrentHashMap<String, Preferences>()
    private val pendingCorruption = ConcurrentHashMap.newKeySet<String>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** Use as a store's `dataStoreFactory` argument in tests. */
    fun create(directory: File, name: String): DataStore<Preferences> = FakeDataStore(keyFor(directory, name))

    /**
     * Marks the store at (directory, name) as corrupt, as if its underlying file held unparseable
     * bytes: the next read heals it to empty preferences, exactly like [preferencesDataStoreAt]'s
     * real `ReplaceFileCorruptionHandler` does. Also touches a real (dummy) file at that path so a
     * store's "did a save already exist" check — a plain filesystem existence check, independent
     * of which `DataStore` is in use — sees it, matching what a genuinely corrupt on-disk save
     * looks like.
     */
    fun corrupt(directory: File, name: String) {
        val file = preferencesFileAt(directory, name)
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(0))
        pendingCorruption += keyFor(directory, name)
    }

    /**
     * Writes a raw string preference directly, bypassing the owning store's typed API — for
     * simulating a stored value that no longer maps to a known enum entry (e.g. after downgrading
     * past a removed option). [Preferences.Key] equality is by name, so a fresh
     * `stringPreferencesKey(name)` here targets the same entry the store itself reads.
     */
    fun setRawStringPreference(directory: File, name: String, preferenceKey: String, value: String) {
        val store = create(directory, name)
        runBlocking { store.edit { it[stringPreferencesKey(preferenceKey)] = value } }
    }

    /** Makes a save look present to the existence check without writing a readable record. */
    fun touchFile(directory: File, name: String) {
        val file = preferencesFileAt(directory, name)
        file.parentFile?.mkdirs()
        if (!file.exists()) file.writeBytes(byteArrayOf(0))
    }

    private fun keyFor(directory: File, name: String): String = preferencesFileAt(directory, name).absolutePath

    private fun lockFor(path: String): Mutex = locks.computeIfAbsent(path) { Mutex() }

    private class FakeDataStore(private val path: String) : DataStore<Preferences> {

        override val data: Flow<Preferences> = flow { emit(currentValue()) }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            lockFor(path).withLock {
                val updated = transform(currentValue())
                content[path] = updated
                updated
            }

        /** Reads the current value, healing a pending-corruption marker exactly once. */
        private fun currentValue(): Preferences {
            if (pendingCorruption.remove(path)) {
                val healed = emptyPreferences()
                content[path] = healed
                return healed
            }
            return content[path] ?: emptyPreferences()
        }
    }
}
