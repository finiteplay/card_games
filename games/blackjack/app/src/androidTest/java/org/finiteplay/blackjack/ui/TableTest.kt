package org.finiteplay.blackjack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.finiteplay.blackjack.rules.Chips
import org.finiteplay.blackjack.rules.HandOutcome
import org.finiteplay.blackjack.rules.SHOE_SIZE
import org.finiteplay.blackjack.storage.BlackjackLedger
import org.finiteplay.blackjack.storage.BlackjackLedgerStore
import org.finiteplay.blackjack.storage.BlackjackRoundStore
import org.finiteplay.blackjack.storage.BlackjackSettingsStore
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * `EXECUTION_PLAN.md` B3's gate on a real emulator: fixed-seed rounds played through the real
 * screen, each settlement's bankroll change asserted to the chip, and the buttons on screen always
 * the engine's legal actions. The shoe and seed come through the view model's constructor seams, so
 * no release code path knows about them.
 */
class TableTest {
    @get:Rule
    val composeRule = createComposeRule()

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

    private fun shoeOf(vararg first: String): List<Card> {
        val filler = Card.CANONICAL_DECK
        return first.map(::card) + List(SHOE_SIZE - first.size) { filler[it % filler.size] }
    }

    private lateinit var viewModel: BlackjackViewModel
    private lateinit var dir: File

    private fun newViewModel(shoes: Array<out List<Card>>): BlackjackViewModel {
        var next = 0
        val bySeed = shoes.withIndex().associate { (i, shoe) -> (i + 1).toLong() to shoe }
        return BlackjackViewModel(
            roundStore = BlackjackRoundStore(dir),
            ledgerStore = BlackjackLedgerStore(dir),
            settingsStore = BlackjackSettingsStore(dir),
            seedSource = { (++next).toLong() },
            shoeOverride = { seed -> bySeed.getValue(seed) },
        )
    }

