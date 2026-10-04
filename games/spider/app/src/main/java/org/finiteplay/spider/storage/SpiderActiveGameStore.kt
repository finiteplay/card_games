package org.finiteplay.spider.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import org.finiteplay.core.storage.ActiveGameLoad
import org.finiteplay.core.storage.ActiveGameRecord
import org.finiteplay.core.storage.ActiveGameRecordStore
import org.finiteplay.core.storage.DealParameters
import org.finiteplay.core.storage.preferencesDataStoreAt
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.session.SpiderSession
import org.finiteplay.spider.session.decodeSpiderLog
import org.finiteplay.spider.session.encodeSpiderLog
import org.finiteplay.spider.session.replaySpiderSession
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Result of [SpiderActiveGameStore.load]. A missing save and a discarded one are distinct so the
 * caller can start a new game silently in the first case and say plainly that a game was lost in
 * the second, without this layer knowing anything about UI.
 */
sealed class SpiderActiveGameLoadResult {
    data object Missing : SpiderActiveGameLoadResult()
    data class Restored(
        val session: SpiderSession,
        val elapsed: Duration,
        val gameId: String,
        val dealNumber: Int,
    ) : SpiderActiveGameLoadResult()
    data object Recovered : SpiderActiveGameLoadResult()
}

/**
 * Spider's active game, wrapped around `:core:storage`'s generic record store.
 *
 * What is Spider's is small: the suit count, and turning a restored record back into a session by
 * replaying its log through the same reducer the live game uses. The format version, the common
 * fields, and the Missing/Recovered distinction are the shared store's and are not reimplemented
 * here — this class is the first caller of the `DealParameters` seam other than Klondike, which is
 * what that seam was built for (`docs/ARCHITECTURE.md`).
 *
 * The suit count is exactly the parameter a seed does not carry: the same seed dealt at one suit
 * and at four is two different games, so restoring without it would hand the player a board they
 * never played.
 */
class SpiderActiveGameStore(
    directory: File,
    dataStoreFactory: (File, String) -> DataStore<Preferences> = ::preferencesDataStoreAt,
) {
    private val store = ActiveGameRecordStore(
        directory = directory,
        formatVersion = CURRENT_FORMAT_VERSION,
        dealParameters = SpiderDeal,
        storeName = STORE_NAME,
        dataStoreFactory = dataStoreFactory,
    )

    suspend fun save(session: SpiderSession, elapsed: Duration, gameId: String, dealNumber: Int) {
        store.save(
            record = ActiveGameRecord(
                gameId = gameId,
                seed = session.state.seed,
                catalogVersion = session.state.versions.catalogVersion,
                rulesVersion = session.state.versions.rulesVersion,
                shuffleVersion = session.state.versions.shuffleVersion,
                elapsedMillis = elapsed.inWholeMilliseconds,
                log = encodeSpiderLog(session.log),
            ),
            deal = SpiderDealParameters(session.state.suitCount, dealNumber),
        )
    }

    suspend fun load(): SpiderActiveGameLoadResult = when (val loaded = store.load()) {
        is ActiveGameLoad.Missing -> SpiderActiveGameLoadResult.Missing
        is ActiveGameLoad.Recovered -> SpiderActiveGameLoadResult.Recovered
        is ActiveGameLoad.Restored -> restore(loaded)
    }

    /**
     * A malformed log throws here rather than inside the generic store, so it takes the same
     * recovery path as any other unreadable save.
     */
    private suspend fun restore(loaded: ActiveGameLoad.Restored<SpiderDealParameters>): SpiderActiveGameLoadResult {
        val record = loaded.record
        return try {
            val session = replaySpiderSession(
                seed = record.seed,
                versions = GameVersions(record.catalogVersion, record.rulesVersion, record.shuffleVersion),
                log = decodeSpiderLog(record.log),
                suitCount = loaded.deal.suitCount,
            )
            SpiderActiveGameLoadResult.Restored(
                session = session,
                elapsed = record.elapsedMillis.milliseconds,
                gameId = record.gameId,
                dealNumber = loaded.deal.dealNumber,
            )
        } catch (e: Exception) {
            clear()
            SpiderActiveGameLoadResult.Recovered
        }
    }

    /** Discards this store's save, and only this store's. */
    suspend fun clear() {
        store.clear()
    }

    companion object {
        // v2 adds the deal number. A v1 save simply lacks the key, fails the version check, and
        // takes the recovery path it already had — the same handling any unreadable save gets,
        // not a new failure mode.
        const val CURRENT_FORMAT_VERSION = 2
        private const val STORE_NAME = ActiveGameRecordStore.DEFAULT_STORE_NAME
    }
}

/**
 * What a Spider seed alone does not say.
 *
 * [dealNumber] is stored rather than recomputed because the seed is derived *from* the number and
 * that direction does not invert: recovering "which deal is this" from a seed would mean searching
 * the sequence for it.
 */
data class SpiderDealParameters(val suitCount: SuitCount, val dealNumber: Int)

private object SpiderDeal : DealParameters<SpiderDealParameters> {
    private val SUIT_COUNT = stringPreferencesKey("suit_count")
    private val DEAL_NUMBER = intPreferencesKey("deal_number")

    override fun write(prefs: MutablePreferences, value: SpiderDealParameters) {
        prefs[SUIT_COUNT] = value.suitCount.name
        prefs[DEAL_NUMBER] = value.dealNumber
    }

    override fun read(prefs: Preferences): SpiderDealParameters {
        val name = prefs[SUIT_COUNT] ?: error("missing suitCount")
        return SpiderDealParameters(
            // An unknown name is a save from a build with a suit count this one does not have,
            // which is unreadable rather than defaultable — guessing would deal a different game.
            suitCount = SuitCount.entries.find { it.name == name } ?: error("unknown suitCount: $name"),
            dealNumber = prefs[DEAL_NUMBER] ?: error("missing dealNumber"),
        )
    }
}
