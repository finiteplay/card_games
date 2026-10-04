package org.finiteplay.klondike.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.finiteplay.core.storage.preferencesDataStoreAt
import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.session.LogEntry
import org.finiteplay.klondike.session.LogEntryCodec

/**
 * One finished game, archived in full: everything
 * [org.finiteplay.klondike.session.replaySession] needs to rebuild every board the player
 * saw, not just the summary [HistoryRecord] keeps.
 *
 * [moves] is that game's **complete** move log — never truncated, however long the game
 * ran. Only the number of archived games is capped ([GameArchiveStore.MAX_ARCHIVED_GAMES]).
 *
 * [countedForStatistics] mirrors `DESIGN.md` "Statistics" (a Replay never counts). It is
 * recorded rather than filtered on, because a replayed game is still a real sequence of
 * player decisions and so still worth analysing — the flag lets analysis separate the two
 * without this store having to take a position on which matters.
 */
data class ArchivedGame(
    val gameId: String,
    val seed: Long,
    val versions: GameVersions,
    val drawMode: DrawMode,
    val initialAutomaticMovesEnabled: Boolean,
    val outcome: Outcome,
    val elapsedMillis: Long,
    val moveCount: Int,
    val timestampMillis: Long,
    val countedForStatistics: Boolean,
    val moves: List<LogEntry>,
)

/**
 * The last [MAX_ARCHIVED_GAMES] finished **games**, each with its whole move log, oldest
 * game evicted first — the raw material for asking how deals are *actually played*, which
 * `docs/games/klondike/DIFFICULTY_LEVELS.md` currently has no data for.
 *
 * The cap counts games, not moves: a 400-move game occupies exactly one of the hundred
 * slots and keeps all 400 moves. Nothing here ever shortens an individual game's log.
 *
 * Deliberately not part of statistics. [HistoryStore] answers "what were the results";
 * this answers "what were the moves", is bounded where that one is not, and nothing here
 * feeds [computeStatistics] — so evicting an old game can never change a win rate, a
 * streak, or a personal best.
 *
 * Local-only, like every other store here: never transmitted, no networking or analytics
 * anywhere in this project (CLAUDE.md). It is cleared by the same confirmed reset that
 * clears history, so "reset my history" never leaves a move archive behind.
 *
 * A corrupt blob reads as an empty archive rather than propagating an error, matching
 * [HistoryStore] — a broken read here must never block or corrupt another store.
 */
class GameArchiveStore(
    directory: File,
    dataStoreFactory: (File, String) -> DataStore<Preferences> = ::preferencesDataStoreAt,
) {
    private val dataStore = dataStoreFactory(directory, STORE_NAME)

    /** Oldest game first. */
    val games: Flow<List<ArchivedGame>> = dataStore.data.map { prefs -> decodeOrEmpty(prefs[GAMES]) }

    suspend fun all(): List<ArchivedGame> = games.first()

    /**
     * Appends [game], or replaces the existing entry with the same [ArchivedGame.gameId],
     * then drops the oldest games beyond [MAX_ARCHIVED_GAMES]. Idempotent for the same
     * reason [HistoryStore.upsert] is: one game's outcome can be observed more than once
     * (a win seen again after rotation or restoration), and that must not consume two slots.
     */
    suspend fun record(game: ArchivedGame) {
        dataStore.edit { prefs ->
            val current = decodeOrEmpty(prefs[GAMES])
            val next = (current.filterNot { it.gameId == game.gameId } + game).takeLast(MAX_ARCHIVED_GAMES)
            prefs[GAMES] = encode(next)
        }
    }

    /** Discards every archived game. Wired to the same confirmed reset as [HistoryStore.clear]. */
    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun decodeOrEmpty(encoded: String?): List<ArchivedGame> {
        if (encoded == null) return emptyList()
        return runCatching { decode(Base64.getDecoder().decode(encoded)) }.getOrDefault(emptyList())
    }

    companion object {
        /**
         * As specified: the last 100 games. A game's own move log is never capped — see
         * [ArchivedGame.moves].
         */
        const val MAX_ARCHIVED_GAMES = 100

        const val STORE_NAME = "game_archive"
        private val GAMES = stringPreferencesKey("games")

        // Written from v1 rather than retrofitted: HistoryStore had to smuggle its
        // version in as a sentinel byte precisely because the first format carried none.
        private const val FORMAT_VERSION = 1

        private fun encode(games: List<ArchivedGame>): String {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { out ->
                out.writeInt(FORMAT_VERSION)
                out.writeInt(games.size)
                for (game in games) {
                    out.writeUTF(game.gameId)
                    out.writeLong(game.seed)
                    out.writeInt(game.versions.catalogVersion)
                    out.writeInt(game.versions.rulesVersion)
                    out.writeInt(game.versions.shuffleVersion)
                    out.writeByte(game.drawMode.ordinal)
                    out.writeBoolean(game.initialAutomaticMovesEnabled)
                    out.writeByte(game.outcome.ordinal)
                    out.writeLong(game.elapsedMillis)
                    out.writeInt(game.moveCount)
                    out.writeLong(game.timestampMillis)
                    out.writeBoolean(game.countedForStatistics)
                    val encodedMoves = LogEntryCodec.encode(game.moves)
                    out.writeInt(encodedMoves.size)
                    out.write(encodedMoves)
                }
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray())
        }

        private fun decode(bytes: ByteArray): List<ArchivedGame> {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val formatVersion = input.readInt()
                require(formatVersion == FORMAT_VERSION) { "unsupported game-archive format version: $formatVersion" }
                val count = input.readInt()
                require(count >= 0) { "negative archived-game count: $count" }
                val outcomes = Outcome.entries
                val drawModes = DrawMode.entries
                return List(count) {
                    val gameId = input.readUTF()
                    val seed = input.readLong()
                    val versions = GameVersions(
                        catalogVersion = input.readInt(),
                        rulesVersion = input.readInt(),
                        shuffleVersion = input.readInt(),
                    )
                    val drawModeOrdinal = input.readByte().toInt()
                    require(drawModeOrdinal in drawModes.indices) { "invalid draw mode ordinal: $drawModeOrdinal" }
                    val initialAutomaticMovesEnabled = input.readBoolean()
                    val outcomeOrdinal = input.readByte().toInt()
                    require(outcomeOrdinal in outcomes.indices) { "invalid outcome ordinal: $outcomeOrdinal" }
                    val elapsedMillis = input.readLong()
                    val moveCount = input.readInt()
                    val timestampMillis = input.readLong()
                    val countedForStatistics = input.readBoolean()
                    val movesSize = input.readInt()
                    require(movesSize >= 0) { "negative move-log size: $movesSize" }
                    val encodedMoves = ByteArray(movesSize)
                    input.readFully(encodedMoves)
                    ArchivedGame(
                        gameId = gameId,
                        seed = seed,
                        versions = versions,
                        drawMode = drawModes[drawModeOrdinal],
                        initialAutomaticMovesEnabled = initialAutomaticMovesEnabled,
                        outcome = outcomes[outcomeOrdinal],
                        elapsedMillis = elapsedMillis,
                        moveCount = moveCount,
                        timestampMillis = timestampMillis,
                        countedForStatistics = countedForStatistics,
                        moves = LogEntryCodec.decode(encodedMoves),
                    )
                }
            }
        }
    }
}
