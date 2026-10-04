package org.finiteplay.buildlogic

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssertExcludedProjectTaskTest {

    @Test
    fun `flags the forbidden project when present`() {
        val paths = listOf(":games:klondike:rules", ":solitaire:catalog", ":tools:catalog")

        assertTrue(containsForbiddenProjectPath(paths, ":tools:catalog"))
    }

    @Test
    fun `passes when the forbidden project is absent`() {
        val paths = listOf(":games:klondike:rules", ":solitaire:catalog")

        assertFalse(containsForbiddenProjectPath(paths, ":tools:catalog"))
    }

    @Test
    fun `passes for an empty classpath`() {
        assertFalse(containsForbiddenProjectPath(emptyList(), ":tools:catalog"))
    }
}
