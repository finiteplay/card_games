package org.finiteplay.blackjack.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import org.finiteplay.blackjack.rules.Chips
import org.finiteplay.blackjack.rules.HandOutcome
import org.finiteplay.blackjack.rules.Settlement
import org.finiteplay.core.storage.preferencesDataStoreAt
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Base64

/**
 * Lifetime statistics, one pool (`docs/games/blackjack/DESIGN.md` "Scoring and statistics"). Each
 * split hand counts as a hand; a push is neither won nor lost; [highWater] and [lifetimeNet] are
 * kept as their own running figures because a reset tops the bankroll up by a varying amount, so
 * neither can be derived from the bankroll again.
 */
data class BlackjackStatistics(
    val handsPlayed: Int = 0,
    val handsWon: Int = 0,
    val handsLost: Int = 0,
    val handsPushed: Int = 0,
    val blackjacks: Int = 0,
    val highWater: Int = Chips.STARTING_BANKROLL,
    val lifetimeNet: Long = 0,
    val resets: Int = 0,
) {
    /** These statistics after [settlement] has paid out, the bankroll now being [bankroll]. */
    fun settled(settlement: Settlement, bankroll: Int): BlackjackStatistics {
        var won = 0
        var lost = 0
        var pushed = 0
        var naturals = 0
        for (hand in settlement.hands) {
            when (hand.outcome) {
                HandOutcome.BLACKJACK -> { won++; naturals++ }
                HandOutcome.WIN -> won++
                HandOutcome.LOSS, HandOutcome.BUST -> lost++
                HandOutcome.PUSH -> pushed++
            }
        }
        return copy(
            handsPlayed = handsPlayed + settlement.hands.size,
            handsWon = handsWon + won,
            handsLost = handsLost + lost,
            handsPushed = handsPushed + pushed,
            blackjacks = blackjacks + naturals,
            highWater = maxOf(highWater, bankroll),
            lifetimeNet = lifetimeNet + settlement.total,
        )
    }
}

/**
 * Everything that outlives a round: the bankroll, the bet last chosen, every statistic, and the
 * seed of the last round settled — the last of which is what makes settlement happen exactly once
 * (`DESIGN.md` "Persistence").
 */
data class BlackjackLedger(
    val bankroll: Int = Chips.STARTING_BANKROLL,
    val selectedBet: Int = Chips.MIN_BET,
    val statistics: BlackjackStatistics = BlackjackStatistics(),
    val lastSettledSeed: Long? = null,
) {
    /** The ledger after a round settles: paid, counted, bet lowered if need be, and the round's seed recorded. */
    fun afterSettlement(settlement: Settlement, seed: Long): BlackjackLedger {
        val next = bankroll + settlement.total
        return copy(
            bankroll = next,
            selectedBet = Chips.betAfterSettlement(selectedBet, next),
            statistics = statistics.settled(settlement, next),
            lastSettledSeed = seed,
        )
    }

    /** A bankroll reset: the starting stake, the minimum bet, and the reset counted. */
    fun afterReset(): BlackjackLedger = copy(
        bankroll = Chips.reset(),
        selectedBet = Chips.MIN_BET,
        statistics = statistics.copy(resets = statistics.resets + 1),
    )
}

/**
 * The ledger store. It holds one value and writes it whole, in one atomic DataStore edit, so a
 * bankroll can never be saved without the statistics and seed that go with it. An unreadable store
 * reads back as the starting ledger — the discard `docs/PLATFORM.md` "Persistence" requires —
 * rather than failing a launch.
 */
class BlackjackLedgerStore(
    directory: File,
    dataStoreFactory: (File, String) -> DataStore<Preferences> = ::preferencesDataStoreAt,
) {
    private val dataStore = dataStoreFactory(directory, STORE_NAME)

    suspend fun load(): BlackjackLedger = decodeOrDefault(dataStore.data.first()[LEDGER])

    suspend fun save(ledger: BlackjackLedger) {
        dataStore.edit { it[LEDGER] = encode(ledger) }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(LEDGER) }
    }

    private fun encode(ledger: BlackjackLedger): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(FORMAT_VERSION)
            out.writeInt(ledger.bankroll)
            out.writeInt(ledger.selectedBet)
            out.writeBoolean(ledger.lastSettledSeed != null)
            out.writeLong(ledger.lastSettledSeed ?: 0L)
            val s = ledger.statistics
            out.writeInt(s.handsPlayed)
            out.writeInt(s.handsWon)
            out.writeInt(s.handsLost)
            out.writeInt(s.handsPushed)
            out.writeInt(s.blackjacks)
            out.writeInt(s.highWater)
            out.writeLong(s.lifetimeNet)
            out.writeInt(s.resets)
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    private fun decodeOrDefault(encoded: String?): BlackjackLedger {
        if (encoded.isNullOrEmpty()) return BlackjackLedger()
        return try {
            DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded))).use { input ->
                if (input.readInt() != FORMAT_VERSION) return BlackjackLedger()
                val bankroll = input.readInt()
                val selectedBet = input.readInt()
                val hasSeed = input.readBoolean()
                val seed = input.readLong()
                val statistics = BlackjackStatistics(
                    handsPlayed = input.readInt(),
                    handsWon = input.readInt(),
                    handsLost = input.readInt(),
                    handsPushed = input.readInt(),
                    blackjacks = input.readInt(),
                    highWater = input.readInt(),
                    lifetimeNet = input.readLong(),
                    resets = input.readInt(),
                )
                require(bankroll >= 0 && selectedBet >= Chips.MIN_BET) { "implausible ledger" }
                BlackjackLedger(bankroll, selectedBet, statistics, if (hasSeed) seed else null)
            }
        } catch (e: Exception) {
            BlackjackLedger()
        }
    }

    private companion object {
        const val STORE_NAME = "ledger"
        const val FORMAT_VERSION = 1

        // On disk on every installed device: renaming it discards every player's bankroll.
        val LEDGER = stringPreferencesKey("ledger")
    }
}
