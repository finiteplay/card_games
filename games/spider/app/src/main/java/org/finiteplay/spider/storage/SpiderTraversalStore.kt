package org.finiteplay.spider.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first
import org.finiteplay.core.storage.preferencesDataStoreAt
import org.finiteplay.spider.game.DealSequence
import org.finiteplay.spider.layout.SuitCount
import java.io.File

/**
 * How far a player has walked each suit count's deal sequence.
 *
 * Kept per suit count, not globally: they are different games sharing a board, and a player who
 * has played forty two-suit deals has not thereby skipped forty one-suit ones. This mirrors
 * Klondike keeping a position per difficulty, and for the same reason — restarting the app should
 * resume each sequence where it was, not redeal number one.
 */
class SpiderTraversalStore(
    directory: File,
    dataStoreFactory: (File, String) -> DataStore<Preferences> = ::preferencesDataStoreAt,
) {
    private val dataStore = dataStoreFactory(directory, STORE_NAME)

    /**
     * The number of the next deal at [suitCount], starting at [DealSequence.FIRST].
     *
     * [wrapAt] is the certified catalog's seed count for a suit count that has one — the deal
     * number is always the 1-based position within that list, so it wraps there instead of
     * growing past the list the way the uncertified formula's number does. Null for the
     * uncertified path, where the number has no ceiling to wrap at.
     */
    suspend fun next(suitCount: SuitCount, wrapAt: Int? = null): Int {
        val stored = dataStore.data.first()[keyFor(suitCount)] ?: DealSequence.FIRST
        return if (wrapAt != null) ((stored - 1).mod(wrapAt)) + 1 else stored
    }

    /** Records that [dealNumber] has been dealt, so the next one follows it. */
    suspend fun advancePast(suitCount: SuitCount, dealNumber: Int, wrapAt: Int? = null) {
        val next = dealNumber + 1
        dataStore.edit { it[keyFor(suitCount)] = if (wrapAt != null) ((next - 1).mod(wrapAt)) + 1 else next }
    }

    /**
     * Takes back [dealNumber], the deal just handed out, so the next [next] returns it again — for a
     * deal dealt and abandoned without a move. Only when [dealNumber] is still the latest handed out
     * (the stored position is the one after it); otherwise later deals have followed it and the
     * position is left alone.
     */
    suspend fun giveBack(suitCount: SuitCount, dealNumber: Int, wrapAt: Int? = null) {
        dataStore.edit { prefs ->
            val key = keyFor(suitCount)
            val stored = prefs[key] ?: DealSequence.FIRST
            val after = dealNumber + 1
            val expected = if (wrapAt != null) ((after - 1).mod(wrapAt)) + 1 else after
            if (stored == expected) prefs[key] = dealNumber
        }
    }

    suspend fun reset(suitCount: SuitCount) {
        dataStore.edit { it.remove(keyFor(suitCount)) }
    }

    private fun keyFor(suitCount: SuitCount) = intPreferencesKey("next_${suitCount.name.lowercase()}")

    private companion object {
        const val STORE_NAME = "deal_traversal"
    }
}
