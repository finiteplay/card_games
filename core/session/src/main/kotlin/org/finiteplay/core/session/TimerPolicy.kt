package org.finiteplay.core.session

/**
 * Whether the elapsed-time timer should be running right now (`docs/PLATFORM.md`
 * "Persistence": never persist a running timer, which is what makes this a policy evaluated on
 * every input rather than a clock the game owns).
 *
 * The four inputs are the whole rule: a game has started, the app is in the foreground, no
 * modal is open over the board, and the game is not already won. [hasPlayerActed] and [isWon]
 * are booleans rather than a game's session type, because the rule cares about nothing else a
 * session might carry — a game passes its own two facts in.
 *
 * The caller re-evaluates this on every input that could change one of the four (a committed
 * action, a foreground/background transition, a modal opening or closing) rather than the rule
 * scheduling anything itself; there is no timer object here to schedule with.
 */
fun shouldRunTimer(
    hasPlayerActed: Boolean,
    isWon: Boolean,
    isAppForeground: Boolean,
    isModalOpen: Boolean,
): Boolean = hasPlayerActed && isAppForeground && !isModalOpen && !isWon
