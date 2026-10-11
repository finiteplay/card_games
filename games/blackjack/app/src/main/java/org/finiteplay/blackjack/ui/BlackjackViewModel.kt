package org.finiteplay.blackjack.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.finiteplay.blackjack.rules.BasicStrategy
import org.finiteplay.blackjack.rules.BlackjackAdvice
import org.finiteplay.blackjack.rules.BlackjackState
import org.finiteplay.blackjack.rules.HandOutcome
import org.finiteplay.blackjack.rules.handValue
import org.finiteplay.cards.Rank
import org.finiteplay.blackjack.rules.advise
import org.finiteplay.blackjack.rules.BlackjackSession
import org.finiteplay.blackjack.rules.Chips
import org.finiteplay.blackjack.rules.Decision
import org.finiteplay.blackjack.rules.Phase
import org.finiteplay.blackjack.rules.decide
import org.finiteplay.blackjack.rules.legalDecisions
import org.finiteplay.blackjack.rules.replayRound
import org.finiteplay.blackjack.rules.shoeFor
import org.finiteplay.blackjack.rules.startRound
import org.finiteplay.blackjack.storage.BlackjackLedger
import org.finiteplay.blackjack.storage.BlackjackLedgerStore
import org.finiteplay.blackjack.storage.BlackjackRoundStore
import org.finiteplay.blackjack.storage.BlackjackSettings
import org.finiteplay.blackjack.storage.BlackjackSettingsStore
import org.finiteplay.blackjack.storage.RoundLoad
import org.finiteplay.blackjack.storage.SavedRound
import org.finiteplay.core.ui.layout.Handedness
import org.finiteplay.core.ui.layout.RestReminderController
import org.finiteplay.core.ui.layout.RestReminderInterval
import org.finiteplay.core.ui.theme.ThemeMode
import java.security.SecureRandom
import java.util.UUID

/** What the table is showing, which decides the action bar (`docs/games/blackjack/UI_SPEC.md` "Screens and States"). */
enum class TableState { LOADING, IDLE, INSURANCE, PLAYING, SETTLED }

/**
 * Owns the displayed round, the ledger and the settings.
 *
 * Two rules from `DESIGN.md` "Persistence" shape every function that changes the round:
 *
 * - **Written before shown.** A card is displayed only after the save that records it has
 *   completed. [session] is the *displayed* round, and each decision computes its successor, awaits
 *   the save, and only then publishes it; while a save is in flight [busy] holds off further input.
 *   Otherwise a kill between seeing a card and the save landing would restore the round from before
 *   the card — an undo by force-stop.
 * - **Settled exactly once.** Settlement writes the ledger (bankroll, statistics and the round's
 *   seed together), *then* clears the round. A kill between the two leaves a round whose seed
 *   equals the ledger's last settled seed, which [restore] discards without paying it again.
 *
 * There is no undo and no way to abandon a round.
 */
