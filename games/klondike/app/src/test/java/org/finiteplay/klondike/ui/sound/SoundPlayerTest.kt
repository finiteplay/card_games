package org.finiteplay.core.ui.sound

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `docs/games/klondike/TODO.md` "Sound": "Prevent sound while backgrounded" and the sound setting.
 * Pure Kotlin — no Android framework needed to prove the gating logic itself.
 */
class SoundPlayerTest {

    private class RecordingSoundPlayer : SoundPlayer {
        val played = mutableListOf<SoundEffect>()
        override fun play(effect: SoundEffect) {
            played += effect
        }
    }

    @Test
    fun `forwards to the delegate when enabled and foreground`() {
        val delegate = RecordingSoundPlayer()
        val player = GatedSoundPlayer(delegate, isEnabled = { true }, isForeground = { true })

        player.play(SoundEffect.MOVE)

        assertEquals(listOf(SoundEffect.MOVE), delegate.played)
    }

    @Test
    fun `does not forward while backgrounded, even if the setting is on`() {
        val delegate = RecordingSoundPlayer()
        val player = GatedSoundPlayer(delegate, isEnabled = { true }, isForeground = { false })

        player.play(SoundEffect.WIN)

        assertEquals(emptyList<SoundEffect>(), delegate.played)
    }

    @Test
    fun `does not forward when the sound setting is off, even in the foreground`() {
        val delegate = RecordingSoundPlayer()
        val player = GatedSoundPlayer(delegate, isEnabled = { false }, isForeground = { true })

        player.play(SoundEffect.INVALID)

        assertEquals(emptyList<SoundEffect>(), delegate.played)
    }

    @Test
    fun `re-evaluates enabled and foreground on every call, not just at construction`() {
        var enabled = false
        var foreground = false
        val delegate = RecordingSoundPlayer()
        val player = GatedSoundPlayer(delegate, isEnabled = { enabled }, isForeground = { foreground })

        player.play(SoundEffect.AUTOMATIC_MOVE)
        assertEquals(emptyList<SoundEffect>(), delegate.played)

        enabled = true
        foreground = true
        player.play(SoundEffect.AUTOMATIC_MOVE)

        assertEquals(listOf(SoundEffect.AUTOMATIC_MOVE), delegate.played)
    }
}
