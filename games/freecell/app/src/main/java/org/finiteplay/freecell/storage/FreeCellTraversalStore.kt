package org.finiteplay.freecell.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.finiteplay.core.storage.preferencesDataStoreAt
import org.finiteplay.solitaire.catalog.catalog.CatalogTraversalState
import org.finiteplay.solitaire.catalog.catalog.DealTraversal
import java.io.File

/**
 * Result of [FreeCellTraversalStore.load]. Missing state, corrupt state, and a stored traversal
 * for a different catalog (version or record count changed) all collapse into [StartNew]
 * (`docs/games/freecell/DEALS.md`) — begin a fresh traversal — so callers don't need to
 * distinguish the reason.
 */
sealed class FreeCellTraversalLoadResult {
    data class Present(val state: CatalogTraversalState) : FreeCellTraversalLoadResult()
    data object StartNew : FreeCellTraversalLoadResult()
}

/**
 * Persists one non-repeating, sequential traversal position over the certified catalog
 * (`org.finiteplay.solitaire.catalog.catalog.DealTraversal`). One position only — FreeCell ships a
 * single flat catalog, unlike a game that tracks a position per difficulty or suit count.
 *
 * This position is deal 1, deal 2, deal 3, ... in the catalog's own committed seed order,
 * wrapping back to deal 1 once every record has been visited — the same order surfaced to the
 * player as the status row's hand number (`FreeCellViewModel.dealNumber`,
 * `docs/games/freecell/DEALS.md` "App integration").
 */
class FreeCellTraversalStore(
    directory: File,
    dataStoreFactory: (File, String) -> DataStore<Preferences> = ::preferencesDataStoreAt,
) {
    private val dataStore = dataStoreFactory(directory, STORE_NAME)

    suspend fun load(currentCatalogVersion: Int, currentRecordCount: Int): FreeCellTraversalLoadResult = try {
        val prefs = dataStore.data.first()
        val catalogVersion = prefs[CATALOG_VERSION]
        val recordCount = prefs[RECORD_COUNT]
        val nextPosition = prefs[NEXT_POSITION]

        if (catalogVersion == null || recordCount == null || nextPosition == null) {
            FreeCellTraversalLoadResult.StartNew
        } else if (catalogVersion != currentCatalogVersion || recordCount != currentRecordCount) {
            FreeCellTraversalLoadResult.StartNew
        } else {
            FreeCellTraversalLoadResult.Present(CatalogTraversalState(catalogVersion, nextPosition))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        FreeCellTraversalLoadResult.StartNew
    }

    suspend fun save(state: CatalogTraversalState, recordCount: Int) {
        dataStore.edit { prefs ->
            prefs[CATALOG_VERSION] = state.catalogVersion
            prefs[RECORD_COUNT] = recordCount
            prefs[NEXT_POSITION] = state.nextPosition
        }
    }

    /** Loads the traversal for the active catalog, starting (and persisting) a fresh one when needed. */
    suspend fun loadOrStartNew(catalogVersion: Int, recordCount: Int): CatalogTraversalState =
        when (val result = load(catalogVersion, recordCount)) {
            is FreeCellTraversalLoadResult.Present -> result.state
            FreeCellTraversalLoadResult.StartNew -> {
                val fresh = DealTraversal.startTraversal(catalogVersion, recordCount)
                save(fresh, recordCount)
                fresh
            }
        }

    private companion object {
        const val STORE_NAME = "catalog_traversal"
        val CATALOG_VERSION = intPreferencesKey("catalog_version")
        val RECORD_COUNT = intPreferencesKey("record_count")
        val NEXT_POSITION = intPreferencesKey("next_position")
    }
}
