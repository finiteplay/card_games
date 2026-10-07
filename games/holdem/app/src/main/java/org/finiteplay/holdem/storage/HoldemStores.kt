package org.finiteplay.holdem.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import org.finiteplay.core.storage.ActiveGameLoad
import org.finiteplay.core.storage.ActiveGameRecord
import org.finiteplay.core.storage.ActiveGameRecordStore
import org.finiteplay.core.storage.DealParameters
import org.finiteplay.core.storage.preferencesDataStoreAt
import org.finiteplay.core.storage.preferencesFileAt
import org.finiteplay.holdem.rules.Contract
import org.finiteplay.holdem.rules.SeatAction
import org.finiteplay.holdem.rules.Tournament
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Base64

/**
 * Everything that outlives a hand (`docs/games/holdem/DESIGN.md` "Persistence"): the tournament as
 * it stood when the hand in progress was dealt — null between tournaments — and every statistic.
 * One value, only ever written whole.
 */
data class StoredTournament(
    val tournament: Tournament? = null,
    val statistics: HoldemStatistics = HoldemStatistics(),
) {
    /** A tournament starting on [seed]: its first hand is not dealt yet, the statistics carry over. */
    fun started(seed: Long): StoredTournament = copy(tournament = Tournament.start(seed))
}

/** What [HoldemTournamentStore.load] found. */
sealed interface TournamentLoad {
    data object Missing : TournamentLoad
    data class Loaded(val stored: StoredTournament) : TournamentLoad

    /** A store existed and was unreadable; it was discarded, statistics with it (`docs/PLATFORM.md` "Persistence"). */
    data object Recovered : TournamentLoad
}

/**
 * The tournament-and-statistics store. It holds one value and writes it whole, in one atomic
 * DataStore edit, so a hand's chips can never be saved without the statistics that count it.
 */
class HoldemTournamentStore(
    private val directory: File,
    dataStoreFactory: (File, String) -> DataStore<Preferences> = ::preferencesDataStoreAt,
) {
    private val dataStore = dataStoreFactory(directory, STORE_NAME)

    suspend fun load(): TournamentLoad {
        val existed = preferencesFileAt(directory, STORE_NAME).let { it.exists() && it.length() > 0L }
        val encoded = dataStore.data.first()[TOURNAMENT]
        if (encoded.isNullOrEmpty()) return if (existed) discard() else TournamentLoad.Missing
        return decode(encoded)?.let(TournamentLoad::Loaded) ?: discard()
    }

    suspend fun save(stored: StoredTournament) {
        dataStore.edit { it[TOURNAMENT] = encode(stored) }
    }

    private suspend fun discard(): TournamentLoad.Recovered {
        dataStore.edit { it.remove(TOURNAMENT) }
        return TournamentLoad.Recovered
    }

    private companion object {
        const val STORE_NAME = "tournament"
        const val FORMAT_VERSION = 1

        // On disk on every installed device: renaming it discards every player's statistics.
        val TOURNAMENT = stringPreferencesKey("tournament")

        fun encode(stored: StoredTournament): String {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { out ->
                out.writeInt(FORMAT_VERSION)
                val t = stored.tournament
                out.writeBoolean(t != null)
                if (t != null) {
                    out.writeInt(t.rulesVersion)
                    out.writeInt(t.shuffleVersion)
                    out.writeLong(t.seed)
                    out.writeInt(t.handNumber)
                    out.writeInt(t.button)
                    t.stacks.forEach(out::writeInt)
                    t.places.forEach { out.writeInt(it ?: NO_PLACE) }
                }
                val s = stored.statistics
                s.placeCounts.forEach(out::writeInt)
                out.writeInt(s.currentStreak)
                out.writeInt(s.bestStreak)
                out.writeInt(s.handsPlayed)
                out.writeInt(s.handsWon)
                out.writeInt(s.showdownsReached)
                out.writeInt(s.showdownsWon)
                out.writeInt(s.largestPot)
                out.writeInt(s.voluntaryHands)
                out.writeInt(s.preflopRaiseHands)
                out.writeInt(s.hintsUsed)
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray())
        }

        /** Null when the record cannot be a store this code wrote. A tournament dealt under other versions is dropped, the statistics kept. */
        fun decode(encoded: String): StoredTournament? = try {
            DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded))).use { input ->
                require(input.readInt() == FORMAT_VERSION) { "format" }
                var tournament: Tournament? = null
                if (input.readBoolean()) {
                    val rules = input.readInt()
                    val shuffle = input.readInt()
                    val seed = input.readLong()
                    val handNumber = input.readInt()
                    val button = input.readInt()
                    val stacks = List(Contract.SEATS) { input.readInt() }
                    val places = List(Contract.SEATS) { input.readInt().takeIf { it != NO_PLACE } }
                    tournament = Tournament(seed, handNumber, button, stacks, places, rules, shuffle)
                    requirePlausible(tournament)
                    if (rules != Contract.RULES_VERSION || shuffle != Contract.SHUFFLE_VERSION) tournament = null
                }
                val statistics = HoldemStatistics(
                    placeCounts = List(Contract.SEATS) { input.readInt() },
                    currentStreak = input.readInt(),
                    bestStreak = input.readInt(),
                    handsPlayed = input.readInt(),
                    handsWon = input.readInt(),
                    showdownsReached = input.readInt(),
                    showdownsWon = input.readInt(),
                    largestPot = input.readInt(),
                    voluntaryHands = input.readInt(),
                    preflopRaiseHands = input.readInt(),
                    hintsUsed = input.readInt(),
                )
                require(input.available() == 0) { "trailing bytes" }
                requirePlausible(statistics)
                StoredTournament(tournament, statistics)
            }
        } catch (e: Exception) {
            null
        }

        const val NO_PLACE = -1

        /** A stored tournament is between hands with the player still in: chips conserved, out seats empty, places a real ranking. */
        fun requirePlausible(t: Tournament) {
            require(t.handNumber >= 1) { "hand number" }
            require(t.stacks.all { it >= 0 } && t.stacks.sum() == Contract.TOTAL_CHIPS) { "chips" }
            require(t.places.all { it == null || it in 1..Contract.SEATS }) { "places" }
            require((0 until Contract.SEATS).all { (t.places[it] == null) == (t.stacks[it] > 0) }) { "out seats hold no chips" }
            require(t.places[PLAYER_SEAT] == null && t.alive.size >= 2) { "the tournament is over" }
            require(t.button in t.alive) { "button" }
        }

        fun requirePlausible(s: HoldemStatistics) {
            require(s.placeCounts.all { it >= 0 } && s.currentStreak >= 0 && s.bestStreak >= s.currentStreak) { "tournaments" }
            require(s.handsWon in 0..s.handsPlayed && s.voluntaryHands in 0..s.handsPlayed) { "hands" }
            require(s.preflopRaiseHands in 0..s.voluntaryHands) { "raises are voluntary" }
            require(s.showdownsWon in 0..s.showdownsReached && s.showdownsReached <= s.handsPlayed) { "showdowns" }
            require(s.largestPot in 0..Contract.TOTAL_CHIPS && s.hintsUsed >= 0) { "figures" }
        }
    }
}

