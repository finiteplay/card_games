package org.finiteplay.core.ui.sound

/** Short, game-neutral sound effects (`docs/PLATFORM.md` "Sound"). */
enum class SoundEffect {
    MOVE,
    AUTOMATIC_MOVE,
    UNDO,
    SHUFFLE,
    HINT,
    DEAL,
    CHIP,
    CARD_DRAW,
    ACTION_CONFIRM,
    WAGER_COMMIT,
    CARD_SPLIT,
    SEQUENCE_COMPLETE,
    BUST,
    NATURAL_WIN,
    ROUND_LOSS,
    GAME_OVER,
    INVALID,
    WIN,
}

fun interface SoundPlayer {
    fun play(effect: SoundEffect)
}

object NoOpSoundPlayer : SoundPlayer {
    override fun play(effect: SoundEffect) = Unit
}

/**
 * Wraps [delegate], only forwarding a play request when both [isEnabled] (the
 * persisted sound setting) and [isForeground] (the app's foreground state) are true
 * at the moment of the call. This is what keeps a sound from playing while
 * backgrounded or while the player has muted it in Settings — independent of, and in
 * addition to, whatever audio-focus/volume behavior [delegate] itself implements.
 * Pure Kotlin (no Android imports), so it is exercised by a plain JVM test even
 * though the real [delegate] it wraps in production is not.
 */
class GatedSoundPlayer(
    private val delegate: SoundPlayer,
    private val isEnabled: () -> Boolean,
    private val isForeground: () -> Boolean,
) : SoundPlayer {
    override fun play(effect: SoundEffect) {
        if (isEnabled() && isForeground()) delegate.play(effect)
    }
}
