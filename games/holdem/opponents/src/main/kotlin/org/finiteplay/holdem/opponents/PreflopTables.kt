package org.finiteplay.holdem.opponents

/**
 * The committed preflop tables, decoded from [PreflopData], which the generator in the test sources
 * writes (`PreflopTableGenerator`) and a test holds to what it would write today.
 *
 * [classLow] and [classHigh] place each of the 169 classes in the ranking by equity against a random
 * hand, as the span of basis points of all combinations it occupies. The push and call-off sets are
 * indexed by effective stack in big blinds, 1 to 15.
 */
internal object PreflopTables {
    val classLow: IntArray = PreflopData.CLASS_LOW
    val classHigh: IntArray = PreflopData.CLASS_HIGH

    private val push = decode(PreflopData.PUSH)
    private val callPush = decode(PreflopData.CALL_PUSH)

    private fun decode(rows: Array<String>) = Array(rows.size) { r -> BooleanArray(HandClasses.COUNT) { rows[r][it] == '1' } }

    /** Classes shoved first in, by seats behind (1..5). */
    fun pushSet(stackBb: Int, behind: Int): BooleanArray = push[(stackBb - 1) * 5 + behind.coerceIn(1, 5) - 1]

    /** Classes that go all in or call off against a push, by the pot-odds bucket (`PreflopRanges.needed`). */
    fun callPushSet(stackBb: Int, bucket: Int): BooleanArray = callPush[(stackBb - 1) * 5 + bucket]
}
