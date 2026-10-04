package org.finiteplay.core.ui.theme

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneCoordinatesTest {

    @Test
    fun `iso 6709 parses the four-digit form`() {
        // Europe/Andorra, +4230+00131: 42 degrees 30 minutes north, 1 degree 31 minutes east.
        val parsed = ZoneCoordinates.parseIso6709("+4230+00131")
        assertEquals(42.5, parsed.latitude, 1e-9)
        assertEquals(1.0 + 31 / 60.0, parsed.longitude, 1e-9)
    }

    @Test
    fun `iso 6709 parses the six-digit form`() {
        // Pacific/Tongatapu, -210800-1751200: seconds are present in both fields.
        val parsed = ZoneCoordinates.parseIso6709("-210800-1751200")
        assertEquals(-21.0 - 8 / 60.0, parsed.latitude, 1e-9)
        assertEquals(-175.0 - 12 / 60.0, parsed.longitude, 1e-9)
    }

    @Test
    fun `south and west come out negative`() {
        val parsed = ZoneCoordinates.parseIso6709("-3352+15113") // Australia/Sydney
        assertTrue(parsed.latitude < 0)
        assertTrue(parsed.longitude > 0)
        assertEquals(-33.0 - 52 / 60.0, parsed.latitude, 1e-9)
    }

    @Test
    fun `known zones resolve to roughly the right place`() {
        val kyiv = requireNotNull(ZoneCoordinates.forZone("Europe/Kiev"))
        assertEquals(50.43, kyiv.latitude, 0.1)
        assertEquals(30.52, kyiv.longitude, 0.1)

        val la = requireNotNull(ZoneCoordinates.forZone("America/Los_Angeles"))
        assertEquals(34.05, la.latitude, 0.2)
        assertTrue("Los Angeles is west of Greenwich", la.longitude < 0)
    }

    @Test
    fun `backward-compatible zone names are carried too`() {
        // The reason this uses zone.tab rather than zone1970.tab: a device may report either
        // spelling, and only the older file lists both.
        assertNotNull(ZoneCoordinates.forZone("Europe/Kiev"))
        assertNotNull(ZoneCoordinates.forZone("Asia/Calcutta"))
    }

    @Test
    fun `an unlisted zone resolves to null rather than to somewhere wrong`() {
        assertNull(ZoneCoordinates.forZone("Mars/Olympus_Mons"))
        assertNull(ZoneCoordinates.forZone(""))
    }

    @Test
    fun `every row parses to a point that exists on Earth`() {
        var count = 0
        for (id in ZoneId.getAvailableZoneIds()) {
            val at = ZoneCoordinates.forZone(id) ?: continue
            count++
            assertTrue("$id latitude ${at.latitude}", at.latitude in -90.0..90.0)
            assertTrue("$id longitude ${at.longitude}", at.longitude in -180.0..180.0)
        }
        // The table is worth nothing if it misses the zones devices actually report, so assert
        // it covers the bulk of the JDK's own list rather than merely that it parsed.
        assertTrue("only $count zones matched the JDK's list", count > 300)
    }
}
