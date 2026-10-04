package org.finiteplay.spider.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.finiteplay.core.storage.preferencesDataStoreAt
import org.finiteplay.spider.layout.SuitCount
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Base64

enum class SpiderOutcome { WIN, LOSS }

/**
 * One finished game.
 *
 * [suitCount] is recorded on the record rather than looked up later, because statistics are kept
 * per suit count and never blended (`DESIGN.md` "Scoring and statistics"): a one-suit win and a
 * four-suit win are not comparable achievements, and a record has to keep meaning what it meant
 * when it was written even if the setting has since changed.
 */
data class SpiderHistoryRecord(
    val gameId: String,
    val resultId: String,
    val outcome: SpiderOutcome,
    val suitCount: SuitCount,
    val elapsedMillis: Long,
    val moveCount: Int,
    val timestampMillis: Long,
    /** Hints the player took in this game, however it ended (`SpiderViewModel.hintsUsedThisGame`). */
    val hintsUsed: Int = 0,
    /**
     * Length of the certified solution shipped with this deal, or 0 where none is (a game dealt
     * without a catalog). Recorded per game rather than looked up later because the catalog can be
     * regenerated: a record's own ratio has to keep meaning what it meant when it was written.
     */
    val solutionMoveCount: Int = 0,
)

/**
 * Append-only history of finished games, keyed by [SpiderHistoryRecord.resultId]. [upsert] is
 * idempotent: writing the same result twice replaces the entry rather than duplicating it.
 *
 * A corrupt blob reads back as an empty history rather than propagating an error, so a broken read
 * here can never block or damage any other store. The encoding is Spider's own — a versioned
 * on-disk format stays with the game that owns it (`docs/ARCHITECTURE.md`).
 */
class SpiderHistoryStore(
    directory: File,
    dataStoreFactory: (File, String) -> DataStore<Preferences> = ::preferencesDataStoreAt,
) {
    private val dataStore = dataStoreFactory(directory, STORE_NAME)

    val records: Flow<List<SpiderHistoryRecord>> = dataStore.data.map { decodeOrEmpty(it[RECORDS]) }

    suspend fun current(): List<SpiderHistoryRecord> = records.first()

    suspend fun upsert(record: SpiderHistoryRecord) {
        dataStore.edit { prefs ->
            val existing = decodeOrEmpty(prefs[RECORDS]).filterNot { it.resultId == record.resultId }
            prefs[RECORDS] = encode(existing + record)
        }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(RECORDS) }
    }

    private fun encode(records: List<SpiderHistoryRecord>): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(FORMAT_VERSION)
            out.writeInt(records.size)
            for (r in records) {
                out.writeUTF(r.gameId)
                out.writeUTF(r.resultId)
                out.writeInt(r.outcome.ordinal)
                out.writeInt(r.suitCount.ordinal)
                out.writeLong(r.elapsedMillis)
                out.writeInt(r.moveCount)
                out.writeLong(r.timestampMillis)
                out.writeInt(r.hintsUsed)
                out.writeInt(r.solutionMoveCount)
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    private fun decodeOrEmpty(encoded: String?): List<SpiderHistoryRecord> {
        if (encoded.isNullOrEmpty()) return emptyList()
        return try {
            DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded))).use { input ->
                val version = input.readInt()
                // v1 wrote no hint count at all; a record from before hints were tracked simply
                // took none, and v2 wrote no solution length. Anything else
                // is a blob this build has never produced.
                if (version != FORMAT_VERSION_1 && version != FORMAT_VERSION_2 && version != FORMAT_VERSION) return emptyList()
                val size = input.readInt()
                // A size read out of a corrupt blob could be anything; refuse an absurd one rather
                // than trying to allocate it.
                if (size < 0 || size > MAX_RECORDS) return emptyList()
                (0 until size).map {
                    SpiderHistoryRecord(
                        gameId = input.readUTF(),
                        resultId = input.readUTF(),
                        outcome = SpiderOutcome.entries[input.readInt()],
                        suitCount = SuitCount.entries[input.readInt()],
                        elapsedMillis = input.readLong(),
                        moveCount = input.readInt(),
                        timestampMillis = input.readLong(),
                        hintsUsed = if (version >= FORMAT_VERSION_2) input.readInt() else 0,
                        solutionMoveCount = if (version >= FORMAT_VERSION) input.readInt() else 0,
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private companion object {
        const val STORE_NAME = "history"

        /** v1 recorded no hint count; v2 added [SpiderHistoryRecord.hintsUsed]; v3 added [SpiderHistoryRecord.solutionMoveCount]. */
        const val FORMAT_VERSION_1 = 1
        const val FORMAT_VERSION_2 = 2
        const val FORMAT_VERSION = 3

        /** Far more games than anyone plays, and small enough that a corrupt length is caught. */
        const val MAX_RECORDS = 100_000

        val RECORDS = stringPreferencesKey("records")
    }
}
