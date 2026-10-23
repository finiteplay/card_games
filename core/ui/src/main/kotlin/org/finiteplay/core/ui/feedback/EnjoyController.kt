package org.finiteplay.core.ui.feedback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.finiteplay.core.session.shouldAskEnjoy
import org.finiteplay.core.storage.EngagementStore

/**
 * The "enjoying this game?" question, from the game's side (`docs/PLATFORM.md` "Feedback and rating"). A game
 * tells it three things: the app started ([appStarted], by [EnjoyHost]), a game or tournament ended well
 * ([goodResult]), and the player is moving on from the result ([moveOn]); it shows [EnjoyFlow] while [visible].
 *
 * Whether the question is due is `core/session`'s [shouldAskEnjoy]. It is decided at the good result, with that
 * result counted, and the question waits for [moveOn] so it never opens over a win dialog or a result banner.
 * The question is put at most once per [moveOn], and is marked asked when it opens: a player who leaves it
 * unanswered is asked again after 30 days, one who answers is not asked again.
 */
class EnjoyController(
    private val store: EngagementStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Whether the question is on screen. */
    var visible by mutableStateOf(false)
        private set

    private var due = false
    private var deciding: Job? = null

    /** The app came to the foreground; the store counts it as a new session unless it is the same sitting. */
    fun appStarted() {
        scope.launch { store.recordStart(clock()) }
    }

    /** A solitaire was won, a Blackjack round won, or a poker tournament finished first or second. */
    fun goodResult() {
        deciding = scope.launch {
            val engagement = store.recordGoodResult()
            due = shouldAskEnjoy(engagement, clock())
        }
    }

    /** The player moves on from the result (a new game, the end dialog closed): the question opens now if it is due. */
    fun moveOn() {
        scope.launch {
            deciding?.join()
            if (!due || visible) return@launch
            due = false
            visible = true
            store.markAsked(clock())
        }
    }

    /** The question ended; [answered] is false when the player just dismissed it. */
    fun finished(answered: Boolean) {
        visible = false
        if (answered) scope.launch { store.markAnswered() }
    }
}

/** Counts the app's starts for [controller] and shows its question while it is due. Put it once, at the top of a game's screen. */
@Composable
fun EnjoyHost(controller: EnjoyController?) {
    if (controller == null) return
    LifecycleEventEffect(Lifecycle.Event.ON_START) { controller.appStarted() }
    if (controller.visible) EnjoyFlow(onFinished = controller::finished)
}
