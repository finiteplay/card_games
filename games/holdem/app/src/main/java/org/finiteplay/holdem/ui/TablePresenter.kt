package org.finiteplay.holdem.ui

import org.finiteplay.core.ui.sound.SoundEffect
import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.Contract
import org.finiteplay.holdem.rules.HandResult
import org.finiteplay.holdem.rules.HistoryEntry
import org.finiteplay.holdem.rules.HoldemState
import org.finiteplay.holdem.rules.Phase

/** One thing to show: wait [pause], then replace the table with [model], play [cues] and say [announce]. */
data class Step(
    val model: TableModel,
    val pause: Pause,
    val cues: List<SoundEffect> = emptyList(),
    val announce: Announcement? = null,
)

/**
 * Turns committed engine states into the steps a player watches (`UI_SPEC.md` "Opponents' Turns",
 * "Showdown and Results"). Pure: every step is computed from states the session already holds, so
 * nothing it shows is ahead of the session.
 */
object TablePresenter {
    private val SEATS = Contract.SEATS

    /** A new hand as dealt: the blinds in, the player's two cards up, no action yet. */
    fun dealt(state: HoldemState): TableModel {
        val tournament = state.tournament
        val blinds = tournament.blinds
        val posted = List(SEATS) { seat ->
            when {
                !state.inHand(seat) -> 0
                seat == state.smallBlindSeat -> minOf(blinds.small, tournament.stacks[seat])
                seat == state.bigBlindSeat -> minOf(blinds.big, tournament.stacks[seat])
                else -> 0
            }
        }
        val seats = List(SEATS) { seat ->
            val inHand = state.inHand(seat)
            val stack = tournament.stacks[seat] - posted[seat]
            SeatModel(
                stack = stack,
                bet = posted[seat],
                inHand = inHand,
                folded = false,
                allIn = inHand && stack == 0,
                lastAction = null,
                cards = if (seat == 0 && inHand) state.holeCards[0] else null,
                place = tournament.places[seat],
                award = 0,
                handValue = null,
            )
        }
        return TableModel(
            handNumber = state.handNumber,
            blinds = blinds,
            button = state.button,
            smallBlind = state.smallBlindSeat,
            bigBlind = state.bigBlindSeat,
            seats = seats,
            board = emptyList(),
            pot = posted.sum(),
            potCount = potCountOf(posted, seats),
            contributions = posted,
            returned = List(SEATS) { 0 },
            actor = null,
            thinking = false,
            stage = Stage.OPPONENT_TURN,
            banner = null,
            highlight = null,
            mark = null,
            confetti = 0,
            lossWash = false,
            finishingPlace = null,
        )
    }

    /**
     * The table as it stood after [log] had been played from the start of the hand [start]: what a
     * hand restored from a save shows. Built by stepping through the same states a live hand shows,
     * keeping only the last model, so a restored table cannot differ from the one that was lost.
     */
    fun restored(start: HoldemState, log: List<org.finiteplay.holdem.rules.SeatAction>): TableModel {
        var state = start
        var model = dealt(state)
        for (entry in log) {
            state = checkNotNull(org.finiteplay.holdem.rules.apply(state, entry.seat, entry.action)) { "a saved action was illegal" }
            model = afterAction(model, state).last().model
        }
        return model
    }

    /** The table after the player left: as it stood, with the tournament over. */
    fun left(shown: TableModel, state: HoldemState): TableModel =
        shown.copy(actor = null, thinking = false, stage = Stage.TOURNAMENT_OVER, finishingPlace = state.places[0])

