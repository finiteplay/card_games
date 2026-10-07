package org.finiteplay.holdem.rules

import org.finiteplay.cards.Card

private fun <T> List<T>.with(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }

/**
 * Deals a hand for [tournament] (`RULES.md` "The Deal", "The Button and the Blinds"): hole cards
 * one at a time from the first live seat to the button's left, twice around; blinds posted, short
 * ones all in; then play runs on to the first decision, or straight through to the end of the hand
 * when no one can bet.
 *
 * [deck] is a seam for tests; a hand that will be saved and replayed is always dealt from its own
 * seed's deck, which is what every public entry point does.
 */
internal fun dealHand(tournament: Tournament, deck: List<Card> = deckFor(tournament.handSeed)): HoldemState {
    val alive = tournament.alive
    require(alive.size >= 2) { "a hand needs two players, ${alive.size} are in" }
    require(deck.size == 52) { "a deck holds 52 cards, was ${deck.size}" }
    val seats = Contract.SEATS
    val order = (1..seats).map { (tournament.button + it) % seats }.filter { it in alive }
    val hole = MutableList<List<Card>>(seats) { emptyList() }
    for ((i, seat) in order.withIndex()) hole[seat] = listOf(deck[i], deck[order.size + i])

    val blinds = tournament.blinds
    val small = if (alive.size == 2) tournament.button else order[0]
    val big = order.first { it != small }
    val stacks = tournament.stacks.toMutableList()
    val bets = MutableList(seats) { 0 }
    val allIn = MutableList(seats) { false }
    for ((seat, blind) in listOf(small to blinds.small, big to blinds.big)) {
        val put = minOf(blind, stacks[seat])
        stacks[seat] -= put
        bets[seat] = put
        allIn[seat] = stacks[seat] == 0
    }
    val started = HoldemState(
        tournament = tournament,
        deck = deck,
        holeCards = hole.toList(),
        board = emptyList(),
        street = Street.PREFLOP,
        phase = Phase.BETTING,
        stacks = stacks.toList(),
        streetBets = bets.toList(),
        contributions = bets.toList(),
        returned = List(seats) { 0 },
        folded = List(seats) { false },
        allIn = allIn.toList(),
        lastActionBet = List(seats) { -1 },
        // A blind that is short is still a bet of the whole blind to match (`RULES.md` "Betting").
        currentBet = blinds.big,
        raiseIncrement = blinds.big,
        toAct = -1,
        history = emptyList(),
        places = tournament.places,
        result = null,
    )
    return if (started.bettingPossible()) started.copy(toAct = started.nextToAct(big)!!) else endStreet(started)
}

/** What the seat to act may do, or null when no one is to act. */
fun legalActions(state: HoldemState): LegalActions? =
    if (state.phase == Phase.BETTING && state.toAct >= 0) legalActionsFor(state, state.toAct) else null

private fun legalActionsFor(state: HoldemState, seat: Int): LegalActions {
    val stack = state.stacks[seat]
    val streetBet = state.streetBets[seat]
    val toCall = state.toCall(seat)
    val maxTotal = streetBet + stack
    var bet: IntRange? = null
    var raise: IntRange? = null
    var allInTo: Int? = null
    if (state.currentBet == 0) {
        if (stack > 0) {
            if (maxTotal >= state.blinds.big) bet = state.blinds.big..maxTotal
            allInTo = maxTotal
        }
    } else if (stack > toCall && state.bettingOpenTo(seat)) {
        val minTotal = state.currentBet + state.raiseIncrement
        if (maxTotal >= minTotal) raise = minTotal..maxTotal
        allInTo = maxTotal
    }
    return LegalActions(
        seat = seat,
        stack = stack,
        toCall = toCall,
        canFold = toCall > 0,
        canCheck = toCall == 0,
        callAmount = if (toCall > 0) minOf(toCall, stack) else null,
        bet = bet,
        raise = raise,
        allInTo = allInTo,
    )
}

