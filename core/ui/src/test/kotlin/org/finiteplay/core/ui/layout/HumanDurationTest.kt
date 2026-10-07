package org.finiteplay.core.ui.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class HumanDurationTest {
    @Test
    fun `under a minute is seconds alone`() {
        assertEquals(DurationParts(0, 0, 45), durationParts(45))
        assertEquals(DurationParts(0, 0, 0), durationParts(0))
    }

    @Test
    fun `from a minute up it is minutes and the seconds left over`() {
        assertEquals(DurationParts(0, 30, 0), durationParts(30 * 60))
        assertEquals(DurationParts(0, 4, 32), durationParts(4 * 60 + 32))
    }

    @Test
    fun `from an hour up it is hours and minutes, and the seconds are dropped`() {
        assertEquals(DurationParts(1, 30, 0), durationParts(90 * 60))
        assertEquals(DurationParts(1, 0, 0), durationParts(3_600 + 40))
        assertEquals(DurationParts(2, 5, 0), durationParts(2 * 3_600 + 5 * 60 + 59))
    }

    @Test
    fun `a negative duration is none`() {
        assertEquals(DurationParts(0, 0, 0), durationParts(-5))
    }
}
