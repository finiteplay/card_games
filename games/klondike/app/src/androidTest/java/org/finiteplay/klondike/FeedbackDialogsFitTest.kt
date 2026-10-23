package org.finiteplay.klondike

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.test.junit4.createComposeRule
import org.finiteplay.core.ui.feedback.EnjoyFlow
import org.finiteplay.core.ui.feedback.FeedbackDialog
import org.finiteplay.core.ui.testing.AuditLocales
import org.finiteplay.core.ui.testing.SweepState
import org.finiteplay.core.ui.testing.TextFitAudit
import org.finiteplay.core.ui.testing.auditDialog
import org.finiteplay.core.ui.testing.setLocalizedContent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The "Enjoying this game?" dialog and the feedback form fit in every language, at the font scales a phone has: no word broken,
 * nothing cut off (`docs/TEXT_FIT.md`). The two dialogs are windows of their own, so they are audited as dialogs.
 */
class FeedbackDialogsFitTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bothDialogsFitInEveryLanguage() {
        val state = SweepState()
        var which by mutableIntStateOf(0)
        composeRule.setLocalizedContent(state) {
            key(which) {
                if (which == 0) EnjoyFlow(onFinished = {}) else FeedbackDialog(onDone = {})
            }
        }
        val audits = mutableListOf<TextFitAudit>()
        for (scale in listOf(1f, 1.3f)) {
            for (tag in AuditLocales.tags) {
                state.locale = tag
                state.fontScale = scale
                val audit = TextFitAudit(tag, scale)
                which = 0
                composeRule.auditDialog(audit, "enjoy")
                which = 1
                composeRule.auditDialog(audit, "feedback")
                audits += audit
            }
        }
        val findings = audits.flatMap { it.findings }
        for (f in findings) Log.i("TEXTFIT", "${f.locale}\t${f.fontScale}\t${f.screen}\t${f.kind}\t${f.widthPx}\t${f.heightPx}\t${f.lines}\t${f.text}")
        assertTrue("${findings.size} labels do not fit: " + findings.take(12).joinToString { "${it.locale} x${it.fontScale} ${it.screen} '${it.text.take(40)}'" }, findings.isEmpty())
    }
}
