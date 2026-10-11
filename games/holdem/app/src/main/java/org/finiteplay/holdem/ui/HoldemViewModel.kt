package org.finiteplay.holdem.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.finiteplay.cards.SplitMix64
import org.finiteplay.core.storage.SYSTEM_LANGUAGE
import org.finiteplay.core.ui.layout.Handedness
import org.finiteplay.core.ui.layout.RestReminderController
import org.finiteplay.core.ui.layout.RestReminderInterval
import org.finiteplay.core.ui.sound.SoundEffect
import org.finiteplay.core.ui.theme.ThemeMode
import org.finiteplay.holdem.opponents.OpponentPolicy
import org.finiteplay.holdem.opponents.ProfilePolicy
import org.finiteplay.holdem.opponents.drawOpponents
import org.finiteplay.holdem.opponents.hint
import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.HoldemSession
import org.finiteplay.holdem.rules.LegalActions
import org.finiteplay.holdem.rules.Phase
import org.finiteplay.holdem.rules.act
import org.finiteplay.holdem.rules.leave
import org.finiteplay.holdem.rules.legalActions
import org.finiteplay.holdem.rules.nextHand
import org.finiteplay.holdem.rules.seatView
import org.finiteplay.holdem.rules.startTournament
import org.finiteplay.holdem.storage.HoldemGameStorage
import org.finiteplay.holdem.storage.HoldemSettings
import org.finiteplay.holdem.storage.HoldemSettingsStore
import org.finiteplay.holdem.storage.HoldemStatistics
import org.finiteplay.holdem.storage.RestorePlan
import org.finiteplay.holdem.storage.StoredTournament
import org.finiteplay.holdem.storage.settleHand
import org.finiteplay.holdem.storage.settleTournament
import java.security.SecureRandom

/** What the table's chrome reads: the settings store's values, or these defaults when none is attached. */
data class TableSettings(
    val animationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val handedness: Handedness = Handedness.RIGHT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val restReminderInterval: RestReminderInterval = RestReminderInterval.DEFAULT,
    val languageTag: String = SYSTEM_LANGUAGE,
) {
    fun toStore() = HoldemSettings(animationsEnabled, handedness, soundEnabled, languageTag, themeMode, restReminderInterval)
}

private fun HoldemSettings.toTable() = TableSettings(animationsEnabled, soundEnabled, handedness, themeMode, restReminderInterval, languageTag)

/** What Hint says: the suggested action and the two figures the notice states (`UI_SPEC.md` "Hint Presentation"). */
data class HintShown(val suggested: Action, val equity: Double, val potOdds: Double, val nothingToCall: Boolean)

/** The dialog when the tournament is over for the player: where they finished, and the two figures it states. */
data class TournamentEnd(val place: Int, val hands: Int, val largestPot: Int)

/** An announcement with a number, so the same words said twice are still said twice. */
data class Announced(val id: Int, val what: Announcement)

/** The decision's salt: from the hand's seed, the seat and the action's index, never the seed itself (`DESIGN.md` "The policy"). */
internal fun decisionSalt(handSeed: Long, seat: Int, actionIndex: Int): Long {
    fun mix(x: Long) = SplitMix64(x).nextULong().toLong()
    return mix(mix(mix(handSeed) + seat) + actionIndex)
}

/**
 * Owns the tournament in progress, in memory only until persistence arrives (`EXECUTION_PLAN.md` H4).
 *
 * Two things shape it:
 *
 * - **The session is ahead of the screen, never behind.** [session] holds every action as soon as it is
 *   taken; [table] is what has been *shown*, stepped forward by [TablePresenter]'s steps with their
 *   pauses. An opponent's decision is made and committed at once and shown after its pause, and a street
 *   the engine has already dealt is not on the table until its step. Nothing on [table] can be a card or
 *   an action the session does not hold.
 * - **A seam for what comes later.** [seedSource] and [policyFor] are constructor parameters, so a
 *   persistence layer or a test supplies seeds and scripted opponents without a release code path
 *   knowing about either.
 *
 * There is no undo.
 */
