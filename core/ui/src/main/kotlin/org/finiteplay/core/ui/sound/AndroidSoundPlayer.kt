package org.finiteplay.core.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import org.finiteplay.core.ui.R

/**
 * Plays short game sound effects via [SoundPool], one sample per [SoundEffect],
 * loaded once at construction. Requests transient, ducking-tolerant audio focus
 * immediately before each play and abandons it right after — appropriate for the
 * brief incidental sounds here, which should momentarily duck whatever
 * else is playing rather than interrupt it, and never need to hold focus between
 * sounds. [SoundPool] plays on the attributes' matching audio stream, so it already
 * respects that stream's system volume without any extra work here.
 *
 * The bundled recordings and generated cues are documented, licensed, and checksummed in
 * `docs/SOUND_ASSETS.md`.
 */
class AndroidSoundPlayer(context: Context) : SoundPlayer {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(attributes)
        .build()

    private val soundIds: Map<SoundEffect, Int> = mapOf(
        SoundEffect.MOVE to soundPool.load(appContext, R.raw.sound_move, 1),
        SoundEffect.AUTOMATIC_MOVE to soundPool.load(appContext, R.raw.sound_automove, 1),
        SoundEffect.UNDO to soundPool.load(appContext, R.raw.sound_undo, 1),
        SoundEffect.SHUFFLE to soundPool.load(appContext, R.raw.sound_shuffle, 1),
        SoundEffect.HINT to soundPool.load(appContext, R.raw.sound_hint, 1),
        SoundEffect.DEAL to soundPool.load(appContext, R.raw.sound_deal, 1),
        SoundEffect.CHIP to soundPool.load(appContext, R.raw.sound_chip, 1),
        SoundEffect.CARD_DRAW to soundPool.load(appContext, R.raw.sound_card_draw, 1),
        SoundEffect.ACTION_CONFIRM to soundPool.load(appContext, R.raw.sound_action_confirm, 1),
        SoundEffect.WAGER_COMMIT to soundPool.load(appContext, R.raw.sound_wager_commit, 1),
        SoundEffect.CARD_SPLIT to soundPool.load(appContext, R.raw.sound_card_split, 1),
        SoundEffect.SEQUENCE_COMPLETE to soundPool.load(appContext, R.raw.sound_sequence_complete, 1),
        SoundEffect.BUST to soundPool.load(appContext, R.raw.sound_bust, 1),
        SoundEffect.ROUND_WIN to soundPool.load(appContext, R.raw.sound_round_win, 1),
        SoundEffect.NATURAL_WIN to soundPool.load(appContext, R.raw.sound_natural_win, 1),
        SoundEffect.VOICE_ANNOUNCEMENT to soundPool.load(appContext, R.raw.sound_voice_announcement, 1),
        SoundEffect.ROUND_LOSS to soundPool.load(appContext, R.raw.sound_round_loss, 1),
        SoundEffect.GAME_OVER to soundPool.load(appContext, R.raw.sound_bankroll_lost, 1),
        SoundEffect.INVALID to soundPool.load(appContext, R.raw.sound_invalid, 1),
        SoundEffect.WIN to soundPool.load(appContext, R.raw.sound_win, 1),
    )

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener {}
        .build()

    override fun play(effect: SoundEffect) {
        val soundId = soundIds[effect] ?: return
        val granted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) return
        soundPool.play(soundId, 1f, 1f, /* priority = */ 1, /* loop = */ 0, /* rate = */ 1f)
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    /** Releases the underlying [SoundPool] and any held audio focus. Call once, when the screen leaves composition. */
    fun release() {
        soundPool.release()
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
}
