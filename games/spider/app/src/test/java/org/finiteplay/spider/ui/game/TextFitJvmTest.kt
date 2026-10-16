package org.finiteplay.spider.ui.game

import androidx.compose.ui.test.junit4.createComposeRule
import org.finiteplay.core.ui.testing.SweepState
import org.finiteplay.core.ui.testing.setLocalizedContent
import org.finiteplay.core.ui.testing.sweepLanguages
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Every label and button fits where it is drawn, in every interface language, at the font scales a phone really
 * has: no letters cut off, no ellipsis, no word broken across lines. The JVM twin of the instrumented
 * `TextFitTest` (Robolectric native graphics), so it runs with the unit tests; it measures with a font about 20%
 * narrower than the device's, so it tests at a 1.2x margin and the emulator stays the final word. The report is
 * written to `build/textfit/`; the build fails on findings only with TEXTFIT_STRICT=1.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class TextFitJvmTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val state = SweepState()

    @Test
    fun everyLabelFitsInEveryLanguage() {
        val viewModel = SpiderViewModel(initialSeed = 1L)
        composeRule.setLocalizedContent(state) { GameScreen(viewModel = viewModel) }
        composeRule.sweepLanguages(state, File("build/textfit/jvm-spider.tsv"))
    }
}
