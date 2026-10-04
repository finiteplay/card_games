package org.finiteplay.core.storage

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The in-app language override (`docs/PLATFORM.md` "Localization"), applied by wrapping the
 * Activity's base context before any view is inflated.
 *
 * Backed by `SharedPreferences` rather than DataStore, uniquely among this app's
 * settings, because `attachBaseContext` runs before a coroutine can suspend and must
 * answer synchronously — a DataStore read there would either block the main thread or
 * arrive after the resources it was meant to configure. [SettingsStore] keeps its own
 * copy as the value the Settings screen displays; this one exists solely to be readable
 * that early, and the two are written together.
 *
 * `AppCompatDelegate.setApplicationLocales` is the sanctioned alternative, but it needs
 * `androidx.appcompat` and an AppCompat-derived activity and theme; this app is
 * Compose-only on a `ComponentActivity`, so adopting it would mean a dependency and a
 * theme migration for one setting. On API 33+ the framework's own per-app language also
 * exists, but relying on it would leave API 26-32 unserved.
 */
/** Language tag meaning "follow the device", as opposed to an explicit override. */
const val SYSTEM_LANGUAGE = ""

object AppLocale {
    private const val PREFS_NAME = "app_locale"
    private const val KEY_LANGUAGE_TAG = "language_tag"

    /** The persisted tag, or [SYSTEM_LANGUAGE] when the device language should be followed. */
    fun currentTag(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LANGUAGE_TAG, SYSTEM_LANGUAGE) ?: SYSTEM_LANGUAGE

    fun setTag(context: Context, languageTag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_LANGUAGE_TAG, languageTag).apply()
    }

    /**
     * [context] reconfigured for the persisted language, or unchanged when following the
     * device. Call from `attachBaseContext`, where it still governs every resource the
     * Activity goes on to resolve.
     */
    fun wrap(context: Context): Context {
        val tag = currentTag(context)
        if (tag == SYSTEM_LANGUAGE) return context
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }
}