class BlackjackViewModel(
    private val roundStore: BlackjackRoundStore,
    private val ledgerStore: BlackjackLedgerStore,
    private val settingsStore: BlackjackSettingsStore,
    /** Drawn at Deal from a source the player can neither predict nor repeat (`RULES.md` "The Shoe"); tests substitute fixed seeds. */
    private val seedSource: () -> Long = SecureRandom()::nextLong,
    /** Test and debug seam: the shoe a round is dealt from. Null in release, where it is always the seed's own. */
    private val shoeOverride: ((Long) -> List<org.finiteplay.cards.Card>)? = null,
) : ViewModel() {

    var isLoading by mutableStateOf(true)
        private set

    var ledger by mutableStateOf(BlackjackLedger())
        private set

    /** The round as the player sees it; null before the first Deal and after a Reset. */
    var session by mutableStateOf<BlackjackSession?>(null)
        private set

    var settings by mutableStateOf(BlackjackSettings.DEFAULT)
        private set

    /** Set when a saved round could not be read and was voided; the player is owed that notice. */
    var recoveryNoticeVisible by mutableStateOf(false)
        private set

    /** True while a save is in flight; input is ignored, so no second decision races the first. */
    var busy by mutableStateOf(false)
        private set

    /** Set once a settled round leaves the bankroll below the table minimum and the offer has not been answered. */
    var resetOfferVisible by mutableStateOf(false)
        private set

    /**
     * What basic strategy says to do right now, once the player asks (`DESIGN.md` "Hint"); null
     * otherwise. Always one of the offered decisions, never a promise: the dealer's hidden cards
     * decide the round. Cleared by any change to the round.
     */
    var advice by mutableStateOf<BlackjackAdvice?>(null)
        private set

    /** The decision the hint recommends, whose button is highlighted. */
    val hint: Decision? get() = advice?.decision

    /** The look back at the round just settled, when a Hint was used in it; cleared when the next round deals. */
    var review by mutableStateOf<RoundReview?>(null)
        private set

    /** The advice shown at the current decision (kept when it is hidden again), and the last decision taken with one. */
    private var pendingAdvice: BlackjackAdvice? = null
    private var pendingDraw: Pair<Rank, Int>? = null
    private var adviceUse: AdviceUse? = null

    private fun resetAdvice() {
        advice = null
        pendingAdvice = null
        pendingDraw = null
        adviceUse = null
        review = null
    }

    /** Whether Hint can be asked: a decision is on offer and nothing is mid-save. */
    val canHint: Boolean get() = !busy && (tableState == TableState.PLAYING || tableState == TableState.INSURANCE)

    /** Shows the recommendation, or hides it if it is already showing. Not counted anywhere: there is no statistic for it. */
    fun requestHint() {
        if (!canHint) return
        val current = session ?: return
        advice = if (advice != null) null else BasicStrategy.advise(current.state, ledger.bankroll)
        if (advice != null) {
            pendingAdvice = advice
            val st = current.state
            pendingDraw = st.shoe.getOrNull(st.shoePosition)?.let { it.rank to handValue(st.hands[st.activeHand].cards + it).total }
        }
    }

    val bankroll: Int get() = ledger.bankroll
    val selectedBet: Int get() = ledger.selectedBet

    val tableState: TableState
        get() {
            if (isLoading) return TableState.LOADING
            val round = session ?: return TableState.IDLE
            return when (round.state.phase) {
                Phase.INSURANCE -> TableState.INSURANCE
                Phase.PLAYING -> TableState.PLAYING
                Phase.SETTLED -> TableState.SETTLED
            }
        }

    /** What the action bar offers right now: exactly the engine's legal decisions, and nothing outside a live round. */
    val legal: Set<Decision>
        get() = session?.let { legalDecisions(it.state, ledger.bankroll) } ?: emptySet()

    /** Between rounds with a bankroll that cannot cover the table minimum: Reset replaces Deal. */
    val needsReset: Boolean
        get() = (tableState == TableState.IDLE || tableState == TableState.SETTLED) && Chips.needsReset(ledger.bankroll)

    val canStepBetDown: Boolean get() = betting && selectedBet > Chips.MIN_BET
    val canStepBetUp: Boolean get() = betting && selectedBet + Chips.BET_STEP <= Chips.maxBetFor(ledger.bankroll)

    private val betting: Boolean
        get() = !busy && (tableState == TableState.IDLE || tableState == TableState.SETTLED) && !needsReset

    init {
        viewModelScope.launch {
            settings = settingsStore.current()
            restore()
            isLoading = false
        }
        viewModelScope.launch { settingsStore.settings.collect { settings = it } }
    }

    /**
     * Restores before an interactive table is shown (`EXECUTION_PLAN.md` B4a): the ledger first,
     * since a round is replayed against its bankroll and checked against its last settled seed.
     */
    private suspend fun restore() {
        ledger = ledgerStore.load()
        when (val loaded = roundStore.load()) {
            RoundLoad.Missing -> Unit
            RoundLoad.Recovered -> recoveryNoticeVisible = true
            is RoundLoad.Restored -> {
                val saved = loaded.round
                if (saved.seed == ledger.lastSettledSeed) {
                    // Already paid and counted; the kill came between settlement's write and clearing the round.
                    roundStore.clear()
                    return
                }
                val replayed = replayRound(saved.seed, saved.bet, ledger.bankroll, saved.log, shoeOverride?.invoke(saved.seed) ?: shoeFor(saved.seed))
                if (replayed == null || replayed.state.isSettled) {
                    roundStore.clear()
                    recoveryNoticeVisible = true
                } else {
                    session = replayed
                    gameId = saved.gameId
                }
            }
        }
    }

    private var gameId: String = UUID.randomUUID().toString()

    // ---- betting ---------------------------------------------------------------------------

    fun stepBet(direction: Int) {
        if (!betting) return
        val next = Chips.steppedBet(selectedBet, direction, ledger.bankroll)
        if (next == selectedBet) return
        val updated = ledger.copy(selectedBet = next)
        ledger = updated
        // A held button steps many times a second; only the last value needs to reach the disk.
        betSave?.cancel()
        betSave = viewModelScope.launch { runCatching { ledgerStore.save(updated) } }
    }

    private var betSave: Job? = null

    // ---- the round -------------------------------------------------------------------------

    /** Deal, or Next round: starts a round at the selected bet from a fresh shoe. */
    fun deal() {
        if (busy || isLoading || !betting) return
        val seed = seedSource()
        val round = runCatching { startRound(seed, selectedBet, ledger.bankroll, shoeOverride?.invoke(seed) ?: shoeFor(seed)) }
            .getOrNull() ?: return
        gameId = UUID.randomUUID().toString()
        resetAdvice()
        publish(round)
    }

    fun decide(decision: Decision) {
        if (busy || isLoading) return
        val current = session ?: return
        val next = current.decide(decision, ledger.bankroll) ?: return
        pendingAdvice?.let { adviceUse = AdviceUse(it, followed = decision == it.decision, taken = decision, wouldDraw = pendingDraw) }
        pendingAdvice = null
        pendingDraw = null
        publish(next)
    }

    /**
     * Saves [next] and only then shows it. A round that has just settled is not saved as a round at
     * all: its settlement is the ledger write, followed by clearing whatever round was on disk.
     */
    private fun publish(next: BlackjackSession) {
        busy = true
        viewModelScope.launch {
            try {
                val state = next.state
                if (state.isSettled) {
                    val paid = ledger.afterSettlement(state.settlement!!, state.seed)
                    ledgerStore.save(paid)
                    ledger = paid
                    roundStore.clear()
                    resetOfferVisible = Chips.needsReset(paid.bankroll)
                } else {
                    roundStore.save(SavedRound(gameId, state.seed, state.bet, next.log))
                }
                session = next
                advice = null
                if (state.isSettled) review = reviewOf(adviceUse, state)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The save did not land, so the card was never shown and nothing was paid: the
                // displayed round is exactly what is on disk, and the player may simply try again.
            } finally {
                busy = false
            }
        }
    }

    // ---- reset -----------------------------------------------------------------------------

    /** The reset offered when the bankroll cannot cover the table minimum (`RULES.md` "Round Lifecycle"). */
    fun resetBankroll() {
        if (busy || isLoading || !needsReset) return
        busy = true
        viewModelScope.launch {
            try {
                val fresh = ledger.afterReset()
                ledgerStore.save(fresh)
                ledger = fresh
                session = null
                resetAdvice()
                resetOfferVisible = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing changed; the offer stays.
            } finally {
                busy = false
            }
        }
    }

    fun dismissResetOffer() {
        resetOfferVisible = false
    }

    fun dismissRecoveryNotice() {
        recoveryNoticeVisible = false
    }

    // ---- statistics ------------------------------------------------------------------------

    /** Clears the statistics, keeping the bankroll — and the high-water mark restarts from it. */
    fun resetStatistics() {
        if (busy) return
        val cleared = ledger.copy(statistics = org.finiteplay.blackjack.storage.BlackjackStatistics(highWater = ledger.bankroll))
        ledger = cleared
        viewModelScope.launch { runCatching { ledgerStore.save(cleared) } }
    }

    // ---- settings --------------------------------------------------------------------------

    fun setAnimationsEnabled(value: Boolean) = update({ it.copy(animationsEnabled = value) }) { it.setAnimationsEnabled(value) }
    fun setHandedness(value: Handedness) = update({ it.copy(handedness = value) }) { it.setHandedness(value) }
    fun setSoundEnabled(value: Boolean) = update({ it.copy(soundEnabled = value) }) { it.setSoundEnabled(value) }
    fun setLanguageTag(value: String) = update({ it.copy(languageTag = value) }) { it.setLanguageTag(value) }
    fun setThemeMode(value: ThemeMode) = update({ it.copy(themeMode = value) }) { it.setThemeMode(value) }

    private fun update(inMemory: (BlackjackSettings) -> BlackjackSettings, persist: suspend (BlackjackSettingsStore) -> Unit) {
        settings = inMemory(settings)
        viewModelScope.launch { persist(settingsStore) }
    }

    // ---- rest reminder (`docs/PLATFORM.md` "Rest Reminders") ----------------------------------

    private val restReminder = RestReminderController(viewModelScope, intervalMinutes = { settings.restReminderInterval.minutes })

    val isForeground: Boolean get() = restReminder.isForeground

    /** True while the reminder's offer is showing. */
    val showRestReminderDialog: Boolean get() = restReminder.showReminderDialog

    /** Seconds left in an accepted break, or null when none is running. */
    val restBreakRemainingSeconds: Int? get() = restReminder.breakRemainingSeconds

    /** The current window's running total, for Settings' read-only display. */
    val restReminderElapsedSeconds: Int get() = restReminder.elapsedSeconds

    /** Applies immediately and persists; restarts the window so a new choice takes effect right away. */
    fun setRestReminderInterval(value: RestReminderInterval) {
        update({ it.copy(restReminderInterval = value) }) { it.setRestReminderInterval(value) }
        restReminder.intervalChanged(value.minutes)
    }

    fun setForeground(foreground: Boolean) = restReminder.setForeground(foreground)

    /** Accepts the offer; the board is blocked meanwhile, but a round is untouched. */
    fun startRestBreak() = restReminder.startBreak()

    fun dismissRestReminder() = restReminder.dismissReminder()

    fun cancelRestBreak() = restReminder.cancelBreak()
}

