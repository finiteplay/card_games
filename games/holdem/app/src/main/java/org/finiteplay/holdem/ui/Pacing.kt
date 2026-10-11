package org.finiteplay.holdem.ui

import org.finiteplay.cards.Card
import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.HistoryEntry
import org.finiteplay.holdem.rules.SeatView

/** The pause before a [Step] is shown (`UI_SPEC.md` "Motion"). Presentation only: it never changes what happens. */
enum class Pause { NONE, GATHER, STREET, REVEAL, POT_FIRST, POT_NEXT, RESULT }

/** Something said to accessibility services as it happens (`UI_SPEC.md` "Accessibility"); the screen words it. */
sealed interface Announcement {
    data class Acted(val seat: Int, val entry: HistoryEntry) : Announcement
    data class BoardDealt(val street: org.finiteplay.holdem.rules.Street, val newCards: List<Card>) : Announcement
    data class Showed(val seat: Int, val name: HandName) : Announcement
    data class Won(val seats: List<Int>, val amount: Int) : Announcement
}

/**
 * `UI_SPEC.md` "Motion", as milliseconds. Skip Animations shortens every pause without collapsing
 * any step into another: each action, street, hand and pot is still shown on its own.
 */
class Pacing(private val animate: Boolean) {
    /** An opponent's pause before acting; longer for a raise, an all-in, or a call of over a third of its stack. */
    fun thinkMs(large: Boolean): Long = if (!animate) 250 else if (large) 1_400 else 800

    fun ms(pause: Pause): Long = when (pause) {
        Pause.NONE -> 0
        Pause.GATHER -> if (animate) 300 else 0
        Pause.STREET -> if (animate) 500 else 200
        Pause.REVEAL -> if (animate) 400 else 150
        Pause.POT_FIRST -> if (animate) 600 else 250
        // A pot's slide (600) and the 300 before the next one starts.
        Pause.POT_NEXT -> if (animate) 900 else 250
        Pause.RESULT -> if (animate) 700 else 250
    }

    companion object {
        /** `UI_SPEC.md`: a raise, an all-in, or a call of more than a third of the seat's stack is a large decision. */
        fun isLarge(view: SeatView, action: Action): Boolean {
            val legal = view.legal ?: return false
            return when (action) {
                is Action.Bet, is Action.Raise, Action.AllIn -> true
                Action.Call -> (legal.callAmount ?: 0) * 3 > legal.stack
                Action.Fold, Action.Check -> false
            }
        }
    }
}
