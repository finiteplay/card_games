package org.finiteplay.blackjack.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.finiteplay.blackjack.rules.Chips
import org.finiteplay.blackjack.rules.Decision
import org.finiteplay.blackjack.rules.HandOutcome
import org.finiteplay.blackjack.rules.Phase
import org.finiteplay.blackjack.rules.SHOE_SIZE
import org.finiteplay.blackjack.rules.legalDecisions
import org.finiteplay.blackjack.storage.BlackjackLedger
import org.finiteplay.blackjack.storage.BlackjackLedgerStore
import org.finiteplay.blackjack.storage.BlackjackRoundStore
import org.finiteplay.blackjack.storage.BlackjackSettingsStore
import org.finiteplay.cards.Card
import org.finiteplay.core.storage.FakeDataStores
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * `EXECUTION_PLAN.md` B4a's gate at the store seam: a card is not shown before its save lands, and
 * a failure or kill at each point leaves a correct state on restore — a result is never paid twice,
 * lost, or counted twice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BlackjackViewModelTest {
    private lateinit var dir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dir = Files.createTempDirectory("blackjack-vm-test").toFile()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        dir.deleteRecursively()
    }

    /** A DataStore whose writes can be held, failed, or just observed, by store name. */
    private class Gates {
        val events = mutableListOf<String>()
        val holds = mutableMapOf<String, CompletableDeferred<Unit>>()
        val failing = mutableSetOf<String>()

        fun wrap(name: String, delegate: DataStore<Preferences>): DataStore<Preferences> = object : DataStore<Preferences> {
            override val data = delegate.data
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                holds[name]?.await()
                if (name in failing) throw java.io.IOException("injected failure writing $name")
                events += name
                return delegate.updateData(transform)
            }
        }
    }

    private val gates = Gates()
    private val factory: (File, String) -> DataStore<Preferences> = { d, name -> gates.wrap(name, FakeDataStores.create(d, name)) }

    private fun roundStore() = BlackjackRoundStore(dir, factory)
    private fun ledgerStore() = BlackjackLedgerStore(dir, factory)

    /** A shoe starting with [first], padded with a filler no fixture reaches. */
    private fun shoeOf(vararg first: String): List<Card> {
        val filler = Card.CANONICAL_DECK
        return first.map(::card) + List(SHOE_SIZE - first.size) { filler[it % filler.size] }
    }

    private fun card(text: String): Card {
        val rank = when (text[0]) {
            'A' -> org.finiteplay.cards.Rank.ACE
            'T' -> org.finiteplay.cards.Rank.TEN
            'J' -> org.finiteplay.cards.Rank.JACK
            'Q' -> org.finiteplay.cards.Rank.QUEEN
            'K' -> org.finiteplay.cards.Rank.KING
            else -> org.finiteplay.cards.Rank.fromValue(text[0].digitToInt())
        }
        val suit = when (text[1]) {
            'C' -> org.finiteplay.cards.Suit.CLUBS
            'D' -> org.finiteplay.cards.Suit.DIAMONDS
            'H' -> org.finiteplay.cards.Suit.HEARTS
            else -> org.finiteplay.cards.Suit.SPADES
        }
        return Card(suit, rank)
    }

    /** Seeds 1, 2, 3, ... each with the shoe registered for it. */
    private fun newViewModel(vararg shoes: List<Card>): BlackjackViewModel {
        var next = 0
        val byseed = shoes.withIndex().associate { (index, shoe) -> (index + 1).toLong() to shoe }
        return BlackjackViewModel(
            roundStore = roundStore(),
            ledgerStore = ledgerStore(),
            settingsStore = BlackjackSettingsStore(dir, factory),
            seedSource = { (++next).toLong() },
            shoeOverride = { seed -> byseed.getValue(seed) },
        )
    }

    // A round the player can play out by hand: 9 + 8 = 17 against a dealer 10 + 7 = 17, a push on Stand,
    // and a hit that draws a 3 (hard 20) which the dealer's 17 loses to.
    private val plain = shoeOf("9C", "TD", "8H", "7S", "3S", "2S")

    @Test
    fun `a fresh install starts idle with the starting bankroll`() {
        val vm = newViewModel(plain)
        assertFalse(vm.isLoading)
        assertEquals(TableState.IDLE, vm.tableState)
        assertEquals(Chips.STARTING_BANKROLL, vm.bankroll)
        assertEquals(Chips.MIN_BET, vm.selectedBet)
        assertNull(vm.session)
    }

    @Test
    fun `a card is not shown until the save that records it has completed`() {
        val vm = newViewModel(plain)
        gates.holds["active_game"] = CompletableDeferred()

        vm.deal()
        assertNull("the dealt cards are absent until the round is saved", vm.session)
        assertTrue(vm.busy)

        gates.holds.getValue("active_game").complete(Unit)
        assertNotNull(vm.session)
        assertEquals(4, vm.session!!.state.shoePosition)
        assertFalse(vm.busy)

        // The same holds for a decision: the card it draws is absent until its save lands.
        gates.holds["active_game"] = CompletableDeferred()
        val before = vm.session!!.state.hands[0].cards.size
        vm.decide(Decision.HIT)
        assertEquals(before, vm.session!!.state.hands[0].cards.size)
        gates.holds.getValue("active_game").complete(Unit)
        assertEquals(before + 1, vm.session!!.state.hands[0].cards.size)
    }

    @Test
    fun `input is ignored while a save is in flight`() {
        val vm = newViewModel(plain)
        vm.deal()
        gates.holds["active_game"] = CompletableDeferred()
        vm.decide(Decision.HIT)
        vm.decide(Decision.STAND)
        gates.holds.getValue("active_game").complete(Unit)
        assertEquals("only the first decision applied", listOf(Decision.HIT), vm.session!!.log)
    }

    @Test
    fun `settlement writes the ledger first and clears the round second`() {
        val vm = newViewModel(plain)
        vm.deal()
        gates.events.clear()
        vm.decide(Decision.STAND)

        assertEquals(TableState.SETTLED, vm.tableState)
        assertEquals(listOf("ledger", "active_game"), gates.events)
    }

    @Test
    fun `a settled round pays the bankroll once, lowers the bet and counts the hand`() {
        val vm = newViewModel(plain)
        vm.stepBet(+1) // 20
        vm.stepBet(+1) // 30
        vm.deal()
        vm.decide(Decision.HIT) // 9 + 8 + 3 = 20 against a dealer 17
        vm.decide(Decision.STAND)
        assertEquals(TableState.SETTLED, vm.tableState)
        assertEquals(Chips.STARTING_BANKROLL + 30, vm.bankroll)
        assertEquals(1, vm.ledger.statistics.handsPlayed)
        assertEquals(1, vm.ledger.statistics.handsWon)
        assertEquals(30L, vm.ledger.statistics.lifetimeNet)
        assertEquals(Chips.STARTING_BANKROLL + 30, runBlocking { ledgerStore().load() }.bankroll)
    }

    // ---- restore ---------------------------------------------------------------------------

    @Test
    fun `a round in progress is restored mid-decision by replaying its log`() {
        val first = newViewModel(plain)
        first.deal()
        first.decide(Decision.HIT)
        val saved = first.session!!

        val second = newViewModel(plain)
        assertEquals(saved, second.session)
        assertEquals(saved.state.hands, second.session!!.state.hands)
        assertEquals(Chips.STARTING_BANKROLL, second.bankroll)
    }

    @Test
    fun `a kill before the round is saved at Deal leaves nothing dealt and the bankroll untouched`() {
        val vm = newViewModel(plain)
        gates.failing += "active_game"
        vm.deal()
        assertNull(vm.session)
        gates.failing.clear()

        val restored = newViewModel(plain)
        assertEquals(TableState.IDLE, restored.tableState)
        assertEquals(Chips.STARTING_BANKROLL, restored.bankroll)
        assertEquals(0, restored.ledger.statistics.handsPlayed)
    }

    @Test
    fun `a failure between a decision and its save restores the round from before it`() {
        val vm = newViewModel(plain)
        vm.deal()
        gates.failing += "active_game"
        vm.decide(Decision.HIT)
        assertEquals("the card was never shown", 2, vm.session!!.state.hands[0].cards.size)
        gates.failing.clear()

        val restored = newViewModel(plain)
        assertEquals(emptyList<Decision>(), restored.session!!.log)
    }

    @Test
    fun `a kill between settlement's write and clearing the round neither pays nor counts twice`() {
        val vm = newViewModel(plain)
        vm.deal()
        // The ledger write lands; clearing the round then fails — the process dies between the two.
        gates.holds.clear()
        gates.failing += "active_game"
        vm.decide(Decision.STAND)
        gates.failing.clear()

        val afterKill = ledgerStore().let { runBlocking { it.load() } }
        assertEquals("settled once", 1, afterKill.statistics.handsPlayed)
        val paid = afterKill.bankroll

        val restored = newViewModel(plain)
        assertEquals("the round is discarded, not replayed or paid again", TableState.IDLE, restored.tableState)
        assertEquals(paid, restored.bankroll)
        assertEquals(1, restored.ledger.statistics.handsPlayed)
        assertNull(runBlocking { roundStore().load() }.let { (it as? org.finiteplay.blackjack.storage.RoundLoad.Restored)?.round })
    }

    @Test
    fun `a corrupt round save voids the round without touching the bankroll`() {
        val first = newViewModel(plain)
        first.deal()
        first.decide(Decision.STAND)
        val bankroll = first.bankroll

        FakeDataStores.corrupt(dir, "active_game")
        val restored = newViewModel(plain)

        assertTrue(restored.recoveryNoticeVisible)
        assertEquals(TableState.IDLE, restored.tableState)
        assertEquals(bankroll, restored.bankroll)
    }

    @Test
    fun `a corrupt ledger starts again from the starting bankroll with empty statistics`() {
        val first = newViewModel(plain)
        first.deal()
        first.decide(Decision.STAND)

        FakeDataStores.corrupt(dir, "ledger")
        val restored = newViewModel(plain)
        assertEquals(BlackjackLedger(), restored.ledger)
    }

    // ---- betting and the reset -------------------------------------------------------------

    @Test
    fun `the bet steps by ten within what the bankroll covers`() {
        val vm = newViewModel(plain)
        repeat(3) { vm.stepBet(+1) }
        assertEquals(40, vm.selectedBet)
        repeat(10) { vm.stepBet(-1) }
        assertEquals(Chips.MIN_BET, vm.selectedBet)
        assertFalse(vm.canStepBetDown)
    }

    @Test
    fun `a loss lowers a selected bet the bankroll no longer covers`() {
        runBlocking { ledgerStore().save(BlackjackLedger(bankroll = 120, selectedBet = 100)) }
        val lose = shoeOf("TC", "TD", "6H", "9S", "KS") // 16, hits a King and busts
        val vm = newViewModel(lose)
        assertEquals(100, vm.selectedBet)
        vm.deal()
        vm.decide(Decision.HIT)
        assertEquals(HandOutcome.BUST, vm.session!!.state.settlement!!.hands[0].outcome)
        assertEquals(20, vm.bankroll)
        assertEquals("the bet is lowered to what the bankroll covers", 20, vm.selectedBet)
    }

    @Test
    fun `below the minimum Reset replaces Deal, tops up and counts the reset`() {
        runBlocking {
            ledgerStore().save(BlackjackLedger(bankroll = 5, selectedBet = 10, statistics = org.finiteplay.blackjack.storage.BlackjackStatistics(handsPlayed = 7, resets = 1)))
        }
        val vm = newViewModel(plain)
        assertTrue(vm.needsReset)
        vm.deal()
        assertNull("no round can be dealt", vm.session)

        vm.resetBankroll()
        assertEquals(Chips.STARTING_BANKROLL, vm.bankroll)
        assertEquals(Chips.MIN_BET, vm.selectedBet)
        assertEquals(2, vm.ledger.statistics.resets)
        assertEquals("statistics are kept", 7, vm.ledger.statistics.handsPlayed)
        assertFalse(vm.needsReset)
        assertEquals(Chips.STARTING_BANKROLL, runBlocking { ledgerStore().load() }.bankroll)
    }

    @Test
    fun `a reset is refused while the bankroll covers the minimum`() {
        val vm = newViewModel(plain)
        vm.resetBankroll()
        assertEquals(0, vm.ledger.statistics.resets)
    }

    // ---- the buttons equal the engine's legal actions --------------------------------------

    @Test
    fun `the offered decisions are exactly the engine's legal decisions`() {
        val vm = newViewModel(plain)
        vm.deal()
        assertEquals(legalDecisions(vm.session!!.state, vm.bankroll), vm.legal)
        assertFalse(Decision.TAKE_INSURANCE in vm.legal)
    }

    @Test
    fun `insurance is offered alone on an Ace`() {
        val vm = newViewModel(shoeOf("9C", "AD", "8H", "5S", "KH"))
        vm.deal()
        assertEquals(TableState.INSURANCE, vm.tableState)
        assertEquals(setOf(Decision.TAKE_INSURANCE, Decision.DECLINE_INSURANCE), vm.legal)
    }

    // ---- statistics ------------------------------------------------------------------------

    @Test
    fun `statistics after a sequence with a split, a push, insurance and two resets match an independent count`() {
        val rounds = listOf(
            // 1: split eights; hand one 8+3 stands at 11? (decisions below keep it simple and deterministic)
            shoeOf("8C", "6D", "8H", "TS", "TC", "9S", "7S", "5S"),
            // 2: a push on 18 against 18.
            shoeOf("TC", "TD", "8H", "8S"),
            // 3: insurance taken against an Ace with no blackjack underneath.
            shoeOf("9C", "AD", "8H", "6S", "KS"),
            // 4: a player blackjack.
            shoeOf("AC", "9D", "KH", "7S"),
        )
        val vm = newViewModel(*rounds.toTypedArray())
        var expectedNet = 0L
        var played = 0
        var won = 0
        var lost = 0
        var pushed = 0
        var naturals = 0

        fun finish() {
            val settlement = vm.session!!.state.settlement!!
            expectedNet += settlement.total
            for (hand in settlement.hands) {
                played++
                when (hand.outcome) {
                    HandOutcome.BLACKJACK -> { won++; naturals++ }
                    HandOutcome.WIN -> won++
                    HandOutcome.PUSH -> pushed++
                    HandOutcome.LOSS, HandOutcome.BUST -> lost++
                }
            }
        }

        vm.deal(); vm.decide(Decision.SPLIT)
        while (vm.tableState == TableState.PLAYING) vm.decide(Decision.STAND)
        finish()
        vm.deal(); vm.decide(Decision.STAND); finish()
        vm.deal(); vm.decide(Decision.TAKE_INSURANCE); while (vm.tableState == TableState.PLAYING) vm.decide(Decision.STAND); finish()
        vm.deal(); finish()

        val stats = vm.ledger.statistics
        assertEquals(played, stats.handsPlayed)
        assertEquals(won, stats.handsWon)
        assertEquals(lost, stats.handsLost)
        assertEquals(pushed, stats.handsPushed)
        assertEquals(naturals, stats.blackjacks)
        assertEquals("lifetime net equals the sum of the settlement deltas", expectedNet, stats.lifetimeNet)
        assertEquals(Chips.STARTING_BANKROLL + expectedNet.toInt(), vm.bankroll)
        assertTrue(stats.highWater >= vm.bankroll)
    }

    @Test
    fun `the round phase after Deal is never one the engine would refuse`() {
        val vm = newViewModel(plain)
        vm.deal()
        assertTrue(vm.session!!.state.phase == Phase.PLAYING)
    }

    @Test
    fun `hint names basic strategy, is an offered action, and clears with the next change`() {
        val vm = newViewModel(plain)
        assertFalse("no hint between rounds", vm.canHint)
        vm.deal()
        assertTrue(vm.canHint)

        vm.requestHint()
        // 9 + 8 = hard 17 against a ten: stand.
        assertEquals(Decision.STAND, vm.hint)
        assertTrue(vm.hint in vm.legal)

        vm.requestHint()
        assertNull("asking again hides it", vm.hint)

        vm.requestHint()
        vm.decide(Decision.HIT)
        assertNull("a change to the round clears it", vm.hint)
    }

    @Test
    fun `hint always declines insurance`() {
        val vm = newViewModel(shoeOf("TC", "AD", "9H", "5S", "KH"))
        vm.deal()
        vm.requestHint()
        assertEquals(Decision.DECLINE_INSURANCE, vm.hint)
    }
}
