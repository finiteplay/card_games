package org.finiteplay.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.ZoneId

/**
 * How the board's theme is chosen (`docs/PLATFORM.md` "Themes").
 *
 * [SYSTEM] and [AUTO] both mean "decide for me" but answer to different things: SYSTEM
 * follows the device's own light/dark setting, AUTO follows the sun regardless of it —
 * which is what a player wants when the device stays light all day but the game is played
 * in bed.
 */
enum class ThemeMode { LIGHT, DARK, SYSTEM, AUTO }

/**
 * Whether [mode] means dark right now.
 *
 * AUTO reads the clock at composition time rather than scheduling anything: the platform
 * forbids background work, so the theme flips the next time the screen recomposes after
 * dusk — on the next move, in practice — not on a timer at the exact minute.
 */
@Composable
fun ThemeMode.resolveDark(): Boolean = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.AUTO -> isDarkOutside(Instant.now(), ZoneId.systemDefault())
}

/**
 * Whether it is dark outside at [instant] for someone in [zone].
 *
 * The zone is the only thing the app knows about where the player is, and it is enough:
 * [ZoneCoordinates] turns it into the coordinates of the zone's reference city and
 * [SolarClock] works out whether the sun is down there. No location permission, no network
 * call, and it tracks the seasons — the thing a fixed schedule cannot do, and the reason a
 * northern summer evening no longer goes dark at 19:00 while it is still broad daylight.
 *
 * Falls back to [isDarkByClock] for a zone tzdb does not list, which is the behaviour this
 * setting had before it learned about the sun.
 */
internal fun isDarkOutside(instant: Instant, zone: ZoneId): Boolean {
    val coordinates = ZoneCoordinates.forZone(zone.id)
        // atZone rather than LocalTime.ofInstant: the latter is API 31 and minSdk here is 26.
        ?: return isDarkByClock(instant.atZone(zone).hour)
    return SolarClock.isDark(instant, coordinates)
}

/** First hour of the day the fallback treats as dark, and the first it treats as light. */
private const val FALLBACK_DARK_FROM_HOUR = 19
private const val FALLBACK_LIGHT_FROM_HOUR = 7

/**
 * The fallback schedule, for a zone with no coordinates on file. Pure so the boundary
 * behaviour is testable without a clock or a composition.
 */
internal fun isDarkByClock(hour: Int): Boolean =
    hour >= FALLBACK_DARK_FROM_HOUR || hour < FALLBACK_LIGHT_FROM_HOUR
