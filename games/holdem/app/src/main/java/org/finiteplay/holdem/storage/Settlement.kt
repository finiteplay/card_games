package org.finiteplay.holdem.storage

import org.finiteplay.holdem.rules.HoldemState
import org.finiteplay.holdem.rules.Phase
import org.finiteplay.holdem.rules.nextHand

/**
 * What [stored] becomes when the hand in [finished] has been awarded: the single write the view
 * model performs before it clears the hand (`DESIGN.md` "Persistence", "Settled exactly once").
 *
 * The hand is counted and the tournament moved on together. While the player is still in, the
 * tournament is its state when the next hand is dealt; once the player is out, whether knocked out
 * or having won, the tournament ends and its finishing place is counted. A hand whose number is
 * not the stored tournament's current hand has been recorded already and [stored] comes back
 * unchanged, so settling twice is the same as settling once.
 */
fun settleHand(stored: StoredTournament, finished: HoldemState): StoredTournament {
    require(finished.phase != Phase.BETTING && finished.result != null) { "the hand is not over" }
    val tournament = stored.tournament ?: return stored
    if (finished.tournament.seed != tournament.seed || finished.handNumber != tournament.handNumber) return stored

    val statistics = stored.statistics.afterHand(handFacts(finished))
    val place = finished.places[PLAYER_SEAT]
    return if (place != null) {
        StoredTournament(null, statistics.afterTournament(place))
    } else {
        StoredTournament(nextHand(finished)!!.tournament, statistics)
    }
}

/**
 * What [stored] becomes when the player leaves in [left], the state [org.finiteplay.holdem.rules.leave]
 * returned (`RULES.md` "Leaving"): the tournament ends in the place the player took, as an
 * elimination does. The hand it was left in counts as played unless it was settled already, which
 * is the case when its number is no longer the stored tournament's current hand — leaving between
 * hands. Nothing is stored for a tournament that has ended, so settling again changes nothing.
 */
fun settleTournament(stored: StoredTournament, left: HoldemState): StoredTournament {
    require(left.phase == Phase.TOURNAMENT_OVER && left.places[PLAYER_SEAT] != null) { "the player has not left" }
    val tournament = stored.tournament ?: return stored
    require(left.tournament.seed == tournament.seed) { "another tournament's hand" }

    val statistics = if (left.handNumber == tournament.handNumber) stored.statistics.afterHand(handFacts(left)) else stored.statistics
    return StoredTournament(null, statistics.afterTournament(left.places[PLAYER_SEAT]!!))
}
