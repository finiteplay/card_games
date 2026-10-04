package org.finiteplay.klondike.storage

import org.finiteplay.core.session.shouldRunTimer as sharedShouldRunTimer
import org.finiteplay.klondike.session.GameSession

/**
 * Klondike's own two facts, handed to the shared rule (`:core:session`, `docs/PLATFORM.md`
 * "Persistence"). Not tied to any clock, `Handler`, or coroutine — the UI layer calls this on
 * every input that could change one of its four inputs (a committed action,
 * foreground/background transition, a modal opening/closing) rather than re-deriving the rule
 * itself.
 *
 * A game "starts" on [GameSession.hasPlayerActed], which is already true only from the
 * first successful scored player action (draw/recycle included); hints, invalid actions,
 * settings, and setup automation never set it.
 *
 * Restoration needs no separate wait-for-action exception: a restored session's
 * [GameSession.hasPlayerActed] already reflects whether the original game had started.
 */
fun shouldRunTimer(
    session: GameSession,
    isAppForeground: Boolean,
    isModalOpen: Boolean,
): Boolean = sharedShouldRunTimer(session.hasPlayerActed, session.state.isWon, isAppForeground, isModalOpen)