/** How a round played after a Hint turned out against what the Hint said (`UI_SPEC.md` "Hint Review"). */
enum class ReviewKind { FOLLOWED_WON, FOLLOWED_LOST, FOLLOWED_PUSH, IGNORED_WON, IGNORED_LOST, IGNORED_PUSH }

/** How the round ended, the way the review tells it. */
sealed interface ReviewDetail {
    data class DealerFinished(val total: Int) : ReviewDetail
    data object DealerBusted : ReviewDetail
    data object DealerBlackjack : ReviewDetail
    data class YouBusted(val total: Int) : ReviewDetail

    /** Hindsight: the card a hit would have drawn, and the total it would have made. */
    data class WouldHaveDrawn(val rank: Rank, val total: Int) : ReviewDetail

    /** The odds a stand had at the moment of the decision, in percent. */
    data class StandChances(val total: Int, val win: Int, val push: Int, val lose: Int) : ReviewDetail
}

/** The look back at a round: [kind], the [advice] it is about, and how the round ended. */
data class RoundReview(val kind: ReviewKind, val advice: BlackjackAdvice, val details: List<ReviewDetail>)

/**
 * The last decision the player took with a Hint on screen: whether they did as it said, what they
 * did, and (hidden at the time) the card a hit would have drawn and the total it would have made.
 */
