package org.finiteplay.freecell.session

import org.finiteplay.freecell.layout.canonicalStateHash
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `EXECUTION_PLAN.md` "RF — Rules Freeze": pins real gameplay output for a fixed seed and move
 * log, so an unintended change to rules, automation, or the shuffle shows up here rather than
 * only in behavior nobody happened to exercise by hand.
 */
class ReferenceGameTest {
    @Test
    fun `replaying the reference game reproduces the frozen canonical hash`() {
        val session = playReferenceGame()

        assertEquals(REFERENCE_GAME_HASH, canonicalStateHash(session.state))
    }

    @Test
    fun `the reference game is deterministic across replays`() {
        assertEquals(canonicalStateHash(playReferenceGame().state), canonicalStateHash(playReferenceGame().state))
    }
}
