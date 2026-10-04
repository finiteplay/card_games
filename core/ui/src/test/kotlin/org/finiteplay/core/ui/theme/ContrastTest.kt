package org.finiteplay.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `docs/PLATFORM.md` "Accessibility" states the floors this pins: at least 4.5:1 for text
 * and 3:1 for meaningful graphic contrast. Card-face rank/suit ink is large, bold glyphs
 * (`CardArt.kt`'s `cornerTextSize` is well past the WCAG large-text threshold), so it is
 * held to the graphic floor here, the same floor the accent and difficulty colors below
 * are held to.
 */
class ContrastTest {

    private fun linearChannel(c: Float): Double =
        if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)

    private fun relativeLuminance(color: Color): Double {
        val r = linearChannel(color.red)
        val g = linearChannel(color.green)
        val b = linearChannel(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val l1 = relativeLuminance(a)
        val l2 = relativeLuminance(b)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertTextFloor(fg: Color, bg: Color, label: String) {
        val ratio = contrastRatio(fg, bg)
        assertTrue("$label: $ratio must be >= 4.5", ratio >= 4.5)
    }

    private fun assertGraphicFloor(fg: Color, bg: Color, label: String) {
        val ratio = contrastRatio(fg, bg)
        assertTrue("$label: $ratio must be >= 3.0", ratio >= 3.0)
    }

    @Test
    fun `dark theme card ink clears the graphic floor on the card face`() {
        assertGraphicFloor(DarkCardColors.blackSuit, DarkCardColors.face, "dark black ink on face")
        assertGraphicFloor(DarkCardColors.redSuit, DarkCardColors.face, "dark red ink on face")
    }

    @Test
    fun `light theme card ink clears the graphic floor on the card face`() {
        assertGraphicFloor(LightCardColors.blackSuit, LightCardColors.face, "light black ink on face")
        assertGraphicFloor(LightCardColors.redSuit, LightCardColors.face, "light red ink on face")
    }

    @Test
    fun `card face clears the graphic floor against its own board`() {
        assertGraphicFloor(DarkCardColors.face, AppDarkColors.background, "dark face on board")
        assertGraphicFloor(LightCardColors.face, AppLightColors.background, "light face on board")
    }

    @Test
    fun `material text roles clear the text floor in both themes`() {
        assertTextFloor(AppDarkColors.onBackground, AppDarkColors.background, "dark onBackground/background")
        assertTextFloor(AppDarkColors.onSurfaceVariant, AppDarkColors.surfaceVariant, "dark onSurfaceVariant/surfaceVariant")
        assertTextFloor(AppLightColors.onBackground, AppLightColors.background, "light onBackground/background")
        assertTextFloor(AppLightColors.onSurfaceVariant, AppLightColors.surfaceVariant, "light onSurfaceVariant/surfaceVariant")
    }

    @Test
    fun `action accent colors clear the graphic floor against the board in both themes`() {
        for ((name, color) in listOf(
            "settings" to DarkActionAccents.settings,
            "statistics" to DarkActionAccents.statistics,
            "replay" to DarkActionAccents.replay,
            "new" to DarkActionAccents.new,
            "hint" to DarkActionAccents.hint,
            "undo" to DarkActionAccents.undo,
        )) {
            assertGraphicFloor(color, AppDarkColors.background, "dark action $name")
        }
        for ((name, color) in listOf(
            "settings" to LightActionAccents.settings,
            "statistics" to LightActionAccents.statistics,
            "replay" to LightActionAccents.replay,
            "new" to LightActionAccents.new,
            "hint" to LightActionAccents.hint,
            "undo" to LightActionAccents.undo,
        )) {
            assertGraphicFloor(color, AppLightColors.background, "light action $name")
        }
    }

    @Test
    fun `difficulty accent colors clear the graphic floor against the board in both themes`() {
        for ((name, color) in listOf(
            "trivial" to DarkDifficultyAccents.trivial,
            "easy" to DarkDifficultyAccents.easy,
            "medium" to DarkDifficultyAccents.medium,
            "hard" to DarkDifficultyAccents.hard,
            "expert" to DarkDifficultyAccents.expert,
            "insane" to DarkDifficultyAccents.insane,
        )) {
            assertGraphicFloor(color, AppDarkColors.background, "dark difficulty $name")
        }
        for ((name, color) in listOf(
            "trivial" to LightDifficultyAccents.trivial,
            "easy" to LightDifficultyAccents.easy,
            "medium" to LightDifficultyAccents.medium,
            "hard" to LightDifficultyAccents.hard,
            "expert" to LightDifficultyAccents.expert,
            "insane" to LightDifficultyAccents.insane,
        )) {
            assertGraphicFloor(color, AppLightColors.background, "light difficulty $name")
        }
    }
}
