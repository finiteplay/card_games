package org.finiteplay.klondike.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import org.finiteplay.core.storage.ActiveGameLoad
import org.finiteplay.core.storage.ActiveGameRecord
import org.finiteplay.core.storage.ActiveGameRecordStore
import org.finiteplay.core.storage.DealParameters
import org.finiteplay.core.storage.preferencesDataStoreAt
import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.session.GameSession
import org.finiteplay.klondike.session.LogEntryCodec
import org.finiteplay.klondike.session.replaySession
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Result of [ActiveGameStore.load]. A missing save and a discarded corrupt/unsupported
 * save are reported as distinct outcomes so the caller can start a new game silently in
 * the first case and show the Recovery notice (`docs/games/klondike/UI_SPEC.md`) in the second, without
 * this layer knowing anything about UI.
 */
sealed class ActiveGameLoadResult {
    data object Missing : ActiveGameLoadResult()
    data class Restored(
        val session: GameSession,
        val elapsed: Duration,
        val initialAutomaticMovesEnabled: Boolean,
        val gameId: String,
    ) : ActiveGameLoadResult()
    data object Recovered : ActiveGameLoadResult()
}

/**
 * Klondike's active game: what makes its deal a deal, wrapped around the generic record store
 * (`:core:storage`) that owns the fields every game saves.
 *
 * What is Klondike's is the pair below — the draw mode, and the automatic-moves setting the game
 * was dealt under — plus turning a restored record back into a session by replaying its log
 * through the same reducer the live game uses. The generic half handles the format version, the
 * common fields, and the Missing/Recovered distinction, none of which is about Klondike.
 *
 * The on-disk keys are unchanged from before that split, and
 * `ActiveGameFormatCompatibilityTest` pins them independently of this class.
 */
class ActiveGameStore(
    directory: File,
    dataStoreFactory: (File, String) -> DataStore<Preferences> = ::preferencesDataStoreAt,
) {
    private val store = ActiveGameRecordStore(
        directory = directory,
        formatVersion = CURRENT_FORMAT_VERSION,
        dealParameters = KlondikeDeal,
        storeName = STORE_NAME,
        dataStoreFactory = dataStoreFactory,
    )

    suspend fun save(session: GameSession, initialAutomaticMovesEnabled: Boolean, elapsed: Duration, gameId: String) {
        store.save(
            record = ActiveGameRecord(
                gameId = gameId,
                seed = session.state.seed,
                catalogVersion = session.state.versions.catalogVersion,
                rulesVersion = session.state.versions.rulesVersion,
                shuffleVersion = session.state.versions.shuffleVersion,
                elapsedMillis = elapsed.inWholeMilliseconds,
                log = LogEntryCodec.encode(session.log),
            ),
            deal = KlondikeDealParameters(initialAutomaticMovesEnabled, session.state.drawMode),
        )
    }

    suspend fun load(): ActiveGameLoadResult = when (val loaded = store.load()) {
        is ActiveGameLoad.Missing -> ActiveGameLoadResult.Missing
        is ActiveGameLoad.Recovered -> ActiveGameLoadResult.Recovered
        is ActiveGameLoad.Restored -> restore(loaded)
    }

    /**
     * A malformed log decodes to an exception here rather than inside the generic store, so it
     * takes the same recovery path as any other unreadable save.
     */
    private suspend fun restore(loaded: ActiveGameLoad.Restored<KlondikeDealParameters>): ActiveGameLoadResult {
        val record = loaded.record
        return try {
            val session = replaySession(
                seed = record.seed,
                versions = GameVersions(record.catalogVersion, record.rulesVersion, record.shuffleVersion),
                initialAutomaticMovesEnabled = loaded.deal.initialAutomaticMovesEnabled,
                log = LogEntryCodec.decode(record.log),
                drawMode = loaded.deal.drawMode,
            )
            ActiveGameLoadResult.Restored(
                session = session,
                elapsed = record.elapsedMillis.milliseconds,
                initialAutomaticMovesEnabled = loaded.deal.initialAutomaticMovesEnabled,
                gameId = record.gameId,
            )
        } catch (e: Exception) {
            clear()
            ActiveGameLoadResult.Recovered
        }
    }

    /** Discards this store's save, and only this store's. */
    suspend fun clear() {
        store.clear()
    }

    companion object {
        // v2 adds DRAW_MODE (draw-three support); a v1 save simply lacks the key,
        // fails the version check, and is discarded through the existing
        // Recovery-notice path — the same handling any unsupported/corrupt save
        // already gets, not a new failure mode.
        const val CURRENT_FORMAT_VERSION = 2
        private const val STORE_NAME = ActiveGameRecordStore.DEFAULT_STORE_NAME
    }
}

/** What a Klondike seed alone does not say: the draw mode, and the automation it was dealt under. */
data class KlondikeDealParameters(
    val initialAutomaticMovesEnabled: Boolean,
    val drawMode: DrawMode,
)

private object KlondikeDeal : DealParameters<KlondikeDealParameters> {
    private val INITIAL_AUTOMATIC_MOVES_ENABLED = booleanPreferencesKey("initial_automatic_moves_enabled")
    private val DRAW_MODE = stringPreferencesKey("draw_mode")

    override fun write(prefs: MutablePreferences, value: KlondikeDealParameters) {
        prefs[INITIAL_AUTOMATIC_MOVES_ENABLED] = value.initialAutomaticMovesEnabled
        prefs[DRAW_MODE] = value.drawMode.name
    }

    override fun read(prefs: Preferences): KlondikeDealParameters {
        val drawModeName = prefs[DRAW_MODE] ?: error("missing drawMode")
        return KlondikeDealParameters(
            initialAutomaticMovesEnabled = prefs[INITIAL_AUTOMATIC_MOVES_ENABLED]
                ?: error("missing initialAutomaticMovesEnabled"),
            // An unknown name is a save from a build that had an option this one does not, which
            // is unreadable rather than defaultable — guessing the mode would deal a different game.
            drawMode = DrawMode.entries.find { it.name == drawModeName } ?: error("unknown drawMode: $drawModeName"),
        )
    }
}