    /**
     * The steps from [shown], the table as last displayed, to [after], the state once the last action in
     * its history was applied: the action, then — if it ended the street — the bets gathering, each
     * street's cards, and at the hand's end each hand turning over and each pot paid.
     */
    fun afterAction(shown: TableModel, after: HoldemState): List<Step> {
        val entry = after.history.last()
        val steps = ArrayList<Step>()
        var model = applyEntry(shown, entry)
        steps += Step(model, Pause.NONE, cueFor(entry), Announcement.Acted(entry.seat, entry))

        val ended = after.phase != Phase.BETTING || after.board.size != shown.board.size
        if (!ended) return steps

        model = gather(model, after)
        steps += Step(model, Pause.GATHER)

        for (size in nextBoardSizes(shown.board.size, after.board.size)) {
            val newCards = after.board.subList(model.board.size, size)
            model = model.copy(board = after.board.take(size), seats = model.seats.map { it.copy(lastAction = null) })
            steps += Step(model, Pause.STREET, listOf(SoundEffect.DEAL), Announcement.BoardDealt(model.street, newCards.toList()))
        }
        if (after.phase != Phase.BETTING) steps += resultSteps(model, after)
        return steps
    }

    // ---- an action -------------------------------------------------------------------------

    private fun applyEntry(shown: TableModel, entry: HistoryEntry): TableModel {
        val seats = shown.seats.toMutableList()
        val seat = seats[entry.seat]
        seats[entry.seat] = seat.copy(
            stack = seat.stack - entry.chips,
            bet = entry.streetTotal,
            folded = seat.folded || entry.action === Action.Fold,
            allIn = entry.allIn,
            lastAction = entry,
        )
        val contributions = shown.contributions.toMutableList().also { it[entry.seat] += entry.chips }
        return shown.copy(
            seats = seats,
            contributions = contributions,
            pot = contributions.sum(),
            potCount = potCountOf(contributions, seats),
            actor = null,
            thinking = false,
        )
    }

    private fun cueFor(entry: HistoryEntry): List<SoundEffect> = listOf(
        when {
            entry.action === Action.Fold -> SoundEffect.MOVE
            entry.action === Action.Check -> SoundEffect.ACTION_CONFIRM
            entry.action === Action.AllIn || entry.allIn -> SoundEffect.WAGER_COMMIT
            else -> SoundEffect.CHIP
        },
    )

    /** The street's bets join the pot, and whatever nobody matched goes back to its owner. */
    private fun gather(model: TableModel, after: HoldemState): TableModel {
        val contributions = model.contributions.toMutableList()
        val seats = model.seats.mapIndexed { seat, shown ->
            val back = after.returned[seat] - model.returned[seat]
            contributions[seat] -= back
            shown.copy(stack = shown.stack + back, bet = 0, lastAction = null)
        }
        return model.copy(
            seats = seats,
            contributions = contributions,
            returned = after.returned,
            pot = contributions.sum(),
            potCount = potCountOf(contributions, seats),
        )
    }

    private fun nextBoardSizes(from: Int, to: Int): List<Int> =
        listOf(3, 4, 5).filter { it > from && it <= to }

    /** One pot, and one more for each all-in that is for less than the top bet: unmatched bets and blinds are not side pots. */
    private fun potCountOf(contributions: List<Int>, seats: List<SeatModel>): Int {
        val contesting = contributions.indices.filter { seats[it].contesting }
        val top = contesting.maxOfOrNull { contributions[it] } ?: return 1
        return contesting.filter { seats[it].allIn && contributions[it] in 1 until top }.map { contributions[it] }.distinct().size + 1
    }

    // ---- the hand's end --------------------------------------------------------------------

