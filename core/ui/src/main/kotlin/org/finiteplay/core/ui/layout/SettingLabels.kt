package org.finiteplay.core.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.finiteplay.core.ui.R
import org.finiteplay.core.ui.theme.ThemeMode

/**
 * The string each [ThemeMode] is named by, so a mode reads identically in every game rather than
 * one calling it "Automatic" and another "Auto".
 */
fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.AUTO -> R.string.theme_auto
}

/** "5 seconds", so a hint timeout reads identically in every game's Settings screen. */
@Composable
fun HintTimeout.label(): String = stringResource(R.string.hint_timeout_seconds_value, seconds)

/** "Never" or "60 minutes", so a rest-reminder interval reads identically in every game's Settings screen. */
@Composable
fun RestReminderInterval.label(): String = minutes?.let { stringResource(R.string.rest_reminder_minutes_value, it) }
    ?: stringResource(R.string.rest_reminder_never)
