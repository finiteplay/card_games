package org.finiteplay.blackjack.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import org.finiteplay.blackjack.rules.Decision
import org.finiteplay.blackjack.rules.RULES_VERSION
import org.finiteplay.blackjack.rules.SHUFFLE_VERSION
import org.finiteplay.blackjack.rules.decodeLog
import org.finiteplay.blackjack.rules.encodeLog
import org.finiteplay.core.storage.ActiveGameLoad
import org.finiteplay.core.storage.ActiveGameRecord
import org.finiteplay.core.storage.ActiveGameRecordStore
import org.finiteplay.core.storage.DealParameters
import org.finiteplay.core.storage.preferencesDataStoreAt
import java.io.File

/** A round in progress as saved: enough to rebuild it by replaying [log] against [seed] at [bet]. */
data class SavedRound(val gameId: String, val seed: Long, val bet: Int, val log: List<Decision>)

/** What [BlackjackRoundStore.load] found. */
sealed class RoundLoad {
    data object Missing : RoundLoad()
    data class Restored(val round: SavedRound) : RoundLoad()

    /**
     * A save existed and was unreadable; it was discarded. Its stakes were never deducted from the
     * bankroll, so voiding the round costs nothing.
     */
    data object Recovered : RoundLoad()
}

/**
 * The round in progress through `core/storage`'s [ActiveGameRecordStore], with the bet as its
 * deal parameter (`docs/games/blackjack/DESIGN.md` "Persistence"). There is no catalog and no
 * timer, so their fields are always 0.
 */
class BlackjackRoundStore(
    directory: File,
    dataStoreFactory: (File, String) -> DataStore<Preferences> = ::preferencesDataStoreAt,
) {
    private val store = ActiveGameRecordStore(
        directory = directory,
        formatVersion = FORMAT_VERSION,
        dealParameters = BetParameter,
        dataStoreFactory = dataStoreFactory,
    )

    suspend fun save(round: SavedRound) {
        store.save(
            ActiveGameRecord(
                gameId = round.gameId,
                seed = round.seed,
                catalogVersion = 0,
                rulesVersion = RULES_VERSION,
                shuffleVersion = SHUFFLE_VERSION,
                elapsedMillis = 0,
                log = encodeLog(round.log),
            ),
            round.bet,
        )
    }

    suspend fun load(): RoundLoad = when (val loaded = store.load()) {
        is ActiveGameLoad.Missing -> RoundLoad.Missing
        is ActiveGameLoad.Recovered -> RoundLoad.Recovered
        is ActiveGameLoad.Restored -> {
            val record = loaded.record
            val log = runCatching { decodeLog(record.log) }.getOrNull()
            // A round dealt under other versions replays differently, so it is not the round that was saved.
            if (log == null || record.rulesVersion != RULES_VERSION || record.shuffleVersion != SHUFFLE_VERSION) {
                store.clear()
                RoundLoad.Recovered
            } else {
                RoundLoad.Restored(SavedRound(record.gameId, record.seed, loaded.deal, log))
            }
        }
    }

    suspend fun clear() = store.clear()

    private object BetParameter : DealParameters<Int> {
        private val BET = intPreferencesKey("bet")
        override fun write(prefs: MutablePreferences, value: Int) { prefs[BET] = value }
        override fun read(prefs: Preferences): Int = prefs[BET] ?: error("missing bet")
    }

    private companion object {
        const val FORMAT_VERSION = 1
    }
}
