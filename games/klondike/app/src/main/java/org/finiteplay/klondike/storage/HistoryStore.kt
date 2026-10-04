package org.finiteplay.klondike.storage

import org.finiteplay.core.storage.preferencesDataStoreAt
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.deal.DifficultyTier
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Base64

enum class Outcome { WIN, LOSS }

/**
 * A minimal completed-game record. S2 builds statistics aggregation on top of this; this
 * layer only guarantees a durable, idempotent write/read primitive
 * (`docs/games/klondike/EXECUTION_PLAN.md`, S1).
 */
data class HistoryRecord(
    val gameId: String,
    val resultId: String,
    val outcome: Outcome,
    val elapsedMillis: Long,
    val moveCount: Int,
    val timestampMillis: Long,
    val drawMode: DrawMode = DrawMode.ONE,
    /** Hints the player took in this game, however the game ended. */
    val hintsUsed: Int = 0,
    /**
     * Length of the certified solution shipped with this deal, or 0 where none is — an
     * uncatalogued draw-three shuffle, or an Insane deal, which ships uncertified
     * (`docs/games/klondike/DIFFICULTY_LEVELS.md`). Recorded per game rather than looked up
     * later because the catalog can be regenerated under a player's feet: a record's own
     * ratio has to keep meaning what it meant when it was written.
     */
    val solutionMoveCount: Int = 0,
    /**
     * The graded level the deal came from, or null where it has none — a draw-three shuffle, or
     * a game recorded before levels were kept. Recorded per game rather than looked up later for
     * the reason [solutionMoveCount] is: the catalog can be regenerated, and a win rate by level
     * has to keep meaning the level the deal was dealt at.
     */
    val difficulty: DifficultyTier? = null,
)

/**
 * Append-only history of completed games, keyed by [HistoryRecord.resultId]. [upsert] is
 * idempotent: writing the same result twice (e.g. after a crash between the active-game
 * write and the history write) replaces the existing entry rather than duplicating it.
 *
 * A corrupt history blob is treated as an empty history rather than propagated as an
 * error, so a broken read here can never block or corrupt any other store.
 */
class HistoryStore(
    directory: File,
    dataStoreFactory: (File, String) -> DataStore<Preferences> = ::preferencesDataStoreAt,
) {
    private val dataStore = dataStoreFactory(directory, STORE_NAME)

    val records: Flow<List<HistoryRecord>> = dataStore.data.map { prefs -> decodeOrEmpty(prefs[RECORDS]) }

    suspend fun all(): List<HistoryRecord> = records.first()

    /** Inserts [record], or replaces the existing entry with the same [HistoryRecord.resultId]. */
    suspend fun upsert(record: HistoryRecord) {
        dataStore.edit { prefs ->
            val current = decodeOrEmpty(prefs[RECORDS])
            val next = current.filterNot { it.resultId == record.resultId } + record
            prefs[RECORDS] = encode(next)
        }
    }

    /** Discards every record. Used by the Statistics screen's reset, behind confirmation. */
    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun decodeOrEmpty(encoded: String?): List<HistoryRecord> {
        if (encoded == null) return emptyList()
        return runCatching { decode(Base64.getDecoder().decode(encoded)) }.getOrDefault(emptyList())
    }

    companion object {
        private const val STORE_NAME = "history"
        private val RECORDS = stringPreferencesKey("records")

        // v1 (unversioned) starts directly with a big-endian record-count Int, whose
        // first byte is therefore always 0..127 for any real (non-negative) count —
        // it can never equal a marker. v2 (draw-three support), v3 (hints taken and
        // the certified solution's length) and v4 (the deal's level) each write their own marker byte first, so
        // every generation of blob stays distinguishable without a migration step. An
        // older blob simply reads as every record being DrawMode.ONE, with no hints and
        // no solution length — which is exactly what those games recorded.
        private const val FORMAT_V2_MARKER: Byte = -1
        private const val FORMAT_V3_MARKER: Byte = -2
        private const val FORMAT_V4_MARKER: Byte = -3
        private const val NO_DIFFICULTY: Int = -1

        private fun encode(records: List<HistoryRecord>): String {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { out ->
                out.writeByte(FORMAT_V4_MARKER.toInt())
                out.writeInt(records.size)
                for (record in records) {
                    out.writeUTF(record.gameId)
                    out.writeUTF(record.resultId)
                    out.writeByte(record.outcome.ordinal)
                    out.writeLong(record.elapsedMillis)
                    out.writeInt(record.moveCount)
                    out.writeLong(record.timestampMillis)
                    out.writeByte(record.drawMode.ordinal)
                    out.writeInt(record.hintsUsed)
                    out.writeInt(record.solutionMoveCount)
                    out.writeByte(record.difficulty?.ordinal ?: NO_DIFFICULTY)
                }
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray())
        }

        private fun decode(bytes: ByteArray): List<HistoryRecord> {
            val isV4 = bytes.isNotEmpty() && bytes[0] == FORMAT_V4_MARKER
            val isV3 = isV4 || (bytes.isNotEmpty() && bytes[0] == FORMAT_V3_MARKER)
            val isV2 = isV3 || (bytes.isNotEmpty() && bytes[0] == FORMAT_V2_MARKER)
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (isV2) input.readByte() // consume the marker
                val count = input.readInt()
                require(count >= 0) { "negative history record count: $count" }
                val outcomes = Outcome.entries
                val drawModes = DrawMode.entries
                val tiers = DifficultyTier.entries
                return List(count) {
                    val gameId = input.readUTF()
                    val resultId = input.readUTF()
                    val outcomeOrdinal = input.readByte().toInt()
                    require(outcomeOrdinal in outcomes.indices) { "invalid outcome ordinal: $outcomeOrdinal" }
                    val elapsedMillis = input.readLong()
                    val moveCount = input.readInt()
                    val timestampMillis = input.readLong()
                    val drawMode = if (isV2) {
                        val drawModeOrdinal = input.readByte().toInt()
                        require(drawModeOrdinal in drawModes.indices) { "invalid draw mode ordinal: $drawModeOrdinal" }
                        drawModes[drawModeOrdinal]
                    } else {
                        DrawMode.ONE
                    }
                    HistoryRecord(
                        gameId = gameId,
                        resultId = resultId,
                        outcome = outcomes[outcomeOrdinal],
                        elapsedMillis = elapsedMillis,
                        moveCount = moveCount,
                        timestampMillis = timestampMillis,
                        drawMode = drawMode,
                        hintsUsed = if (isV3) input.readInt() else 0,
                        solutionMoveCount = if (isV3) input.readInt() else 0,
                        difficulty = if (isV4) tiers.getOrNull(input.readByte().toInt()) else null,
                    )
                }
            }
        }
    }
}
