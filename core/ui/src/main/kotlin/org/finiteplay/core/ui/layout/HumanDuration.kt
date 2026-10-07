package org.finiteplay.core.ui.layout

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/** A duration split into the units it is read in; zero parts are not said. */
data class DurationParts(val hours: Int, val minutes: Int, val seconds: Int)

/**
 * [totalSeconds] as the two largest units it needs: hours and minutes from an hour up, minutes and
 * seconds from a minute up, seconds alone below that. The smaller unit is dropped from an hour on
 * (a break of "1 hour 30 minutes" has no use for its seconds), and a zero part is never said.
 */
fun durationParts(totalSeconds: Int): DurationParts {
    val total = totalSeconds.coerceAtLeast(0)
    val hours = total / 3_600
    val minutes = total % 3_600 / 60
    val seconds = total % 60
    return when {
        hours > 0 -> DurationParts(hours, minutes, 0)
        minutes > 0 -> DurationParts(0, minutes, seconds)
        else -> DurationParts(0, 0, seconds)
    }
}

/**
 * [totalSeconds] in words, in [locale] — `30 minutes`, `1 hour 30 minutes`, `4 minutes 32 seconds` —
 * with the units and their plurals the platform's own, so no language needs a string from this app.
 */
fun humanDuration(totalSeconds: Int, locale: Locale): String {
    val parts = durationParts(totalSeconds)
    val measures = buildList {
        if (parts.hours > 0) add(Measure(parts.hours, MeasureUnit.HOUR))
        if (parts.minutes > 0) add(Measure(parts.minutes, MeasureUnit.MINUTE))
        if (parts.seconds > 0 || isEmpty()) add(Measure(parts.seconds, MeasureUnit.SECOND))
    }
    return MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE).formatMeasures(*measures.toTypedArray())
}

/** [humanDuration] in the language the app is showing. */
@Composable
fun humanDuration(totalSeconds: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(totalSeconds, locale) { humanDuration(totalSeconds, locale) }
}
