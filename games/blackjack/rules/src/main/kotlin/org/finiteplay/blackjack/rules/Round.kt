package org.finiteplay.blackjack.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.DECK_SIZE
import org.finiteplay.cards.shuffleDeckIndices
import org.finiteplay.core.session.Session

/** Six decks, shuffled fresh for every round (`RULES.md` "The Shoe"). */
const val SHOE_DECKS = 6
const val SHOE_SIZE = SHOE_DECKS * DECK_SIZE

/** A round's most hands: the original plus three splits (`RULES.md` "Table Rules"). */
const val MAX_HANDS = 4

/**
 * The shoe for [seed]: shuffled value `v` names the card at canonical index `v % DECK_SIZE`, and
 * the six copies of a card are interchangeable (`EXECUTION_PLAN.md` "Deterministic Shoe Contract").
 */
fun shoeFor(seed: Long): List<Card> = shuffleDeckIndices(seed, SHOE_SIZE).map { Card.fromId(it % DECK_SIZE) }

/** The bankroll less every stake already on the table: bets, doubles, split hands and insurance. */
fun availableBankroll(state: BlackjackState, bankroll: Int): Int =
    bankroll - state.hands.sumOf { it.bet } - state.insuranceStake

/**
 * What the player may do now, and nothing else — an action absent here is never shown rather than
 * shown and refused (`RULES.md` "Legal Actions").
 */
fun legalDecisions(state: BlackjackState, bankroll: Int): Set<Decision> {
    when (state.phase) {
        Phase.SETTLED -> return emptySet()
        Phase.INSURANCE -> return setOf(Decision.TAKE_INSURANCE, Decision.DECLINE_INSURANCE)
        Phase.PLAYING -> Unit
    }
    val hand = state.hands[state.activeHand]
    val available = availableBankroll(state, bankroll)
    return buildSet {
        if (hand.value.total < 21) add(Decision.HIT)
        add(Decision.STAND)
        if (hand.cards.size == 2 && !hand.splitAces && available >= hand.bet) add(Decision.DOUBLE)
        if (hand.isPair && state.splitCount < MAX_HANDS - 1 && available >= hand.bet) add(Decision.SPLIT)
    }
}

/**
 * Deals a round: the bet is fixed, four cards are dealt in the contract's order, and the round
 * runs on to its first decision (or all the way to settlement, if the peek or the player's own
 * blackjack ends it).
 *
 * [shoe] is a seam for tests and the debug fixture loader; it must be the shoe for [seed] in any
 * round that will be saved and replayed.
 */
fun deal(
    seed: Long,
    bet: Int,
    bankroll: Int,
    shoe: List<Card> = shoeFor(seed),
): BlackjackState {
    require(bet in Chips.MIN_BET..Chips.maxBetFor(bankroll) && bet % Chips.BET_STEP == 0) {
        "bet $bet is not offered at a bankroll of $bankroll"
    }
    require(shoe.size >= SHOE_SIZE) { "shoe holds ${shoe.size} cards" }
    val started = BlackjackState(
        seed = seed,
        rulesVersion = RULES_VERSION,
        shuffleVersion = SHUFFLE_VERSION,
        bet = bet,
        shoe = shoe,
        // Player, dealer up, player, dealer hole (`RULES.md` "The Shoe").
        shoePosition = 4,
        dealer = listOf(shoe[1], shoe[3]),
        holeRevealed = false,
        hands = listOf(PlayerHand(cards = listOf(shoe[0], shoe[2]), bet = bet)),
        activeHand = 0,
        insuranceStake = 0,
        phase = Phase.PLAYING,
        settlement = null,
    )
    // Insurance is offered only on an Ace, and only when the bankroll covers half the bet.
    if (started.dealerUpCard.isAce && availableBankroll(started, bankroll) >= bet / 2) {
        return started.copy(phase = Phase.INSURANCE)
    }
    return afterInsurance(started)
}

