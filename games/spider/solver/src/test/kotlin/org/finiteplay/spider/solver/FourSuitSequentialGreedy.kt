package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.dealGame
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/** Uses every logical processor on one seed at a time; intended only for offline experiments. */
object FourSuitSequentialGreedy {
    private val versions = GameVersions(0, 1, 1)
    private val playout = SpiderSolver::class.java.getDeclaredMethod(
        "playout",
        FastBoard::class.java,
        Int::class.javaPrimitiveType,
        IntArrayList::class.java,
    ).apply { isAccessible = true }

    @JvmStatic
    fun main(args: Array<String>) {
        val directory = File(args[0]).apply { mkdirs() }
        val secondsPerSeed = args.getOrNull(1)?.toLong() ?: 3_600L
        val workers = args.getOrNull(2)?.toInt() ?: Runtime.getRuntime().availableProcessors()
        val stop = AtomicBoolean()
        val results = File(directory, "results.csv").apply {
            writeText("seed,started,finished,playouts,bestBanked,solved\n")
        }

        for (seed in listOf(1, 2)) {
            if (stop.get()) break
            val started = Instant.now()
            val deadline = System.nanoTime() + secondsPerSeed * 1_000_000_000L
            val nextVariation = AtomicInteger()
            val playouts = AtomicLong()
            val bestBanked = AtomicInteger(-1)
            val start = FastBoard.from(dealGame(seed.toLong(), versions, SuitCount.FOUR))
            val threads = (0 until workers).map { worker ->
                thread(name = "greedy-seed-$seed-$worker") {
                    val solver = SpiderSolver(SolverLimits(cacheCapacityPowerOfTwo = 1024))
                    val record = IntArrayList(600)
                    while (!stop.get() && System.nanoTime() < deadline) {
                        val variation = nextVariation.getAndIncrement()
                        val board = start.copy()
                        record.clear()
                        playout.invoke(solver, board, variation, record)
                        playouts.incrementAndGet()
                        if (board.banked > bestBanked.get()) {
                            synchronized(directory) {
                                if (board.banked > bestBanked.get()) {
                                    bestBanked.set(board.banked)
                                    File(directory, "best-$seed.txt").writeText(
                                        "seed=$seed variation=$variation banked=${board.banked} stockPos=${board.stockPos} moves=${record.size}\n" +
                                            (0 until record.size).joinToString("\n") { record[it].toString() },
                                    )
                                    println("BEST seed=$seed variation=$variation banked=${board.banked} moves=${record.size}")
                                }
                            }
                        }
                        if (board.isWon) {
                            synchronized(directory) {
                                if (!stop.get()) {
                                    val text = "seed=$seed suitCount=FOUR versions=$versions stage=greedy variation=$variation\n" +
                                        FourSuitLongCampaign.verify(seed.toLong(), SuitCount.FOUR, record)
                                    File(directory, "solution-$seed.txt").writeText(text)
                                    File(directory, "solution-$seed.packed").writeText(
                                        (0 until record.size).joinToString("\n") { record[it].toString() },
                                    )
                                    println("VERIFIED_WIN seed=$seed variation=$variation moves=${record.size}")
                                    stop.set(true)
                                }
                            }
                        }
                    }
                }
            }
            while (threads.any { it.isAlive }) {
                File(directory, "status.properties").writeText(
                    "status=running\nseed=$seed\nstarted=$started\nsecondsPerSeed=$secondsPerSeed\n" +
                        "playouts=${playouts.get()}\nbestBanked=${bestBanked.get()}\nsolved=${stop.get()}\ntime=${Instant.now()}\n",
                )
                threads.forEach { it.join(1_000) }
            }
            val finished = Instant.now()
            results.appendText("$seed,$started,$finished,${playouts.get()},${bestBanked.get()},${stop.get()}\n")
            println("SEED_DONE seed=$seed playouts=${playouts.get()} bestBanked=${bestBanked.get()} solved=${stop.get()}")
        }
        File(directory, "status.properties").writeText(
            "status=${if (stop.get()) "solved" else "complete"}\nsolved=${stop.get()}\ntime=${Instant.now()}\n",
        )
        println("END solved=${stop.get()}")
    }
}
