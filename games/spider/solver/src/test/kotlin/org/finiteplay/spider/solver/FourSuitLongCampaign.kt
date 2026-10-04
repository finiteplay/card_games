package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.dealGame
import org.finiteplay.spider.rules.applyMove
import org.finiteplay.spider.rules.isLegal
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/** Desktop-only experiment. The application and its hint limits are unaffected. */
object FourSuitLongCampaign {
    private val versions = GameVersions(0, 1, 1)
    private val beam = SpiderSolver::class.java.getDeclaredMethod("beamSearch", FastBoard::class.java, Int::class.javaPrimitiveType, IntArrayList::class.java).apply { isAccessible = true }
    private val playout = SpiderSolver::class.java.getDeclaredMethod("playout", FastBoard::class.java, Int::class.javaPrimitiveType, IntArrayList::class.java).apply { isAccessible = true }
    private val clock = SpiderSolver::class.java.getDeclaredField("deadline").apply { isAccessible = true }
    private val nodes = SpiderSolver::class.java.getDeclaredField("nodes").apply { isAccessible = true }

    internal fun verify(seed: Long, suits: SuitCount, record: IntArrayList): String {
        var state = dealGame(seed, versions, suits)
        val text = StringBuilder()
        for (i in 0 until record.size) {
            val move = toRulesMove(FastMove(record[i]))
            check(isLegal(state, move)) { "Illegal certificate move $i: $move" }
            state = applyMove(state, move)
            text.append(move).append('\n')
        }
        check(state.isWon) { "Certificate did not replay to a win" }
        return text.toString()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val endMillis = Instant.parse(args[0]).toEpochMilli()
        val directory = File(args[1]).apply { mkdirs() }
        val smoke = args.getOrNull(2) == "smoke"
        val deadline = System.nanoTime() + (endMillis - System.currentTimeMillis()).coerceAtLeast(0) * 1_000_000
        val done = AtomicBoolean()
        val failed = AtomicInteger()
        val attempts = AtomicLong()
        val active = AtomicInteger()
        val best = Array(4) { AtomicInteger(-1) }
        val starts = Array(4) { FastBoard.from(dealGame(it + 1L, versions, SuitCount.FOUR)) }
        val results = File(directory, "results.csv")
        results.writeText("stage,seed,width,variation,nodes,millis,banked,moves,won\n")
        fun running() = !done.get() && System.nanoTime() < deadline
        fun writeStatus(status: String) {
            File(directory, "status.properties").writeText("status=$status\ntime=${Instant.now()}\ndeadline=${args[0]}\nattempts=${attempts.get()}\nactive=${active.get()}\nworkerFailures=${failed.get()}\nheapUsedBytes=${Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()}\nbestBanked=${best.joinToString(",") { it.get().toString() }}\n")
        }
        fun save(seed: Int, stage: String, width: Int, variation: Int, board: FastBoard, record: IntArrayList, visits: Long, elapsed: Long, log: Boolean = true) {
            attempts.incrementAndGet()
            synchronized(directory) {
                if (board.banked > best[seed - 1].get()) {
                    best[seed - 1].set(board.banked)
                    File(directory, "best-$seed.txt").writeText("seed=$seed stage=$stage width=$width variation=$variation banked=${board.banked} stockPos=${board.stockPos} moves=${record.size}\n" + (0 until record.size).joinToString("\n") { record[it].toString() })
                    println("BEST seed=$seed stage=$stage banked=${board.banked} stock=${board.stockPos} moves=${record.size}")
                }
                if (board.isWon) {
                    val text = StringBuilder("seed=$seed suitCount=FOUR versions=$versions stage=$stage width=$width variation=$variation\n")
                    text.append(verify(seed.toLong(), SuitCount.FOUR, record))
                    File(directory, "solution-$seed.txt").writeText(text.toString())
                    File(directory, "solution-$seed.packed").writeText((0 until record.size).joinToString("\n") { record[it].toString() })
                    done.set(true)
                    println("VERIFIED_WIN seed=$seed stage=$stage moves=${record.size}")
                }
                if (log || board.isWon) results.appendText("$stage,$seed,$width,$variation,$visits,$elapsed,${board.banked},${record.size},${board.isWon}\n")
            }
        }
        fun worker(name: String, work: () -> Unit) {
            active.incrementAndGet()
            thread(name = name, isDaemon = true) {
                try { work() } catch (error: Throwable) {
                    failed.incrementAndGet()
                    synchronized(directory) { File(directory, "errors.log").appendText("$name\n${error.stackTraceToString()}\n") }
                    error.printStackTrace()
                } finally { active.decrementAndGet() }
            }
        }
        println("START deadline=${args[0]} processors=${Runtime.getRuntime().availableProcessors()} heapMax=${Runtime.getRuntime().maxMemory()} smoke=$smoke")
        for (seed in 1..4) {
            worker("greedy-$seed") {
                val solver = SpiderSolver(SolverLimits(cacheCapacityPowerOfTwo = 1024))
                val record = IntArrayList(600)
                var variation = 0
                var lastLog = System.nanoTime()
                while (running() && variation < Int.MAX_VALUE) {
                    val board = starts[seed - 1].copy()
                    record.clear()
                    playout.invoke(solver, board, variation, record)
                    val now = System.nanoTime()
                    val log = now - lastLog > 30_000_000_000L
                    save(seed, "greedy", 0, variation++, board, record, nodes.getLong(solver), (now - lastLog) / 1_000_000, log)
                    if (log) { lastLog = now; nodes.setLong(solver, 0) }
                }
            }
            for (width in listOf(3, 5)) worker("beam-$seed-$width") {
                val solver = SpiderSolver(SolverLimits(maxNodes = Long.MAX_VALUE, playouts = 0, beamWidth = width, maxCacheEntries = if (smoke) 10_000 else 64_000_000))
                clock.setLong(solver, deadline)
                val record = IntArrayList(600)
                val board = starts[seed - 1].copy()
                val begin = System.nanoTime()
                println("START_BEAM seed=$seed width=$width")
                val depth = beam.invoke(solver, board, 0, record) as Int
                if (depth >= 0) record.reverse()
                save(seed, "beam", width, 0, board, record, nodes.getLong(solver), (System.nanoTime() - begin) / 1_000_000)
            }
            worker("hybrid-$seed") {
                val greedy = SpiderSolver(SolverLimits(cacheCapacityPowerOfTwo = 1024))
                var variation = 100_000
                while (running()) {
                    val route = IntArrayList(600)
                    val terminal = starts[seed - 1].copy()
                    playout.invoke(greedy, terminal, variation, route)
                    if (terminal.isWon) { save(seed, "hybrid-greedy", 0, variation, terminal, route, 0, 0); break }
                    // Change the starting branch, not only the size of a deterministic DFS tree.
                    val fraction = 1 + variation % 4
                    val cut = route.size * fraction / 4
                    val board = starts[seed - 1].copy()
                    val record = IntArrayList(1200)
                    val undo = UndoRecord()
                    for (i in 0 until cut) { applyFast(board, FastMove(route[i]), undo); record.add(route[i]) }
                    val width = if (variation % 2 == 0) 3 else 5
                    val solver = SpiderSolver(SolverLimits(maxNodes = Long.MAX_VALUE, playouts = 0, beamWidth = width, maxCacheEntries = if (smoke) 10_000 else 4_000_000))
                    val begin = System.nanoTime()
                    clock.setLong(solver, minOf(deadline, begin + 60_000_000_000L))
                    val suffix = IntArrayList(600)
                    val depth = beam.invoke(solver, board, 0, suffix) as Int
                    if (depth >= 0) { suffix.reverse(); for (i in 0 until suffix.size) record.add(suffix[i]) }
                    save(seed, "hybrid", width, variation++, board, record, nodes.getLong(solver), (System.nanoTime() - begin) / 1_000_000)
                }
            }
        }
        while (running() && active.get() > 0) {
            writeStatus("running")
            val remainingMs = ((deadline - System.nanoTime()) / 1_000_000).coerceAtLeast(1)
            Thread.sleep(minOf(if (smoke) 100L else 10_000L, remainingMs))
        }
        // The verified certificate is flushed before done is set. Daemon workers cannot extend
        // the hard wall-clock deadline or keep computing after another worker wins.
        writeStatus(if (done.get()) "solved" else if (System.nanoTime() >= deadline) "deadline" else "workers-finished")
        println("END solved=${done.get()} attempts=${attempts.get()} failures=${failed.get()}")
        exitProcess(if (failed.get() == 0) 0 else 1)
    }
}
