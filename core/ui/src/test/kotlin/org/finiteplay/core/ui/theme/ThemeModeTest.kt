package org.finiteplay.core.ui.theme

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    private val kyiv = ZoneId.of("Europe/Kiev")

    @Test
    fun `auto follows the sun, not the clock`() {
        // 20:30 local, the same wall-clock time in both halves of the year. In June the sun is
        // still up in Kyiv; in December it set hours ago. A fixed evening boundary calls both
        // of these dark, which is exactly what this setting used to get wrong.
        assertFalse(isDarkOutside(Instant.parse("2025-06-21T17:30:00Z"), kyiv)) // 20:30 EEST
        assertTrue(isDarkOutside(Instant.parse("2025-12-21T18:30:00Z"), kyiv)) // 20:30 EET
    }

    @Test
    fun `auto is dark in the small hours and light at midday, in either season`() {
        for (date in listOf("2025-06-21", "2025-12-21")) {
            assertTrue("$date night", isDarkOutside(Instant.parse("${date}T00:00:00Z"), kyiv))
            assertFalse("$date midday", isDarkOutside(Instant.parse("${date}T10:00:00Z"), kyiv))
        }
    }

    @Test
    fun `a zone with no coordinates falls back to the fixed schedule`() {
        // Etc/GMT+5 is a real ZoneId that zone.tab does not describe, because it names an
        // offset rather than a place. There is nowhere to put the sun, so the old schedule
        // answers: 22:00 in that zone is dark, 12:00 is light.
        val offsetOnly = ZoneId.of("Etc/GMT+5")
        assertTrue(isDarkOutside(Instant.parse("2025-06-22T03:00:00Z"), offsetOnly)) // 22:00 local
        assertFalse(isDarkOutside(Instant.parse("2025-06-21T17:00:00Z"), offsetOnly)) // 12:00 local
    }

    @Test
    fun `the fallback schedule treats evening and night as dark`() {
        for (hour in listOf(19, 21, 23, 0, 3, 6)) {
            assertTrue("hour $hour", isDarkByClock(hour))
        }
    }

    @Test
    fun `the fallback schedule treats daytime as light`() {
        for (hour in listOf(7, 9, 12, 17, 18)) {
            assertFalse("hour $hour", isDarkByClock(hour))
        }
    }

    @Test
    fun `the fallback boundaries belong to the interval they open`() {
        // 18:59 is still light and 19:00 is already dark; 06:59 still dark, 07:00 light.
        assertFalse(isDarkByClock(18))
        assertTrue(isDarkByClock(19))
        assertTrue(isDarkByClock(6))
        assertFalse(isDarkByClock(7))
    }
}
