package org.finiteplay.klondike.session

import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.board.canonicalStateHash
import org.finiteplay.klondike.rules.Move

/**
 * A fixed, hand-verified sequence of legal moves from a known seed, committed by RF
 * (`docs/games/klondike/EXECUTION_PLAN.md` "RF — Rules Freeze") so the JVM engine and Android runtime
 * can both be checked against the same reference playthrough — the gameplay analogue
 * of [org.finiteplay.klondike.deal.DECK_SHUFFLE_REFERENCE_VECTORS]. Exercises one
 * committed transaction of each kind the log can hold: draws, tableau-to-tableau runs,
 * waste-to-tableau, a foundation move, an automatic cascade (automatic moves stay
 * enabled until the final entry), an undo, and an automation-setting change.
 *
 * [REFERENCE_GAME_HASH] changing for this fixed seed/log/versions means a rules,
 * automation, or shuffle change altered real gameplay output, which requires a new
 * rules or shuffle version per `docs/games/klondike/EXECUTION_PLAN.md`.
 */
const val REFERENCE_GAME_SEED = 2L
val REFERENCE_GAME_VERSIONS = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1)

val REFERENCE_GAME_LOG: List<LogEntry> = listOf(
    Move.Draw,
    Move.Draw,
    Move.Draw,
    Move.Draw,
    Move.WasteToTableau(0),
    Move.TableauToTableau(0, 1, 5),
    Move.TableauToTableau(1, 1, 5),
    Move.TableauToTableau(5, 6, 0),
    Move.Draw,
    Move.TableauToFoundation(1),
    Move.TableauToTableau(0, 1, 5),
    Move.TableauToTableau(2, 2, 4),
    Move.TableauToTableau(5, 6, 0),
    Move.Draw,
    Move.TableauToTableau(0, 1, 5),
    Move.Draw,
    Move.WasteToTableau(0),
    Move.TableauToTableau(5, 7, 0),
    Move.Draw,
    Move.TableauToTableau(0, 2, 5),
    Move.Draw,
    Move.WasteToTableau(1),
    Move.TableauToTableau(0, 0, 1),
    Move.TableauToTableau(5, 7, 1),
    Move.TableauToTableau(1, 0, 0),
    Move.TableauToTableau(0, 3, 5),
).map(LogEntry::PlayerMove) + listOf(
    LogEntry.Undo,
    LogEntry.SetAutomaticMoves(false),
)

/**
 * Frozen expected value of [canonicalStateHash] after replaying [REFERENCE_GAME_LOG].
 * Recomputed once, when [canonicalStateHash] gained a [org.finiteplay.klondike.board.DrawMode]
 * term (draw-three support): the reference game's own moves and resulting board are
 * unchanged (it is, and remains, a draw-one game), only the hash *formula* grew a new
 * mixed-in field, so every state's hash value shifts — this is not a rules or
 * automation behavior change and does not warrant a rules-version bump.
 */
const val REFERENCE_GAME_HASH = -439809326084033263L

/** Replays [REFERENCE_GAME_LOG] from [REFERENCE_GAME_SEED], the same way a restored save would. */
fun playReferenceGame(): GameSession =
    replaySession(REFERENCE_GAME_SEED, REFERENCE_GAME_VERSIONS, initialAutomaticMovesEnabled = true, REFERENCE_GAME_LOG)
