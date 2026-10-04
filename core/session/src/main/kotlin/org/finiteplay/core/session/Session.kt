package org.finiteplay.core.session

/**
 * A game in progress: the current board, the boards undo can return to, and the append-only
 * log of everything committed so far (`docs/PLATFORM.md` "Persistence").
 *
 * Generic over the board [S] and the log entry [E] because those are the two things a game
 * owns outright — a board is seven columns or ten, and a log entry is spelled in that game's
 * own move alphabet, which is also its certificate format. What is *not* game-specific is the
 * bookkeeping between them, and that is all this type holds.
 *
 * There is deliberately no transition protocol here: no interface a game implements to say
 * how a move applies. That would be inventing the shape of a reducer from the outside, and
 * the reducers differ in every way that matters. What the operations below enforce instead is
 * narrower and worth enforcing — **a state change and its log entry happen together, or not at
 * all.** A log that has drifted from the boards it describes cannot be replayed, and replay is
 * the whole reason the log exists.
 */
data class Session<S, E>(
    val state: S,
    val undoStack: List<S>,
    val log: List<E>,
) {
    /**
     * Whether a board is available to return to. A game with a further condition — refusing undo
     * once a win is recorded, say — narrows this rather than replacing it.
     */
    val canUndo: Boolean get() = undoStack.isNotEmpty()

    companion object {
        /** A session on its dealt board, with nothing to undo and nothing logged. */
        fun <S, E> start(state: S): Session<S, E> = Session(state, emptyList(), emptyList())
    }
}

/**
 * Commits a player's move: [next] becomes the board, the one it replaced becomes undoable, and
 * [entry] records it.
 *
 * [next] is the board *after* everything the move set off — a flip, a cascade, a banked
 * sequence — because those are consequences of the move rather than moves of their own, and
 * undo has to return past all of them at once.
 */
fun <S, E> Session<S, E>.commit(next: S, entry: E): Session<S, E> = copy(
    state = next,
    undoStack = undoStack + state,
    log = log + entry,
)

/**
 * Steps back to the previous board, passing it through [restore] first.
 *
 * [restore] exists for one rule, and it is the rule this whole file is here to stop a second
 * game from getting wrong: **undo restores the board but not the moves already counted, then
 * adds one** (`docs/PLATFORM.md`). The prior board is handed in; what comes back is that board
 * carrying the current move count plus one. Undo is itself logged, because a log that skipped
 * it would not replay.
 *
 * A no-op when there is nothing to undo, so a caller may offer it unconditionally.
 */
fun <S, E> Session<S, E>.undo(entry: E, restore: (prior: S) -> S): Session<S, E> {
    if (!canUndo) return this
    return copy(
        state = restore(undoStack.last()),
        undoStack = undoStack.dropLast(1),
        log = log + entry,
    )
}

/**
 * Logs an event that changes how the session behaves without moving a card: a setting toggled
 * mid-game, where what the setting does is the game's business but *when* it changed is the
 * log's.
 *
 * It joins the log because replay has to reproduce the setting as it was at each point, and it
 * leaves the undo stack alone because there is no board to step back to.
 */
fun <S, E> Session<S, E>.record(entry: E): Session<S, E> = copy(log = log + entry)

/**
 * Commits a terminal transition: [next] becomes the board and **the undo stack is cleared**.
 *
 * For a transition after which stepping back is not offered — a game finishing itself once the
 * outcome is decided, and recording the result. Separate from [commit] because clearing the stack
 * silently inside an ordinary commit is exactly the kind of thing that should be spelled at the
 * call site.
 */
fun <S, E> Session<S, E>.finish(next: S, entry: E): Session<S, E> = copy(
    state = next,
    undoStack = emptyList(),
    log = log + entry,
)

// There is deliberately no `replay` helper here. Restoring a save means folding the log through
// the game's own dispatch (`docs/PLATFORM.md` "Persistence"), and the accumulator is whatever the
// game's session type is, and a game whose session carries settings or a cursor alongside the
// board does not fold over a bare `Session` at all. A wrapper over `fold` that only fits a game
// with no extra state would push every other game into contorting its call site to use it, which
// is worse than the standard-library fold it was wrapping.
