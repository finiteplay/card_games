package org.finiteplay.blackjack

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Every locale must define exactly the same set of translatable keys as the base `values/`
 * (mirroring the other games' `LocaleStringsCompletenessTest`) — not fewer (a missing
 * key silently falls back to English, which this test is here to catch) and not more (a stale key
 * nobody asked for). `translatable="false"` keys (proper nouns) are intentionally never expected
 * in a locale file: Android always resolves those from the base file.
 *
 * Blackjack has one string file — its help text lives in the same
 * `strings.xml` as everything else.
 */
class LocaleStringsCompletenessTest {

    private val resDir = File("src/main/res")

    private val stringFiles = listOf("strings.xml")

    /** Same 30 locales Klondike and Spider support. */
    private val localeQualifiers = listOf(
        "es", "fr", "de", "it", "pt-rBR", "uk", "pl", "nl", "sv", "nb",
        "da", "fi", "tr", "el", "cs", "ro", "hu", "bg", "hr", "sk",
        "ar", "iw", "hi", "in", "vi", "th", "ja", "ko", "zh-rCN", "zh-rTW",
    )

    private fun parseStrings(file: File): Map<String, StringEntry> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val result = LinkedHashMap<String, StringEntry>()
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as Element
            val name = element.getAttribute("name")
            val translatable = element.getAttribute("translatable") != "false"
            result[name] = StringEntry(translatable = translatable, value = element.textContent)
        }
        return result
    }

    private data class StringEntry(val translatable: Boolean, val value: String)

    private fun parseLocale(qualifier: String?): Map<String, StringEntry> {
        val dir = File(resDir, if (qualifier == null) "values" else "values-$qualifier")
        val merged = LinkedHashMap<String, StringEntry>()
        for (name in stringFiles) {
            val file = File(dir, name)
            if (file.exists()) merged += parseStrings(file)
        }
        return merged
    }

    private fun missingFiles(qualifier: String): List<String> =
        stringFiles.filterNot { File(resDir, "values-$qualifier/$it").exists() }

    private fun formatArgs(value: String): Set<String> =
        Regex("""%(\d+)\$[sd]""").findAll(value).map { it.groupValues[1] }.toSet()

    @Test
    fun `every supported locale defines exactly the base translatable key set`() {
        val base = parseLocale(null)
        val expectedKeys = base.filterValues { it.translatable }.keys

        val failures = mutableListOf<String>()
        for (qualifier in localeQualifiers) {
            val absent = missingFiles(qualifier)
            if (absent.isNotEmpty()) {
                failures += absent.map { "values-$qualifier/$it is missing" }
                continue
            }
            val localeKeys = parseLocale(qualifier).keys
            val missing = expectedKeys - localeKeys
            val extra = localeKeys - expectedKeys
            if (missing.isNotEmpty()) failures += "values-$qualifier is missing keys: $missing"
            if (extra.isNotEmpty()) failures += "values-$qualifier has unexpected keys: $extra"
        }

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `every locale's format strings reference the same argument positions as the base`() {
        val base = parseLocale(null).filterValues { it.translatable }

        val failures = mutableListOf<String>()
        for (qualifier in localeQualifiers) {
            val locale = parseLocale(qualifier)
            for ((key, entry) in base) {
                val expectedArgs = formatArgs(entry.value)
                if (expectedArgs.isEmpty()) continue
                val localeEntry = locale[key] ?: continue
                val actualArgs = formatArgs(localeEntry.value)
                if (actualArgs != expectedArgs) {
                    failures += "values-$qualifier/$key: expected args $expectedArgs, found $actualArgs"
                }
            }
        }

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `no locale file defines a translatable=false key`() {
        val failures = mutableListOf<String>()
        for (qualifier in localeQualifiers) {
            val nonTranslatable = parseLocale(qualifier).filterValues { !it.translatable }.keys
            if (nonTranslatable.isNotEmpty()) {
                failures += "values-$qualifier defines translatable=false keys it shouldn't: $nonTranslatable"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `no locale value is empty`() {
        val failures = mutableListOf<String>()
        for (qualifier in localeQualifiers) {
            for ((key, entry) in parseLocale(qualifier)) {
                if (entry.value.isBlank()) failures += "values-$qualifier/$key is blank"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