/**
 * Applies [action] by [seat], or returns null when it is not currently legal — the wrong seat, or
 * an action or amount [legalActions] does not offer. The one place a hand's state changes; the
 * return of uncalled bets, the next street, the run-out, the showdown and the award all happen
 * inside this call, so a hand never rests half-way through one (`RULES.md` "Betting").
 */
fun apply(state: HoldemState, seat: Int, action: Action): HoldemState? {
    val legal = legalActions(state) ?: return null
    if (seat != legal.seat || !legal.allows(action)) return null
    val streetBet = state.streetBets[seat]
    val stack = state.stacks[seat]
    val put = when (action) {
        Action.Fold, Action.Check -> 0
        Action.Call -> legal.callAmount!!
        is Action.Bet -> action.amount - streetBet
        is Action.Raise -> action.total - streetBet
        Action.AllIn -> stack
    }
    val total = streetBet + put
    val raising = action is Action.Bet || action is Action.Raise || action === Action.AllIn
    val level = if (raising) total else state.currentBet
    val increment = if (raising) total - state.currentBet else 0
    val left = stack - put
    val next = state.copy(
        stacks = state.stacks.with(seat, left),
        streetBets = state.streetBets.with(seat, total),
        contributions = state.contributions.with(seat, state.contributions[seat] + put),
        folded = if (action === Action.Fold) state.folded.with(seat, true) else state.folded,
        allIn = if (left == 0 && action !== Action.Fold) state.allIn.with(seat, true) else state.allIn,
        lastActionBet = state.lastActionBet.with(seat, level),
        currentBet = level,
        raiseIncrement = maxOf(state.raiseIncrement, increment),
        history = state.history + HistoryEntry(state.street, seat, action, put, total, left == 0 && action !== Action.Fold),
    )
    return afterAction(next, seat)
}

// ---- the street and the hand ---------------------------------------------------------------

/** A betting round waits on a contesting seat that has chips and has not acted, or has not matched the bet. */
private fun HoldemState.needsToAct(seat: Int): Boolean =
    active(seat) && (lastActionBet[seat] < 0 || streetBets[seat] < currentBet)

private fun HoldemState.nextToAct(after: Int): Int? {
    for (k in 1..Contract.SEATS) {
        val seat = (after + k) % Contract.SEATS
        if (needsToAct(seat)) return seat
    }
    return null
}

/** `RULES.md` "Betting": betting only starts while at least two contesting seats still have chips. */
private fun HoldemState.bettingPossible(): Boolean = (0 until Contract.SEATS).count(::active) >= 2

private fun afterAction(state: HoldemState, actor: Int): HoldemState {
    if ((0 until Contract.SEATS).count(state::contesting) == 1) return finishHand(returnUncalled(state))
    val next = state.nextToAct(actor)
    return if (next != null) state.copy(toAct = next) else endStreet(state)
}

/**
 * The round is over: the uncalled part of a bet goes back (`RULES.md` "Betting"), then streets are
 * dealt until one has betting — or until the river is out, and the showdown comes.
 */
private fun endStreet(ended: HoldemState): HoldemState {
    var state = returnUncalled(ended)
    while (true) {
        if (state.street == Street.RIVER) return finishHand(state)
        state = dealStreet(state)
        if (state.bettingPossible()) return state.copy(toAct = state.nextToAct(state.button)!!)
    }
}

/** Flop, turn or river from the deck positions the contract fixes: a burn, then the card or cards. */
private fun dealStreet(state: HoldemState): HoldemState {
    val dealt = 2 * state.seatsInHand.size
    val (street, board) = when (state.street) {
        Street.PREFLOP -> Street.FLOP to state.deck.subList(dealt + 1, dealt + 4)
        Street.FLOP -> Street.TURN to state.board + state.deck[dealt + 5]
        Street.TURN -> Street.RIVER to state.board + state.deck[dealt + 7]
        Street.RIVER -> error("no street after the river")
    }
    return state.copy(
        street = street,
        board = board.toList(),
        streetBets = List(Contract.SEATS) { 0 },
        lastActionBet = List(Contract.SEATS) { -1 },
        currentBet = 0,
        raiseIncrement = state.blinds.big,
        toAct = -1,
    )
}

