package org.finiteplay.holdem.rules

import org.finiteplay.cards.Card

/** `RULES.md` "Hand Rankings", lowest first so `ordinal` orders the categories. */
enum class HandCategory { HIGH_CARD, PAIR, TWO_PAIR, THREE_OF_A_KIND, STRAIGHT, FLUSH, FULL_HOUSE, FOUR_OF_A_KIND, STRAIGHT_FLUSH }

/**
 * A hand's strength: a bigger [strength] beats a smaller one, equal strengths tie, and nothing
 * else — suits never break a tie. The number itself is not a contract; only its order is.
 */
@JvmInline
value class HandValue(val strength: Int) : Comparable<HandValue> {
    override fun compareTo(other: HandValue): Int = strength.compareTo(other.strength)
}

/** The best five-card hand among 5 to 7 [cards]. */
fun evaluate(cards: List<Card>): HandValue {
    val n = cards.size
    require(n in 5..7) { "a hand is the best five of 5 to 7 cards, was $n" }
    var sm = 0L
    var one = 0
    var two = 0
    var three = 0
    var four = 0
    for (i in 0 until n) {
        val id = cards[i].id
        sm = sm or SUIT_BIT[id]
        val b = RANK_BIT[id]
        four = four or (three and b)
        three = three or (two and b)
        two = two or (one and b)
        one = one or b
    }
    return HandValue(strengthOf(sm, one, two, three, four))
}

/**
 * [evaluate] over the first [count] card ids (`Card.id`, 0..51) of [ids], without allocating:
 * the form the opponents' simulations call. The ids must be distinct.
 */
fun evaluate(ids: IntArray, count: Int = ids.size): HandValue {
    require(count in 5..7 && count <= ids.size) { "a hand is the best five of 5 to 7 cards, was $count" }
    var sm = 0L
    var one = 0
    var two = 0
    var three = 0
    var four = 0
    for (i in 0 until count) {
        val id = ids[i]
        sm = sm or SUIT_BIT[id]
        val b = RANK_BIT[id]
        four = four or (three and b)
        three = three or (two and b)
        two = two or (one and b)
        one = one or b
    }
    return HandValue(strengthOf(sm, one, two, three, four))
}

/** The category of the best hand: what its name is called at showdown. */
fun categoryOf(value: HandValue): HandCategory = HandCategory.entries[value.strength ushr CATEGORY_SHIFT]

// Strength is category << 26 | a 26-bit payload. Ranks are bit indices 0 (deuce) .. 12 (ace) in
// 13-bit masks; between equal-size sets the larger mask holds the lexicographically larger ranks.
private const val CATEGORY_SHIFT = 26
private const val PAYLOAD_BITS = 13

private val SUIT_BIT = LongArray(52)
private val RANK_BIT = IntArray(52)
private val STRAIGHT_HIGH = IntArray(1 shl 13)
private val TOP2 = IntArray(1 shl 13)
private val TOP3 = IntArray(1 shl 13)
private val TOP5 = IntArray(1 shl 13)

@Suppress("unused")
private val tablesBuilt = buildTables()

private fun buildTables(): Boolean {
    for (id in 0 until 52) {
        val r = id % 13
        val rankIndex = if (r == 0) 12 else r - 1
        RANK_BIT[id] = 1 shl rankIndex
        SUIT_BIT[id] = (1L shl rankIndex) shl (16 * (id / 13))
    }
    for (mask in 0 until (1 shl 13)) {
        var straight = -1
        for (high in 12 downTo 4) {
            val run = 31 shl (high - 4)
            if (mask and run == run) { straight = high; break }
        }
        if (straight < 0 && mask and 0x100F == 0x100F) straight = 3
        STRAIGHT_HIGH[mask] = straight
        TOP2[mask] = topBits(mask, 2)
        TOP3[mask] = topBits(mask, 3)
        TOP5[mask] = topBits(mask, 5)
    }
    return true
}

private fun topBits(mask: Int, k: Int): Int {
    var rest = mask
    var out = 0
    repeat(k) {
        if (rest != 0) {
            val bit = Integer.highestOneBit(rest)
            out = out or bit
            rest = rest xor bit
        }
    }
    return out
}

private fun hi(mask: Int): Int = 31 - Integer.numberOfLeadingZeros(mask)

private fun strengthOf(suitMasks: Long, one: Int, two: Int, three: Int, four: Int): Int {
    var flush = 0
    for (s in 0 until 4) {
        val m = ((suitMasks ushr (16 * s)) and 0x1FFF).toInt()
        if (Integer.bitCount(m) >= 5) flush = m
    }
    if (flush != 0) {
        val sf = STRAIGHT_HIGH[flush]
        if (sf >= 0) return (8 shl CATEGORY_SHIFT) or sf
    }
    if (four != 0) {
        val q = hi(four)
        return (7 shl CATEGORY_SHIFT) or (q shl PAYLOAD_BITS) or hi(one and (1 shl q).inv())
    }
    if (three != 0) {
        val t = hi(three)
        val rest = two and (1 shl t).inv()
        if (rest != 0) return (6 shl CATEGORY_SHIFT) or (t shl PAYLOAD_BITS) or hi(rest)
    }
    if (flush != 0) return (5 shl CATEGORY_SHIFT) or TOP5[flush]
    val straight = STRAIGHT_HIGH[one]
    if (straight >= 0) return (4 shl CATEGORY_SHIFT) or straight
    if (three != 0) {
        val t = hi(three)
        return (3 shl CATEGORY_SHIFT) or (t shl PAYLOAD_BITS) or TOP2[one and (1 shl t).inv()]
    }
    if (two != 0) {
        if (Integer.bitCount(two) >= 2) {
            val pairs = TOP2[two]
            return (2 shl CATEGORY_SHIFT) or (pairs shl PAYLOAD_BITS) or hi(one and pairs.inv())
        }
        return (1 shl CATEGORY_SHIFT) or (hi(two) shl PAYLOAD_BITS) or TOP3[one and two.inv()]
    }
    return TOP5[one]
}
