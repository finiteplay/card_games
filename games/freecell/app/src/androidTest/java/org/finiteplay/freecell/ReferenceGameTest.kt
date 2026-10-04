package org.finiteplay.freecell

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.finiteplay.freecell.layout.canonicalStateHash
import org.finiteplay.freecell.session.REFERENCE_GAME_HASH
import org.finiteplay.freecell.session.playReferenceGame
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Re-asserts the RF reference game on-device (`EXECUTION_PLAN.md` "RF — Rules Freeze"), proving
 * the JVM and Android runtimes reproduce identical gameplay output for the same seed and move log
 * — the gameplay analogue of `core:cards`' own deck-shuffle reference vector test.
 */
@RunWith(AndroidJUnit4::class)
class ReferenceGameTest {
    @Test
    fun replayingTheReferenceGameReproducesTheFrozenHashOnAndroid() {
        val session = playReferenceGame()

        assertEquals(REFERENCE_GAME_HASH, canonicalStateHash(session.state))
    }
}
