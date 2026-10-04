package org.finiteplay.klondike.rules

/**
 * The frozen engine surface D1s/D1b build against (`docs/games/klondike/EXECUTION_PLAN.md`, E2a).
 * The solver needs only this package plus [org.finiteplay.klondike.board] and
 * [org.finiteplay.cards] — never scoring, hints, undo, or persistence.
 *
 * - [org.finiteplay.klondike.board.dealGame] / `dealGameWithSetup`: the raw and
 *   setup-cascade deals a candidate seed must be solved from.
 * - [legalMoves] / [isLegal]: legal-move generation and membership.
 * - [applyMove]: pure mechanical transition (tableau/waste/foundation moves, draw,
 *   recycle, automatic flips, win detection). Certificates replay through this
 *   function, never through the solver's own optimized search transitions.
 * - [runAutomaticFoundationCascade] / [findNextSafeAutomaticMove]: the same automation
 *   the shipped app runs, so a certificate replays identically whether automation was
 *   enabled (including setup) or disabled, per the D1b catalog gate.
 * - [org.finiteplay.klondike.rules.findInvariantViolations]: board-consistency
 *   checks the solver can use to fail fast on an inconsistent search state.
 *
 * The automatic finish (`autoFinish`, arriving in E2b) is deliberately excluded: it is
 * a transition the reducer offers and only the game screen invokes, never headless
 * replay, catalog validation, or the solver.
 */
private val ENGINE_SURFACE_DOC_ANCHOR = Unit
