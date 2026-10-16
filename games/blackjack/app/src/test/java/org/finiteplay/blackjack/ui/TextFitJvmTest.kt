package org.finiteplay.blackjack.ui

import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.finiteplay.blackjack.storage.BlackjackLedgerStore
import org.finiteplay.blackjack.storage.BlackjackRoundStore
import org.finiteplay.blackjack.storage.BlackjackSettingsStore
import org.finiteplay.core.storage.FakeDataStores
import org.finiteplay.core.ui.testing.SweepState
import org.finiteplay.core.ui.testing.audit
import org.finiteplay.core.ui.testing.auditStandardScenes
import org.finiteplay.core.ui.testing.setLocalizedContent
import org.finiteplay.core.ui.testing.sweepLanguages
import org.finiteplay.core.ui.testing.tapIfPresent
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

/**
 * Every label and button fits where it is drawn, in every interface language, at the font scales a phone really
 * has: no letters cut off, no ellipsis, no word broken across lines. The JVM twin of the instrumented
 * `TextFitTest` (Robolectric native graphics), so it runs with the unit tests; it measures with a font about 20%
 * narrower than the device's, so it tests at a 1.2x margin and the emulator stays the final word. The report is
 * written to `build/textfit/`; the build fails on findings only with TEXTFIT_STRICT=1.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class TextFitJvmTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val state = SweepState()
    private val dir = Files.createTempDirectory("blackjack-textfit").toFile()

    @Before
    fun show() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val factory = { d: File, name: String -> FakeDataStores.create(d, name) }
        val viewModel = BlackjackViewModel(
            roundStore = BlackjackRoundStore(dir, factory),
            ledgerStore = BlackjackLedgerStore(dir, factory),
            settingsStore = BlackjackSettingsStore(dir, factory),
            seedSource = { 1L },
        )
        composeRule.setLocalizedContent(state) { GameScreen(viewModel) }
        composeRule.waitUntil(5_000) { !viewModel.isLoading }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        dir.deleteRecursively()
    }

    @Test
    fun everyLabelFitsInEveryLanguage() {
        composeRule.sweepLanguages(state, File("build/textfit/jvm-blackjack.tsv")) { audit ->
            auditStandardScenes(audit)
            // Betting is over: the buttons a hand offers, then the hint card over them.
            if (tapIfPresent("action_deal")) audit(audit, "round")
            if (tapIfPresent("action_hint")) audit(audit, "hint")
        }
    }
}