/**
 * Applies [decision], or returns null when it is not currently legal. The one place a round's
 * state changes; everything a decision sets off — the peek, the next split hand's card, the
 * dealer's play, settlement — happens inside this call, so a round never rests half-way through
 * one (`RULES.md` "Order of Play").
 */
fun BlackjackState.decide(decision: Decision, bankroll: Int): BlackjackState? {
    if (decision !in legalDecisions(this, bankroll)) return null
    return when (decision) {
        Decision.TAKE_INSURANCE -> afterInsurance(copy(insuranceStake = bet / 2, phase = Phase.PLAYING))
        Decision.DECLINE_INSURANCE -> afterInsurance(copy(phase = Phase.PLAYING))
        Decision.HIT -> {
            val hand = hands[activeHand]
            val hit = hand.copy(cards = hand.cards + shoe[shoePosition])
            advance(copy(hands = hands.replace(activeHand, hit), shoePosition = shoePosition + 1))
        }
        Decision.STAND -> advance(withActive { it.copy(stood = true) })
        Decision.DOUBLE -> {
            val hand = hands[activeHand]
            val doubled = hand.copy(cards = hand.cards + shoe[shoePosition], bet = hand.bet * 2, doubled = true)
            advance(copy(hands = hands.replace(activeHand, doubled), shoePosition = shoePosition + 1))
        }
        Decision.SPLIT -> {
            val hand = hands[activeHand]
            val aces = hand.cards[0].isAce
            val first = PlayerHand(listOf(hand.cards[0]), bet = hand.bet, fromSplit = true, splitAces = aces)
            val second = PlayerHand(listOf(hand.cards[1]), bet = hand.bet, fromSplit = true, splitAces = aces)
            advance(copy(hands = hands.take(activeHand) + first + second + hands.drop(activeHand + 1)))
        }
    }
}

// ---- internals ------------------------------------------------------------------------------

private fun <T> List<T>.replace(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }

private fun BlackjackState.withActive(change: (PlayerHand) -> PlayerHand): BlackjackState =
    copy(hands = hands.replace(activeHand, change(hands[activeHand])))

/**
 * The peek, then player blackjack, then on to the player's decisions (`RULES.md` "Order of Play"
 * steps 3–5). Reached once insurance is decided, or straight from the deal when there is none.
 */
private fun afterInsurance(state: BlackjackState): BlackjackState {
    val up = state.dealerUpCard
    // The dealer peeks on an Ace or a ten-value card: the hole card is looked at, not shown, unless
    // it makes blackjack.
    val dealerBlackjack = (up.isAce || isTenValue(up.rank)) && isBlackjackShape(state.dealer)
    if (dealerBlackjack) return settle(state.copy(holeRevealed = true), dealerBlackjack = true)

    val hand = state.hands[0]
    if (isBlackjackShape(hand.cards)) return settle(state.copy(holeRevealed = true), dealerBlackjack = false)

    return advance(state.copy(phase = Phase.PLAYING))
}

/**
 * Moves play to the next hand that still needs a decision, dealing a split hand its second card as
 * it becomes the active hand. When every hand is complete the dealer plays and the round settles,
 * in this same call.
 */
private fun advance(state: BlackjackState): BlackjackState {
    var current = state
    while (true) {
        val index = current.hands.indexOfFirst { !it.complete }
        if (index < 0) return dealerPlaysAndSettles(current)
        val hand = current.hands[index]
        if (hand.pendingSecondCard) {
            val next = hand.copy(cards = hand.cards + current.shoe[current.shoePosition])
            current = current.copy(
                hands = current.hands.replace(index, next),
                shoePosition = current.shoePosition + 1,
                activeHand = index,
            )
            continue
        }
        return current.copy(activeHand = index, phase = Phase.PLAYING)
    }
}

/**
 * Dealer play (`RULES.md` "Dealer Play"): the hole card turns over; the dealer then draws nothing
 * if every hand has busted, and otherwise hits on 16 or less and stands on 17 or more, soft or
 * hard. The house's own fixed turn, no setting reaches it.
 */
