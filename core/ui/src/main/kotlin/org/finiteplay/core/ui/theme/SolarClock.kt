package org.finiteplay.core.ui.theme

import java.time.Instant
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where the sun is, so the AUTO theme can flip at dusk rather than at a fixed hour
 * (`docs/PLATFORM.md` "Themes").
 *
 * This computes the sun's **altitude** at an instant and compares it to a horizon, rather
 * than computing sunset and sunrise times and asking whether now falls between them. The two
 * agree everywhere the second one is defined, and the first stays defined where the second is
 * not: above the Arctic and Antarctic circles there are days with no sunset to compute, and an
 * events-based version needs a special case for each of polar day and polar night. An altitude
 * simply stays high all day, or stays low all day, and the comparison below is still the right
 * answer. Nothing here has a branch for latitude.
 *
 * The formulae are the low-precision solar position from the Astronomical Almanac, good to
 * about a minute of time either side of the true crossing. A theme flip does not need better,
 * and the coordinates feeding it ([ZoneCoordinates]) are the timezone's city rather than the
 * player's own position, which is by far the larger error.
 */
internal object SolarClock {

    /**
     * "Dusk" is civil twilight: the sun six degrees below the horizon. Geometric sunset — the
     * sun exactly on the horizon — is too early to want a dark screen, because the sky is still
     * bright for a good half hour after it. Civil twilight is the point where colour vision
     * starts to go and artificial light takes over, which is the moment this setting is named
     * after and the moment a bright screen starts to hurt.
     */
    const val CIVIL_TWILIGHT_DEGREES = -6.0

    /** Whether the sun is below [horizon] at [instant], as seen from [at]. */
    fun isDark(instant: Instant, at: Coordinates, horizon: Double = CIVIL_TWILIGHT_DEGREES): Boolean =
        sunAltitudeDegrees(instant, at) < horizon

    /**
     * The sun's altitude above the horizon in degrees — negative when it has set — as seen from
     * [at] at [instant]. Positive north and east, as [Coordinates] documents.
     *
     * Internal rather than private so the tests can check it against published almanac values
     * directly, instead of only ever seeing the boolean [isDark] reduces it to.
     */
    internal fun sunAltitudeDegrees(instant: Instant, at: Coordinates): Double {
        // Days elapsed since the J2000.0 epoch (2000-01-01T12:00:00Z), the origin every term
        // below is stated against. Fractional, so it carries the time of day as well.
        val n = instant.toEpochMilli() / MILLIS_PER_DAY - EPOCH_DAY_J2000

        // The sun's position along the ecliptic: where it would be if the Earth's orbit were a
        // circle, plus the correction for the fact that it is an ellipse.
        val meanAnomaly = Math.toRadians((357.529 + 0.98560028 * n).mod(360.0))
        val meanLongitude = (280.459 + 0.98564736 * n).mod(360.0)
        val eclipticLongitude = Math.toRadians(
            (meanLongitude + 1.915 * sin(meanAnomaly) + 0.020 * sin(2 * meanAnomaly)).mod(360.0),
        )

        // Tilt of the Earth's axis, which is what turns that ecliptic position into a position
        // in the equatorial frame the observer stands in — and what gives us seasons at all.
        val obliquity = Math.toRadians(23.439 - 0.00000036 * n)
        val declination = asin(sin(obliquity) * sin(eclipticLongitude))
        val rightAscension = atan2(cos(obliquity) * sin(eclipticLongitude), cos(eclipticLongitude))

        // How far the Earth has turned: sidereal time at Greenwich, carried east to the
        // observer by their longitude. The hour angle is the sun's offset from due south.
        val greenwichSiderealHours = (18.697374558 + 24.06570982441908 * n).mod(24.0)
        val localSiderealDegrees = greenwichSiderealHours * DEGREES_PER_HOUR + at.longitude
        val hourAngle = Math.toRadians(localSiderealDegrees - Math.toDegrees(rightAscension))

        val latitude = Math.toRadians(at.latitude)
        val sinAltitude =
            sin(latitude) * sin(declination) + cos(latitude) * cos(declination) * cos(hourAngle)
        return Math.toDegrees(asin(sinAltitude.coerceIn(-1.0, 1.0)))
    }

    private const val MILLIS_PER_DAY = 86_400_000.0

    /** 2000-01-01T12:00:00Z as a count of days since the Unix epoch. */
    private const val EPOCH_DAY_J2000 = 10_957.5

    private const val DEGREES_PER_HOUR = 15.0
}
