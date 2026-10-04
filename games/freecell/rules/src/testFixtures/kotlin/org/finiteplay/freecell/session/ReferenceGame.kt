package org.finiteplay.freecell.session

import org.finiteplay.freecell.layout.GameVersions
import org.finiteplay.freecell.layout.canonicalStateHash
import org.finiteplay.freecell.rules.Move

/**
 * A fixed, hand-verified sequence of legal moves from a known seed, committed by RF
 * (`docs/games/freecell/EXECUTION_PLAN.md` "RF — Rules Freeze") so the JVM engine and Android
 * runtime can both be checked against the same reference playthrough — the gameplay analogue of
 * `core:cards`' own `DECK_SHUFFLE_REFERENCE_VECTORS`. Exercises four of the five move kinds
 * (`TableauToTableau`, `TableauToFreeCell`, `FreeCellToTableau`, `TableauToFoundation`), an
 * automation-setting change, a real automatic cascade once automation is back on, and an undo.
 *
 * `FreeCellToFoundation` is not exercised: a free cell only ever holds a card set aside because
 * nothing else wanted it yet, so a card sitting in one becoming foundation-eligible while the
 * player still holds it there — rather than it having already gone back to the tableau, or been
 * claimed by the automatic cascade the moment it turned safe — is a genuinely narrow window, and
 * this deal never opened one in the moves this log actually plays. It gets its own dedicated
 * coverage in `LegalMovesTest`/`ApplyMoveTest`/`AutomationTest` instead.
 *
 * [REFERENCE_GAME_HASH] changing for this fixed seed/log/versions means a rules, automation, or
 * shuffle change altered real gameplay output, which requires a new rules or shuffle version per
 * `docs/games/freecell/EXECUTION_PLAN.md`.
 */
const val REFERENCE_GAME_SEED = 2L
val REFERENCE_GAME_VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

/** Automation stays off through this phase so exposed foundation-eligible cards are not claimed
 * before the player can choose to bank them — the only way a manual `TableauToFoundation` ever
 * gets a turn against an automatic cascade that would otherwise beat it there. */
private val PHASE_1_MOVES: List<Move> = listOf(
    Move.TableauToFreeCell(0, 0),
    Move.TableauToTableau(2, 6, 0),
    Move.TableauToFreeCell(0, 1),
    Move.FreeCellToTableau(1, 0),
    Move.TableauToFreeCell(0, 2),
    Move.TableauToFreeCell(0, 1),
    Move.TableauToFoundation(0),
    Move.TableauToTableau(0, 3, 1),
    Move.TableauToFreeCell(0, 3),
    Move.TableauToFoundation(0),
    Move.TableauToTableau(0, 0, 5),
    Move.TableauToTableau(1, 7, 0),
    Move.TableauToFoundation(0),
    Move.TableauToTableau(1, 6, 0),
    Move.TableauToFoundation(0),
    Move.TableauToTableau(1, 5, 0),
    Move.TableauToTableau(4, 5, 7),
    Move.TableauToTableau(5, 6, 4),
    Move.TableauToTableau(6, 5, 3),
    Move.TableauToTableau(4, 5, 5),
)

/** A few more moves once automation is back on, so the log also exercises a real automatic
 * cascade committed as part of an ordinary player move. */
private val PHASE_2_MOVES: List<Move> = listOf(
    Move.TableauToTableau(5, 6, 4),
    Move.FreeCellToTableau(1, 0),
    Move.TableauToFreeCell(1, 1),
    Move.TableauToTableau(1, 3, 5),
    Move.FreeCellToTableau(2, 0),
)

val REFERENCE_GAME_LOG: List<FreeCellLogEntry> =
    PHASE_1_MOVES.map(FreeCellLogEntry::PlayerMove) +
        listOf(FreeCellLogEntry.SetAutomaticMoves(true)) +
        PHASE_2_MOVES.map(FreeCellLogEntry::PlayerMove) +
        listOf(FreeCellLogEntry.Undo)

/** Frozen expected value of [canonicalStateHash] after replaying [REFERENCE_GAME_LOG]. */
const val REFERENCE_GAME_HASH = 4249701719891417622L

/** Replays [REFERENCE_GAME_LOG] from [REFERENCE_GAME_SEED], the same way a restored save would. */
fun playReferenceGame(): FreeCellSession =
    replayFreeCellSession(REFERENCE_GAME_SEED, REFERENCE_GAME_VERSIONS, initialAutomaticMovesEnabled = false, REFERENCE_GAME_LOG)
