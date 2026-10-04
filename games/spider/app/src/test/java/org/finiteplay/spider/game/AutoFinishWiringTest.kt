package org.finiteplay.spider.game

import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.dealGame
import org.finiteplay.spider.rules.isAutoFinishAvailable
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The wiring guarantee that matters and is easy to get wrong: a real game must never be within
 * reach of the automatic finish while the player still has decisions to make.
 *
 * The finish's own correctness lives in `:games:spider:rules`' AutoFinishTest; this only pins that
 * an ordinary board does not qualify, at every suit count.
 */
class AutoFinishWiringTest {
    private val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    @Test
    fun `no freshly dealt game at any suit count is eligible to finish itself`() {
        for (suitCount in SuitCount.entries) {
            for (seed in 1L..25L) {
                val state = dealGame(seed = seed, versions = versions, suitCount = suitCount)
                assertFalse(
                    "seed $seed at $suitCount must not offer an automatic finish on the opening board",
                    isAutoFinishAvailable(state),
                )
            }
        }
    }
}
