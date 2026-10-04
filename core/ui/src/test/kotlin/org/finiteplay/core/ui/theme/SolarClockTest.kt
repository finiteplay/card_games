package org.finiteplay.core.ui.theme

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The astronomy is checked against facts that do not depend on the formula used to get them:
 * at a pole on a solstice the sun's altitude *is* the Earth's axial tilt all day long, and at
 * the equator at equinox noon it *is* directly overhead. If [SolarClock] agrees with those it
 * has the geometry right, in a way that asserting numbers copied out of its own output could
 * never show.
 */
class SolarClockTest {

    private val northPole = Coordinates(90.0, 0.0)
    private val southPole = Coordinates(-90.0, 0.0)
    private val nullIsland = Coordinates(0.0, 0.0)

    /** The Earth's axial tilt, which is what the sun's altitude at a pole reduces to. */
    private val axialTilt = 23.44

    @Test
    fun `at the north pole on the june solstice the sun sits at the axial tilt`() {
        val altitude = SolarClock.sunAltitudeDegrees(Instant.parse("2025-06-21T12:00:00Z"), northPole)
        assertEquals(axialTilt, altitude, 0.2)
    }

    @Test
    fun `at the north pole the sun does not rise or set through the solstice day`() {
        // A pole has no diurnal motion in altitude: the sun circles the horizon at a constant
        // height. Any hour of the day must give the same answer, which is the invariant an
        // events-based sunset calculation cannot express at all.
        val hours = listOf("00", "06", "12", "18").map {
            SolarClock.sunAltitudeDegrees(Instant.parse("2025-06-21T$it:00:00Z"), northPole)
        }
        for (altitude in hours) assertEquals(hours.first(), altitude, 0.05)
    }

    @Test
    fun `the poles mirror each other`() {
        val instant = Instant.parse("2025-06-21T12:00:00Z")
        assertEquals(
            -SolarClock.sunAltitudeDegrees(instant, northPole),
            SolarClock.sunAltitudeDegrees(instant, southPole),
            0.05,
        )
    }

    @Test
    fun `at the north pole on the december solstice the sun sits as far below`() {
        val altitude = SolarClock.sunAltitudeDegrees(Instant.parse("2025-12-21T12:00:00Z"), northPole)
        assertEquals(-axialTilt, altitude, 0.2)
    }

    @Test
    fun `at the equator at equinox noon the sun is overhead`() {
        // Solar noon at longitude 0 in late March runs a few minutes ahead of 12:00 UTC
        // (the equation of time), so this is close to, not exactly, the maximum.
        val altitude = SolarClock.sunAltitudeDegrees(Instant.parse("2025-03-20T12:07:00Z"), nullIsland)
        assertTrue("expected near-overhead, got $altitude", altitude > 89.0)
    }

    @Test
    fun `polar day never gets dark and polar night never gets light`() {
        val svalbard = requireNotNull(ZoneCoordinates.forZone("Arctic/Longyearbyen"))

        // Local midnight in June, at 78 degrees north: the midnight sun.
        assertFalse(SolarClock.isDark(Instant.parse("2025-06-21T22:00:00Z"), svalbard))
        // Local noon in December, same place: the sun does not come up at all.
        assertTrue(SolarClock.isDark(Instant.parse("2025-12-21T11:00:00Z"), svalbard))
    }

    @Test
    fun `dusk is civil twilight rather than geometric sunset`() {
        // Kyiv, 21 June. Sunset is about 21:12 local (18:12 UTC) and civil twilight ends about
        // 22:03 local (19:03 UTC). Half an hour after the sun goes down the sky is still bright
        // and the screen should still be light - that gap is the whole point of the choice.
        val kyiv = requireNotNull(ZoneCoordinates.forZone("Europe/Kiev"))
        assertFalse("just after sunset is still light", SolarClock.isDark(Instant.parse("2025-06-21T18:30:00Z"), kyiv))
        assertTrue("after civil twilight it is dark", SolarClock.isDark(Instant.parse("2025-06-21T19:30:00Z"), kyiv))
    }

    /**
     * Civil dusk at four spread-out places on both solstices, computed independently by the
     * `astral` Python library rather than by anything here. Five minutes either side is
     * headroom rather than slack: every one of these crossings agrees to within three, and the
     * margin is for the low-precision formulae drifting on a date far from these, not for
     * covering a discrepancy today.
     */
    @Test
    fun `crossings agree with an independent almanac`() {
        val duskUtc = listOf(
            Triple("Europe/Kyiv", "2025-06-21T18:59:00Z", "2025-12-21T14:35:00Z"),
            Triple("Australia/Sydney", "2025-06-21T07:21:00Z", "2025-12-21T09:35:00Z"),
            Triple("Pacific/Honolulu", "2025-06-21T05:41:00Z", "2025-12-21T04:19:00Z"),
            Triple("America/New_York", "2025-06-21T01:04:00Z", "2025-12-21T22:03:00Z"),
        )
        for ((zone, june, december) in duskUtc) {
            val at = requireNotNull(ZoneCoordinates.forZone(zone)) { zone }
            for (dusk in listOf(june, december)) {
                val crossing = Instant.parse(dusk)
                assertFalse(
                    "$zone should still be light 5 min before $dusk",
                    SolarClock.isDark(crossing.minusSeconds(300), at),
                )
                assertTrue(
                    "$zone should be dark 5 min after $dusk",
                    SolarClock.isDark(crossing.plusSeconds(300), at),
                )
            }
        }
    }

    @Test
    fun `the southern hemisphere has its seasons the other way round`() {
        val sydney = requireNotNull(ZoneCoordinates.forZone("Australia/Sydney"))
        // 20:00 local (09:00 UTC) is light in December and dark in June - the reverse of Kyiv.
        assertFalse(SolarClock.isDark(Instant.parse("2025-12-21T09:00:00Z"), sydney))
        assertTrue(SolarClock.isDark(Instant.parse("2025-06-21T09:00:00Z"), sydney))
    }
}
