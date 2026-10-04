package org.finiteplay.spider.tools.catalog

import java.io.File
import java.util.zip.GZIPInputStream

fun main(args: Array<String>) {
    val bytes = GZIPInputStream(File(args[0]).inputStream()).use { it.readBytes() }
    val solutions = SpiderSolutionCodec.decode(bytes)
    val seed = args[1].toLong()
    val line = solutions[seed]
    if (line == null) {
        println("seed=$seed not found in solutions blob")
    } else {
        println("seed=$seed moves=${line.size}")
    }
}
