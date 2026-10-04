package org.finiteplay.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.Base64

/**
 * What every game's active-game save carries: which game this is, which deal, which versions it
 * was dealt under, how long it has been played, and the log that rebuilds the board
 * (`docs/PLATFORM.md` "Persistence").
 *
 * The log is bytes here because its encoding is the game's — the same alphabet its certificates
 * are written in — and this layer only stores and returns it.
 */
class ActiveGameRecord(
    val gameId: String,
    val seed: Long,
    val catalogVersion: Int,
    val rulesVersion: Int,
    val shuffleVersion: Int,
    val elapsedMillis: Long,
    val log: ByteArray,
)

/**
 * The parameters fixed when the deal was made, which a game supplies and gets back on restore.
 *
 * Every game has some: they are the reason a seed alone does not name a board. Whatever they are,
 * they must be written and read here, or a restored game is a *different* game from the one that
 * was saved — the same failure in either direction, however the game spells it.
 *
 * [read] should throw when a value is missing or no longer recognised. The store treats that as
 * corruption and discards the save, which is the honest outcome: a deal parameter that cannot be
 * read is a board that cannot be rebuilt.
 */
interface DealParameters<T> {
    fun write(prefs: MutablePreferences, value: T)
    fun read(prefs: Preferences): T
}

/** Outcome of [ActiveGameRecordStore.load]. */
sealed class ActiveGameLoad<out T> {
    /** Nothing was ever saved here. Start a new game silently. */
    data object Missing : ActiveGameLoad<Nothing>()

    data class Restored<T>(val record: ActiveGameRecord, val deal: T) : ActiveGameLoad<T>()

    /**
     * A save existed and was discarded, unreadable. Distinct from [Missing] because a player
     * whose game vanished is owed a notice and a player who never had one is not.
     */
    data object Recovered : ActiveGameLoad<Nothing>()
}

/**
 * Persists one game in progress, and exactly one — this class owns a single file, so discarding a
 * corrupt save here can never touch settings, statistics, or history.
 *
 * Restoring hands back the ingredients rather than a board: the caller replays the log through
 * its own reducer, which is what makes a restored game one the engine produced rather than one a
 * parser assembled.
 *
 * [formatVersion] is the game's, not this layer's. A save whose version does not match is
 * discarded rather than guessed at, so adding a field is always safe: bump the number and old
 * saves take the recovery path they already have, instead of loading with a field silently
 * defaulted.
 */
class ActiveGameRecordStore<T>(
    directory: File,
    private val formatVersion: Int,
    private val dealParameters: DealParameters<T>,
    storeName: String = DEFAULT_STORE_NAME,
    dataStoreFactory: (File, String) -> DataStore<Preferences> = ::preferencesDataStoreAt,
) {
    private val file = preferencesFileAt(directory, storeName)
    private val dataStore = dataStoreFactory(directory, storeName)

    suspend fun save(record: ActiveGameRecord, deal: T) {
        dataStore.edit { prefs ->
            prefs[FORMAT_VERSION] = formatVersion
            prefs[GAME_ID] = record.gameId
            prefs[SEED] = record.seed
            prefs[CATALOG_VERSION] = record.catalogVersion
            prefs[RULES_VERSION] = record.rulesVersion
            prefs[SHUFFLE_VERSION] = record.shuffleVersion
            prefs[ELAPSED_MILLIS] = record.elapsedMillis
            prefs[LOG] = Base64.getEncoder().encodeToString(record.log)
            dealParameters.write(prefs, deal)
        }
    }

    suspend fun load(): ActiveGameLoad<T> {
        // A file that never existed is unambiguously "no save"; anything else that fails to yield
        // a well-formed record below was corrupt, which is a distinct and reportable outcome.
        val hadExistingFile = file.exists() && file.length() > 0L

        return try {
            val prefs = dataStore.data.first()
            val storedVersion = prefs[FORMAT_VERSION]
                ?: return if (hadExistingFile) discard() else ActiveGameLoad.Missing
            if (storedVersion != formatVersion) return discard()

            val record = ActiveGameRecord(
                gameId = prefs[GAME_ID] ?: error("missing gameId"),
                seed = prefs[SEED] ?: error("missing seed"),
                catalogVersion = prefs[CATALOG_VERSION] ?: error("missing catalogVersion"),
                rulesVersion = prefs[RULES_VERSION] ?: error("missing rulesVersion"),
                shuffleVersion = prefs[SHUFFLE_VERSION] ?: error("missing shuffleVersion"),
                elapsedMillis = prefs[ELAPSED_MILLIS] ?: error("missing elapsedMillis"),
                log = Base64.getDecoder().decode(prefs[LOG] ?: error("missing log")),
            )
            ActiveGameLoad.Restored(record, dealParameters.read(prefs))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            discard()
        }
    }

    private suspend fun discard(): ActiveGameLoad.Recovered {
        clear()
        return ActiveGameLoad.Recovered
    }

    /** Discards this store's save, and only this store's. */
    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    companion object {
        const val DEFAULT_STORE_NAME = "active_game"

        // These key names are on disk on every installed device. Renaming one makes every save
        // written before the rename unreadable, which is a format change and needs a version bump
        // rather than a rename.
        private val FORMAT_VERSION = intPreferencesKey("format_version")
        private val GAME_ID = stringPreferencesKey("game_id")
        private val SEED = longPreferencesKey("seed")
        private val CATALOG_VERSION = intPreferencesKey("catalog_version")
        private val RULES_VERSION = intPreferencesKey("rules_version")
        private val SHUFFLE_VERSION = intPreferencesKey("shuffle_version")
        private val ELAPSED_MILLIS = longPreferencesKey("elapsed_millis")
        private val LOG = stringPreferencesKey("log")
    }
}