    /** Shows the table over fresh stores, optionally preloaded, with animations skipped so the reveal is quick. */
    private fun showTable(
        vararg shoes: List<Card>,
        ledger: BlackjackLedger? = null,
        landscape: Boolean = false,
        darkTheme: Boolean = true,
        leftHanded: Boolean = false,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        dir = File(context.cacheDir, "table-test-${System.nanoTime()}").also { it.mkdirs() }
        runBlocking {
            BlackjackSettingsStore(dir).setAnimationsEnabled(false)
            if (leftHanded) BlackjackSettingsStore(dir).setHandedness(org.finiteplay.core.ui.layout.Handedness.LEFT)
            if (ledger != null) BlackjackLedgerStore(dir).save(ledger)
        }
        viewModel = newViewModel(shoes)
        composeRule.setContent {
            val base = androidx.compose.ui.platform.LocalConfiguration.current
            val configuration = android.content.res.Configuration(base).apply {
                orientation = if (landscape) android.content.res.Configuration.ORIENTATION_LANDSCAPE else android.content.res.Configuration.ORIENTATION_PORTRAIT
            }
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides configuration) {
                FinitePlayTheme(darkTheme = darkTheme) { GameScreen(viewModel) }
            }
        }
        composeRule.waitUntil(5_000) { !viewModel.isLoading }
    }

    private fun tap(tag: String) {
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitForIdle()
    }

    private fun present(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun waitForResults() = composeRule.waitUntil(8_000) { present("summary_net") }

    @Test
    fun anIdleTableOffersTheBetStepperAndDealAndNothingElse() {
        showTable(shoeOf("9C", "TD", "8H", "7S"))
        composeRule.onNodeWithTag("action_deal").assertIsDisplayed()
        composeRule.onNodeWithTag("action_bet_up").assertIsEnabled()
        composeRule.onNodeWithTag("action_bet_down").assertIsNotEnabled()
        for (hidden in listOf("action_hit", "action_stand", "action_double", "action_split", "action_insure")) {
            assertEquals("$hidden is not offered between rounds", false, present(hidden))
        }
    }

    @Test
    fun theBetSteppedAboveTheMinimumIsTheBetDealt() {
        showTable(shoeOf("TC", "TD", "8H", "8S"))
        tap("action_bet_up")
        tap("action_bet_up")
        assertEquals(30, viewModel.selectedBet)
        tap("action_deal")
        assertEquals(30, viewModel.session!!.state.bet)
    }

    @Test
    fun theButtonsOnScreenAreExactlyTheLegalActions() {
        showTable(shoeOf("9C", "TD", "8H", "7S"))
        tap("action_deal")
        // 9 + 8 against a ten: Hit, Stand and Double are legal; Split and Insure are not.
        for (shown in listOf("action_hit", "action_stand", "action_double")) assertEquals(shown, true, present(shown))
        for (hidden in listOf("action_split", "action_insure", "action_decline")) assertEquals(hidden, false, present(hidden))
    }

    @Test
    fun aPushReturnsTheStakeAndSaysEven() {
        showTable(shoeOf("TC", "TD", "8H", "8S"))
        tap("action_deal")
        tap("action_stand")
        waitForResults()
        assertEquals(Chips.STARTING_BANKROLL, viewModel.bankroll)
        assertEquals(HandOutcome.PUSH, viewModel.session!!.state.settlement!!.hands[0].outcome)
        composeRule.onNodeWithTag("hand_0_result").assertIsDisplayed()
    }

    @Test
    fun aHitThenStandWinsAndPaysTheBet() {
        // 9 + 8 + 3 = 20 against a dealer 10 + 7 = 17.
        showTable(shoeOf("9C", "TD", "8H", "7S", "3S"))
        tap("action_bet_up")
        tap("action_deal")
        tap("action_hit")
        tap("action_stand")
        waitForResults()
        assertEquals(Chips.STARTING_BANKROLL + 20, viewModel.bankroll)
    }

    @Test
    fun aDoubleTakesOneCardAndSettlesOnTheDoubledStake() {
        // 5 + 6 doubled takes a 10 for 21 against a dealer 10 + 6 that draws a 10 and busts.
        showTable(shoeOf("5C", "TD", "6H", "6S", "TS", "TH"))
        tap("action_deal")
        tap("action_double")
        waitForResults()
        assertEquals(20, viewModel.session!!.state.hands[0].bet)
        assertEquals(Chips.STARTING_BANKROLL + 20, viewModel.bankroll)
    }

    @Test
    fun aSplitPlaysEachHandAndOffersAResplit() {
        showTable(shoeOf("8C", "6D", "8H", "TS", "8S", "9S", "TC", "2S", "3S", "4S"))
        tap("action_deal")
        tap("action_split")
        // The first hand took an 8: a pair again, so Split is still offered; two hands are on the table.
        assertEquals(true, present("action_split"))
        assertEquals(true, present("hand_1"))
        assertEquals(true, present("active_hand_marker"))
    }

    @Test
    fun insuranceIsOfferedAloneOnAnAceAndPaysTwoToOneOnABlackjack() {
        showTable(shoeOf("9C", "AD", "8H", "KS"))
        tap("action_deal")
        composeRule.onNodeWithTag("action_insure").assertIsDisplayed()
        composeRule.onNodeWithTag("action_decline").assertIsDisplayed()
        assertEquals(false, present("action_hit"))
        tap("action_insure")
        waitForResults()
        // The hand loses its stake and the insurance pays 2:1 on half of it: even.
        assertEquals(Chips.STARTING_BANKROLL, viewModel.bankroll)
        assertEquals(true, present("summary_insurance"))
    }

    @Test
    fun aDealerBlackjackIsFoundAtThePeekBeforeAnyDecision() {
        showTable(shoeOf("9C", "KD", "8H", "AS"))
        tap("action_deal")
        waitForResults()
        assertEquals(Chips.STARTING_BANKROLL - 10, viewModel.bankroll)
        assertEquals(false, present("action_hit"))
    }

    @Test
    fun aLossLowersTheBetToWhatTheBankrollCoversAndBelowTheMinimumOffersAReset() {
        showTable(
            shoeOf("TC", "TD", "6H", "9S", "KS"),
            ledger = BlackjackLedger(bankroll = 15, selectedBet = 10),
        )
        tap("action_deal")
        tap("action_hit")
        waitForResults()
        assertEquals(5, viewModel.bankroll)
        // Below the minimum: Deal is replaced by Reset, and the offer opens once.
        composeRule.waitUntil(5_000) { present("reset_offer") }
        tap("reset_offer_confirm")
        composeRule.waitUntil(5_000) { viewModel.bankroll == Chips.STARTING_BANKROLL }
        assertEquals(Chips.MIN_BET, viewModel.selectedBet)
        assertEquals(1, viewModel.ledger.statistics.resets)
    }

    @Test
    fun aRoundInProgressIsRestoredMidDecisionByAFreshViewModel() {
        val shoe = shoeOf("9C", "TD", "8H", "7S", "3S")
        showTable(shoe)
        tap("action_deal")
        tap("action_hit")
        val before = viewModel.session!!

        // The process dies and starts again: a new view model over the same stores restores the round.
        val restored = newViewModel(arrayOf(shoe))
        composeRule.waitUntil(5_000) { !restored.isLoading }
        assertEquals(before, restored.session)
        assertEquals(listOf(org.finiteplay.blackjack.rules.Decision.HIT), restored.session!!.log)
    }

    @Test
    fun landscapePlaysARoundWithTheSameButtons() {
        showTable(shoeOf("9C", "TD", "8H", "7S", "3S"), landscape = true)
        tap("action_deal")
        for (shown in listOf("action_hit", "action_stand", "action_double")) assertEquals(shown, true, present(shown))
        tap("action_stand")
        waitForResults()
        // 17 against 17.
        assertEquals(Chips.STARTING_BANKROLL, viewModel.bankroll)
    }

    @Test
    fun theLightThemeAndLeftHandedLayoutBothPlay() {
        showTable(shoeOf("TC", "TD", "8H", "8S"), darkTheme = false, leftHanded = true)
        tap("action_deal")
        tap("action_stand")
        waitForResults()
        composeRule.onNodeWithTag("summary_net").assertIsDisplayed()
    }

    @Test
    fun theDealersDrawsAreRevealedBeforeAnyResult() {
        // Dealer 5 + 6 draws 3, 2, 3: five cards in all, so the reveal has several steps.
        showTable(shoeOf("TC", "5D", "8H", "6S", "3S", "2S", "3H"))
        tap("action_deal")
        tap("action_stand")
        // The settlement is already computed and paid, but nothing about it is shown yet.
        assertEquals(false, present("summary_net"))
        waitForResults()
        assertEquals(5, viewModel.session!!.state.dealer.size)
    }

    @Test
    fun statisticsSettingsAndHelpOpenAndCloseOnBack() {
        showTable(shoeOf("TC", "TD", "8H", "8S"))
        tap("action_deal")
        tap("action_stand")
        waitForResults()

        for ((open, panel) in listOf("statistics_button" to "statistics_screen", "help_button" to "help_screen", "action_settings" to "settings_screen")) {
            tap(open)
            composeRule.onNodeWithTag(panel).assertIsDisplayed()
            androidx.test.espresso.Espresso.pressBack()
            composeRule.waitForIdle()
            assertEquals("$panel closes on Back", false, present(panel))
        }
        assertEquals(1, viewModel.ledger.statistics.handsPlayed)
        assertEquals(1, viewModel.ledger.statistics.handsPushed)
    }
}
