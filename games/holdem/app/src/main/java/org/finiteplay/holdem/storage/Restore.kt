package org.finiteplay.holdem.storage

import org.finiteplay.holdem.rules.HoldemSession
import org.finiteplay.holdem.rules.replayHand

/** What the game does on launch, decided by [planRestore] from what the two stores held. */
sealed interface RestorePlan {
    /** The tournament store as it stands: the statistics are kept unless the store itself was unreadable. */
    val stored: StoredTournament

    /** The hand save on disk is stale or unusable and must be cleared before play goes on. */
    val clearHandSave: Boolean

    /**
     * No tournament is in progress: the caller draws a seed and writes [StoredTournament.started]
     * before dealing. [tournamentRecovered] is set when an unreadable store was discarded.
     */
    data class NewTournament(
        override val stored: StoredTournament,
        val tournamentRecovered: Boolean,
        override val clearHandSave: Boolean,
    ) : RestorePlan

    /**
     * The hand in progress, as [session]. [saved] is whether a save holding exactly this log is on
     * disk; when it is not, the hand is being dealt again from its start and must be saved before
     * any of it is shown. [handRecovered] is set when a save existed and could not be used, which
     * is what the interface tells the player about (`UI_SPEC.md` "Screens and States").
     *
     * A session may already be over, when the kill came after the last action and before its
     * settlement; the caller settles it as it would have.
     */
    data class ResumeHand(
        override val stored: StoredTournament,
        val session: HoldemSession,
        val saved: Boolean,
        val handRecovered: Boolean,
        override val clearHandSave: Boolean,
    ) : RestorePlan
}

/**
 * The restore decision, pure (`DESIGN.md` "Persistence"):
 *
 * - No tournament, or an unreadable store: a new tournament, with whatever hand save there was dropped.
 * - A hand save whose number is not the stored tournament's current hand has been recorded already:
 *   it is discarded and the current hand is dealt from its start.
 * - A hand save that is corrupt, or whose log does not replay, or whose seed is not this tournament's
 *   hand's: discarded, and the hand is replayed from its start — same seed, same deck — rather than voided.
 * - No hand save: the hand is dealt from its start, as when the kill came before its first save.
 */
fun planRestore(tournaments: TournamentLoad, hand: HandLoad): RestorePlan {
    val stored = (tournaments as? TournamentLoad.Loaded)?.stored ?: StoredTournament()
    val tournament = stored.tournament
    val unusableSave = hand is HandLoad.Restored
    if (tournament == null) {
        return RestorePlan.NewTournament(stored, tournamentRecovered = tournaments is TournamentLoad.Recovered, clearHandSave = unusableSave)
    }

    fun fromStart(handRecovered: Boolean): RestorePlan {
        val session = replayHand(tournament, emptyList())
            ?: return RestorePlan.NewTournament(stored.copy(tournament = null), tournamentRecovered = true, clearHandSave = unusableSave)
        return RestorePlan.ResumeHand(stored, session, saved = false, handRecovered = handRecovered, clearHandSave = unusableSave)
    }

    return when (hand) {
        HandLoad.Missing -> fromStart(handRecovered = false)
        HandLoad.Recovered -> fromStart(handRecovered = true)
        is HandLoad.Restored -> {
            val saved = hand.hand
            if (saved.handNumber != tournament.handNumber) return fromStart(handRecovered = false)
            val session = if (saved.seed == tournament.handSeed) replayHand(tournament, saved.log) else null
            if (session == null) fromStart(handRecovered = true)
            else RestorePlan.ResumeHand(stored, session, saved = true, handRecovered = false, clearHandSave = false)
        }
    }
}
