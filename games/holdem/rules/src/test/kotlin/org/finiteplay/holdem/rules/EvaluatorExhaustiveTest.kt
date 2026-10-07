package org.finiteplay.holdem.rules

import org.junit.Assert.assertEquals
import org.junit.Test

/** Run by `exhaustiveEvaluatorTest`, never by `test` or `check`: it walks every seven-card hand. */
class EvaluatorExhaustiveTest {
    @Test
    fun `all 133,784,560 seven-card hands give the standard category counts`() {
        val counts = LongArray(HandCategory.entries.size)
        val ids = IntArray(7)
        val start = System.nanoTime()
        var total = 0L
        for (a in 0 until 46) { ids[0] = a
            for (b in a + 1 until 47) { ids[1] = b
                for (c in b + 1 until 48) { ids[2] = c
                    for (d in c + 1 until 49) { ids[3] = d
                        for (e in d + 1 until 50) { ids[4] = e
                            for (f in e + 1 until 51) { ids[5] = f
                                for (g in f + 1 until 52) {
                                    ids[6] = g
                                    counts[categoryOf(evaluate(ids)).ordinal]++
                                    total++
                                }
                            }
                        }
                    }
                }
            }
        }
        val seconds = (System.nanoTime() - start) / 1e9
        println("seven-card evaluations: $total in %.1f s = %.1f million/s".format(seconds, total / seconds / 1e6))
        assertEquals(133_784_560L, total)
        val expected = mapOf(
            HandCategory.STRAIGHT_FLUSH to 41_584L, HandCategory.FOUR_OF_A_KIND to 224_848L,
            HandCategory.FULL_HOUSE to 3_473_184L, HandCategory.FLUSH to 4_047_644L, HandCategory.STRAIGHT to 6_180_020L,
            HandCategory.THREE_OF_A_KIND to 6_461_620L, HandCategory.TWO_PAIR to 31_433_400L,
            HandCategory.PAIR to 58_627_800L, HandCategory.HIGH_CARD to 23_294_460L,
        )
        for ((category, count) in expected) assertEquals(category.name, count, counts[category.ordinal])
    }
}
