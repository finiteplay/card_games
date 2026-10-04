package org.finiteplay.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Base64

/**
 * How far a player has got with one deal. A deal with no entry has not been played, so "not
 * played" is the absence of a value rather than a third one: the store holds only deals the
 * player has touched.
 *
 * [PLAYED] is a first move, not a result — a deal abandoned for another is still played, and a
 * win is [WON] however many tries it took.
 */
enum class DealStatus { PLAYED, WON }

/**
 * Which deals have been played and which won, keyed by seed. A seed names a board exactly (a game
 * that keeps separate seed spaces per mode or suit count never repeats one across them), so the
 * key needs nothing beyond it.
 *
 * Progress only moves forward: [mark] never turns a [DealStatus.WON] deal back into
 * [DealStatus.PLAYED], so replaying a deal already won leaves it won. A corrupt blob reads back as
 * no progress rather than an error, so a broken read here can never block another store.
 */
class DealProgressStore(
    directory: File,
    name: String = DEFAULT_NAME,
    dataStoreFactory: (File, String) -> DataStore<Preferences> = ::preferencesDataStoreAt,
) {
    private val dataStore = dataStoreFactory(directory, name)

    val progress: Flow<Map<Long, DealStatus>> = dataStore.data.map { decodeOrEmpty(it[RECORDS]) }

    suspend fun current(): Map<Long, DealStatus> = progress.first()

    /** Raises [seed] to [status]; a no-op when it is already at or past it. */
    suspend fun mark(seed: Long, status: DealStatus) {
        dataStore.edit { prefs ->
            val existing = decodeOrEmpty(prefs[RECORDS])
            val old = existing[seed]
            if (old != null && old.ordinal >= status.ordinal) return@edit
            prefs[RECORDS] = encode(existing + (seed to status))
        }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(RECORDS) }
    }

    private fun encode(progress: Map<Long, DealStatus>): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(FORMAT_VERSION)
            out.writeInt(progress.size)
            for ((seed, status) in progress) {
                out.writeLong(seed)
                out.writeByte(status.ordinal)
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    private fun decodeOrEmpty(encoded: String?): Map<Long, DealStatus> {
        if (encoded.isNullOrEmpty()) return emptyMap()
        return try {
            DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded))).use { input ->
                if (input.readInt() != FORMAT_VERSION) return emptyMap()
                val size = input.readInt()
                // A size read out of a corrupt blob could be anything; refuse an absurd one rather
                // than trying to allocate it.
                if (size < 0 || size > MAX_ENTRIES) return emptyMap()
                val statuses = DealStatus.entries
                buildMap(size) {
                    repeat(size) { put(input.readLong(), statuses[input.readByte().toInt()]) }
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    companion object {
        const val DEFAULT_NAME = "deal_progress"
        private const val FORMAT_VERSION = 1

        /** Far more deals than anyone plays, and small enough that a corrupt length is caught. */
        private const val MAX_ENTRIES = 1_000_000

        private val RECORDS = stringPreferencesKey("records")
    }
}
