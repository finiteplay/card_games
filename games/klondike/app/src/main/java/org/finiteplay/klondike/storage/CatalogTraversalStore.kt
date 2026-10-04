package org.finiteplay.klondike.storage

import org.finiteplay.core.storage.preferencesDataStoreAt
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.finiteplay.solitaire.catalog.catalog.CatalogTraversalState
import org.finiteplay.solitaire.catalog.catalog.DealTraversal
import java.io.File

/**
 * Result of [CatalogTraversalStore.load]. Missing state, corrupt state, and a stored
 * traversal for a different catalog (version or record count changed) all collapse into
 * [StartNew]: `docs/games/klondike/DEALS.md` treats them identically — begin a fresh
 * traversal — so callers don't need to distinguish the reason.
 */
sealed class TraversalLoadResult {
    data class Present(val state: CatalogTraversalState) : TraversalLoadResult()
    data object StartNew : TraversalLoadResult()
}

/**
 * Persists [CatalogTraversalState] plus the record count it was computed against, so a
 * catalog upgrade (new version or a changed record count under the same version) is
 * detected here and starts a fresh traversal without touching the active-game, settings,
 * or history stores (`docs/games/klondike/DEALS.md`, "Deal Selection").
 */
class CatalogTraversalStore(
    directory: File,
    dataStoreFactory: (File, String) -> DataStore<Preferences> = ::preferencesDataStoreAt,
) {
    private val dataStore = dataStoreFactory(directory, STORE_NAME)

    suspend fun load(currentCatalogVersion: Int, currentRecordCount: Int): TraversalLoadResult = try {
        val prefs = dataStore.data.first()
        val catalogVersion = prefs[CATALOG_VERSION]
        val recordCount = prefs[RECORD_COUNT]
        val nextPosition = prefs[NEXT_POSITION]

        if (catalogVersion == null || recordCount == null || nextPosition == null) {
            TraversalLoadResult.StartNew
        } else if (catalogVersion != currentCatalogVersion || recordCount != currentRecordCount) {
            TraversalLoadResult.StartNew
        } else {
            TraversalLoadResult.Present(CatalogTraversalState(catalogVersion, nextPosition))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        TraversalLoadResult.StartNew
    }

    suspend fun save(state: CatalogTraversalState, recordCount: Int) {
        dataStore.edit { prefs ->
            prefs[CATALOG_VERSION] = state.catalogVersion
            prefs[RECORD_COUNT] = recordCount
            prefs[NEXT_POSITION] = state.nextPosition
        }
    }

    /**
     * Position within each difficulty's own seed list, keyed by difficulty name
     * (`docs/games/klondike/DEALS.md`). Each level tracks its place independently, so playing a few
     * Hard games and switching back to Easy resumes Easy where it was left rather than
     * restarting it or skipping ahead by the games played elsewhere.
     *
     * [recordCounts] guards each entry the way [load] guards the single-list position: a
     * difficulty whose list has changed size since the position was written starts over
     * rather than resuming at an index that now means something else.
     */
    suspend fun loadDifficultyPositions(catalogVersion: Int, recordCounts: Map<String, Int>): Map<String, Int> = try {
        val prefs = dataStore.data.first()
        // The count catches a list that grew or shrank; the version catches one that was
        // reordered, which is invisible to the count and turns a stored index into a
        // different deal.
        if (prefs[DIFFICULTY_CATALOG_VERSION] != catalogVersion) return emptyMap()
        recordCounts.mapNotNull { (difficulty, count) ->
            val stored = prefs[difficultyPositionKey(difficulty)]
            val storedCount = prefs[difficultyCountKey(difficulty)]
            if (stored == null || storedCount != count) null else difficulty to stored
        }.toMap()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyMap()
    }

    suspend fun saveDifficultyPositions(catalogVersion: Int, positions: Map<String, Int>, recordCounts: Map<String, Int>) {
        dataStore.edit { prefs ->
            prefs[DIFFICULTY_CATALOG_VERSION] = catalogVersion
            for ((difficulty, index) in positions) {
                prefs[difficultyPositionKey(difficulty)] = index
                recordCounts[difficulty]?.let { prefs[difficultyCountKey(difficulty)] = it }
            }
        }
    }

    /** Loads the traversal for the active catalog, starting (and persisting) a fresh one when needed. */
    suspend fun loadOrStartNew(catalogVersion: Int, recordCount: Int): CatalogTraversalState =
        when (val result = load(catalogVersion, recordCount)) {
            is TraversalLoadResult.Present -> result.state
            TraversalLoadResult.StartNew -> {
                val fresh = DealTraversal.startTraversal(catalogVersion, recordCount)
                save(fresh, recordCount)
                fresh
            }
        }

    companion object {
        /** Internal, not private: the debug-only reinstall-reset hook (`debug/DebugTools.kt`) targets this same backing file directly, synchronously, without going through this class's suspend API. */
        internal const val STORE_NAME = "catalog_traversal"
        private val CATALOG_VERSION = intPreferencesKey("catalog_version")
        private val RECORD_COUNT = intPreferencesKey("record_count")
        private val NEXT_POSITION = intPreferencesKey("next_position")

        private val DIFFICULTY_CATALOG_VERSION = intPreferencesKey("difficulty_catalog_version")

        private fun difficultyPositionKey(difficulty: String) = intPreferencesKey("next_position_$difficulty")
        private fun difficultyCountKey(difficulty: String) = intPreferencesKey("record_count_$difficulty")
    }
}