internal data class AdviceUse(
    val advice: BlackjackAdvice,
    val followed: Boolean,
    val taken: Decision,
    val wouldDraw: Pair<Rank, Int>? = null,
)

/**
 * The review of a settled round, or null with no Hint used. [net] is the bankroll's change over
 * the round, so a win and a loss are told by its sign and a push by zero.
 */
internal fun reviewOf(use: AdviceUse?, state: BlackjackState): RoundReview? {
    use ?: return null
    val settlement = state.settlement ?: return null
    val net = settlement.total
    val kind = when {
        net > 0 -> if (use.followed) ReviewKind.FOLLOWED_WON else ReviewKind.IGNORED_WON
        net < 0 -> if (use.followed) ReviewKind.FOLLOWED_LOST else ReviewKind.IGNORED_LOST
        else -> if (use.followed) ReviewKind.FOLLOWED_PUSH else ReviewKind.IGNORED_PUSH
    }
    val details = ArrayList<ReviewDetail>(3)
    val advised = use.advice.decision
    if (use.taken == Decision.STAND && (advised == Decision.HIT || advised == Decision.DOUBLE) && use.wouldDraw != null) {
        details += ReviewDetail.WouldHaveDrawn(use.wouldDraw.first, use.wouldDraw.second)
    } else if (use.taken == Decision.STAND && use.advice.total <= 21) {
        val o = use.advice.odds
        details += ReviewDetail.StandChances(use.advice.total, Math.round(o.standWin * 100).toInt(), Math.round(o.standPush * 100).toInt(), Math.round(o.standLose * 100).toInt())
    }
    val busted = settlement.hands.withIndex().firstOrNull { it.value.outcome == HandOutcome.BUST }
    busted?.let { details += ReviewDetail.YouBusted(state.hands[it.index].value.total) }
    val allBust = settlement.hands.all { it.outcome == HandOutcome.BUST }
    if (!allBust) {
        when {
            settlement.dealerBlackjack -> details += ReviewDetail.DealerBlackjack
            state.dealerValue.busted -> details += ReviewDetail.DealerBusted
            else -> details += ReviewDetail.DealerFinished(state.dealerValue.total)
        }
    }
    return RoundReview(kind, use.advice, details)
}