    private fun resultSteps(model: TableModel, after: HoldemState): List<Step> {
        val result = checkNotNull(after.result)
        val steps = ArrayList<Step>()
        var shown = model.copy(stage = Stage.SHOWDOWN)

        if (result.showdown) {
            for (seat in revealOrder(after, result)) {
                val value = result.values.getValue(seat)
                val seats = shown.seats.toMutableList()
                seats[seat] = seats[seat].copy(cards = after.holeCards[seat], handValue = value)
                shown = shown.copy(seats = seats)
                steps += Step(shown, Pause.REVEAL, announce = Announcement.Showed(seat, handName(after.holeCards[seat] + after.board, value)))
            }
        }

        // The main pot is paid last (`RULES.md` "Pots and Showdown"): the awards are built bottom-up.
        val payOrder = result.awards.indices.reversed()
        var heardWin = false
        for ((n, index) in payOrder.withIndex()) {
            val award = result.awards[index]
            val seats = shown.seats.toMutableList()
            for ((i, winner) in award.winners.withIndex()) {
                val share = award.shares[i]
                seats[winner] = seats[winner].copy(stack = seats[winner].stack + share, award = seats[winner].award + share)
            }
            val highlight = if (result.showdown) {
                award.winners.flatMap { bestFive(after.holeCards[it] + after.board, result.values.getValue(it)) }.toSet()
            } else {
                null
            }
            val kind = when {
                result.awards.size == 1 -> PotKind.SINGLE
                index == 0 -> PotKind.MAIN
                else -> PotKind.SIDE
            }
            val cues = if (0 in award.winners && !heardWin) {
                heardWin = true
                listOf(if (award.shares[award.winners.indexOf(0)] * 2 > Contract.TOTAL_CHIPS) SoundEffect.NATURAL_WIN else SoundEffect.WIN)
            } else {
                emptyList()
            }
            shown = shown.copy(
                seats = seats,
                pot = shown.pot - award.pot.amount,
                potCount = result.awards.size - n - 1,
                banner = PotBanner(kind, award.pot.amount),
                highlight = highlight,
            )
            steps += Step(shown, if (n == 0) Pause.POT_FIRST else Pause.POT_NEXT, cues, Announcement.Won(award.winners, award.pot.amount))
        }
        steps += finalStep(shown, after, result)
        return steps
    }

    /** Clockwise from the last aggressor (the first seat left of the button when no one bet), live hands only. */
    internal fun revealOrder(after: HoldemState, result: HandResult): List<Int> {
        var high = 0
        var street = after.history.firstOrNull()?.street
        var aggressor: Int? = null
        for (entry in after.history) {
            if (entry.street != street) {
                street = entry.street
                high = 0
            }
            val raised = entry.streetTotal > high && entry.action !== Action.Call && entry.action !== Action.Check && entry.action !== Action.Fold
            if (raised) aggressor = entry.seat
            high = maxOf(high, entry.streetTotal)
        }
        val start = aggressor ?: ((after.button + 1) % SEATS)
        return result.values.keys.sortedBy { (it - start + SEATS) % SEATS }
    }

    private fun finalStep(shown: TableModel, after: HoldemState, result: HandResult): Step {
        val playerIn = after.contesting(0)
        val net = after.stacks[0] - after.tournament.stacks[0]
        val mark = if (playerIn) ResultMarkModel(if (net > 0) MarkKind.WIN else if (net == 0) MarkKind.EVEN else MarkKind.LOSE, net) else null
        val soleWinner = result.showdown && result.awards.any { it.winners == listOf(0) }
        val confetti = when {
            !soleWinner -> 0
            result.payouts[0] * 2 > Contract.TOTAL_CHIPS -> 84
            else -> 44
        }
        val lostAllIn = result.showdown && playerIn && net < 0 && after.allIn[0]
        val over = after.phase == Phase.TOURNAMENT_OVER || after.playerOut
        val cues = buildList {
            if (lostAllIn) add(SoundEffect.ROUND_LOSS)
            if (over) add(if (after.places[0] == 1) SoundEffect.WIN else SoundEffect.GAME_OVER)
        }
        val seats = shown.seats.mapIndexed { seat, model -> model.copy(place = after.places[seat]) }
        return Step(
            model = shown.copy(
                seats = seats,
                stage = if (over) Stage.TOURNAMENT_OVER else Stage.HAND_OVER,
                banner = null,
                mark = mark,
                confetti = confetti,
                lossWash = lostAllIn,
                finishingPlace = after.places[0],
            ),
            pause = Pause.RESULT,
            cues = cues,
        )
    }
}
