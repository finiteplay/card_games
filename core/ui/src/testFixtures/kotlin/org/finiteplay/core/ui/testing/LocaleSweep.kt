package org.finiteplay.core.ui.testing

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.isDialog

/** The interface languages of every game (`docs/languages.txt`), as the tags the in-app language override takes. */
object AuditLocales {
    val tags = listOf(
        "en", "es", "de", "fr", "it", "pt-BR", "nl", "uk", "pl", "sv", "nb", "da", "fi", "tr", "el",
        "cs", "ro", "hu", "bg", "hr", "sk", "ar", "he", "hi", "id", "vi", "th", "ja", "ko", "zh-CN", "zh-TW",
    )
}

/** Files what is on screen now under [screen]. */
fun ComposeTestRule.audit(audit: TextFitAudit, screen: String) {
    waitForIdle()
    audit.scan(onRoot(useUnmergedTree = true).fetchSemanticsNode(), screen)
}

/** Files what is in the dialog on screen now under [screen]: a dialog is a window of its own, so [audit] does not see it. */
fun ComposeTestRule.auditDialog(audit: TextFitAudit, screen: String) {
    waitForIdle()
    audit.scan(onNode(isDialog(), useUnmergedTree = true).fetchSemanticsNode(), screen)
}

/** Taps [tag] if the screen has it; returns whether it did. */
fun ComposeTestRule.tapIfPresent(tag: String): Boolean {
    if (onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) return false
    onNodeWithTag(tag).performClick()
    waitForIdle()
    return true
}
