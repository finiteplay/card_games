package org.finiteplay.klondike.tools.catalog

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LevelCatalogBuildTest {

    @Rule
    @JvmField
    val temp = TemporaryFolder()

    /**
     * Run from the wrong working directory the build read no grades, reported "0 of 0
     * candidates" for every level, and then wrote that: six empty seed lists and a
     * zero-byte solutions.bin over a working catalog. An empty input is a broken run, not
     * an empty catalog, and it has to stop before anything is written.
     */
    @Test
    fun `a missing grading file fails the build instead of emptying the catalog`() {
        val dealDir = temp.newFolder("deal")
        val asset = File(temp.newFolder("assets"), "solutions.bin")
        asset.writeBytes(ByteArray(64) { 1 })

        val failure = assertThrows(IllegalStateException::class.java) {
            buildLevelCatalogs(
                rulesetGradesPath = File(temp.root, "absent_ruleset_grades.csv").path,
                searchGradesPath = File(temp.root, "absent_search_grades.csv").path,
                dealDir = dealDir.path,
                assetPath = asset.path,
                log = {},
            )
        }

        assertEquals(true, failure.message.orEmpty().contains("absent_ruleset_grades.csv"))
        assertEquals("the catalog it would have overwritten", 64, asset.readBytes().size)
        assertEquals("files written into the deal directory", 0, dealDir.list()?.size)
    }

    /** The order deals ship in is fixed: the same input has to renumber to the same catalog forever. */
    @Test
    fun `the presentation order is a stable permutation of the level`() {
        val seeds = (1L..500L).toList()
        val once = presentationOrderForTest(CatalogLevel.EASY, seeds)
        val twice = presentationOrderForTest(CatalogLevel.EASY, seeds)

        assertEquals(once, twice)
        assertEquals(seeds.toSet(), once.toSet())
        assertEquals("a level ordered as it was selected", false, once == seeds)
        // Each level gets its own permutation, so the same position in two levels is not the
        // same rank in the pool they were cut from.
        assertEquals(false, once == presentationOrderForTest(CatalogLevel.MEDIUM, seeds))
    }
}
