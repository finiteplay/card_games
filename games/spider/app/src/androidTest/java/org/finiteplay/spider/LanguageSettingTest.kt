package org.finiteplay.spider

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import org.finiteplay.core.storage.AppLocale
import org.finiteplay.core.storage.SYSTEM_LANGUAGE
import org.finiteplay.spider.storage.SpiderActiveGameStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Picking a language in Settings must both persist it where [AppLocale.wrap] reads it from
 * (`MainActivity.attachBaseContext`) and take hold immediately, the same way Klondike's own
 * `GameScreen` wires its `onLanguageChange` — a `SpiderViewModel` setting alone is invisible to
 * `attachBaseContext`, which runs before any view model exists.
 */
class LanguageSettingTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun resetLanguageAndSave() = runBlocking {
        AppLocale.setTag(composeRule.activity, SYSTEM_LANGUAGE)
        SpiderActiveGameStore(composeRule.activity.filesDir).clear()
    }

    @Test
    fun pickingALanguageInSettingsPersistsItForAttachBaseContext() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("action_settings").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("setting_language").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("setting_language_es").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("es", AppLocale.currentTag(composeRule.activity))
    }
}