/** The part of the highest bet that no one matched goes back to its owner (`RULES.md` "Betting"). */
private fun returnUncalled(state: HoldemState): HoldemState {
    val top = state.streetBets.max()
    if (top == 0) return state
    val owner = state.streetBets.indexOf(top)
    if (state.streetBets.count { it == top } != 1) return state
    val second = state.streetBets.filterIndexed { i, _ -> i != owner }.max()
    val back = top - second
    if (back <= 0) return state
    return state.copy(
        stacks = state.stacks.with(owner, state.stacks[owner] + back),
        streetBets = state.streetBets.with(owner, second),
        contributions = state.contributions.with(owner, state.contributions[owner] - back),
        returned = state.returned.with(owner, state.returned[owner] + back),
    )
}

/**
 * Awards the pots (`RULES.md` "Pots and Showdown") and settles the tournament: knocked-out seats get
 * their places, and the tournament is over when one seat holds every chip.
 */
private fun finishHand(state: HoldemState): HoldemState {
    val contesting = (0 until Contract.SEATS).filter(state::contesting)
    val showdown = contesting.size >= 2
    val values = if (showdown) contesting.associateWith { evaluate(state.holeCards[it] + state.board) } else emptyMap()
    val pots = sidePots(state.contributions, List(Contract.SEATS, state::contesting))
    val awards = awardPots(pots, values.ifEmpty { contesting.associateWith { HandValue(0) } }, state.button)
    val payouts = MutableList(Contract.SEATS) { 0 }
    for (award in awards) for ((i, seat) in award.winners.withIndex()) payouts[seat] += award.shares[i]
    val stacks = state.stacks.mapIndexed { seat, chips -> chips + payouts[seat] }

    val alive = state.tournament.alive
    val knockedOut = state.seatsInHand.filter { stacks[it] == 0 }
    val places = state.places.toMutableList()
    for ((seat, place) in placesForKnockouts(knockedOut, state.tournament.stacks, alive.size)) places[seat] = place
    val survivors = alive.filter { it !in knockedOut }
    if (survivors.size == 1) places[survivors[0]] = 1
    return state.copy(
        stacks = stacks,
        streetBets = List(Contract.SEATS) { 0 },
        places = places,
        phase = if (survivors.size == 1) Phase.TOURNAMENT_OVER else Phase.HAND_OVER,
        toAct = -1,
        result = HandResult(showdown, awards, payouts, values, knockedOut),
    )
}

// ---- the tournament ------------------------------------------------------------------------

/**
 * The next hand once this one is awarded (`RULES.md` "The Button and the Blinds"): the button moves
 * to the next seat still in the tournament, the level follows the hand number. Null unless a hand
 * is over and the tournament is not.
 */
fun nextHand(state: HoldemState): HoldemState? {
    if (state.phase != Phase.HAND_OVER) return null
    val alive = (0 until Contract.SEATS).filter { state.places[it] == null }
    val button = (1..Contract.SEATS).map { (state.button + it) % Contract.SEATS }.first { it in alive }
    return dealHand(
        Tournament(
            seed = state.tournament.seed,
            handNumber = state.handNumber + 1,
            button = button,
            stacks = state.stacks,
            places = state.places,
            rulesVersion = state.tournament.rulesVersion,
            shuffleVersion = state.tournament.shuffleVersion,
        ),
    )
}

/**
 * The player leaves (`RULES.md` "Leaving"): they finish in the place they would take if knocked
 * out now, the chips they have in the hand are lost with it, and the tournament is over. Null when
 * the player is already out or the tournament is already over.
 */
fun leave(state: HoldemState): HoldemState? {
    if (state.phase == Phase.TOURNAMENT_OVER || state.places[0] != null) return null
    return state.copy(
        places = state.places.with(0, state.places.count { it == null }),
        phase = Phase.TOURNAMENT_OVER,
        toAct = -1,
    )
}
