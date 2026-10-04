package org.finiteplay.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Both schemes fill in the whole surface-container family, not just `surface`.
 *
 * Material draws menus, dialogs and sheets on `surfaceContainer`, which is a *separate*
 * role from `surface`: leaving it unset takes the factory default, and a scheme that
 * overrides `onSurface` but not the container it is drawn on paints its text on a colour
 * from a different palette. That is what made the settings dropdown unreadable — white
 * label text on the default near-white container.
 */
// internal, not private: ContrastTest (same module) asserts WCAG floors against these
// directly rather than duplicating the hex values, which would drift the moment a palette
// is retuned.
internal val AppDarkColors = darkColorScheme(
    primary = Color(0xFFB8D9C5),
    onPrimary = Color(0xFF0C3827),
    primaryContainer = Color(0xFF1E4A36),
    onPrimaryContainer = Color(0xFFD6EFE0),
    secondary = Color(0xFFA9C7E0),
    onSecondary = Color(0xFF0C2A3B),
    background = Color(0xFF101814),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF101814),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF18231D),
    onSurfaceVariant = Color(0xFFAEB7B1),
    surfaceContainerLowest = Color(0xFF0A100D),
    surfaceContainerLow = Color(0xFF141D18),
    surfaceContainer = Color(0xFF18231D),
    surfaceContainerHigh = Color(0xFF1F2D25),
    surfaceContainerHighest = Color(0xFF27382E),
    inverseSurface = Color(0xFFE3E6E3),
    inverseOnSurface = Color(0xFF101814),
    outline = Color(0xFF6E7B73),
    outlineVariant = Color(0xFF3A453E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF561E19),
    scrim = Color(0xFF000000),
)

/**
 * The light board is a casino card table: deep baize green, white cards. Light here means
 * the *cards* are bright and the room need not be dim — not that the table is a white
 * page, which no card game looks right on.
 *
 * The green is dark enough that white sits at about 7:1 on it, which is the same band the
 * dark theme's card face targets and for the same reason (`docs/PLATFORM.md` "Themes").
 * That also lets the accents carry real colour instead of the near-white pastels a
 * mid-tone table would have forced.
 *
 * This is as light as the table goes without giving something up, and the whole ladder moves
 * with it — Material draws dialogs and rows on the container roles, so a table that rises
 * while they stay put swallows them. Two things bound it: white on the table passes AAA at
 * 7.07:1 and would not at the next step up, and `undo`, the weakest action accent, sits at
 * 3.66:1 against 3:1. Going lighter means re-tuning the accents *darker*, which is a
 * different palette, not a lighter version of this one.
 */
internal val AppLightColors = lightColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF0B3B26),
    primaryContainer = Color(0xFF21774F),
    onPrimaryContainer = Color(0xFFE8F5EE),
    secondary = Color(0xFFCFE8DA),
    onSecondary = Color(0xFF0B3B26),
    background = Color(0xFF18653F),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF18653F),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1E774D),
    onSurfaceVariant = Color(0xFFE0F0E7),
    surfaceContainerLowest = Color(0xFF0F492D),
    surfaceContainerLow = Color(0xFF145639),
    surfaceContainer = Color(0xFF196B45),
    surfaceContainerHigh = Color(0xFF1E774D),
    surfaceContainerHighest = Color(0xFF238356),
    inverseSurface = Color(0xFFE8F5EE),
    inverseOnSurface = Color(0xFF0B3B26),
    outline = Color(0xFF8FC4A9),
    outlineVariant = Color(0xFF458D68),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF561E19),
    scrim = Color(0xFF000000),
)

/**
 * Everything a board needs that Material's `ColorScheme` has no word for: the cards, and
 * the per-action and per-difficulty accents. Kept together so a theme is one object to
 * swap rather than three parallel lookups.
 */