/**
 * A hand in progress as saved: enough to rebuild it from the tournament as it stood when the hand
 * was dealt by replaying [log]. [seed] is the hand's own seed, [handNumber] its place in the tournament.
 */
data class SavedHand(val gameId: String, val handNumber: Int, val seed: Long, val log: List<SeatAction>)

/** What [HoldemHandStore.load] found. */
sealed interface HandLoad {
    data object Missing : HandLoad
    data class Restored(val hand: SavedHand) : HandLoad

    /** A save existed and was unreadable; it was discarded. The hand is replayed from its start, not voided. */
    data object Recovered : HandLoad
}

/**
 * The hand in progress through `core/storage`'s [ActiveGameRecordStore], with the hand number as its
 * deal parameter (`docs/games/holdem/DESIGN.md` "Persistence"). There is no catalog and no timer, so
 * their fields are always 0.
 */
class HoldemHandStore(
    directory: File,
    dataStoreFactory: (File, String) -> DataStore<Preferences> = ::preferencesDataStoreAt,
) {
    private val store = ActiveGameRecordStore(
        directory = directory,
        formatVersion = FORMAT_VERSION,
        dealParameters = HandNumber,
        dataStoreFactory = dataStoreFactory,
    )

    suspend fun save(hand: SavedHand) {
        store.save(
            ActiveGameRecord(
                gameId = hand.gameId,
                seed = hand.seed,
                catalogVersion = 0,
                rulesVersion = Contract.RULES_VERSION,
                shuffleVersion = Contract.SHUFFLE_VERSION,
                elapsedMillis = 0,
                log = encodeHandLog(hand.log),
            ),
            hand.handNumber,
        )
    }

    suspend fun load(): HandLoad = when (val loaded = store.load()) {
        is ActiveGameLoad.Missing -> HandLoad.Missing
        is ActiveGameLoad.Recovered -> HandLoad.Recovered
        is ActiveGameLoad.Restored -> {
            val record = loaded.record
            val log = runCatching { decodeHandLog(record.log) }.getOrNull()
            // A hand dealt under other versions replays differently, so it is not the hand that was saved.
            if (log == null || record.rulesVersion != Contract.RULES_VERSION || record.shuffleVersion != Contract.SHUFFLE_VERSION) {
                store.clear()
                HandLoad.Recovered
            } else {
                HandLoad.Restored(SavedHand(record.gameId, loaded.deal, record.seed, log))
            }
        }
    }

    suspend fun clear() = store.clear()

    private object HandNumber : DealParameters<Int> {
        private val HAND_NUMBER = intPreferencesKey("hand_number")
        override fun write(prefs: MutablePreferences, value: Int) { prefs[HAND_NUMBER] = value }
        override fun read(prefs: Preferences): Int = prefs[HAND_NUMBER]?.also { require(it >= 1) } ?: error("missing hand number")
    }

    private companion object {
        const val FORMAT_VERSION = 1
    }
}
