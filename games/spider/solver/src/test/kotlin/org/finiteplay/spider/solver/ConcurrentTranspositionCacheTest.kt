package org.finiteplay.spider.solver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ConcurrentTranspositionCache]'s safety valve — never spin forever on a full shard, and flag
 * [ConcurrentTranspositionCache.saturated] when that happens — tested directly against a cache
 * forced tiny, rather than through the full search pipeline: a pipeline-level test needs a board
 * complex enough to genuinely need more states than the cache holds before it can be proved
 * unsolvable, which is a much less direct and more fragile way to exercise the same contract.
 */
class ConcurrentTranspositionCacheTest {

    @Test
    fun `a freshly added key is found`() {
        val cache = ConcurrentTranspositionCache(shardCountPow2 = 0, perShardCapacityPow2 = 10)
        assertTrue(cache.add(42L))
        assertFalse("just-added key must be found", cache.add(42L))
    }

    @Test
    fun `never spins forever once every slot is taken, and reports it as saturated`() {
        val cache = ConcurrentTranspositionCache(shardCountPow2 = 0, perShardCapacityPow2 = 4) // 16 slots, one shard
        for (i in 1L..16L) assertTrue("key $i should be new", cache.add(i))
        assertFalse("shard is now full", cache.saturated.get())

        // The 17th distinct key cannot find an empty slot anywhere in a full 16-slot shard. The
        // class's contract is to give up after one full pass and report the key as unseen (true)
        // rather than loop -- this call terminating at all is most of what this test is pinning.
        val result = cache.add(17L)

        assertTrue("a key that could not be inserted must be treated as unseen, never as a false duplicate", result)
        assertTrue("saturated must be set once a shard is proven full", cache.saturated.get())
    }

    @Test
    fun `many threads hammering a tiny cache never hang`() {
        val cache = ConcurrentTranspositionCache(shardCountPow2 = 2, perShardCapacityPow2 = 4) // 4 shards x 16 slots
        val threads = (0 until 8).map { t ->
            Thread {
                for (i in 0L until 5_000L) cache.add(t * 100_000L + i)
            }
        }
        val start = System.nanoTime()
        threads.forEach { it.start() }
        threads.forEach { it.join(10_000) }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        threads.forEach { assertFalse("a worker thread did not finish -- the cache hung", it.isAlive) }
        assertTrue("40,000 inserts into a tiny cache took ${elapsedMs}ms", elapsedMs < 10_000)
        assertTrue("a cache this small against 40,000 distinct keys should have saturated", cache.saturated.get())
    }
}
