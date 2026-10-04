package org.finiteplay.core.ui.layout

/**
 * How often a game offers a rest reminder after continuous play, shared across every game's
 * Settings screen (`docs/PLATFORM.md` "Rest Reminders") — the continuity rule and what happens
 * when it fires are each game's own `GameViewModel`; this is only the number a player picks.
 * [NEVER] turns the feature off outright rather than needing a separate toggle alongside it.
 */
enum class RestReminderInterval(val minutes: Int?) {
    NEVER(null), THIRTY(30), FORTY_FIVE(45), SIXTY(60), NINETY(90), ONE_TWENTY(120);

    companion object {
        val DEFAULT = SIXTY
    }
}
