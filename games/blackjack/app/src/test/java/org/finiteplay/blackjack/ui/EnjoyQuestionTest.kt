package org.finiteplay.blackjack.ui

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.finiteplay.blackjack.rules.Decision
import org.finiteplay.blackjack.rules.SHOE_SIZE
import org.finiteplay.blackjack.storage.BlackjackLedgerStore
import org.finiteplay.blackjack.storage.BlackjackRoundStore
import org.finiteplay.blackjack.storage.BlackjackSettingsStore
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.core.session.ENJOY_MIN_GOOD_RESULTS
import org.finiteplay.core.storage.EngagementStore
import org.finiteplay.core.storage.FakeDataStores
import org.finiteplay.core.storage.seedVeteran
import org.finiteplay.core.ui.feedback.EnjoyController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Blackjack's part of the "enjoying this game?" question (`docs/PLATFORM.md` "Feedback and rating"): a won round is a
 * good result, and the question opens a few seconds later, on the settled table, never at once and never over the next round.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EnjoyQuestionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scheduler = TestCoroutineScheduler()
    private val day = TimeUnit.DAYS.toMillis(1)
    private var now = 1_000_000_000_000L
    private lateinit var dir: File
    private lateinit var store: EngagementStore
    private lateinit var enjoy: EnjoyController

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        dir = folder.newFolder()
        store = EngagementStore(dir, dataStoreFactory = FakeDataStores::create)
        // A player one good result short of the question: ten sessions over several days, two good results so far.
        now = store.seedVeteran(now, ENJOY_MIN_GOOD_RESULTS - 1)
        enjoy = EnjoyController(store, CoroutineScope(UnconfinedTestDispatcher(scheduler)), clock = { now })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun card(text: String): Card {
        val rank = when (text[0]) {
            'A' -> Rank.ACE
            'T' -> Rank.TEN
            'J' -> Rank.JACK
            'Q' -> Rank.QUEEN
            'K' -> Rank.KING
            else -> Rank.fromValue(text[0].digitToInt())
        }
        val suit = when (text[1]) {
            'C' -> Suit.CLUBS
            'D' -> Suit.DIAMONDS
            'H' -> Suit.HEARTS
            else -> Suit.SPADES
        }
        return Card(suit, rank)
    }

    /** Dealt player, dealer, player, dealer, then a filler no fixture reaches. */
    private fun shoeOf(vararg first: String): List<Card> {
        val filler = Card.CANONICAL_DECK
        return first.map(::card) + List(SHOE_SIZE - first.size) { filler[it % filler.size] }
    }

    private fun viewModel(vararg shoes: List<Card>): BlackjackViewModel {
        var next = 0
        val bySeed = shoes.withIndex().associate { (i, shoe) -> (i + 1).toLong() to shoe }
        return BlackjackViewModel(
            roundStore = BlackjackRoundStore(dir, FakeDataStores::create),
            ledgerStore = BlackjackLedgerStore(dir, FakeDataStores::create),
            settingsStore = BlackjackSettingsStore(dir, FakeDataStores::create),
            seedSource = { (++next).toLong() },
            shoeOverride = { seed -> bySeed.getValue(seed) },
            enjoy = enjoy,
        )
    }

    /** Ace and king for the player against a nine and a seven: a natural, paid 3 to 2, settled at once. */
    private val naturalWin = shoeOf("AS", "9C", "KD", "7S")

    /** Ten and two for the player against a ten and a seven: 12 against 17, lost on Stand. */
    private val standAndLose = shoeOf("TC", "TD", "2H", "7S")

    @Test
    fun `a won round is a good result, and the question opens after the result banner`() = runTest {
        val vm = viewModel(naturalWin)
        vm.deal()

        assertEquals(ENJOY_MIN_GOOD_RESULTS, store.current().goodResults)
        assertFalse("not while the result banner is up", enjoy.visible)

        scheduler.advanceTimeBy(5_001)
        scheduler.runCurrent()

        assertTrue(enjoy.visible)
    }

    @Test
    fun `a lost round is not a good result and asks nothing`() = runTest {
        val vm = viewModel(standAndLose)
        vm.deal()
        vm.decide(Decision.STAND)

        assertEquals(ENJOY_MIN_GOOD_RESULTS - 1, store.current().goodResults)
        scheduler.advanceTimeBy(10_000)
        scheduler.runCurrent()

        assertFalse(enjoy.visible)
    }

    @Test
    fun `dealing the next round before the question is due keeps it from opening over that round`() = runTest {
        val vm = viewModel(naturalWin, standAndLose)
        vm.deal()
        scheduler.advanceTimeBy(1_000)
        scheduler.runCurrent()

        vm.deal()
        scheduler.advanceTimeBy(10_000)
        scheduler.runCurrent()

        assertFalse(enjoy.visible)
    }
}