class HoldemViewModel(
    /** Drawn at New Tournament from a source the player can neither predict nor repeat (`RULES.md` "The Deal"). */
    private val seedSource: () -> Long = SecureRandom()::nextLong,
    /** The policy for an opponent's seat, 1 to 5; null plays each seat's drawn opponent (`drawOpponents`). */
    private val policyFor: ((seat: Int) -> OpponentPolicy)? = null,
    private val roster: OpponentRoster = DrawnRoster,
    initialSettings: TableSettings = TableSettings(),
    /** The two stores in their write order; null keeps the tournament in memory, as the tests do. */
    private val storage: HoldemGameStorage? = null,
    private val settingsStore: HoldemSettingsStore? = null,
) : ViewModel() {

    var settings by mutableStateOf(initialSettings)
        private set

    /** Every action taken so far, the player's and the opponents', as the engine holds it. */
    var session: HoldemSession by mutableStateOf(startTournament(0L))
        private set

    /** What the player is shown: always at or behind [session]. */
    var table: TableModel by mutableStateOf(TablePresenter.dealt(session.state))
        private set

    /** Seats 1 to 5, indexed from 0. */
    var profiles: List<OpponentProfile> by mutableStateOf(emptyList())
        private set

    var sizing: Sizing? by mutableStateOf(null)
        private set

    /** True while steps are being shown or an opponent is thinking; the table is not touchable. */
    var busy by mutableStateOf(false)
        private set

    var announcement: Announced? by mutableStateOf(null)
        private set
    private var announcementCount = 0

    var endDialog: TournamentEnd? by mutableStateOf(null)
        private set

    var leaveConfirmVisible by mutableStateOf(false)
        private set

    /** True until the saved tournament has been read; nothing is touchable while it is. */
    var isLoading by mutableStateOf(storage != null)
        private set

    /** A saved hand could not be read and was dealt again from its start (`UI_SPEC.md` "Screens and States"). */
    var recoveryNoticeVisible by mutableStateOf(false)
        private set

    var hintShown: HintShown? by mutableStateOf(null)
        private set

    /** The tournament as it stood when the current hand was dealt, and every statistic. */
    private var stored = StoredTournament()
    var statistics by mutableStateOf(HoldemStatistics())
        private set

    private var opponentPolicies: List<OpponentPolicy> = emptyList()

    private var largestPot = 0

    /** Where cues go; the screen sets it to its sound player. */
    var soundSink: (SoundEffect) -> Unit = {}

    private val pacing: Pacing get() = Pacing(settings.animationsEnabled)

    /** What the action bar offers: exactly the engine's legal actions on the player's turn, nothing otherwise. */
    val legal: LegalActions?
        get() = if (table.stage == Stage.YOUR_TURN) legalActions(session.state) else null

    private var playJob: Job? = null

    init {
        if (settingsStore != null) {
            viewModelScope.launch { settingsStore.settings.collect { settings = it.toTable() } }
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (storage == null) {
                begin()
            } else {
                restoreOrBegin(storage)
            }
            isLoading = false
        }
    }

    private fun policyOf(seat: Int): OpponentPolicy = policyFor?.invoke(seat) ?: opponentPolicies[seat - 1]

    private suspend fun restoreOrBegin(storage: HoldemGameStorage) {
        val plan = try {
            storage.restore()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        stored = plan?.stored ?: StoredTournament()
        statistics = stored.statistics
        when (plan) {
            is RestorePlan.ResumeHand -> resume(plan, storage)
            is RestorePlan.NewTournament -> {
                recoveryNoticeVisible = plan.tournamentRecovered
                begin()
            }
            null -> begin()
        }
    }

    private suspend fun resume(plan: RestorePlan.ResumeHand, storage: HoldemGameStorage) {
        val resumed = plan.session
        val tournament = resumed.state.tournament
        session = resumed
        profiles = roster.profiles(tournament.seed)
        opponentPolicies = drawOpponents(tournament.seed).map { ProfilePolicy(it.profile) }
        recoveryNoticeVisible = plan.handRecovered
        largestPot = 0
        if (!plan.saved) storage.newHand()
        commit(resumed, saveOnly = !plan.saved)
        val start = org.finiteplay.holdem.rules.replayHand(tournament, emptyList())!!.state
        table = TablePresenter.restored(start, resumed.log)
        play { advance() }
    }

    /**
     * The state [next] reached is on disk before any of it is shown: a hand still in play is saved with
     * its log, and a hand that is over is settled — the tournament and statistics in one write, then the
     * hand save cleared. A write that fails is not retried and does not stop play: there is no undo
     * to protect once the state is in memory, and a failed disk is not the player's problem to wait on.
     */
    private suspend fun commit(next: HoldemSession, saveOnly: Boolean = false) {
        val state = next.state
        try {
            if (state.phase == Phase.BETTING) {
                storage?.saveHand(next)
            } else if (!saveOnly) {
                stored = storage?.settleHand(stored, state) ?: settleHand(stored, state)
                statistics = stored.statistics
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (state.phase != Phase.BETTING && !saveOnly) {
                stored = settleHand(stored, state)
                statistics = stored.statistics
            }
        }
    }

    // ---- the tournament --------------------------------------------------------------------

    /** New Tournament: a new seed, a fresh table, hand 1 dealt. */
    fun newTournament() {
        playJob?.cancel()
        playJob = null
        endDialog = null
        leaveConfirmVisible = false
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) { begin() }
    }

    private suspend fun begin() {
        val seed = seedSource()
        session = startTournament(seed)
        profiles = roster.profiles(seed)
        opponentPolicies = drawOpponents(seed).map { ProfilePolicy(it.profile) }
        largestPot = 0
        sizing = null
        hintShown = null
        stored = stored.started(seed)
        try {
            storage?.beginTournament(stored)
            storage?.newHand()
            storage?.saveHand(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Played in memory; see commit().
        }
        table = TablePresenter.dealt(session.state)
        soundSink(SoundEffect.SHUFFLE)
        soundSink(SoundEffect.DEAL)
        play { advance() }
    }

    // ---- the player ------------------------------------------------------------------------

    /** Applies the player's action if it is the player's turn and the engine allows it. */
    fun act(action: Action) {
        if (busy || table.stage != Stage.YOUR_TURN) return
        val next = session.act(0, action) ?: return
        session = next
        sizing = null
        hintShown = null
        play {
            commit(next)
            present(TablePresenter.afterAction(table, next.state))
            advance()
        }
    }

    /** Hint, once per press (`UI_SPEC.md` "Hint Presentation"): shows the suggestion, or hides it if already showing. */
    fun requestHint() {
        if (busy || table.stage != Stage.YOUR_TURN) return
        if (hintShown != null) {
            hintShown = null
            return
        }
        val state = session.state
        val result = hint(state.seatView(0))
        val legal = legalActions(state)
        hintShown = HintShown(result.suggested, result.equity, result.potOdds, nothingToCall = legal?.canCheck == true)
        when (val suggested = result.suggested) {
            is Action.Bet -> sizing = sizing?.withAmount(suggested.amount)
            is Action.Raise -> sizing = sizing?.withAmount(suggested.total)
            else -> Unit
        }
        soundSink(SoundEffect.HINT)
        stored = stored.copy(statistics = stored.statistics.hintUsed())
        statistics = stored.statistics
        viewModelScope.launch { runCatching { storage?.saveStatistics(stored, stored.statistics) } }
    }

    /** Reset Statistics: every figure cleared, the tournament in progress untouched (`UI_SPEC.md` "Statistics"). */
    fun resetStatistics() {
        stored = stored.copy(statistics = HoldemStatistics())
        statistics = stored.statistics
        viewModelScope.launch { runCatching { storage?.saveStatistics(stored, stored.statistics) } }
    }

    fun dismissRecoveryNotice() {
        recoveryNoticeVisible = false
    }

    fun stepSizing(direction: Int) {
        sizing = sizing?.stepped(direction)
    }

    /** The slider: any whole amount between the smallest legal bet or raise and all in. */
    fun setSizingAmount(value: Int) {
        sizing = sizing?.withAmount(value)
    }

    fun setSizingPreset(preset: Preset) {
        sizing = sizing?.preset(preset)
    }

    /** Next hand, pressed before the pause is over: deals at once. */
    fun nextHand() {
        if (table.stage != Stage.HAND_OVER) return
        play {
            dealNext()
            advance()
        }
    }

    fun requestLeave() {
        if (table.stage == Stage.TOURNAMENT_OVER) return
        leaveConfirmVisible = true
    }

    fun dismissLeave() {
        leaveConfirmVisible = false
    }

    /** Leaving forfeits at once: the place is the one a knockout now would take (`RULES.md` "Leaving"). */
    fun confirmLeave() {
        leaveConfirmVisible = false
        val left = session.leave() ?: return
        playJob?.cancel()
        playJob = null
        busy = false
        session = left
        sizing = null
        hintShown = null
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                stored = storage?.leave(stored, left.state) ?: settleTournament(stored, left.state)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stored = settleTournament(stored, left.state)
            }
            statistics = stored.statistics
            table = TablePresenter.left(table, left.state)
            showEnd()
        }
    }

    fun dismissEndDialog() {
        endDialog = null
    }

    // ---- presentation ----------------------------------------------------------------------

    private fun play(block: suspend () -> Unit) {
        playJob?.cancel()
        busy = true
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                if (playJob === coroutineContext[Job]) busy = false
            }
        }
        playJob = job
        job.start()
    }

    private suspend fun present(steps: List<Step>) {
        for (step in steps) {
            val wait = pacing.ms(step.pause)
            if (wait > 0) delay(wait)
            table = step.model
            step.cues.forEach(soundSink)
            step.announce?.let { announcement = Announced(++announcementCount, it) }
            if (step.model.stage == Stage.HAND_OVER || step.model.stage == Stage.TOURNAMENT_OVER) handEnded()
        }
    }

    private fun handEnded() {
        val state = session.state
        largestPot = maxOf(largestPot, state.result?.payouts?.get(0) ?: 0)
        if (table.stage == Stage.TOURNAMENT_OVER) showEnd()
    }

    private fun showEnd() {
        val state = session.state
        endDialog = TournamentEnd(place = state.places[0] ?: return, hands = state.handNumber, largestPot = largestPot)
    }

    /** Plays opponents' turns until it is the player's, or the hand is over. */
    private suspend fun advance() {
        while (true) {
            val state = session.state
            when {
                // A finished hand waits for the player: the result stays up for as long as they want to look at
                // it, and Next hand deals (`UI_SPEC.md` "Showdown and Results").
                state.phase == Phase.TOURNAMENT_OVER || state.playerOut || state.phase == Phase.HAND_OVER -> return
                state.toAct == 0 -> {
                    sizing = legalActions(state)?.let { Sizing.of(state, it) }
                    table = table.copy(actor = 0, thinking = false, stage = Stage.YOUR_TURN)
                    return
                }
                else -> opponentActs()
            }
        }
    }

    private suspend fun opponentActs() {
        val state = session.state
        val seat = state.toAct
        table = table.copy(actor = seat, thinking = true, stage = Stage.OPPONENT_TURN)
        val view = state.seatView(seat)
        val legal = checkNotNull(view.legal)
        val decided = policyOf(seat).decide(view, decisionSalt(state.tournament.handSeed, seat, state.history.size))
        val action = if (legal.allows(decided)) decided else if (legal.canCheck) Action.Check else Action.Fold
        val next = checkNotNull(session.act(seat, action))
        session = next
        commit(next)
        delay(pacing.thinkMs(Pacing.isLarge(view, action)))
        present(TablePresenter.afterAction(table, next.state))
    }

    private suspend fun dealNext() {
        session = checkNotNull(session.nextHand()) { "the hand is not over" }
        sizing = null
        hintShown = null
        try {
            storage?.newHand()
            storage?.saveHand(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Played in memory; see commit().
        }
        table = TablePresenter.dealt(session.state)
        soundSink(SoundEffect.SHUFFLE)
        soundSink(SoundEffect.DEAL)
    }

    // ---- settings --------------------------------------------------------------------------

    private fun write(block: suspend HoldemSettingsStore.() -> Unit) {
        settingsStore?.let { store -> viewModelScope.launch { runCatching { store.block() } } }
    }

    fun setAnimationsEnabled(value: Boolean) {
        settings = settings.copy(animationsEnabled = value)
        write { setAnimationsEnabled(value) }
    }

    fun setSoundEnabled(value: Boolean) {
        settings = settings.copy(soundEnabled = value)
        write { setSoundEnabled(value) }
    }

    fun setHandedness(value: Handedness) {
        settings = settings.copy(handedness = value)
        write { setHandedness(value) }
    }

    fun setThemeMode(value: ThemeMode) {
        settings = settings.copy(themeMode = value)
        write { setThemeMode(value) }
    }

    fun setLanguageTag(value: String) {
        settings = settings.copy(languageTag = value)
        write { setLanguageTag(value) }
    }

    // ---- rest reminder (`docs/PLATFORM.md` "Rest Reminders") ----------------------------------

    private val restReminder = RestReminderController(viewModelScope, intervalMinutes = { settings.restReminderInterval.minutes })

    val isForeground: Boolean get() = restReminder.isForeground
    val showRestReminderDialog: Boolean get() = restReminder.showReminderDialog
    val restBreakRemainingSeconds: Int? get() = restReminder.breakRemainingSeconds
    val restReminderElapsedSeconds: Int get() = restReminder.elapsedSeconds

    fun setRestReminderInterval(value: RestReminderInterval) {
        settings = settings.copy(restReminderInterval = value)
        write { setRestReminderInterval(value) }
        restReminder.intervalChanged(value.minutes)
    }

    fun setForeground(foreground: Boolean) = restReminder.setForeground(foreground)
    fun startRestBreak() = restReminder.startBreak()
    fun dismissRestReminder() = restReminder.dismissReminder()
    fun cancelRestBreak() = restReminder.cancelBreak()
}
