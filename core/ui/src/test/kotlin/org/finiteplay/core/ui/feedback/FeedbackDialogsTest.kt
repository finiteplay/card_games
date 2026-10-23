package org.finiteplay.core.ui.feedback

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What can be checked on the JVM: the mail draft the form opens and the question's "Yes". Anything with the form's
 * text field inside a dialog does not go idle under Robolectric (a limit of the test environment: even a bare
 * text field in a dialog fails), so those flows are in the emulator suite (`FeedbackFlowTest` in Klondike).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class FeedbackDialogsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `the draft goes to the support address through an email app only`() {
        val intent = feedbackMailIntent("contact@finiteplay.org", "Klondike by FinitePlay 0.3.1 – Idea", "Please add a dark felt")

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto", intent.data?.scheme)
        assertArrayEquals(arrayOf("contact@finiteplay.org"), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals("Klondike by FinitePlay 0.3.1 – Idea", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("Please add a dark felt", intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun `Yes opens the store page and counts as an answer`() {
        val finished = mutableListOf<Boolean>()
        composeRule.setContent { FinitePlayTheme { EnjoyFlow(onFinished = { finished += it }) } }
        composeRule.onNodeWithTag("enjoy_dialog").assertIsDisplayed()

        composeRule.onNodeWithTag("enjoy_yes").performClick()

        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(intent.data.toString().startsWith("market://details?id=org.finiteplay."))
        assertEquals(listOf(true), finished)
    }

    @Test
    fun `the store page of a debug build is the release app's page`() {
        assertEquals("org.finiteplay.klondike", storePackageId("org.finiteplay.klondike.dev"))
        assertEquals("org.finiteplay.klondike", storePackageId("org.finiteplay.klondike"))
    }
}
