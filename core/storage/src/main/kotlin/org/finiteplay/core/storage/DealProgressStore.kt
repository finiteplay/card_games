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
 * A deal's [status] and the moves that go with it: the **fewest** moves of any win when it is
 * [DealStatus.WON], and the moves of the game **last played** when it is only [DealStatus.PLAYED].
 * Zero means unknown, which is what progress recorded before moves were kept reads back as.
 */
data class DealProgress(val status: DealStatus, val moves: Int = 0) {
    /**
     * This deal's progress after a game of it that reached [reached] with [moves] made (null for a
     * deal not yet touched): a win replaces a played record and keeps the better of two wins, a
     * played game replaces an earlier played record, and neither can take a won deal back to played.
     */
    fun advancedBy(reached: DealStatus, moves: Int): DealProgress = when {
        status == DealStatus.WON ->
            if (reached == DealStatus.WON) DealProgress(DealStatus.WON, fewest(this.moves, moves)) else this
        reached == DealStatus.WON -> DealProgress(DealStatus.WON, moves)
        else -> DealProgress(DealStatus.PLAYED, moves)
    }
}

/** [DealProgress.advancedBy] for a deal that may not have been touched yet. */
fun DealProgress?.advancedBy(reached: DealStatus, moves: Int): DealProgress =
    this?.advancedBy(reached, moves) ?: DealProgress(reached, moves)

/** The smaller of two move counts, where zero means unknown and never wins. */
private fun fewest(a: Int, b: Int): Int = when {
    a <= 0 -> b
    b <= 0 -> a
    else -> minOf(a, b)
}

/**
 * Which deals have been played and which won, with their moves, keyed by seed. A seed names a board
 * exactly (a game that keeps separate seed spaces per mode or suit count never repeats one across
 * them), so the key needs nothing beyond it.
 *
 * Progress only moves forward: [mark] never turns a [DealStatus.WON] deal back into
 * [DealStatus.PLAYED], so replaying a deal already won leaves it won, with its best moves. A corrupt
 * blob reads back as no progress rather than an error, so a broken read here can never block
 * another store.
 */
class DealProgressStore(
    directory: File,
    name: String = DEFAULT_NAME,
    dataStoreFactory: (File, String) -> DataStore<Preferences> = ::preferencesDataStoreAt,
) {
    private val dataStore = dataStoreFactory(directory, name)

    val progress: Flow<Map<Long, DealProgress>> = dataStore.data.map { decodeOrEmpty(it[RECORDS]) }

    suspend fun current(): Map<Long, DealProgress> = progress.first()

    /** Records that a game of [seed] reached [status] with [moves] made, by [DealProgress.advancedBy]'s rules. */
    suspend fun mark(seed: Long, status: DealStatus, moves: Int = 0) {
        dataStore.edit { prefs ->
            val existing = decodeOrEmpty(prefs[RECORDS])
            val old = existing[seed]
            val updated = old.advancedBy(status, moves)
            if (updated == old) return@edit
            prefs[RECORDS] = encode(existing + (seed to updated))
        }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(RECORDS) }
    }

    private fun encode(progress: Map<Long, DealProgress>): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(FORMAT_VERSION)
            out.writeInt(progress.size)
            for ((seed, deal) in progress) {
                out.writeLong(seed)
                out.writeByte(deal.status.ordinal)
                out.writeInt(deal.moves)
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    private fun decodeOrEmpty(encoded: String?): Map<Long, DealProgress> {
        if (encoded.isNullOrEmpty()) return emptyMap()
        return try {
            DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded))).use { input ->
                val version = input.readInt()
                // v1 recorded no moves: those deals read back with the moves unknown.
                if (version != FORMAT_VERSION_1 && version != FORMAT_VERSION) return emptyMap()
                val size = input.readInt()
                // A size read out of a corrupt blob could be anything; refuse an absurd one rather
                // than trying to allocate it.
                if (size < 0 || size > MAX_ENTRIES) return emptyMap()
                val statuses = DealStatus.entries
                buildMap(size) {
                    repeat(size) {
                        val seed = input.readLong()
                        val status = statuses[input.readByte().toInt()]
                        val moves = if (version >= FORMAT_VERSION) input.readInt() else 0
                        put(seed, DealProgress(status, moves))
                    }
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    companion object {
        const val DEFAULT_NAME = "deal_progress"

        /** v1 recorded a status per deal; v2 added the moves. */
        private const val FORMAT_VERSION_1 = 1
        private const val FORMAT_VERSION = 2

        /** Far more deals than anyone plays, and small enough that a corrupt length is caught. */
        private const val MAX_ENTRIES = 1_000_000

        private val RECORDS = stringPreferencesKey("records")
    }
}
