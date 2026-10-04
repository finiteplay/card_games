package org.finiteplay.freecell.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import org.finiteplay.core.storage.ActiveGameLoad
import org.finiteplay.core.storage.ActiveGameRecord
import org.finiteplay.core.storage.ActiveGameRecordStore
import org.finiteplay.core.storage.DealParameters
import org.finiteplay.core.storage.preferencesDataStoreAt
import org.finiteplay.freecell.layout.GameVersions
import org.finiteplay.freecell.session.FreeCellLogCodec
import org.finiteplay.freecell.session.FreeCellSession
import org.finiteplay.freecell.session.replayFreeCellSession
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Result of [FreeCellActiveGameStore.load]. A missing save and a discarded one are distinct so
 * the caller can start a new game silently in the first case and say plainly that a game was
 * lost in the second, without this layer knowing anything about UI.
 */
sealed class FreeCellActiveGameLoadResult {
    data object Missing : FreeCellActiveGameLoadResult()
    data class Restored(
        val session: FreeCellSession,
        val elapsed: Duration,
        val gameId: String,
    ) : FreeCellActiveGameLoadResult()
    data object Recovered : FreeCellActiveGameLoadResult()
}

/**
 * What a FreeCell seed alone does not say: not a board-shape axis — FreeCell has none, unlike
 * Klondike's draw mode or Spider's suit count (`docs/games/freecell/RULES.md` "What FreeCell
 * does not have") — but the automatic-moves setting *as it stood when the deal was made*, needed
 * for the same reason Klondike's own `KlondikeDealParameters` carries it: replay re-runs the
 * cascade forward from this starting value through the log's own `SetAutomaticMoves` entries, so
 * restoring without it can replay a different game than the one saved.
 */
data class FreeCellDealParameters(val initialAutomaticMovesEnabled: Boolean)

/**
 * FreeCell's active game, wrapped around `:core:storage`'s generic record store. The format
 * version, the common fields, and the Missing/Recovered distinction are the shared store's and
 * are not reimplemented here.
 */
class FreeCellActiveGameStore(
    directory: File,
    dataStoreFactory: (File, String) -> DataStore<Preferences> = ::preferencesDataStoreAt,
) {
    private val store = ActiveGameRecordStore(
        directory = directory,
        formatVersion = CURRENT_FORMAT_VERSION,
        dealParameters = FreeCellDeal,
        storeName = STORE_NAME,
        dataStoreFactory = dataStoreFactory,
    )

    suspend fun save(session: FreeCellSession, initialAutomaticMovesEnabled: Boolean, elapsed: Duration, gameId: String) {
        store.save(
            record = ActiveGameRecord(
                gameId = gameId,
                seed = session.state.seed,
                catalogVersion = session.state.versions.catalogVersion,
                rulesVersion = session.state.versions.rulesVersion,
                shuffleVersion = session.state.versions.shuffleVersion,
                elapsedMillis = elapsed.inWholeMilliseconds,
                log = FreeCellLogCodec.encode(session.log),
            ),
            deal = FreeCellDealParameters(initialAutomaticMovesEnabled),
        )
    }

    suspend fun load(): FreeCellActiveGameLoadResult = when (val loaded = store.load()) {
        is ActiveGameLoad.Missing -> FreeCellActiveGameLoadResult.Missing
        is ActiveGameLoad.Recovered -> FreeCellActiveGameLoadResult.Recovered
        is ActiveGameLoad.Restored -> restore(loaded)
    }

    /**
     * A malformed log throws here rather than inside the generic store, so it takes the same
     * recovery path as any other unreadable save.
     */
    private suspend fun restore(loaded: ActiveGameLoad.Restored<FreeCellDealParameters>): FreeCellActiveGameLoadResult {
        val record = loaded.record
        return try {
            val session = replayFreeCellSession(
                seed = record.seed,
                versions = GameVersions(record.catalogVersion, record.rulesVersion, record.shuffleVersion),
                initialAutomaticMovesEnabled = loaded.deal.initialAutomaticMovesEnabled,
                log = FreeCellLogCodec.decode(record.log),
            )
            FreeCellActiveGameLoadResult.Restored(
                session = session,
                elapsed = record.elapsedMillis.milliseconds,
                gameId = record.gameId,
            )
        } catch (e: Exception) {
            clear()
            FreeCellActiveGameLoadResult.Recovered
        }
    }

    /** Discards this store's save, and only this store's. */
    suspend fun clear() {
        store.clear()
    }

    companion object {
        const val CURRENT_FORMAT_VERSION = 1
        private const val STORE_NAME = ActiveGameRecordStore.DEFAULT_STORE_NAME
    }
}

private object FreeCellDeal : DealParameters<FreeCellDealParameters> {
    private val INITIAL_AUTOMATIC_MOVES_ENABLED = booleanPreferencesKey("initial_automatic_moves_enabled")

    override fun write(prefs: MutablePreferences, value: FreeCellDealParameters) {
        prefs[INITIAL_AUTOMATIC_MOVES_ENABLED] = value.initialAutomaticMovesEnabled
    }

    override fun read(prefs: Preferences): FreeCellDealParameters =
        FreeCellDealParameters(
            initialAutomaticMovesEnabled = prefs[INITIAL_AUTOMATIC_MOVES_ENABLED]
                ?: error("missing initialAutomaticMovesEnabled"),
        )
}
