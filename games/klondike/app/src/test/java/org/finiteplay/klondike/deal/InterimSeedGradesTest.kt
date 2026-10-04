package org.finiteplay.klondike.deal

import org.junit.Assert.assertEquals
import org.junit.Test

class InterimSeedGradesTest {

    @Test
    fun `grades exactly the interim solvable seed list, no more and no fewer`() {
        assertEquals(INTERIM_SOLVABLE_SEEDS.toSet(), INTERIM_SEED_GRADES.keys)
    }

    @Test
    fun `every difficulty tier is represented in the graded batch`() {
        assertEquals(DifficultyTier.entries.toSet(), INTERIM_SEED_GRADES.values.toSet())
    }

    /**
     * The catalog build enforces this by construction, and this asserts it on what actually
     * shipped: [INTERIM_SEED_GRADES] resolves a duplicate by "last level wins", so a seed in
     * two lists would silently display as the harder one — which is how fifteen Trivial deals
     * once shipped labelled Insane.
     */
    @Test
    fun `no seed appears in two levels`() {
        val duplicated = INTERIM_SOLVABLE_SEEDS.groupingBy { it }.eachCount().filterValues { it > 1 }

        assertEquals(emptyMap<Long, Int>(), duplicated)
    }
}
