package org.finiteplay.core.ui.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackMessageTest {
    private val info = DeviceInfo(
        app = "Klondike by FinitePlay",
        versionName = "0.3.1",
        androidRelease = "14",
        sdk = 34,
        device = "Google Pixel 8",
        language = "de-DE",
    )

    @Test
    fun `the subject names the app, the build and the category in English`() {
        assertEquals("Klondike by FinitePlay 0.3.1 – Problem", feedbackSubject(info.app, info.versionName, FeedbackCategory.PROBLEM))
        assertEquals("Klondike by FinitePlay 0.3.1 – Idea", feedbackSubject(info.app, info.versionName, FeedbackCategory.IDEA))
        assertEquals("Klondike by FinitePlay 0.3.1 – Other", feedbackSubject(info.app, info.versionName, FeedbackCategory.OTHER))
    }

    @Test
    fun `a message sent without device details is only the message`() {
        assertEquals("The hint button is hard to find.", feedbackBody("  The hint button is hard to find.\n", null))
    }

    @Test
    fun `device details follow the message when the player allows them`() {
        val body = feedbackBody("Crash on start", info)
        assertTrue(body.startsWith("Crash on start\n\n--\n"))
        assertTrue("App: Klondike by FinitePlay 0.3.1" in body)
        assertTrue("Android: 14 (API 34)" in body)
        assertTrue("Device: Google Pixel 8" in body)
        assertTrue("Language: de-DE" in body)
    }

    @Test
    fun `the details name only the build and the device`() {
        val lines = feedbackBody("x", info).lines().filter { it.isNotBlank() }
        assertEquals(listOf("x", "--", "App: Klondike by FinitePlay 0.3.1", "Android: 14 (API 34)", "Device: Google Pixel 8", "Language: de-DE"), lines)
    }

    @Test
    fun `an overlong message is cut to the limit`() {
        val body = feedbackBody("a".repeat(FEEDBACK_MAX_CHARS + 500), null)
        assertEquals(FEEDBACK_MAX_CHARS, body.length)
    }

    @Test
    fun `an empty or blank message cannot be sent`() {
        assertFalse(canSendFeedback(""))
        assertFalse(canSendFeedback("  \n "))
        assertTrue(canSendFeedback("hi"))
    }
}
