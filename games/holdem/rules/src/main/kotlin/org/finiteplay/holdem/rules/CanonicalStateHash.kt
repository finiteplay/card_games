package org.finiteplay.holdem.rules

/**
 * A stable 64-bit content hash of [state], for pinning a reference hand's exact behaviour across
 * engine changes (`EXECUTION_PLAN.md` "RF — Rules Freeze"). Every input is a card id, an ordinal or
 * a plain number — never a JVM identity hash — so the value is the same across processes,
 * platforms and Kotlin/JVM versions.
 *
 * It covers the tournament as dealt, the whole deck, every seat's cards, chips and flags, the
 * betting position, the history and the result. It is never used for play and is not part of the
 * saved format, which is a seed, the tournament at the hand's start and a log.
 */
fun canonicalStateHash(state: HoldemState): Long {
    var h = -3750763034362895579L // FNV-1a 64-bit offset basis
    val prime = 1099511628211L

    fun mixLong(v: Long) {
        h = h xor v
        h *= prime
    }
    fun mixInt(v: Int) = mixLong(v.toLong())
    fun mixBool(v: Boolean) = mixInt(if (v) 1 else 0)
    fun mixInts(values: List<Int>) {
        mixInt(1000 + values.size)
        values.forEach(::mixInt)
    }

    val t = state.tournament
    mixLong(t.seed)
    mixInt(t.rulesVersion)
    mixInt(t.shuffleVersion)
    mixInt(t.handNumber)
    mixInt(t.button)
    mixInts(t.stacks)
    mixInts(t.places.map { it ?: 0 })

    mixInts(state.deck.map { it.id })
    for (cards in state.holeCards) mixInts(cards.map { it.id })
    mixInts(state.board.map { it.id })
    mixInt(state.street.ordinal)
    mixInt(state.phase.ordinal)
    mixInts(state.stacks)
    mixInts(state.streetBets)
    mixInts(state.contributions)
    mixInts(state.returned)
    state.folded.forEach(::mixBool)
    state.allIn.forEach(::mixBool)
    mixInts(state.lastActionBet)
    mixInt(state.currentBet)
    mixInt(state.raiseIncrement)
    mixInt(state.toAct)
    mixInts(state.places.map { it ?: 0 })

    mixInt(2000 + state.history.size)
    for (entry in state.history) {
        mixInt(entry.street.ordinal)
        mixInt(entry.seat)
        when (val action = entry.action) {
            Action.Fold -> mixInt(1)
            Action.Check -> mixInt(2)
            Action.Call -> mixInt(3)
            is Action.Bet -> { mixInt(4); mixInt(action.amount) }
            is Action.Raise -> { mixInt(5); mixInt(action.total) }
            Action.AllIn -> mixInt(6)
        }
        mixInt(entry.chips)
        mixInt(entry.streetTotal)
        mixBool(entry.allIn)
    }

    val result = state.result
    mixBool(result != null)
    if (result != null) {
        mixBool(result.showdown)
        mixInt(3000 + result.awards.size)
        for (award in result.awards) {
            mixInt(award.pot.amount)
            mixInts(award.pot.eligible)
            mixInts(award.winners)
            mixInts(award.shares)
        }
        mixInts(result.payouts)
        for ((seat, value) in result.values.toSortedMap()) {
            mixInt(seat)
            mixInt(value.strength)
        }
        mixInts(result.eliminated)
    }
    return h
}
