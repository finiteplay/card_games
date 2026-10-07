package org.finiteplay.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LevelUpTest {
    private val levels = listOf("easy", "medium", "hard")

    @Test
    fun `the tenth win at the level being played earns an offer for the next level`() {
        assertEquals(LevelUpOffer("easy", "medium", 10), levelUpFor(levels, won = "easy", winsAtLevel = 10, playing = "easy"))
    }

    @Test
    fun `no offer before the tenth win, or after it`() {
        assertNull(levelUpFor(levels, "easy", 9, "easy"))
        assertNull(levelUpFor(levels, "easy", 11, "easy"))
    }

    @Test
    fun `no offer to someone who has already moved on or plays a mix`() {
        assertNull(levelUpFor(levels, "easy", 10, playing = "medium"))
        assertNull(levelUpFor(levels, "easy", 10, playing = null))
    }

    @Test
    fun `no offer from the last level, which has nothing above it`() {
        assertNull(levelUpFor(levels, "hard", 10, "hard"))
        assertNull(nextLevel(levels, "hard"))
    }

    @Test
    fun `the next level is the one listed after`() {
        assertEquals("medium", nextLevel(levels, "easy"))
        assertEquals("hard", nextLevel(levels, "medium"))
    }
}
