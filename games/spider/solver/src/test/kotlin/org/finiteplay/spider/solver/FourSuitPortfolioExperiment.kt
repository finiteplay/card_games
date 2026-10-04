package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.dealGame
import org.finiteplay.spider.rules.applyMove
import org.finiteplay.spider.rules.isLegal
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Opt-in offline experiment; normal test runs skip the twelve-minute search. */
class FourSuitPortfolioExperiment {
    @Test
    fun findCertificate() {
        assumeTrue(java.lang.Boolean.getBoolean("spider.portfolio"))
        val directory = File("build/reports/four-suit-portfolio").apply { mkdirs() }
        val results = File(directory, "results.csv")
        results.writeText("stage,seed,width,attempts,nodes,wallMs,solved,moves\n")
        val started = System.nanoTime()
        val deadline = started + 720_000_000_000L
        val found = AtomicBoolean(false)
        val finished = AtomicBoolean(false)
        val completed = AtomicInteger()
        val versions = GameVersions(0, 1, 1)
        val beam = SpiderSolver::class.java.getDeclaredMethod("beamSearch", FastBoard::class.java, Int::class.javaPrimitiveType, IntArrayList::class.java).apply { isAccessible = true }
        val playout = SpiderSolver::class.java.getDeclaredMethod("playout", FastBoard::class.java, Int::class.javaPrimitiveType, IntArrayList::class.java).apply { isAccessible = true }
        val clock = SpiderSolver::class.java.getDeclaredField("deadline").apply { isAccessible = true }
        val nodes = SpiderSolver::class.java.getDeclaredField("nodes").apply { isAccessible = true }
        val heartbeat = thread(isDaemon = true) {
            while (!finished.get()) {
                Thread.sleep(30_000)
                val heap = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used / (1024 * 1024)
                println("HEARTBEAT elapsedSeconds=${(System.nanoTime() - started) / 1_000_000_000} completed=${completed.get()} heapUsedMiB=$heap solved=${found.get()}")
            }
        }
        fun save(seed: Long, stage: String, width: Int, attempts: Int, visits: Long, elapsedMs: Long, record: IntArrayList, solved: Boolean) {
            if (solved) {
                var state = dealGame(seed, versions, SuitCount.FOUR)
                val text = StringBuilder("seed=$seed suitCount=FOUR catalogVersion=0 rulesVersion=1 shuffleVersion=1 stage=$stage width=$width\n")
                for (i in 0 until record.size) {
                    val move = toRulesMove(FastMove(record[i]))
                    assertTrue("illegal certificate move $i: $move", isLegal(state, move))
                    state = applyMove(state, move)
                    text.append(move).append('\n')
                }
                assertTrue("certificate must win through the real reducer", state.isWon)
                File(directory, "seed-$seed-$stage-$width.txt").writeText(text.toString())
                found.set(true)
            }
            synchronized(results) {
                val row = "$stage,$seed,$width,$attempts,$visits,$elapsedMs,$solved,${if (solved) record.size else 0}"
                results.appendText("$row\n")
                println("RESULT $row")
            }
            completed.incrementAndGet()
        }
        val errors = ConcurrentLinkedQueue<Throwable>()
        try {
            val greedyDeadline = started + 45_000_000_000L
            val nextSeed = AtomicInteger(1)
            val greedyWorkers = (0 until 8).map {
                thread {
                    try {
                        val solver = SpiderSolver(SolverLimits(cacheCapacityPowerOfTwo = 1024))
                        while (!found.get() && System.nanoTime() < greedyDeadline) {
                            val seed = nextSeed.getAndIncrement().toLong()
                            val start = FastBoard.from(dealGame(seed, versions, SuitCount.FOUR))
                            val record = IntArrayList(600)
                            val begin = System.nanoTime()
                            nodes.setLong(solver, 0)
                            var attempts = 0
                            var solved = false
                            while (attempts < 1000 && !found.get() && System.nanoTime() < greedyDeadline) {
                                val board = start.copy()
                                record.clear()
                                playout.invoke(solver, board, attempts, record)
                                attempts++
                                if (board.isWon) { solved = true; break }
                            }
                            save(seed, "greedy", 0, attempts, nodes.getLong(solver), (System.nanoTime() - begin) / 1_000_000, record, solved)
                        }
                    } catch (error: Throwable) { errors.add(error) }
                }
            }
            greedyWorkers.forEach { it.join() }
            val tasks = ConcurrentLinkedQueue<Pair<Long, Int>>()
            for (seed in 1L..32L) for (width in listOf(2, 3, 6)) tasks.add(seed to width)
            val beamWorkers = (0 until 8).map {
                thread {
                    try {
                        while (!found.get() && System.nanoTime() < deadline) {
                            val (seed, width) = tasks.poll() ?: break
                            val begin = System.nanoTime()
                            val solver = SpiderSolver(SolverLimits(maxNodes = 500_000_000_000L, playouts = 0, beamWidth = width, maxCacheEntries = 8_000_000))
                            clock.setLong(solver, minOf(deadline, begin + 90_000_000_000L))
                            val record = IntArrayList(600)
                            val board = FastBoard.from(dealGame(seed, versions, SuitCount.FOUR))
                            println("START beam seed=$seed width=$width")
                            val depth = beam.invoke(solver, board, 0, record) as Int
                            if (depth >= 0) record.reverse()
                            save(seed, "beam", width, 1, nodes.getLong(solver), (System.nanoTime() - begin) / 1_000_000, record, depth >= 0)
                        }
                    } catch (error: Throwable) { errors.add(error) }
                }
            }
            beamWorkers.forEach { it.join() }
            assertTrue("worker failures: $errors", errors.isEmpty())
            println("SUMMARY solved=${found.get()} completed=${completed.get()} elapsedMs=${(System.nanoTime() - started) / 1_000_000} heapMax=${Runtime.getRuntime().maxMemory()}")
        } finally {
            finished.set(true)
            heartbeat.interrupt()
        }
    }
}
