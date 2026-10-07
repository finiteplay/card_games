package org.finiteplay.holdem.opponents

import org.finiteplay.cards.SplitMix64

/** Rank 0 (deuce) to 12 (ace) of a card id. */
internal fun rankIdx(id: Int): Int {
    val r = id % 13
    return if (r == 0) 12 else r - 1
}

internal fun suitIdx(id: Int): Int = id / 13

/**
 * The 169 starting-hand classes on a 13 x 13 grid (row = high rank, column = low rank): pairs on the
 * diagonal, suited hands below it, offsuit above.
 */
internal object HandClasses {
    const val COUNT = 169
    private const val RANKS = "23456789TJQKA"

    fun of(a: Int, b: Int): Int {
        val ra = rankIdx(a)
        val rb = rankIdx(b)
        val hi = maxOf(ra, rb)
        val lo = minOf(ra, rb)
        return when {
            hi == lo -> hi * 13 + hi
            suitIdx(a) == suitIdx(b) -> hi * 13 + lo
            else -> lo * 13 + hi
        }
    }

    fun isPair(c: Int) = c / 13 == c % 13
    fun isSuited(c: Int) = c / 13 > c % 13
    fun high(c: Int) = maxOf(c / 13, c % 13)
    fun low(c: Int) = minOf(c / 13, c % 13)
    fun combos(c: Int) = if (isPair(c)) 6 else if (isSuited(c)) 4 else 12

    fun name(c: Int): String = "" + RANKS[high(c)] + RANKS[low(c)] + if (isPair(c)) "" else if (isSuited(c)) "s" else "o"

    /** [a] is strictly stronger than [b] on every count a player could name: ranks, pairing, suitedness. */
    fun dominates(a: Int, b: Int): Boolean {
        if (a == b) return false
        val pa = isPair(a)
        val pb = isPair(b)
        return when {
            pa && pb -> high(a) > high(b)
            pa -> high(a) >= high(b)
            pb -> false
            else -> high(a) >= high(b) && low(a) >= low(b) && (isSuited(a) || !isSuited(b))
        }
    }
}

/** The 1,326 two-card combinations, numbered, with their class. */
internal object Combos {
    const val COUNT = 1326
    val first = IntArray(COUNT)
    val second = IntArray(COUNT)
    val classOf = IntArray(COUNT)
    val indexOf = Array(52) { IntArray(52) }

    init {
        var n = 0
        for (a in 0 until 52) for (b in a + 1 until 52) {
            first[n] = a
            second[n] = b
            classOf[n] = HandClasses.of(a, b)
            indexOf[a][b] = n
            indexOf[b][a] = n
            n++
        }
    }
}

internal fun hasStraight(mask: Int): Boolean {
    for (high in 4..12) {
        val run = 31 shl (high - 4)
        if (mask and run == run) return true
    }
    return mask and 0x100F == 0x100F
}

/**
 * Cards to come that would improve a hand with a draw: nine for a flush draw, four for each rank that
 * completes a straight with a hole card. Zero on the preflop and the river.
 */
internal fun drawOuts(h0: Int, h1: Int, board: IntArray, boardCount: Int): Int {
    if (boardCount !in 3..4) return 0
    var boardMask = 0
    val boardSuit = IntArray(4)
    for (i in 0 until boardCount) {
        boardMask = boardMask or (1 shl rankIdx(board[i]))
        boardSuit[suitIdx(board[i])]++
    }
    val all = boardMask or (1 shl rankIdx(h0)) or (1 shl rankIdx(h1))
    var outs = 0
    for (s in 0..3) {
        val holeIn = (if (suitIdx(h0) == s) 1 else 0) + (if (suitIdx(h1) == s) 1 else 0)
        if (holeIn >= 1 && boardSuit[s] + holeIn == 4) outs += 9
    }
    if (!hasStraight(all)) {
        for (r in 0..12) {
            val bit = 1 shl r
            if (all and bit == 0 && hasStraight(all or bit) && !hasStraight(boardMask or bit)) outs += 4
        }
    }
    return outs
}

/** How draw-heavy the board is, 0 (dry) to 1 (wet): suited and connected cards. */
internal fun wetness(board: IntArray, count: Int): Double {
    if (count < 3) return 0.0
    val suits = IntArray(4)
    var mask = 0
    for (i in 0 until count) {
        suits[suitIdx(board[i])]++
        mask = mask or (1 shl rankIdx(board[i]))
    }
    val suited = when (suits.max()) {
        4, 5 -> 1.0
        3 -> 0.6
        2 -> 0.2
        else -> 0.0
    }
    var best = Integer.bitCount(mask and 0x100F)
    for (high in 4..12) best = maxOf(best, Integer.bitCount(mask and (31 shl (high - 4))))
    val connected = ((best - 1) / 3.0).coerceIn(0.0, 1.0)
    return 0.5 * suited + 0.5 * connected
}

internal fun isPaired(board: IntArray, count: Int): Boolean {
    var mask = 0
    for (i in 0 until count) mask = mask or (1 shl rankIdx(board[i]))
    return Integer.bitCount(mask) < count
}

/** SplitMix64's finaliser over two words: a cheap, well-mixed combination. */
internal fun mix(a: Long, b: Long): Long {
    var z = (a xor (b * -7046029254386353131L)) + -7046029254386353131L
    z = (z xor (z ushr 30)) * -4658895280553007687L
    z = (z xor (z ushr 27)) * -7723592293110705685L
    return z xor (z ushr 31)
}

internal fun SplitMix64.nextDouble(): Double = (nextULong() shr 11).toDouble() / 9007199254740992.0

internal fun SplitMix64.nextInt(bound: Int): Int = nextBounded(bound.toUInt()).toInt()
