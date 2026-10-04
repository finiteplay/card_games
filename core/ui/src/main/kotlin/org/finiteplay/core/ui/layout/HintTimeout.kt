package org.finiteplay.core.ui.layout

/**
 * How long the on-device hint search is allowed to run before giving up, shared across every
 * game's Settings screen (`docs/PLATFORM.md` "Performance") — the search itself, and what happens
 * when it runs long, are each game's own (`HintEngine`, `SolverLimits`); this is only the number a
 * player picks.
 */
enum class HintTimeout(val seconds: Int) {
    ONE(1), TWO(2), THREE(3), FIVE(5), EIGHT(8), TWELVE(12);

    companion object {
        val DEFAULT = FIVE
    }
}
