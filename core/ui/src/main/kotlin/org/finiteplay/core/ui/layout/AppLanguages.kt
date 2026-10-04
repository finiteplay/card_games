package org.finiteplay.core.ui.layout

import org.finiteplay.core.storage.SYSTEM_LANGUAGE
import java.util.Locale

/**
 * The languages every app in this family ships (`docs/PLATFORM.md` "Localization"), offered in Settings.
 *
 * Each is named in **its own language** rather than translated into the current one, so
 * a player who has accidentally selected a language they cannot read can still find
 * their way back — the usual convention for a language picker, and the reason these
 * names are not string resources. [SYSTEM_LANGUAGE] leads the list and *is* translated,
 * since a player choosing it can by definition read the current language.
 *
 * Ordered by tag rather than by display name: sorting endonyms alphabetically would mean
 * sorting across scripts, which has no single sensible answer.
 */
object AppLanguages {
    /** BCP-47 tags matching the `values-*` resource directories shared by `core:ui`. */
    val TAGS: List<String> = listOf(
        "en", "ar", "bg", "cs", "da", "de", "el", "es", "fi", "fr", "hi", "hr", "hu",
        "in", "it", "iw", "ja", "ko", "nb", "nl", "pl", "pt-BR", "ro", "sk", "sv",
        "th", "tr", "uk", "vi", "zh-CN", "zh-TW",
    )

    /** Every option, system default first. */
    val OPTIONS: List<String> = listOf(SYSTEM_LANGUAGE) + TAGS

    /**
     * The language's own name for itself, capitalised in its own locale. Falls back to
     * the tag when a device has no display name for it, which is better than an empty
     * row.
     */
    fun displayName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        val name = locale.getDisplayName(locale)
        return if (name.isBlank()) tag else name.replaceFirstChar { it.titlecase(locale) }
    }
}
