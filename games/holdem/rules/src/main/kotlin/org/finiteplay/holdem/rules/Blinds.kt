package org.finiteplay.holdem.rules

/** One level of the schedule (`RULES.md` "The Tournament"). */
data class Blinds(val level: Int, val small: Int, val big: Int)

object BlindSchedule {
    private val table = listOf(
        10 to 20, 15 to 30, 25 to 50, 50 to 100, 75 to 150, 100 to 200, 150 to 300,
        200 to 400, 300 to 600, 400 to 800, 600 to 1_200, 800 to 1_600, 1_000 to 2_000,
    )

    /** Levels past the table keep doubling; the shift is capped so a runaway tournament cannot overflow. */
    private const val MAX_DOUBLINGS = 16

    /** Hands are numbered from 1; the level rises every [Contract.HANDS_PER_LEVEL] of them. */
    fun levelOf(handNumber: Int): Int {
        require(handNumber >= 1) { "hand numbers start at 1, was $handNumber" }
        return (handNumber - 1) / Contract.HANDS_PER_LEVEL + 1
    }

    fun blinds(level: Int): Blinds {
        require(level >= 1) { "levels start at 1, was $level" }
        if (level <= table.size) return Blinds(level, table[level - 1].first, table[level - 1].second)
        val shift = (level - table.size).coerceAtMost(MAX_DOUBLINGS)
        val last = table.last()
        return Blinds(level, last.first shl shift, last.second shl shift)
    }

    fun blindsForHand(handNumber: Int): Blinds = blinds(levelOf(handNumber))

    /** Hands left at this level, this one included: the "blinds rise in N hands" of the status line. */
    fun handsLeftAtLevel(handNumber: Int): Int =
        Contract.HANDS_PER_LEVEL - (handNumber - 1) % Contract.HANDS_PER_LEVEL
}
