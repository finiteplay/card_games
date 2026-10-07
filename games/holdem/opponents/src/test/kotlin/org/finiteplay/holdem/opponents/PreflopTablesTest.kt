package org.finiteplay.holdem.opponents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PreflopTablesTest {
    private val file = File("src/main/kotlin/org/finiteplay/holdem/opponents/PreflopData.kt")

    @Test
    fun `the committed tables are what the generator writes`() {
        val generated = PreflopTableGenerator.generate()
        if (System.getenv("WRITE_PREFLOP_TABLES") == "1") file.writeText(generated)
        assertEquals(generated.replace("\r\n", "\n"), file.readText().replace("\r\n", "\n"))
    }

    @Test
    fun `the ranking covers the whole deck once and puts pocket aces first`() {
        var combos = 0
        for (c in 0 until HandClasses.COUNT) combos += HandClasses.combos(c)
        assertEquals(1326, combos)
        val first = (0 until HandClasses.COUNT).minBy { PreflopTables.classLow[it] }
        assertEquals("AA", HandClasses.name(first))
        assertEquals(0, PreflopTables.classLow[first])
        assertEquals(10_000, PreflopTables.classHigh.max())
    }

    @Test
    fun `no class is pushed where a strictly stronger one folds`() {
        for (stack in 1..15) for (behind in 1..5) {
            val set = PreflopTables.pushSet(stack, behind)
            assertMonotone("push $stack bb, $behind behind", set)
            for (weak in 0 until HandClasses.COUNT) if (set[weak]) {
                for (strong in 0 until HandClasses.COUNT) {
                    if (PreflopTables.classLow[strong] < PreflopTables.classLow[weak]) {
                        assertTrue("push $stack bb, $behind behind: ${HandClasses.name(weak)} pushed, ${HandClasses.name(strong)} ranks above and folds", set[strong])
                    }
                }
            }
        }
        for (stack in 1..15) for (bucket in PreflopRanges.needed.indices) {
            assertMonotone("call off $stack bb, bucket $bucket", PreflopTables.callPushSet(stack, bucket))
        }
    }

    private fun assertMonotone(what: String, set: BooleanArray) {
        for (weak in 0 until HandClasses.COUNT) if (set[weak]) {
            for (strong in 0 until HandClasses.COUNT) {
                if (HandClasses.dominates(strong, weak)) assertTrue("$what: ${HandClasses.name(weak)} pushed, ${HandClasses.name(strong)} folds", set[strong])
            }
        }
    }

    @Test
    fun `the sets widen as the stack shrinks and the position improves`() {
        fun size(s: BooleanArray) = s.count { it }
        for (behind in 1..5) for (stack in 2..15) {
            assertTrue(size(PreflopTables.pushSet(stack - 1, behind)) >= size(PreflopTables.pushSet(stack, behind)))
        }
        for (stack in 1..15) for (behind in 2..5) {
            assertTrue(size(PreflopTables.pushSet(stack, behind - 1)) >= size(PreflopTables.pushSet(stack, behind)))
        }
    }
}
