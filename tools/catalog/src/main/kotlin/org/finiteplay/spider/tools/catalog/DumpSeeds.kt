package org.finiteplay.spider.tools.catalog

import java.io.File
import org.finiteplay.solitaire.catalog.catalog.CatalogLoadResult
import org.finiteplay.solitaire.catalog.catalog.loadCatalog

fun main(args: Array<String>) {
    val file = File(args[0])
    when (val result = loadCatalog(file.readBytes())) {
        is CatalogLoadResult.Invalid -> println("INVALID: ${result.reason}")
        is CatalogLoadResult.Valid -> {
            val seeds = result.seeds.map { it.toLong() }.sorted()
            println("count=${seeds.size} min=${seeds.first()} max=${seeds.last()}")
            File(args[1]).writeText(seeds.joinToString("\n"))
        }
    }
}
