package org.finiteplay.core.ui.testing

import android.content.res.Configuration
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.finiteplay.core.ui.theme.FinitePlayTheme
import java.io.File
import java.util.Locale

/**
 * The language and font scale a [setLocalizedContent] screen is shown in; change them between audits and the
 * same composition re-resolves every string, without restarting anything.
 */
class SweepState {
    var locale by mutableStateOf("en")
    var fontScale by mutableFloatStateOf(1f)
}

/**
 * Shows [content] the way a phone of [widthDp] x [heightDp] would, in [state]'s language and font scale.
 * Resources are resolved against a context configured for the language, and the layout direction follows it.
 */
fun ComposeContentTestRule.setLocalizedContent(
    state: SweepState,
    widthDp: Int = 360,
    heightDp: Int = 780,
    content: @Composable () -> Unit,
) {
    setContent {
        val base = LocalContext.current
        val tag = Locale.forLanguageTag(state.locale)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(tag)
            setLayoutDirection(tag)
        }
        val localized = base.createConfigurationContext(configuration)
        CompositionLocalProvider(
            LocalContext provides localized,
            LocalResources provides localized.resources,
            LocalConfiguration provides configuration,
            LocalLayoutDirection provides if (configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr,
            LocalDensity provides Density(LocalDensity.current.density, state.fontScale),
        ) {
            FinitePlayTheme { Box(Modifier.size(widthDp.dp, heightDp.dp)) { content() } }
        }
    }
}

/**
 * The JVM measures text with a font about 20% narrower than the device's Roboto, so a sweep here runs at 1.2 (the
 * device's 1.0) and 1.4 (a phone at 1.15) to keep a margin. The emulator test stays the final word.
 */
val JVM_FONT_SCALES = listOf(1.2f, 1.4f)

/** Taps [tag] if present, audits what opened under [screen], and closes it again. */
private fun ComposeContentTestRule.auditPanel(audit: TextFitAudit, tag: String, screen: String) {
    if (!tapIfPresent(tag)) return
    audit(audit, screen)
    tapIfPresent("panel_close")
}

/** The screens every game has: the table, Settings, Statistics, Help, and the hint when the game offers one. */
fun ComposeContentTestRule.auditStandardScenes(audit: TextFitAudit) {
    audit(audit, "table")
    auditPanel(audit, "action_settings", "settings")
    auditPanel(audit, "statistics_button", "statistics")
    auditPanel(audit, "help_button", "help")
    if (tapIfPresent("action_hint")) audit(audit, "hint")
}

/**
 * Audits [scenes] in every language at every scale in [scales], writes the report to [report], and returns the
 * findings. The build fails on them only when the environment sets TEXTFIT_STRICT=1, so the report is always
 * produced and the fixes can land before the test is made to enforce them.
 */
fun ComposeContentTestRule.sweepLanguages(
    state: SweepState,
    report: File,
    scales: List<Float> = JVM_FONT_SCALES,
    scenes: ComposeContentTestRule.(TextFitAudit) -> Unit = { auditStandardScenes(it) },
): List<TextFitAudit.Finding> {
    val audits = mutableListOf<TextFitAudit>()
    for (scale in scales) {
        for (tag in AuditLocales.tags) {
            state.locale = tag
            state.fontScale = scale
            waitForIdle()
            val audit = TextFitAudit(tag, scale)
            scenes(audit)
            audits += audit
        }
    }
    TextFitAudit.write(report, audits)
    val findings = audits.flatMap { it.findings }
    if (System.getenv("TEXTFIT_STRICT") == "1" && findings.isNotEmpty()) {
        val nl = System.lineSeparator()
        val worst = findings.take(15).joinToString(nl) { "${it.locale} x${it.fontScale} ${it.screen} ${it.kind} '${it.text}'" }
        throw AssertionError("${findings.size} labels do not fit (report: ${report.path}):$nl$worst")
    }
    return findings
}