data class AppColors(
    val card: CardColors,
    val action: ActionAccentColors,
    val difficulty: DifficultyAccentColors,
    /**
     * Which of the two themes is in force, for art that ships once per theme rather than being
     * tinted — a wordmark, say.
     *
     * Stated rather than inferred from the background's luminance: *both* tables are dark
     * green, since a card game on a white page looks wrong, so luminance answers "dark" for
     * the light theme too. It is also the only thing that stays right when a player forces a
     * theme in Settings rather than following the system.
     */
    val isDark: Boolean,
)

/**
 * One distinct accent per action-bar button, so the row reads as scannable actions rather
 * than identically tinted icons. Disabled buttons ignore these and fall back to
 * `onSurfaceVariant` — color communicates which action this is, never whether it is
 * currently usable (`docs/PLATFORM.md` "Accessibility").
 */
data class ActionAccentColors(
    val settings: Color,
    val statistics: Color,
    val replay: Color,
    val new: Color,
    val hint: Color,
    val undo: Color,
)

/** Pastels at roughly `primary`'s own lightness, each past the 3:1 floor on the dark board. */
internal val DarkActionAccents = ActionAccentColors(
    settings = Color(0xFFA9C7E0),
    statistics = Color(0xFFE0C98A),
    replay = Color(0xFF9FD6D2),
    new = Color(0xFFB8D9C5),
    hint = Color(0xFFE8D06B),
    undo = Color(0xFFE0A79F),
)

/**
 * The same six hues, lighter than the baize but genuinely coloured. Against the table green
 * each clears the 3:1 floor at 3.7:1 to 5.2:1, which is what lets them keep their saturation —
 * an earlier, lighter table forced them almost to white, where they cleared the floor easily
 * but stopped reading as six different colours, the only reason they exist. `undo`, the
 * warmest and lightest of them, is the one with least room and so the one that decides how
 * light the table may go.
 */
internal val LightActionAccents = ActionAccentColors(
    settings = Color(0xFF8FC4EC),
    statistics = Color(0xFFF0CE8A),
    replay = Color(0xFF8FDCD6),
    new = Color(0xFFA8E0BE),
    hint = Color(0xFFF5DC7A),
    undo = Color(0xFFF0A9A0),
)

/**
 * One color per difficulty for the status row's "Medium #5" label, running cool-to-warm so
 * the ramp reads as increasing difficulty rather than as six arbitrary hues. Color is never
 * the only cue — the level's name is always spelled out beside it — so nothing depends on
 * telling the hues apart.
 */
data class DifficultyAccentColors(
    val trivial: Color,
    val easy: Color,
    val medium: Color,
    val hard: Color,
    val expert: Color,
    val insane: Color,
)

internal val DarkDifficultyAccents = DifficultyAccentColors(
    trivial = Color(0xFFA8D8B0),
    easy = Color(0xFFB8D9C5),
    medium = Color(0xFFE0C98A),
    hard = Color(0xFFE8B07A),
    expert = Color(0xFFE0A79F),
    insane = Color(0xFFD79AC7),
)

internal val LightDifficultyAccents = DifficultyAccentColors(
    trivial = Color(0xFF9EDCB4),
    easy = Color(0xFFBEE9CE),
    medium = Color(0xFFF0CE8A),
    hard = Color(0xFFF2B98C),
    expert = Color(0xFFF0A9A0),
    insane = Color(0xFFE4A8D4),
)

// Declared after the palettes they compose: top-level property initializers run in file
// order, so putting these first would leave every accent null at construction time.
private val DarkAppColors = AppColors(DarkCardColors, DarkActionAccents, DarkDifficultyAccents, isDark = true)
private val LightAppColors = AppColors(LightCardColors, LightActionAccents, LightDifficultyAccents, isDark = false)

/**
 * The board palette in force. `staticCompositionLocalOf` because the theme changes at
 * most once per user action, and reading it should cost nothing on the frames between.
 */
val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

@Composable
fun FinitePlayTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppColors provides if (darkTheme) DarkAppColors else LightAppColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) AppDarkColors else AppLightColors,
            content = content,
        )
    }
}