private fun dealerPlaysAndSettles(state: BlackjackState): BlackjackState {
    var dealer = state.dealer
    var position = state.shoePosition
    if (!state.hands.all { it.busted }) {
        while (handValue(dealer).total <= 16) {
            dealer = dealer + state.shoe[position]
            position++
        }
    }
    return settle(state.copy(dealer = dealer, shoePosition = position, holeRevealed = true), dealerBlackjack = false)
}

/**
 * Settlement (`RULES.md` "Settlement"): every hand on its own stake, plus the insurance, as one
 * [Settlement] the bankroll is updated by once.
 */
internal fun settle(state: BlackjackState, dealerBlackjack: Boolean): BlackjackState {
    val dealerTotal = state.dealerValue
    val results = state.hands.map { hand ->
        val playerBlackjack = !hand.fromSplit && isBlackjackShape(hand.cards)
        when {
            dealerBlackjack ->
                if (playerBlackjack) HandResult(HandOutcome.PUSH, hand.bet, 0) else HandResult(HandOutcome.LOSS, hand.bet, -hand.bet)
            playerBlackjack -> HandResult(HandOutcome.BLACKJACK, hand.bet, hand.bet * 3 / 2)
            hand.busted -> HandResult(HandOutcome.BUST, hand.bet, -hand.bet)
            dealerTotal.busted -> HandResult(HandOutcome.WIN, hand.bet, hand.bet)
            hand.value.total > dealerTotal.total -> HandResult(HandOutcome.WIN, hand.bet, hand.bet)
            hand.value.total < dealerTotal.total -> HandResult(HandOutcome.LOSS, hand.bet, -hand.bet)
            else -> HandResult(HandOutcome.PUSH, hand.bet, 0)
        }
    }
    val insuranceDelta = when {
        state.insuranceStake == 0 -> 0
        dealerBlackjack -> state.insuranceStake * 2
        else -> -state.insuranceStake
    }
    return state.copy(phase = Phase.SETTLED, settlement = Settlement(results, insuranceDelta, dealerBlackjack))
}

// ---- the round as a Session ------------------------------------------------------------------

/**
 * A round on `core/session`'s [Session]: the board and the log of decisions that produced it.
 *
 * Only the commit-and-log half is used. The undo stack is never written — [decide] builds the next
 * session directly rather than through `commit`, which would push the prior board — so
 * `canUndo` is false for the life of a round and no undo is reachable (`DESIGN.md` "What Blackjack
 * is not").
 */
typealias BlackjackSession = Session<BlackjackState, Decision>

fun startRound(seed: Long, bet: Int, bankroll: Int, shoe: List<Card> = shoeFor(seed)): BlackjackSession =
    Session.start(deal(seed, bet, bankroll, shoe))

/** Applies [decision] and logs it, or returns null when it is not currently legal. */
fun BlackjackSession.decide(decision: Decision, bankroll: Int): BlackjackSession? {
    val next = state.decide(decision, bankroll) ?: return null
    return copy(state = next, log = log + decision)
}

/**
 * Rebuilds a round from its seed, bet and decisions — the only way a saved round is restored.
 * Null when the log is not a legal play of that round, which a caller treats as a corrupt save.
 * [shoe] is the same test seam [deal] has; a restored round is always replayed from its own seed's shoe.
 */
fun replayRound(
    seed: Long,
    bet: Int,
    bankroll: Int,
    log: List<Decision>,
    shoe: List<Card> = shoeFor(seed),
): BlackjackSession? {
    var session = runCatching { startRound(seed, bet, bankroll, shoe) }.getOrNull() ?: return null
    for (decision in log) session = session.decide(decision, bankroll) ?: return null
    return session
}

/** The log as bytes, one opcode per decision, for the active-game record. */
fun encodeLog(log: List<Decision>): ByteArray = ByteArray(log.size) { log[it].opcode }

/** The inverse of [encodeLog]; throws on an opcode outside the alphabet, which a caller treats as corruption. */
fun decodeLog(bytes: ByteArray): List<Decision> = bytes.map(Decision::fromOpcode)
