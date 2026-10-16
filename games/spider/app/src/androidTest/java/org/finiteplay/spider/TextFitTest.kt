package org.finiteplay.spider

import android.content.res.Resources
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.finiteplay.core.storage.AppLocale
import org.finiteplay.core.storage.SYSTEM_LANGUAGE
import org.finiteplay.core.ui.testing.AuditLocales
import org.finiteplay.core.ui.testing.TextFitAudit
import org.finiteplay.core.ui.testing.audit
import org.finiteplay.core.ui.testing.tapIfPresent
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Every label and button must fit where it is drawn, in every interface language: no letters cut
 * off, no ellipsis, no word broken across lines. Runs the real activity in each language, looks at
 * the table, Settings, Statistics, Help and a Hint, and writes what does not fit to
 * `textfit-spider-<fontScale>.tsv` in the app's external files directory (`docs/TEXT_FIT.md`).
 *
 * Run it at the font scales a phone really has, for example
 * `adb shell settings put system font_scale 1.3` before the run.
 */
class TextFitTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun restoreLanguage() {
        AppLocale.setTag(composeRule.activity, SYSTEM_LANGUAGE)
    }

    @Test
    fun everyLabelFitsInEveryLanguage() {
        val fontScale = Resources.getSystem().configuration.fontScale
        val audits = mutableListOf<TextFitAudit>()
        for (tag in AuditLocales.tags) {
            AppLocale.setTag(composeRule.activity, tag)
            composeRule.activityRule.scenario.recreate()
            composeRule.waitForIdle()
            val audit = TextFitAudit(tag, fontScale)
            composeRule.audit(audit, "table")
            for ((button, screen) in listOf("action_settings" to "settings", "statistics_button" to "statistics", "help_button" to "help", "action_hint" to "hint")) {
                if (!composeRule.tapIfPresent(button)) continue
                composeRule.audit(audit, screen)
                if (screen != "hint") composeRule.tapIfPresent("panel_close")
            }
            audits += audit
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report = File(context.getExternalFilesDir(null), "textfit-spider-$fontScale.tsv")
        val count = TextFitAudit.write(report, audits)
        val worst = audits.flatMap { it.findings }.take(15).joinToString(NL) { "${it.locale} ${it.screen} ${it.kind} '${it.text}'" }
        assertTrue("$count labels do not fit (see ${report.path}):$NL$worst", count == 0)
    }
}

private val NL: String = System.lineSeparator()
