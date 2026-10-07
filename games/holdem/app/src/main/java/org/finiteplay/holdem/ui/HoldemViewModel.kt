package org.finiteplay.holdem.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.finiteplay.cards.SplitMix64
import org.finiteplay.core.ui.layout.Handedness
import org.finiteplay.core.ui.layout.RestReminderController
import org.finiteplay.core.ui.layout.RestReminderInterval
import org.finiteplay.core.ui.sound.SoundEffect
import org.finiteplay.core.ui.theme.ThemeMode
import org.finiteplay.holdem.opponents.OpponentPolicy
import org.finiteplay.holdem.opponents.PlaceholderPolicy
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
import java.security.SecureRandom

/** What the table's chrome reads until the settings screen and its store arrive (`EXECUTION_PLAN.md` H5). */
data class TableSettings(
    val animationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val handedness: Handedness = Handedness.RIGHT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val restReminderInterval: RestReminderInterval = RestReminderInterval.DEFAULT,
)

/** The dialog when the tournament is over for the player: where they finished, and the two figures it states. */
data class TournamentEnd(val place: Int, val hands: Int, val largestPot: Int)

/** An announcement with a number, so the same words said twice are still said twice. */
data class Announced(val id: Int, val what: Announcement)

/** The decision's salt: from the hand's seed, the seat and the action's index, never the seed itself (`DESIGN.md` "The policy"). */
internal fun decisionSalt(handSeed: Long, seat: Int, actionIndex: Int): Long {
    val perSeat = SplitMix64(handSeed + seat).nextULong().toLong()
    return SplitMix64(perSeat + actionIndex).nextULong().toLong()
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
    /** The policy for an opponent's seat, 1 to 5. */
    private val policyFor: (seat: Int) -> OpponentPolicy = { PlaceholderPolicy },
    private val roster: OpponentRoster = SeededRoster,
    initialSettings: TableSettings = TableSettings(),
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

    private var largestPot = 0

    /** Where cues go; the screen sets it to its sound player. */
    var soundSink: (SoundEffect) -> Unit = {}

    private val pacing: Pacing get() = Pacing(settings.animationsEnabled)

    /** What the action bar offers: exactly the engine's legal actions on the player's turn, nothing otherwise. */
    val legal: LegalActions?
        get() = if (table.stage == Stage.YOUR_TURN) legalActions(session.state) else null

    private var playJob: Job? = null

    init {
        begin()
    }

    // ---- the tournament --------------------------------------------------------------------

    /** New Tournament: a new seed, a fresh table, hand 1 dealt. */
    fun newTournament() {
        playJob?.cancel()
        playJob = null
        endDialog = null
        leaveConfirmVisible = false
        begin()
    }

    private fun begin() {
        val seed = seedSource()
        session = startTournament(seed)
        profiles = roster.profiles(seed)
        largestPot = 0
        sizing = null
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
        play {
            present(TablePresenter.afterAction(table, next.state))
            advance()
        }
    }

    fun stepSizing(direction: Int) {
        sizing = sizing?.stepped(direction)
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
        table = TablePresenter.left(table, left.state)
        showEnd()
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

    /** Plays opponents' turns until it is the player's, or the hand is over (and the next one dealt after its pause). */
    private suspend fun advance() {
        while (true) {
            val state = session.state
            when {
                state.phase == Phase.TOURNAMENT_OVER || state.playerOut -> return
                state.phase == Phase.HAND_OVER -> {
                    delay(pacing.nextHandMs)
                    dealNext()
                }
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
        val decided = policyFor(seat).decide(view, decisionSalt(state.tournament.handSeed, seat, state.history.size))
        val action = if (legal.allows(decided)) decided else if (legal.canCheck) Action.Check else Action.Fold
        val next = checkNotNull(session.act(seat, action))
        session = next
        delay(pacing.thinkMs(Pacing.isLarge(view, action)))
        present(TablePresenter.afterAction(table, next.state))
    }

    private fun dealNext() {
        session = checkNotNull(session.nextHand()) { "the hand is not over" }
        sizing = null
        table = TablePresenter.dealt(session.state)
        soundSink(SoundEffect.SHUFFLE)
        soundSink(SoundEffect.DEAL)
    }

    // ---- settings --------------------------------------------------------------------------

    fun setAnimationsEnabled(value: Boolean) {
        settings = settings.copy(animationsEnabled = value)
    }

    fun setSoundEnabled(value: Boolean) {
        settings = settings.copy(soundEnabled = value)
    }

    fun setHandedness(value: Handedness) {
        settings = settings.copy(handedness = value)
    }

    fun setThemeMode(value: ThemeMode) {
        settings = settings.copy(themeMode = value)
    }

    // ---- rest reminder (`docs/PLATFORM.md` "Rest Reminders") ----------------------------------

    private val restReminder = RestReminderController(viewModelScope, intervalMinutes = { settings.restReminderInterval.minutes })

    val isForeground: Boolean get() = restReminder.isForeground
    val showRestReminderDialog: Boolean get() = restReminder.showReminderDialog
    val restBreakRemainingSeconds: Int? get() = restReminder.breakRemainingSeconds
    val restReminderElapsedSeconds: Int get() = restReminder.elapsedSeconds

    fun setRestReminderInterval(value: RestReminderInterval) {
        settings = settings.copy(restReminderInterval = value)
        restReminder.intervalChanged(value.minutes)
    }

    fun setForeground(foreground: Boolean) = restReminder.setForeground(foreground)
    fun startRestBreak() = restReminder.startBreak()
    fun dismissRestReminder() = restReminder.dismissReminder()
    fun cancelRestBreak() = restReminder.cancelBreak()
}
