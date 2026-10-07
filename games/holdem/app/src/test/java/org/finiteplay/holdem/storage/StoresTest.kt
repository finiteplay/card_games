package org.finiteplay.holdem.storage

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.finiteplay.core.storage.FakeDataStores
import org.finiteplay.core.ui.layout.Handedness
import org.finiteplay.core.ui.layout.RestReminderInterval
import org.finiteplay.core.ui.theme.ThemeMode
import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.Contract
import org.finiteplay.holdem.rules.SeatAction
import org.finiteplay.holdem.rules.Tournament
import org.finiteplay.holdem.rules.act
import org.finiteplay.holdem.rules.legalActions
import org.finiteplay.holdem.rules.nextHand
import org.finiteplay.holdem.rules.replayHand
import org.finiteplay.holdem.rules.startTournament
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64

class StoresTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun tournaments(dir: java.io.File) = HoldemTournamentStore(dir, FakeDataStores::create)
    private fun hands(dir: java.io.File) = HoldemHandStore(dir, FakeDataStores::create)

    // ---- the log codec ---------------------------------------------------------------------

    @Test
    fun `the log codec round-trips every hand of a soak of seeded tournaments, and each replays to the same hand`() {
        var handsChecked = 0
        var sawBetAndRaise = false
        for (seed in 1L..40L) {
            val choose = randomChooser(wild = seed % 2 == 0L)
            var session = startTournament(seed)
            while (true) {
                session = playOut(session, choose)
                val state = session.state
                val decoded = decodeHandLog(encodeHandLog(session.log))
                assertEquals("seed $seed hand ${state.handNumber}", session.log, decoded)
                val replayed = replayHand(state.tournament, decoded)!!
                assertEquals(state, replayed.state)
                sawBetAndRaise = sawBetAndRaise || session.log.any { it.action is Action.Bet } && session.log.any { it.action is Action.Raise }
                handsChecked++
                session = session.nextHand() ?: break
            }
        }
        assertTrue("$handsChecked hands", handsChecked > 500)
        assertTrue(sawBetAndRaise)
    }

    @Test
    fun `every action and seat has a distinct encoding`() {
        val log = (0 until Contract.SEATS).flatMap { seat ->
            listOf(
                Action.Fold, Action.Check, Action.Call, Action.AllIn,
                Action.Bet(1), Action.Bet(127), Action.Bet(128), Action.Bet(9_000),
                Action.Raise(40), Action.Raise(8_999),
            ).map { SeatAction(seat, it) }
        }
        assertEquals(log, decodeHandLog(encodeHandLog(log)))
        assertEquals(0, encodeHandLog(emptyList()).size)
    }

    @Test
    fun `a log outside the alphabet is rejected`() {
        val bad = listOf(
            byteArrayOf(6), // opcode 6
            byteArrayOf(7),
            byteArrayOf((6 shl 3).toByte()), // seat 6
            byteArrayOf(0x40), // reserved bits
            byteArrayOf(3), // a bet with no amount
            byteArrayOf(3, 0), // a bet of nothing
            byteArrayOf(4, 0x80.toByte()), // a raise whose amount does not end
            byteArrayOf(3, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F), // overlong
            byteArrayOf(3, 0xFF.toByte(), 0x7F), // more chips than exist
        )
        for (bytes in bad) assertThrows(bytes.joinToString(), IllegalArgumentException::class.java) { decodeHandLog(bytes) }
    }

    // ---- the tournament store --------------------------------------------------------------

    private fun midTournament(): StoredTournament {
        var session = startTournament(77L)
        repeat(3) {
            session = playOut(session, randomChooser()).nextHand() ?: error("tournament ended early")
        }
        return StoredTournament(
            session.state.tournament,
            HoldemStatistics(
                placeCounts = listOf(2, 1, 0, 3, 0, 4),
                currentStreak = 1,
                bestStreak = 2,
                handsPlayed = 100,
                handsWon = 40,
                showdownsReached = 30,
                showdownsWon = 12,
                largestPot = 2_750,
                voluntaryHands = 31,
                preflopRaiseHands = 14,
                hintsUsed = 5,
            ),
        )
    }

    @Test
    fun `the tournament and statistics round-trip whole through a restart`() = runTest {
        val dir = folder.newFolder()
        val stored = midTournament()
        tournaments(dir).save(stored)
        assertEquals(TournamentLoad.Loaded(stored), tournaments(dir).load())

        val between = StoredTournament(null, stored.statistics)
        tournaments(dir).save(between)
        assertEquals(TournamentLoad.Loaded(between), tournaments(dir).load())
    }

    @Test
    fun `an absent tournament store is missing, an unreadable one is discarded and reported`() = runTest {
        assertEquals(TournamentLoad.Missing, tournaments(folder.newFolder()).load())

        val garbled = folder.newFolder()
        FakeDataStores.corrupt(garbled, "tournament")
        assertEquals(TournamentLoad.Recovered, tournaments(garbled).load())

        val notARecord = folder.newFolder()
        FakeDataStores.setRawStringPreference(notARecord, "tournament", "tournament", "not base64 !!")
        assertEquals(TournamentLoad.Recovered, tournaments(notARecord).load())

        val truncated = folder.newFolder()
        val good = midTournament()
        tournaments(truncated).save(good)
        val raw = Base64.getEncoder().encodeToString(Base64.getDecoder().decode(rawOf(truncated)).copyOf(30))
        FakeDataStores.setRawStringPreference(truncated, "tournament", "tournament", raw)
        assertEquals(TournamentLoad.Recovered, tournaments(truncated).load())
    }

    private suspend fun rawOf(dir: java.io.File): String {
        val prefs = FakeDataStores.create(dir, "tournament").data.first()
        return prefs.asMap().entries.single().value as String
    }

    @Test
    fun `an implausible tournament is discarded rather than dealt`() = runTest {
        val good = midTournament()
        val t = good.tournament!!
        val broken = listOf(
            // chips that do not sum to what is in play
            t.copy(stacks = t.stacks.mapIndexed { i, s -> if (i == 0) s + 1 else s }),
            // the player is already out
            t.copy(places = t.places.mapIndexed { i, p -> if (i == 0) 6 else p }, stacks = t.stacks.mapIndexed { i, s -> if (i == 0) 0 else s }),
            // a button on a seat that is out of the tournament
            Tournament.start(1L).copy(button = 9),
            t.copy(handNumber = 0),
        )
        for (tournament in broken) {
            val dir = folder.newFolder()
            tournaments(dir).save(StoredTournament(tournament, good.statistics))
            assertEquals(tournament.toString(), TournamentLoad.Recovered, tournaments(dir).load())
        }
        val dir = folder.newFolder()
        tournaments(dir).save(StoredTournament(null, good.statistics.copy(showdownsWon = 31)))
        assertEquals(TournamentLoad.Recovered, tournaments(dir).load())
    }

    @Test
    fun `a tournament dealt under other versions is dropped and the statistics kept`() = runTest {
        val dir = folder.newFolder()
        val good = midTournament()
        tournaments(dir).save(good.copy(tournament = good.tournament!!.copy(shuffleVersion = Contract.SHUFFLE_VERSION + 1)))
        assertEquals(TournamentLoad.Loaded(StoredTournament(null, good.statistics)), tournaments(dir).load())
    }

    // ---- the hand store --------------------------------------------------------------------

    @Test
    fun `the hand store restores number, seed and log, and reports nothing when empty`() = runTest {
        val dir = folder.newFolder()
        assertEquals(HandLoad.Missing, hands(dir).load())
        val hand = SavedHand(
            "g1", 4, 99L,
            listOf(SeatAction(3, Action.Fold), SeatAction(4, Action.Raise(60)), SeatAction(5, Action.AllIn), SeatAction(0, Action.Call)),
        )
        hands(dir).save(hand)
        assertEquals(HandLoad.Restored(hand), hands(dir).load())
        hands(dir).clear()
        assertEquals(HandLoad.Missing, hands(dir).load())
    }

    @Test
    fun `a corrupt hand save, or one with a log outside the alphabet, is discarded and reported`() = runTest {
        val garbled = folder.newFolder()
        FakeDataStores.corrupt(garbled, "active_game")
        assertEquals(HandLoad.Recovered, hands(garbled).load())

        val badLog = folder.newFolder()
        val store = org.finiteplay.core.storage.ActiveGameRecordStore(
            badLog, 1, object : org.finiteplay.core.storage.DealParameters<Int> {
                override fun write(prefs: androidx.datastore.preferences.core.MutablePreferences, value: Int) {
                    prefs[androidx.datastore.preferences.core.intPreferencesKey("hand_number")] = value
                }
                override fun read(prefs: androidx.datastore.preferences.core.Preferences): Int = error("unused")
            },
            dataStoreFactory = FakeDataStores::create,
        )
        store.save(
            org.finiteplay.core.storage.ActiveGameRecord("g", 1L, 0, Contract.RULES_VERSION, Contract.SHUFFLE_VERSION, 0, byteArrayOf(7)),
            2,
        )
        assertEquals(HandLoad.Recovered, hands(badLog).load())
        assertEquals(HandLoad.Missing, hands(badLog).load())
    }

    // ---- settings --------------------------------------------------------------------------

    @Test
    fun `settings default and survive a restart`() = runTest {
        val dir = folder.newFolder()
        assertEquals(HoldemSettings.DEFAULT, HoldemSettingsStore(dir, FakeDataStores::create).current())

        HoldemSettingsStore(dir, FakeDataStores::create).apply {
            setAnimationsEnabled(false)
            setHandedness(Handedness.LEFT)
            setSoundEnabled(true)
            setThemeMode(ThemeMode.entries.last())
            setLanguageTag("de")
            setRestReminderInterval(RestReminderInterval.THIRTY)
        }
        assertEquals(
            HoldemSettings(false, Handedness.LEFT, true, "de", ThemeMode.entries.last(), RestReminderInterval.THIRTY),
            HoldemSettingsStore(dir, FakeDataStores::create).current(),
        )
    }

    @Test
    fun `a setting that no longer names an option reads as its default`() = runTest {
        val dir = folder.newFolder()
        FakeDataStores.setRawStringPreference(dir, "settings", "theme_mode", "NO_SUCH_THEME")
        FakeDataStores.setRawStringPreference(dir, "settings", "handedness", "AMBIDEXTROUS")
        val settings = HoldemSettingsStore(dir, FakeDataStores::create).current()
        assertEquals(HoldemSettings.DEFAULT.themeMode, settings.themeMode)
        assertEquals(HoldemSettings.DEFAULT.handedness, settings.handedness)
    }

    @Test
    fun `a corrupt settings store reads as the defaults`() = runTest {
        val dir = folder.newFolder()
        FakeDataStores.corrupt(dir, "settings")
        assertEquals(HoldemSettings.DEFAULT, HoldemSettingsStore(dir, FakeDataStores::create).current())
    }

    // ---- the restore decision --------------------------------------------------------------

    private val tournament = Tournament.start(5L)
    private val stored = StoredTournament(tournament, HoldemStatistics(handsPlayed = 3, handsWon = 1))
    private val loaded = TournamentLoad.Loaded(stored)

    private fun savedAfter(actions: Int, number: Int = 1, seed: Long = tournament.handSeed): Pair<SavedHand, org.finiteplay.holdem.rules.HoldemSession> {
        var session = replayHand(tournament, emptyList())!!
        val choose = randomChooser()
        repeat(actions) { session = session.act(legalActions(session.state)!!.seat, choose(session.state))!! }
        return SavedHand("g", number, seed, session.log) to session
    }

    @Test
    fun `a hand save for the stored tournament's current hand resumes exactly there`() {
        val (hand, expected) = savedAfter(actions = 4)
        val plan = planRestore(loaded, HandLoad.Restored(hand)) as RestorePlan.ResumeHand
        assertEquals(expected.state, plan.session.state)
        assertEquals(expected.log, plan.session.log)
        assertEquals(true, plan.saved)
        assertEquals(false, plan.handRecovered)
        assertEquals(false, plan.clearHandSave)
        assertEquals(stored, plan.stored)
    }

    @Test
    fun `no hand save deals the current hand from its start without a notice`() {
        val plan = planRestore(loaded, HandLoad.Missing) as RestorePlan.ResumeHand
        assertEquals(replayHand(tournament, emptyList())!!.state, plan.session.state)
        assertEquals(false, plan.saved)
        assertEquals(false, plan.handRecovered)
    }

    @Test
    fun `a hand already recorded is discarded, whether the tournament moved on or the save is from the future`() {
        for (number in listOf(0, 2, 7)) {
            val plan = planRestore(loaded, HandLoad.Restored(savedAfter(3).first.copy(handNumber = number))) as RestorePlan.ResumeHand
            assertEquals(false, plan.saved)
            assertEquals(true, plan.clearHandSave)
            assertEquals(false, plan.handRecovered)
            assertEquals(emptyList<SeatAction>(), plan.session.log)
            assertEquals(stored.statistics, plan.stored.statistics)
        }
    }

    @Test
    fun `a corrupt hand save replays the hand from its start, same deck, with a notice`() {
        val plan = planRestore(loaded, HandLoad.Recovered) as RestorePlan.ResumeHand
        assertEquals(true, plan.handRecovered)
        assertEquals(false, plan.saved)
        assertEquals(tournament.handSeed, plan.session.state.tournament.handSeed)
        assertEquals(replayHand(tournament, emptyList())!!.state.deck, plan.session.state.deck)
    }

    @Test
    fun `a hand save whose log does not replay, or whose seed is another's, is replayed from its start with a notice`() {
        val (good, _) = savedAfter(3)
        val illegal = good.copy(log = good.log + SeatAction(0, Action.Check) + SeatAction(0, Action.Check) + SeatAction(0, Action.Check))
        val wrongSeed = good.copy(seed = good.seed + 1)
        for (hand in listOf(illegal, wrongSeed)) {
            val plan = planRestore(loaded, HandLoad.Restored(hand)) as RestorePlan.ResumeHand
            assertEquals(true, plan.handRecovered)
            assertEquals(false, plan.saved)
            assertEquals(true, plan.clearHandSave)
            assertEquals(emptyList<SeatAction>(), plan.session.log)
        }
    }

    @Test
    fun `no tournament, or an unreadable store, is a new tournament, and a hand save with it is dropped`() {
        val statistics = HoldemStatistics(handsPlayed = 9)
        val between = planRestore(TournamentLoad.Loaded(StoredTournament(null, statistics)), HandLoad.Restored(savedAfter(2).first))
        between as RestorePlan.NewTournament
        assertEquals(statistics, between.stored.statistics)
        assertEquals(true, between.clearHandSave)
        assertEquals(false, between.tournamentRecovered)

        val first = planRestore(TournamentLoad.Missing, HandLoad.Missing) as RestorePlan.NewTournament
        assertEquals(StoredTournament(), first.stored)
        assertEquals(false, first.clearHandSave)

        // The unreadable store takes its statistics with it (`docs/PLATFORM.md` "Persistence").
        val corrupt = planRestore(TournamentLoad.Recovered, HandLoad.Restored(savedAfter(2).first)) as RestorePlan.NewTournament
        assertEquals(StoredTournament(), corrupt.stored)
        assertEquals(true, corrupt.tournamentRecovered)
        assertEquals(true, corrupt.clearHandSave)
    }
}
