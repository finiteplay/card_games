package org.finiteplay.spider.solver

import java.io.File
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.dealGame
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.applyMove

fun main(args: Array<String>) {
    val input = File(args[0])
    val output = File(args[1])
    var state = dealGame(1, GameVersions(0, 1, 1), SuitCount.FOUR)
    val certificate = ArrayList<String>()
    var rawMoves = 0
    var implicitBanks = 0

    for (line in input.readLines()) {
        if (!line.matches(Regex("\\d+,\\d+,\\d+,-?\\d+,[01],[01],[01]"))) continue
        rawMoves++
        val fields = line.split(',').map(String::toInt)
        val type = fields[0]
        val from = fields[1]
        val to = fields[2]
        val count = fields[3]

        when {
            type == 3 -> {
                state = applyMove(state, Move.DealRow)
                certificate += "DEAL"
            }
            to in 0..7 -> {
                implicitBanks++
                check(state.sequencesBanked >= implicitBanks) {
                    "Solvitaire bank $implicitBanks at raw move $rawMoves occurred before FinitePlay banked it"
                }
            }
            type == 0 || type == 1 -> {
                check(from in 9..18 && to in 9..18) {
                    "unsupported pile move at raw move $rawMoves: $line"
                }
                val fromColumn = from - 9
                val toColumn = to - 9
                val fromIndex = state.tableau[fromColumn].size - count
                val move = Move.TableauToTableau(fromColumn, fromIndex, toColumn)
                state = applyMove(state, move)
                certificate += "MOVE $fromColumn $fromIndex $toColumn"
            }
            else -> error("unsupported raw move $rawMoves: $line")
        }
    }

    check(state.isWon) { "Solvitaire line replayed without a FinitePlay win" }
    output.writeText(buildString {
        appendLine("seed=1 suitCount=FOUR catalogVersion=0 rulesVersion=1 shuffleVersion=1")
        appendLine("rawMoves=$rawMoves finitePlayMoves=${certificate.size} implicitBanks=$implicitBanks verified=true")
        certificate.forEach(::appendLine)
    })
    println("VERIFIED rawMoves=$rawMoves finitePlayMoves=${certificate.size} implicitBanks=$implicitBanks")
}
