package org.finiteplay.holdem.storage

import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.Contract
import org.finiteplay.holdem.rules.SeatAction
import java.io.ByteArrayOutputStream

/**
 * A hand's move log as bytes: one byte per action — the opcode in the low three bits, the seat in
 * the next three — followed, for Bet and Raise only, by the amount as an unsigned varint
 * (`docs/games/holdem/EXECUTION_PLAN.md` "Deterministic Deal Contract": the alphabet is Fold,
 * Check, Call, Bet, Raise, AllIn, each with its seat). Changing it needs a new rules version.
 */
fun encodeHandLog(log: List<SeatAction>): ByteArray {
    val out = ByteArrayOutputStream()
    for (entry in log) {
        require(entry.seat in 0 until Contract.SEATS) { "no seat ${entry.seat}" }
        out.write(opcodeOf(entry.action) or (entry.seat shl SEAT_SHIFT))
        when (val action = entry.action) {
            is Action.Bet -> writeVarint(out, action.amount)
            is Action.Raise -> writeVarint(out, action.total)
            else -> Unit
        }
    }
    return out.toByteArray()
}

/** The inverse of [encodeHandLog]; throws on anything outside the alphabet, which a caller treats as corruption. */
fun decodeHandLog(bytes: ByteArray): List<SeatAction> {
    val log = ArrayList<SeatAction>()
    var i = 0
    while (i < bytes.size) {
        val head = bytes[i++].toInt() and 0xFF
        require(head and RESERVED_MASK == 0) { "reserved bits set in $head" }
        val opcode = head and OPCODE_MASK
        val seat = head shr SEAT_SHIFT
        require(seat < Contract.SEATS) { "no seat $seat" }
        val action = when (opcode) {
            FOLD -> Action.Fold
            CHECK -> Action.Check
            CALL -> Action.Call
            BET, RAISE -> {
                var amount = 0
                var shift = 0
                while (true) {
                    require(i < bytes.size && shift <= MAX_VARINT_SHIFT) { "truncated or overlong amount" }
                    val b = bytes[i++].toInt() and 0xFF
                    amount = amount or ((b and 0x7F) shl shift)
                    if (b and 0x80 == 0) break
                    shift += 7
                }
                require(amount in 1..Contract.TOTAL_CHIPS) { "implausible amount $amount" }
                if (opcode == BET) Action.Bet(amount) else Action.Raise(amount)
            }
            ALL_IN -> Action.AllIn
            else -> throw IllegalArgumentException("no opcode $opcode")
        }
        log += SeatAction(seat, action)
    }
    return log
}

private const val FOLD = 0
private const val CHECK = 1
private const val CALL = 2
private const val BET = 3
private const val RAISE = 4
private const val ALL_IN = 5
private const val OPCODE_MASK = 0b111
private const val SEAT_SHIFT = 3
private const val RESERVED_MASK = 0xC0
private const val MAX_VARINT_SHIFT = 28

private fun opcodeOf(action: Action): Int = when (action) {
    Action.Fold -> FOLD
    Action.Check -> CHECK
    Action.Call -> CALL
    is Action.Bet -> BET
    is Action.Raise -> RAISE
    Action.AllIn -> ALL_IN
}

private fun writeVarint(out: ByteArrayOutputStream, value: Int) {
    require(value in 1..Contract.TOTAL_CHIPS) { "implausible amount $value" }
    var rest = value
    while (rest >= 0x80) {
        out.write((rest and 0x7F) or 0x80)
        rest = rest ushr 7
    }
    out.write(rest)
}
