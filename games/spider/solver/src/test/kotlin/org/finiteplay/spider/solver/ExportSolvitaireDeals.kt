package org.finiteplay.spider.solver

import java.io.File
import org.finiteplay.cards.Card
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.TableauCard
import org.finiteplay.spider.layout.dealGame

private fun Card.solvitaire(faceUp: Boolean = true): String {
    val rankText = when (rank.value) {
        1 -> "A"
        11 -> "J"
        12 -> "Q"
        13 -> "K"
        else -> rank.value.toString()
    }
    val suitText = suit.name.first().let { if (faceUp) it else it.lowercaseChar() }
    return "\"$rankText$suitText\""
}

private fun TableauCard.solvitaire(): String = card.solvitaire(faceUp)

/** args: outputDir firstSeed lastSeed (inclusive) [suitCount]. firstSeed/lastSeed default to 1/4, suitCount to FOUR, for the original single-batch call shape. */
fun main(args: Array<String>) {
    val output = File(args[0]).apply { mkdirs() }
    val firstSeed = args.getOrNull(1)?.toLong() ?: 1L
    val lastSeed = args.getOrNull(2)?.toLong() ?: 4L
    val suitCount = args.getOrNull(3)?.uppercase()?.let { SuitCount.valueOf(it) } ?: SuitCount.FOUR
    val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)
    val suitLabel = suitCount.name.lowercase()

    for (seed in firstSeed..lastSeed) {
        val state = dealGame(seed, versions, suitCount)
        val json = buildString {
            appendLine("{")
            appendLine("  \"tableau piles\": [")
            state.tableau.forEachIndexed { index, column ->
                append("    [").append(column.joinToString(",") { it.solvitaire() }).append("]")
                appendLine(if (index == state.tableau.lastIndex) "" else ",")
            }
            appendLine("  ],")
            append("  \"stock\": [")
            append(state.stock.asReversed().joinToString(",") { it.solvitaire() })
            appendLine("]")
            appendLine("}")
        }
        File(output, "finiteplay-$suitLabel-seed-$seed.json").writeText(json)
    }
}
