package org.finiteplay.blackjack.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.finiteplay.blackjack.rules.BasicStrategy
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
import org.finiteplay.core.session.RestReminderWindow
import org.finiteplay.core.session.foregroundEntered
import org.finiteplay.core.session.foregroundExited
import org.finiteplay.core.session.foregroundMsAsOf
import org.finiteplay.core.session.isRestReminderDue
import org.finiteplay.core.ui.layout.Handedness
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

    var isForeground = true
        private set

    /**
     * What basic strategy says to do right now, once the player asks (`DESIGN.md` "Hint"); null
     * otherwise. Always one of the offered decisions, never a promise: the dealer's hidden cards
     * decide the round. Cleared by any change to the round.
     */
    var hint by mutableStateOf<Decision?>(null)
        private set

    /** Whether Hint can be asked: a decision is on offer and nothing is mid-save. */
    val canHint: Boolean get() = !busy && (tableState == TableState.PLAYING || tableState == TableState.INSURANCE)

    /** Shows the recommendation, or hides it if it is already showing. Not counted anywhere: there is no statistic for it. */
    fun requestHint() {
        if (!canHint) return
        val current = session ?: return
        hint = if (hint != null) null else BasicStrategy.recommend(current.state, ledger.bankroll)
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
        viewModelScope.launch { runCatching { ledgerStore.save(updated) } }
    }

    // ---- the round -------------------------------------------------------------------------

    /** Deal, or Next round: starts a round at the selected bet from a fresh shoe. */
    fun deal() {
        if (busy || isLoading || !betting) return
        val seed = seedSource()
        val round = runCatching { startRound(seed, selectedBet, ledger.bankroll, shoeOverride?.invoke(seed) ?: shoeFor(seed)) }
            .getOrNull() ?: return
        gameId = UUID.randomUUID().toString()
        publish(round)
    }

    fun decide(decision: Decision) {
        if (busy || isLoading) return
        val current = session ?: return
        val next = current.decide(decision, ledger.bankroll) ?: return
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
                hint = null
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
                hint = null
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

    private var restReminderWindow: RestReminderWindow? = null
    private var restReminderTickerJob: Job? = null
    private var restBreakJob: Job? = null

    /** True while the reminder's offer is showing. */
    var showRestReminderDialog by mutableStateOf(false)
        private set

    /** Seconds left in an accepted break, or null when none is running. */
    var restBreakRemainingSeconds by mutableStateOf<Int?>(null)
        private set

    /** The current window's running total, for Settings' read-only display. */
    var restReminderElapsedSeconds by mutableIntStateOf(0)
        private set

    /** Applies immediately and persists; restarts the window so a new choice takes effect right away. */
    fun setRestReminderInterval(value: RestReminderInterval) {
        update({ it.copy(restReminderInterval = value) }) { it.setRestReminderInterval(value) }
        restReminderWindow = RestReminderWindow.start(System.currentTimeMillis())
        refreshRestReminderElapsed()
        syncRestReminderTicker()
    }

    fun setForeground(foreground: Boolean) {
        isForeground = foreground
        val now = System.currentTimeMillis()
        restReminderWindow = if (foreground) {
            (restReminderWindow ?: RestReminderWindow.start(now)).foregroundEntered(now)
        } else {
            restReminderWindow?.foregroundExited(now)
        }
        refreshRestReminderElapsed()
        syncRestReminderTicker()
    }

    /**
     * Accepts the offer: a countdown for half the configured interval, during which no usage is
     * tracked. The board is blocked meanwhile, but a round is untouched — it is saved before every
     * card is shown, and nothing about a round is timed.
     */
    fun startRestBreak() {
        showRestReminderDialog = false
        val minutes = settings.restReminderInterval.minutes ?: return
        restBreakRemainingSeconds = (minutes * 60) / 2
        syncRestReminderTicker()
        restBreakJob = viewModelScope.launch {
            while ((restBreakRemainingSeconds ?: 0) > 0) {
                delay(1_000)
                restBreakRemainingSeconds = (restBreakRemainingSeconds ?: 1) - 1
            }
            endRestBreak()
        }
    }

    /** Declines the offer; the next is a full interval away, since the window already rolled over. */
    fun dismissRestReminder() {
        showRestReminderDialog = false
    }

    /** Ends an accepted break early. */
    fun cancelRestBreak() {
        restBreakJob?.cancel()
        endRestBreak()
    }

    private fun endRestBreak() {
        restBreakJob = null
        restBreakRemainingSeconds = null
        // The break is excluded from usage entirely, so a fresh window starts rather than resuming the old one.
        restReminderWindow = RestReminderWindow.start(System.currentTimeMillis())
        refreshRestReminderElapsed()
        syncRestReminderTicker()
    }

    private fun syncRestReminderTicker() {
        val shouldRun = isForeground && restBreakRemainingSeconds == null
        if (shouldRun) {
            if (restReminderTickerJob?.isActive == true) return
            restReminderTickerJob = viewModelScope.launch {
                while (isActive) {
                    delay(1_000)
                    refreshRestReminderElapsed()
                    checkRestReminder()
                }
            }
        } else {
            restReminderTickerJob?.cancel()
            restReminderTickerJob = null
        }
    }

    private fun refreshRestReminderElapsed() {
        val window = restReminderWindow
        restReminderElapsedSeconds = if (window == null) 0 else (window.foregroundMsAsOf(System.currentTimeMillis()) / 1_000L).toInt()
    }

    private fun checkRestReminder() {
        val minutes = settings.restReminderInterval.minutes ?: return
        val window = restReminderWindow ?: return
        val now = System.currentTimeMillis()
        if (!isRestReminderDue(window, now, minutes * 60_000L)) return
        showRestReminderDialog = true
        restReminderWindow = RestReminderWindow.start(now)
        refreshRestReminderElapsed()
    }
}
