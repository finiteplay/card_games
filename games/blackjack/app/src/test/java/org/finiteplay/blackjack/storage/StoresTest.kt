package org.finiteplay.blackjack.storage

import kotlinx.coroutines.test.runTest
import org.finiteplay.blackjack.rules.Chips
import org.finiteplay.blackjack.rules.Decision
import org.finiteplay.blackjack.rules.HandOutcome
import org.finiteplay.blackjack.rules.HandResult
import org.finiteplay.blackjack.rules.Settlement
import org.finiteplay.core.storage.FakeDataStores
import org.finiteplay.core.ui.layout.Handedness
import org.finiteplay.core.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StoresTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `the ledger round-trips whole through a simulated restart`() = runTest {
        val dir = folder.newFolder()
        val ledger = BlackjackLedger(
            bankroll = 1_340,
            selectedBet = 60,
            statistics = BlackjackStatistics(12, 5, 4, 3, 1, 1_500, -85L, 2),
            lastSettledSeed = -7L,
        )
        BlackjackLedgerStore(dir, FakeDataStores::create).save(ledger)

        assertEquals(ledger, BlackjackLedgerStore(dir, FakeDataStores::create).load())
    }

    @Test
    fun `an absent or garbage ledger reads as the starting ledger`() = runTest {
        assertEquals(BlackjackLedger(), BlackjackLedgerStore(folder.newFolder(), FakeDataStores::create).load())
        val dir = folder.newFolder()
        FakeDataStores.corrupt(dir, "ledger")
        assertEquals(BlackjackLedger(), BlackjackLedgerStore(dir, FakeDataStores::create).load())
    }

    @Test
    fun `settlement pays, counts, lowers the bet and records the seed in one step`() {
        val settlement = Settlement(
            hands = listOf(HandResult(HandOutcome.BLACKJACK, 100, 150), HandResult(HandOutcome.LOSS, 100, -100)),
            insuranceDelta = -50,
            dealerBlackjack = false,
        )
        val after = BlackjackLedger(bankroll = 130, selectedBet = 100).afterSettlement(settlement, seed = 9L)
        assertEquals(130, after.bankroll) // +150 −100 −50 = 0
        val lose = BlackjackLedger(bankroll = 130, selectedBet = 100).afterSettlement(
            Settlement(listOf(HandResult(HandOutcome.BUST, 100, -100)), 0, false), 3L,
        )
        assertEquals(30, lose.bankroll)
        assertEquals(30, lose.selectedBet)
        assertEquals(3L, lose.lastSettledSeed)
        assertEquals(1, lose.statistics.handsLost)
        assertEquals(-100L, lose.statistics.lifetimeNet)
        assertEquals(Chips.STARTING_BANKROLL, lose.statistics.highWater)
        assertEquals(2, after.statistics.handsPlayed)
        assertEquals(1, after.statistics.blackjacks)
    }

    @Test
    fun `the round store restores seed, bet and log, and reports nothing when empty`() = runTest {
        val dir = folder.newFolder()
        val store = BlackjackRoundStore(dir, FakeDataStores::create)
        assertEquals(RoundLoad.Missing, store.load())

        val round = SavedRound("g1", 77L, 120, listOf(Decision.HIT, Decision.DOUBLE, Decision.STAND))
        store.save(round)
        assertEquals(RoundLoad.Restored(round), BlackjackRoundStore(dir, FakeDataStores::create).load())

        store.clear()
        assertEquals(RoundLoad.Missing, store.load())
    }

    @Test
    fun `a corrupt round save is discarded and reported`() = runTest {
        val dir = folder.newFolder()
        FakeDataStores.corrupt(dir, "active_game")
        val store = BlackjackRoundStore(dir, FakeDataStores::create)
        assertEquals(RoundLoad.Recovered, store.load())
    }

    @Test
    fun `settings default and survive a restart`() = runTest {
        val dir = folder.newFolder()
        assertEquals(BlackjackSettings.DEFAULT, BlackjackSettingsStore(dir, FakeDataStores::create).current())

        BlackjackSettingsStore(dir, FakeDataStores::create).apply {
            setAnimationsEnabled(false)
            setHandedness(Handedness.LEFT)
            setSoundEnabled(true)
            setThemeMode(ThemeMode.entries.last())
            setLanguageTag("de")
            setRestReminderInterval(org.finiteplay.core.ui.layout.RestReminderInterval.THIRTY)
        }
        val reopened = BlackjackSettingsStore(dir, FakeDataStores::create).current()
        assertEquals(false, reopened.animationsEnabled)
        assertEquals(Handedness.LEFT, reopened.handedness)
        assertEquals(true, reopened.soundEnabled)
        assertEquals(ThemeMode.entries.last(), reopened.themeMode)
        assertEquals("de", reopened.languageTag)
        assertEquals(org.finiteplay.core.ui.layout.RestReminderInterval.THIRTY, reopened.restReminderInterval)
    }
}
