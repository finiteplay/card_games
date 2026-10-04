package org.finiteplay.klondike.tools.catalog

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.TABLEAU_COLUMNS

/**
 * An allocation-free board for grading Trivial at scale.
 *
 * The `GameState`-based generator allocates a fresh immutable board per move — and, because
 * the pile has to be walked with real draws to enumerate it, dozens more per node merely to
 * *look*. That caps throughput at roughly 48,000 states/second, which is the ceiling on
 * everything the Trivial search does. This is the same model over primitive arrays, mutated
 * in place with make/unmake: the search is a depth-first walk of an acyclic graph no deeper
 * than about ninety choices, so one board can be reused for the entire traversal.
 *
 * The pile is the part worth explaining, since a previous attempt got it wrong three times.
 * Cards sit in [pile] in draw order, with [wasteCount] recording how many have been drawn:
 *
 * ```
 * waste (top first) = pile[wasteCount-1] .. pile[0]
 * stock (next first) = pile[wasteCount] .. pile[pileSize-1]
 * ```
 *
 * Drawing is `wasteCount++`; recycling — legal only once the stock is empty — is
 * `wasteCount = 0`, which is exactly `stock = waste.reversed()` under this layout. Playing
 * the waste top removes `pile[wasteCount-1]` and leaves `wasteCount` one lower, so the card
 * *beneath* becomes top. That last part is what no closed-form rotation over `GameState`
 * could express: the stock/waste boundary is real state recording when the last recycle
 * happened, not something recoverable from which card is face up.
 */
internal class FastBoard {

    val columnCards = Array(TABLEAU_COLUMNS) { IntArray(MAX_COLUMN) }
    val columnSize = IntArray(TABLEAU_COLUMNS)

    /** Face-down cards per column. They are always a prefix, so the top is face-down exactly when this equals the size. */
    val columnDown = IntArray(TABLEAU_COLUMNS)

    /** Highest rank banked per suit, 0 for empty. Indexed by suit ordinal. */
    val foundations = IntArray(SUITS)

    val pile = IntArray(DECK)
    var pileSize = 0
    var wasteCount = 0

    /**
     * Per-column avalanched signatures and their running sum, maintained incrementally.
     *
     * Recomputing the whole fingerprint per node walked all 52 cards; a move touches at most
     * two columns, so only those are recomputed and the sum is adjusted by subtracting the
     * old contribution and adding the new. Addition is what makes that possible — it has an
     * inverse, which XOR's self-cancelling would also give but at the cost of the empty-column
     * collision documented on [fingerprint].
     *
     * Every mutation path must keep this current. [fingerprintFromScratch] exists so a test
     * can assert it does after every single make and unmake, because a stale signature is
     * invisible: it does not crash, it silently merges two different boards into one memo
     * entry and reports a search space smaller than the real one.
     */
    /**
     * How far above the lowest foundation a card may be banked before the restraint rule
     * applies. Tunable rather than fixed because the whole question is where the threshold
     * belongs — `Easy` guesses four and `Medium` replaces it with a safety test, neither
     * measured.
     */
    var foundationSpreadLimit = DEFAULT_FOUNDATION_SPREAD

    /**
     * Whether a withheld move is **deferred** rather than **skipped**.
     *
     * The tier rulesets defer: a withheld card ranks behind every other move but stays
     * playable, so restraint is an ordering and can never cost a win. Skipping removes the
     * move outright, which is a real pruning and can. The two are worth measuring apart,
     * because "don't rush the foundation" is stated as folklore without saying which it is.
     */
    var deferWithheld = false

    private val columnSignature = LongArray(TABLEAU_COLUMNS)
    private var signatureSum = 0L
    /** Bits 0..6 are accepting tableau columns; bit 7 is the card's foundation. */
    private val destinationsByCard = ByteArray(DECK)
    private val columnAcceptor = IntArray(TABLEAU_COLUMNS) { NO_ACCEPTOR }

    private val undoKind = IntArray(MAX_DEPTH)
    private val undoA = IntArray(MAX_DEPTH)
    private val undoB = IntArray(MAX_DEPTH)
    private val undoC = IntArray(MAX_DEPTH)
    private val undoD = IntArray(MAX_DEPTH)
    private var depth = 0

    fun loadFrom(state: GameState) {
        for (column in 0 until TABLEAU_COLUMNS) {
            val cards = state.tableau[column]
            columnSize[column] = cards.size
            var down = 0
            for (index in cards.indices) {
                columnCards[column][index] = cards[index].card.id
                if (!cards[index].faceUp) down++
            }
            columnDown[column] = down
        }
        for (suit in 0 until SUITS) foundations[suit] = 0
        for ((suit, rank) in state.foundations) foundations[suit.ordinal] = rank
        // GameState's waste is top-first and its stock next-first; laid end to end in draw
        // order that is the waste reversed, then the stock.
        pileSize = 0
        for (index in state.waste.indices.reversed()) pile[pileSize++] = state.waste[index].id
        for (card in state.stock) pile[pileSize++] = card.id
        wasteCount = state.waste.size
        depth = 0
        resync()
    }

    fun isWon(): Boolean {
        for (suit in 0 until SUITS) if (foundations[suit] != RANKS) return false
        return true
    }

    /**
     * Column-order-invariant fingerprint. Per-column signatures are combined by **addition**,
     * which is commutative — so invariance to column order costs no sort — while equal
     * signatures accumulate instead of cancelling.
     *
     * Not XOR, which is the obvious commutative choice and is wrong here: two columns with
     * identical contents cancel to zero under it. Distinct cards make that impossible for
     * occupied columns but not for **empty** ones, which all share a signature — so a board
     * with two empty columns would have collided with one having none, and empty columns are
     * the single most consequential feature of a Klondike position. Each signature is
     * avalanched before being added so that near-identical columns do not produce near-equal
     * contributions.
     */
    fun fingerprint(): Long {
        var hash = signatureSum
        for (suit in 0 until SUITS) hash = (hash xor (3000L + foundations[suit])) * PRIME
        return hash
    }

    /** The same value computed from the board alone, for differencing against the incremental sum. */
    fun fingerprintFromScratch(): Long {
        var accumulated = 0L
        for (column in 0 until TABLEAU_COLUMNS) accumulated += computeColumnSignature(column)
        var hash = accumulated
        for (suit in 0 until SUITS) hash = (hash xor (3000L + foundations[suit])) * PRIME
        return hash
    }

    private fun computeColumnSignature(column: Int): Long {
        var signature = BASIS
        val size = columnSize[column]
        val down = columnDown[column]
        val cards = columnCards[column]
        for (index in 0 until size) {
            signature = (signature xor (cards[index].toLong() * 2 + if (index < down) 1L else 0L)) * PRIME
        }
        return avalanche(signature)
    }

    /**
     * Re-derives every column's signature from the board.
     *
     * [make] and [unmake] keep the running sum current by themselves; this exists for the one
     * other way the board can change, which is a caller writing [columnSize] or [columnCards]
     * directly. Only tests do that, to build a position no sequence of legal moves reaches,
     * and without this the fingerprint would silently keep reporting the pre-edit board.
     */
    fun resync() {
        signatureSum = 0L
        destinationsByCard.fill(0)
        columnAcceptor.fill(NO_ACCEPTOR)
        for (column in 0 until TABLEAU_COLUMNS) {
            columnSignature[column] = computeColumnSignature(column)
            signatureSum += columnSignature[column]
            addColumnDestinations(column)
        }
        for (suit in 0 until SUITS) addFoundationDestination(suit)
    }

    /** Re-derives one column's contribution to [signatureSum]. Call after any change to it. */
    private fun refresh(column: Int) {
        removeColumnDestinations(column)
        signatureSum -= columnSignature[column]
        columnSignature[column] = computeColumnSignature(column)
        signatureSum += columnSignature[column]
        addColumnDestinations(column)
    }

    private fun toggleDestination(card: Int, bit: Int) {
        destinationsByCard[card] = (destinationsByCard[card].toInt() xor bit).toByte()
    }

    private fun toggleColumnDestinations(column: Int, acceptor: Int) {
        val bit = 1 shl column
        if (acceptor == EMPTY_ACCEPTOR) {
            for (suit in 0 until SUITS) toggleDestination(suit * RANKS + RANKS - 1, bit)
            return
        }
        val acceptedRank = rankOf(acceptor) - 1
        if (acceptedRank == 0) return
        val firstSuit = if (isRed(acceptor)) 0 else 1
        val secondSuit = if (isRed(acceptor)) 3 else 2
        toggleDestination(firstSuit * RANKS + acceptedRank - 1, bit)
        toggleDestination(secondSuit * RANKS + acceptedRank - 1, bit)
    }

    private fun removeColumnDestinations(column: Int) {
        val acceptor = columnAcceptor[column]
        if (acceptor != NO_ACCEPTOR) toggleColumnDestinations(column, acceptor)
    }

    private fun addColumnDestinations(column: Int) {
        val acceptor = if (columnSize[column] == 0) EMPTY_ACCEPTOR else columnCards[column][columnSize[column] - 1]
        columnAcceptor[column] = acceptor
        toggleColumnDestinations(column, acceptor)
    }

    private fun addFoundationDestination(suit: Int) {
        val rank = foundations[suit]
        if (rank < RANKS) toggleDestination(suit * RANKS + rank, FOUNDATION_DESTINATION)
    }

    private fun setFoundationRank(suit: Int, rank: Int) {
        val oldRank = foundations[suit]
        if (oldRank < RANKS) toggleDestination(suit * RANKS + oldRank, FOUNDATION_DESTINATION)
        foundations[suit] = rank
        addFoundationDestination(suit)
    }

    // ---- rules, over card ids ----

    private fun canBuild(under: Int, over: Int): Boolean =
        rankOf(over) == rankOf(under) - 1 && isRed(over) != isRed(under)

    fun canPlaceOnColumn(column: Int, card: Int): Boolean {
        return destinationsByCard[card].toInt() and (1 shl column) != 0
    }

    fun canPlaceOnFoundation(card: Int): Boolean =
        destinationsByCard[card].toInt() and FOUNDATION_DESTINATION != 0

    internal fun destinationMask(card: Int): Int = destinationsByCard[card].toInt() and 0xFF

    private fun isSafeFoundation(card: Int): Boolean {
        if (rankOf(card) <= 2) return true
        val previous = rankOf(card) - 1
        for (suit in 0 until SUITS) if (foundations[suit] < previous) return false
        return true
    }

    /** Deepest index whose run to the top is valid and face-up — `validRunStartIndices(...).last()`, or -1. */
    fun deepestRunStart(column: Int): Int {
        val size = columnSize[column]
        if (size == 0) return -1
        val faceUpStart = columnDown[column]
        if (faceUpStart >= size) return -1
        val cards = columnCards[column]
        var index = size - 1
        while (index > faceUpStart && canBuild(cards[index - 1], cards[index])) index--
        return index
    }

    // ---- move generation ----

    /**
     * Writes the obvious taps into [out] as packed ints and returns the count, in the same
     * order the `GameState` generator emits them: per column a foundation play then a
     * reveal, then the pile in the order a player cycling from the current position meets it.
     * Order is load-bearing — the reference line is the first tap of the highest-priority
     * kind — so it is part of the contract, not an implementation detail.
     */
    fun generateTaps(out: IntArray, ruleset: Ruleset = Ruleset.TRIVIAL): Int {
        var count = 0
        for (column in 0 until TABLEAU_COLUMNS) {
            val size = columnSize[column]
            if (size > 0 && columnDown[column] < size) {
                val top = columnCards[column][size - 1]
                if (canPlaceOnFoundation(top)) out[count++] = pack(KIND_TABLEAU_FOUNDATION, TAP_FOUNDATION, column, 0, 0)
            }
            // `deepest == 0` means the whole column is one face-up run, so moving it empties
            // the column. That counts as obvious: freeing a column for a King is as natural
            // to a beginner as turning a card over, and it is the only non-revealing tableau
            // move that is. General rearrangement stays excluded — no tier below Hard offers
            // it, and admitting all of it is what makes the graph explode.
            val deepest = deepestRunStart(column)
            if (deepest >= 0) {
                val destination = resolveTableauTap(column, deepest)
                // `deepest == 0` empties the column rather than revealing anything, which is
                // a different priority even though both are obvious.
                val category = if (deepest > 0) TAP_REVEAL else TAP_EMPTY_COLUMN
                if (destination >= 0) out[count++] = pack(KIND_TABLEAU_TABLEAU, category, column, deepest, destination)
            }
            // Hard alone: split the face-up run and move its upper part. Every index above
            // `deepest` is a legal run start and a tap the UI already accepts, so this stays
            // inside the tap model rather than assuming a player who drags.
            if (ruleset.offersSetupMoves && deepest >= 0) {
                for (fromIndex in deepest + 1 until size) {
                    if (!isProductiveSetup(column, fromIndex)) continue
                    val to = resolveTableauTap(column, fromIndex)
                    if (to >= 0) out[count++] = pack(KIND_TABLEAU_TABLEAU, TAP_SETUP, column, fromIndex, to)
                }
            }
        }
        for (cycleIndex in 0 until pileSize) {
            val card = pile[pileIndexAt(cycleIndex)]
            if (!canPlaceOnFoundation(card) && !anyColumnAccepts(card)) continue
            val resolved = resolveWasteTap(card)
            if (resolved == RESOLVE_NONE) continue
            if (resolved == RESOLVE_FOUNDATION) {
                out[count++] = pack(KIND_WASTE_FOUNDATION, TAP_PILE, cycleIndex, 0, 0)
            } else {
                out[count++] = pack(KIND_WASTE_TABLEAU, TAP_PILE, cycleIndex, 0, resolved)
            }
        }
        // Expert alone: take a banked card back. Tap resolution sends a withdrawal to the
        // leftmost legal column (`UI_SPEC.md` "Tap"), so the destination is not a choice.
        if (ruleset.offersWithdrawal) {
            for (suit in 0 until SUITS) {
                val rank = foundations[suit]
                if (rank == 0) continue
                val card = suit * RANKS + rank - 1
                val to = leftmostColumnAccepting(card)
                if (to >= 0 && isProductiveWithdrawal(card, to)) {
                    out[count++] = pack(KIND_FOUNDATION_TABLEAU, TAP_WITHDRAW, suit, 0, to)
                }
            }
        }
        return count
    }

    internal fun leftmostColumnAccepting(card: Int): Int {
        val destinations = destinationMask(card) and TABLEAU_DESTINATIONS
        return if (destinations == 0) -1 else Integer.numberOfTrailingZeros(destinations)
    }

    /**
     * Whether taking [card] back onto [to] is worth doing.
     *
     * A withdrawal is the one move in the game that runs the score backwards, so admitting it
     * unfiltered is worse than admitting setup moves unfiltered: the card can be banked and
     * withdrawn for ever, and every cycle looks like progress to a search that only counts
     * states. The filter is the same shape as the setup move's — the card must **give a home
     * to a card that has none** — which is exactly what a player withdraws for: to unblock a
     * column that nothing else can take.
     */
    internal fun isProductiveWithdrawal(card: Int, to: Int): Boolean {
        for (other in 0 until TABLEAU_COLUMNS) {
            if (other == to) continue
            val start = deepestRunStart(other)
            if (start >= 0 && givesFirstHome(card, columnCards[other][start], other)) return true
        }
        for (index in 0 until pileSize) if (givesFirstHome(card, pile[index], -1)) return true
        return false
    }

    /**
     * Every legal **choice** from this board — the player's real options, not the tap-resolved
     * subset the tier rulesets use.
     *
     * "Choice" is the glossary's term: a legal move that changes the state. Draw and recycle
     * are excluded because they change no state and cost no decision, which is also why every
     * pile card is offered here rather than only the waste top. A run may start at any valid
     * index and land on any column that accepts it, because a player drags; nothing here is
     * filtered for being productive.
     *
     * This is the set the restricted rulesets are a subset of, and the one any claim about
     * what a player can or cannot do has to be measured against.
     */
    fun generateChoices(
        out: IntArray,
        includeWithdrawal: Boolean = true,
        /**
         * Partial-order reduction. Two moves touching disjoint piles reach the same board in
         * either order, so only one ordering need be explored: after playing [afterMove], any
         * *independent* move ordered before it is skipped, because that sequence is reached by
         * playing the two the other way round from the same parent.
         *
         * Pass -1 to disable, which the caller must do for any state reachable by two
         * different last moves — the filter is sound only relative to the one it came from.
         */
        afterMove: Int = -1,
        afterMask: Int = 0,
        /**
         * Emit only one empty destination column. Every empty column gives the same board
         * under a column-order-invariant fingerprint, so the rest are states already defined
         * as identical.
         */
        canonicaliseEmptyColumns: Boolean = false,
        /**
         * When a *safe* foundation move exists, generate it alone. Sound for winnability by
         * the exchange argument set out in `SearchState.kt`: any winning line converts to one
         * no longer that banks the safe card first. Unlike the two above, this genuinely
         * removes reachable states — deliberately, because none of them is needed.
         */
        forceSafeFoundation: Boolean = false,
        /**
         * Bitmask of candidate strategy rules ([RULE_FOUNDATION_SPREAD] and friends).
         *
         * These are **hypotheses under test**, not established facts, so they are applied as
         * preferences rather than laws: if the whole rule set leaves nothing to play, the
         * generator falls back to the unpruned move list. A rule that would otherwise strand
         * the search reports as a lost win instead of a fabricated dead end, which is the
         * difference between measuring a rule and being misled by it.
         */
        rules: Int = 0,
    ): Int {
        if (forceSafeFoundation) {
            for (column in 0 until TABLEAU_COLUMNS) {
                val size = columnSize[column]
                if (size == 0 || columnDown[column] >= size) continue
                val top = columnCards[column][size - 1]
                if (canPlaceOnFoundation(top) && isSafeFoundation(top)) {
                    out[0] = pack(KIND_TABLEAU_FOUNDATION, TAP_FOUNDATION, column, 0, 0)
                    return 1
                }
            }
            for (cycleIndex in 0 until pileSize) {
                val card = pile[pileIndexAt(cycleIndex)]
                if (canPlaceOnFoundation(card) && isSafeFoundation(card)) {
                    out[0] = pack(KIND_WASTE_FOUNDATION, TAP_PILE, cycleIndex, 0, 0)
                    return 1
                }
            }
        }
        // Hypothesis (d): when every empty column is already spoken for by a King that needs
        // one, no empty column has any other use, so taking one now costs nothing.
        if (rules and RULE_KING_WHEN_COLUMNS_SPARE != 0) {
            val empty = emptyColumns()
            if (empty > 0 && empty >= kingsNeedingAColumn()) {
                for (column in 0 until TABLEAU_COLUMNS) {
                    val size = columnSize[column]
                    if (size == 0 || columnDown[column] >= size) continue
                    val start = deepestRunStart(column)
                    if (start <= 0 || rankOf(columnCards[column][start]) != RANKS) continue
                    for (to in 0 until TABLEAU_COLUMNS) {
                        if (columnSize[to] == 0) {
                            out[0] = pack(KIND_TABLEAU_TABLEAU, TAP_REVEAL, column, start, to)
                            return 1
                        }
                    }
                }
            }
        }
        var count = 0
        var emptySeen = -1
        for (column in 0 until TABLEAU_COLUMNS) {
            val size = columnSize[column]
            if (size == 0) continue
            if (columnDown[column] < size) {
                val top = columnCards[column][size - 1]
                if (canPlaceOnFoundation(top)) {
                    val move = pack(KIND_TABLEAU_FOUNDATION, TAP_FOUNDATION, column, 0, 0)
                    admit(move, rules, afterMove, afterMask).let { if (it != 0) out[count++] = it }
                }
            }
            val deepest = deepestRunStart(column)
            if (deepest >= 0) {
                for (fromIndex in deepest until size) {
                    val sequenceBottom = columnCards[column][fromIndex]
                    var destinations = destinationMask(sequenceBottom) and TABLEAU_DESTINATIONS
                    while (destinations != 0) {
                        val to = Integer.numberOfTrailingZeros(destinations)
                        destinations = destinations and (destinations - 1)
                        if (canonicaliseEmptyColumns && columnSize[to] == 0) {
                            if (emptySeen >= 0 && emptySeen != to) continue
                            emptySeen = to
                        }
                        val category = when {
                            fromIndex > deepest -> TAP_SETUP
                            columnDown[column] > 0 -> TAP_REVEAL
                            else -> TAP_EMPTY_COLUMN
                        }
                        val move = pack(KIND_TABLEAU_TABLEAU, category, column, fromIndex, to)
                        admit(move, rules, afterMove, afterMask).let { if (it != 0) out[count++] = it }
                    }
                }
            }
        }
        for (cycleIndex in 0 until pileSize) {
            val card = pile[pileIndexAt(cycleIndex)]
            if (canPlaceOnFoundation(card)) {
                val move = pack(KIND_WASTE_FOUNDATION, TAP_PILE, cycleIndex, 0, 0)
                admit(move, rules, afterMove, afterMask).let { if (it != 0) out[count++] = it }
            }
            var destinations = destinationMask(card) and TABLEAU_DESTINATIONS
            while (destinations != 0) {
                val to = Integer.numberOfTrailingZeros(destinations)
                destinations = destinations and (destinations - 1)
                if (canonicaliseEmptyColumns && columnSize[to] == 0) {
                    if (emptySeen >= 0 && emptySeen != to) continue
                    emptySeen = to
                }
                val move = pack(KIND_WASTE_TABLEAU, TAP_PILE, cycleIndex, 0, to)
                admit(move, rules, afterMove, afterMask).let { if (it != 0) out[count++] = it }
            }
        }
        if (includeWithdrawal) {
            for (suit in 0 until SUITS) {
                val rank = foundations[suit]
                if (rank == 0) continue
                val card = suit * RANKS + rank - 1
                var destinations = destinationMask(card) and TABLEAU_DESTINATIONS
                while (destinations != 0) {
                    val to = Integer.numberOfTrailingZeros(destinations)
                    destinations = destinations and (destinations - 1)
                    val move = pack(KIND_FOUNDATION_TABLEAU, TAP_WITHDRAW, suit, 0, to)
                    admit(move, rules, afterMove, afterMask).let { if (it != 0) out[count++] = it }
                }
            }
        }
        // Every rule yields rather than strands: an empty list here means the rule set, not
        // the board, ran out of moves, and a search must not read that as a dead end.
        if (count == 0 && rules != 0) {
            return generateChoices(out, includeWithdrawal, afterMove, afterMask, canonicaliseEmptyColumns, forceSafeFoundation, rules = 0)
        }
        return count
    }

    /** Empty columns on the board right now. */
    fun emptyColumns(): Int {
        var n = 0
        for (column in 0 until TABLEAU_COLUMNS) if (columnSize[column] == 0) n++
        return n
    }

    /**
     * Kings still needing an empty column: not banked, and not already sitting at the bottom
     * of a column, where they need nothing.
     */
    fun kingsNeedingAColumn(): Int {
        var n = 0
        for (suit in 0 until SUITS) {
            if (foundations[suit] == RANKS) continue
            val king = suit * RANKS + RANKS - 1
            var placed = false
            for (column in 0 until TABLEAU_COLUMNS) {
                if (columnSize[column] > 0 && columnCards[column][0] == king) { placed = true; break }
            }
            if (!placed) n++
        }
        return n
    }

    /** Whether any card that could stack on [card] is available to play onto it. */
    private fun anyTakerFor(card: Int): Boolean {
        val wanted = rankOf(card) - 1
        if (wanted < 1) return false
        for (column in 0 until TABLEAU_COLUMNS) {
            val start = deepestRunStart(column)
            if (start >= 0) {
                val bottom = columnCards[column][start]
                if (rankOf(bottom) == wanted && isRed(bottom) != isRed(card)) return true
            }
        }
        for (index in 0 until pileSize) {
            val c = pile[index]
            if (rankOf(c) == wanted && isRed(c) != isRed(card)) return true
        }
        return false
    }

    /**
     * Applies the candidate rules, returning the move to emit, the move retagged as deferred,
     * or 0 to drop it.
     */
    private fun admit(move: Int, rules: Int, afterMove: Int, afterMask: Int): Int {
        if (!keep(move, afterMove, afterMask)) return 0
        if (withheld(move, rules)) {
            return if (deferWithheld) pack(kindOf(move), TAP_DEFERRED, fieldA(move), fieldB(move), fieldC(move)) else 0
        }
        return move
    }

    /** Whether the rules under test object to this move. */
    private fun withheld(move: Int, rules: Int): Boolean {
        if (rules == 0) return false
        val kind = kindOf(move)
        if (rules and RULE_FOUNDATION_SPREAD != 0 &&
            (kind == KIND_TABLEAU_FOUNDATION || kind == KIND_WASTE_FOUNDATION)
        ) {
            val card = if (kind == KIND_TABLEAU_FOUNDATION) {
                val a = fieldA(move)
                columnCards[a][columnSize[a] - 1]
            } else {
                pile[pileIndexAt(fieldA(move))]
            }
            var lowest = RANKS
            for (suit in 0 until SUITS) if (foundations[suit] < lowest) lowest = foundations[suit]
            if (rankOf(card) - lowest > foundationSpreadLimit) return true
        }
        if (rules and RULE_NO_IDLE_LOW_PILE != 0 && kind == KIND_WASTE_TABLEAU) {
            val card = pile[pileIndexAt(fieldA(move))]
            if (rankOf(card) in 2..5 && !anyTakerFor(card)) return true
        }
        if (rules and RULE_NO_IDLE_SETUP != 0 && tapOf(move) == TAP_SETUP) {
            if (!isProductiveSetup(fieldA(move), fieldB(move))) return true
        }
        return false
    }

    /** Skip a move only when it is ordered before [afterMove] *and* independent of it. */
    private fun keep(move: Int, afterMove: Int, afterMask: Int): Boolean {
        if (afterMove < 0 || move >= afterMove) return true
        return (moveMask(move) and afterMask) != 0
    }

    /**
     * Which piles a move touches, as a bitmask: one bit per tableau column, one for the whole
     * stock-and-waste, one per foundation suit. Two moves are independent exactly when their
     * masks are disjoint.
     *
     * The pile is one resource rather than a bit per card, so every pile move depends on every
     * other. That is deliberate and necessary: playing one pile card shifts which card each
     * remaining index refers to, so pile moves do not commute.
     */
    fun moveMask(move: Int): Int {
        val a = fieldA(move)
        val c = fieldC(move)
        return when (kindOf(move)) {
            KIND_TABLEAU_FOUNDATION -> (1 shl a) or (FOUNDATION_BIT shl suitOf(columnCards[a][columnSize[a] - 1]))
            KIND_TABLEAU_TABLEAU -> (1 shl a) or (1 shl c)
            KIND_WASTE_FOUNDATION -> PILE_BIT or (FOUNDATION_BIT shl suitOf(pile[pileIndexAt(a)]))
            KIND_WASTE_TABLEAU -> PILE_BIT or (1 shl c)
            else -> (FOUNDATION_BIT shl a) or (1 shl c)
        }
    }

    /** Compact copy of everything [fingerprint] and [generateChoices] read, for a BFS frontier. */
    fun snapshot(): ByteArray {
        val out = ByteArray(SNAPSHOT_BYTES)
        var at = 0
        for (column in 0 until TABLEAU_COLUMNS) {
            out[at++] = columnSize[column].toByte()
            out[at++] = columnDown[column].toByte()
            for (index in 0 until columnSize[column]) out[at++] = columnCards[column][index].toByte()
            at += MAX_COLUMN - columnSize[column]
        }
        for (suit in 0 until SUITS) out[at++] = foundations[suit].toByte()
        out[at++] = pileSize.toByte()
        out[at++] = wasteCount.toByte()
        for (index in 0 until pileSize) out[at++] = pile[index].toByte()
        return out
    }

    fun restore(state: ByteArray) {
        var at = 0
        for (column in 0 until TABLEAU_COLUMNS) {
            columnSize[column] = state[at++].toInt()
            columnDown[column] = state[at++].toInt()
            for (index in 0 until columnSize[column]) columnCards[column][index] = state[at++].toInt()
            at += MAX_COLUMN - columnSize[column]
        }
        for (suit in 0 until SUITS) foundations[suit] = state[at++].toInt()
        pileSize = state[at++].toInt()
        wasteCount = state[at++].toInt()
        for (index in 0 until pileSize) pile[index] = state[at++].toInt()
        depth = 0
        resync()
    }

    private fun anyColumnAccepts(card: Int): Boolean {
        return destinationMask(card) and TABLEAU_DESTINATIONS != 0
    }

    /**
     * Whether moving the run starting at [fromIndex] off [column] is a **productive** setup
     * move — the only kind Hard admits.
     *
     * Splitting a run changes exactly one thing about what is available: it uncovers
     * `cards[fromIndex - 1]`, which was face-up but buried. The destination gains nothing new,
     * since the run's top card was already exposed. So the move is productive precisely when
     * that newly uncovered card does something: it can be banked, or it gives a home to a card
     * that has none.
     *
     * Restricting to productive moves is not an optimisation, it is what keeps the tier
     * playable. `DIFFICULTY_LEVELS.md` records the measurement: admitting setup moves
     * unrestricted collapsed Hard's win rate to 2/100, below Trivial's, because the ruleset
     * relocated cards for ever instead of progressing. This model cannot reproduce the worst
     * of that — a face-up King is always at the deepest run start, since no card outranks it,
     * so a split run can never contain one — but the same shape of waste remains without it.
     */
    internal fun isProductiveSetup(column: Int, fromIndex: Int): Boolean {
        val uncovered = columnCards[column][fromIndex - 1]
        if (canPlaceOnFoundation(uncovered)) return true
        for (other in 0 until TABLEAU_COLUMNS) {
            if (other == column) continue
            val start = deepestRunStart(other)
            if (start >= 0 && givesFirstHome(uncovered, columnCards[other][start], other)) return true
        }
        for (index in 0 until pileSize) {
            if (givesFirstHome(uncovered, pile[index], -1)) return true
        }
        return false
    }

    /** True when [mover] could stack on [uncovered] and has nowhere to go on the board today. */
    private fun givesFirstHome(uncovered: Int, mover: Int, moverColumn: Int): Boolean {
        if (!canBuild(uncovered, mover)) return false
        for (to in 0 until TABLEAU_COLUMNS) {
            if (to == moverColumn) continue
            if (canPlaceOnColumn(to, mover)) return false
        }
        return true
    }

    /**
     * `resolveTableauTap`: nearest legal column strictly to the right, then a foundation for a
     * single card, then nearest legal column to the left. Returns the destination column, or
     * -1 when the tap resolves to something that is not a tableau move (the caller only emits
     * tableau-to-tableau reveals, matching the `GameState` generator).
     */
    private fun resolveTableauTap(fromColumn: Int, fromIndex: Int): Int {
        val sequenceBottom = columnCards[fromColumn][fromIndex]
        for (to in fromColumn + 1 until TABLEAU_COLUMNS) if (canPlaceOnColumn(to, sequenceBottom)) return to
        val sequenceLength = columnSize[fromColumn] - fromIndex
        if (sequenceLength == 1 && canPlaceOnFoundation(sequenceBottom)) return -1
        for (to in 0 until fromColumn) if (canPlaceOnColumn(to, sequenceBottom)) return to
        return -1
    }

    /** `resolveWasteTap`: safe foundation, else lowest legal column, else an unsafe foundation. */
    private fun resolveWasteTap(card: Int): Int {
        if (canPlaceOnFoundation(card) && isSafeFoundation(card)) return RESOLVE_FOUNDATION
        for (column in 0 until TABLEAU_COLUMNS) if (canPlaceOnColumn(column, card)) return column
        if (canPlaceOnFoundation(card)) return RESOLVE_FOUNDATION
        return RESOLVE_NONE
    }

    /**
     * The [pile] index holding the card at [cycleIndex] positions ahead, where 0 is the card
     * already face up. Drawing advances through the stock, then a recycle wraps to the start
     * of the waste, so the mapping is arithmetic rather than a walk.
     */
    fun pileIndexAt(cycleIndex: Int): Int {
        if (wasteCount == 0) return cycleIndex
        if (cycleIndex == 0) return wasteCount - 1
        val untilStockEmpty = pileSize - wasteCount
        return if (cycleIndex <= untilStockEmpty) {
            wasteCount - 1 + cycleIndex
        } else {
            cycleIndex - untilStockEmpty - 1
        }
    }

    // ---- make / unmake ----

    fun make(move: Int) {
        val kind = kindOf(move)
        val a = fieldA(move)
        val c = fieldC(move)
        when (kind) {
            KIND_TABLEAU_FOUNDATION -> {
                val size = --columnSize[a]
                val card = columnCards[a][size]
                setFoundationRank(suitOf(card), foundations[suitOf(card)] + 1)
                val flipped = flipIfNeeded(a)
                refresh(a)
                push(kind, a, card, if (flipped) 1 else 0, 0)
            }
            KIND_TABLEAU_TABLEAU -> {
                val fromIndex = fieldB(move)
                val sequenceLength = columnSize[a] - fromIndex
                val target = columnSize[c]
                for (offset in 0 until sequenceLength) columnCards[c][target + offset] = columnCards[a][fromIndex + offset]
                columnSize[c] += sequenceLength
                columnSize[a] = fromIndex
                val flipped = flipIfNeeded(a)
                refresh(a)
                refresh(c)
                push(kind, a, c, sequenceLength, if (flipped) 1 else 0)
            }
            KIND_FOUNDATION_TABLEAU -> {
                val rank = foundations[a]
                setFoundationRank(a, foundations[a] - 1)
                columnCards[c][columnSize[c]++] = a * RANKS + rank - 1
                refresh(c)
                push(kind, a, c, 0, 0)
            }
            KIND_WASTE_FOUNDATION, KIND_WASTE_TABLEAU -> {
                val savedWaste = wasteCount
                val index = pileIndexAt(a)
                val card = pile[index]
                for (shift in index until pileSize - 1) pile[shift] = pile[shift + 1]
                pileSize--
                wasteCount = index
                if (kind == KIND_WASTE_FOUNDATION) {
                    setFoundationRank(suitOf(card), foundations[suitOf(card)] + 1)
                } else {
                    columnCards[c][columnSize[c]++] = card
                    refresh(c)
                }
                push(kind, index, card, savedWaste, c)
            }
        }
    }

    fun unmake() {
        val slot = --depth
        when (undoKind[slot]) {
            KIND_TABLEAU_FOUNDATION -> {
                val column = undoA[slot]
                if (undoC[slot] == 1) columnDown[column]++
                val card = undoB[slot]
                setFoundationRank(suitOf(card), foundations[suitOf(card)] - 1)
                columnCards[column][columnSize[column]++] = card
                refresh(column)
            }
            KIND_TABLEAU_TABLEAU -> {
                val from = undoA[slot]
                val to = undoB[slot]
                val sequenceLength = undoC[slot]
                if (undoD[slot] == 1) columnDown[from]++
                val source = columnSize[to] - sequenceLength
                for (offset in 0 until sequenceLength) columnCards[from][columnSize[from] + offset] = columnCards[to][source + offset]
                columnSize[from] += sequenceLength
                columnSize[to] -= sequenceLength
                refresh(from)
                refresh(to)
            }
            KIND_FOUNDATION_TABLEAU -> {
                columnSize[undoB[slot]]--
                refresh(undoB[slot])
                setFoundationRank(undoA[slot], foundations[undoA[slot]] + 1)
            }
            KIND_WASTE_FOUNDATION, KIND_WASTE_TABLEAU -> {
                val index = undoA[slot]
                val card = undoB[slot]
                if (undoKind[slot] == KIND_WASTE_FOUNDATION) {
                    setFoundationRank(suitOf(card), foundations[suitOf(card)] - 1)
                } else {
                    columnSize[undoD[slot]]--
                    refresh(undoD[slot])
                }
                for (shift in pileSize downTo index + 1) pile[shift] = pile[shift - 1]
                pile[index] = card
                pileSize++
                wasteCount = undoC[slot]
            }
        }
    }

    /** Auto-flip: a column left with only face-down cards turns its top face up. */
    private fun flipIfNeeded(column: Int): Boolean {
        val size = columnSize[column]
        if (size > 0 && columnDown[column] == size) {
            columnDown[column] = size - 1
            return true
        }
        return false
    }

    private fun push(kind: Int, a: Int, b: Int, c: Int, d: Int) {
        undoKind[depth] = kind
        undoA[depth] = a
        undoB[depth] = b
        undoC[depth] = c
        undoD[depth] = d
        depth++
    }

    // ---- priority order ----

    /**
     * Priority rank of a packed tap under [ruleset]: **lower wins**, and equal ranks go to
     * whichever the generator emitted first.
     *
     * This is the single implementation of every tier's priority order. The solution
     * generator enumerates moves over `GameState` instead, because a shipped line needs the
     * draws too, but it ranks them by calling back into the functions below — so the line
     * that ships cannot drift from the line the tier was graded on.
     */
    fun tapRank(move: Int, ruleset: Ruleset = Ruleset.TRIVIAL): Int = when (tapOf(move)) {
        TAP_FOUNDATION -> foundationRank(fieldA(move), ruleset)
        TAP_REVEAL -> revealRank(fieldA(move), ruleset)
        TAP_EMPTY_COLUMN -> emptyColumnRank()
        TAP_SETUP -> setupRank()
        TAP_WITHDRAW -> withdrawRank()
        else -> pileRank(pile[pileIndexAt(fieldA(move))], kindOf(move) == KIND_WASTE_FOUNDATION, ruleset)
    }

    /**
     * Sending [column]'s top card up. Trivial always ranks this first — it ignores safety
     * entirely. Easy and Medium withhold the card in the cases below, which demotes the tap
     * to last resort rather than removing it.
     */
    fun foundationRank(column: Int, ruleset: Ruleset): Int {
        if (ruleset == Ruleset.TRIVIAL) return TAP_FOUNDATION * RANK_STEP
        val card = columnCards[column][columnSize[column] - 1]
        val withhold = rushesFoundation(card) ||
            isOnlyLandingSpot(column) ||
            (ruleset >= Ruleset.MEDIUM && heldByFullRestraint(card))
        return if (withhold) RANK_WITHHELD else TAP_FOUNDATION * RANK_STEP
    }

    /**
     * Turning a card over. Easy prefers the reveal whose column still has the most face-down
     * cards buried in it — the tier's one comparison between moves that are both obvious,
     * and cheap because the count is already maintained. Medium inherits it unchanged.
     */
    fun revealRank(column: Int, ruleset: Ruleset): Int =
        if (ruleset == Ruleset.TRIVIAL) TAP_REVEAL * RANK_STEP
        else TAP_REVEAL * RANK_STEP + (MAX_COLUMN - columnDown[column])

    fun emptyColumnRank(): Int = TAP_EMPTY_COLUMN * RANK_STEP

    fun setupRank(): Int = TAP_SETUP * RANK_STEP

    fun withdrawRank(): Int = TAP_WITHDRAW * RANK_STEP

    /**
     * Bringing [card] down from the pile. A tap that resolves to a foundation is a foundation
     * play wherever the card came from, so the restraint rules apply to it too — but not
     * [isOnlyLandingSpot], since a card still in the pile is not a landing spot for anything.
     */
    fun pileRank(card: Int, toFoundation: Boolean, ruleset: Ruleset): Int {
        if (ruleset == Ruleset.TRIVIAL || !toFoundation) return TAP_PILE * RANK_STEP
        val withhold = rushesFoundation(card) || (ruleset >= Ruleset.MEDIUM && heldByFullRestraint(card))
        return if (withhold) RANK_WITHHELD else TAP_PILE * RANK_STEP
    }

    /**
     * Medium's **full foundation restraint**: rank 3 and above is held back until sending it
     * is provably safe, unconditionally, rather than only when a one-step check finds an
     * immediate reason not to.
     *
     * "Provably safe" is the classic rule the strategy literature states and
     * `DIFFICULTY_LEVELS.md` specifies for this tier — *both opposite-colour foundations
     * already at the rank beneath*. That is deliberately weaker than the app's own
     * `isSafeFoundationMove` (`RULES.md`), which requires **every** suit to be there before it
     * will auto-play a card. The app's rule governs what automation does unasked, where the
     * cost of being wrong is a move the player did not choose; this one describes a player
     * deciding for themselves, and holding a card back for a same-colour foundation buys
     * nothing — nothing of that colour can ever need it as cover.
     */
    private fun heldByFullRestraint(card: Int): Boolean {
        if (rankOf(card) <= 2) return false
        val needed = rankOf(card) - 1
        val red = isRed(card)
        for (suit in 0 until SUITS) {
            if (isRed(suit * RANKS) == red) continue
            if (foundations[suit] < needed) return true
        }
        return false
    }

    /**
     * "Don't rush the foundation": a card more than four ranks above the lowest foundation is
     * probably still wanted as tableau cover. A crude numeric stand-in, chosen because it
     * costs nothing to evaluate and needs no lookahead.
     *
     * It can never fire on a *safe* card. Safety requires every foundation to be at least at
     * the rank beneath, so the lowest is at most one below — well inside the margin.
     */
    internal fun rushesFoundation(card: Int): Boolean {
        var lowest = RANKS
        for (suit in 0 until SUITS) if (foundations[suit] < lowest) lowest = foundations[suit]
        return rankOf(card) > lowest + foundationSpreadLimit
    }

    /**
     * True when [column]'s top card is the only place some other face-up card can currently
     * go — banking it would strand that card.
     *
     * Deliberately a one-step check against the board **as it stands**: the movable bottom of
     * every other column's run, plus the card face up on the waste. Cards still in the stock
     * are not considered, because that would be planning for what might be drawn rather than
     * reading what is visible, and Easy's whole character is that it never looks ahead.
     */
    internal fun isOnlyLandingSpot(column: Int): Boolean {
        val card = columnCards[column][columnSize[column] - 1]
        for (other in 0 until TABLEAU_COLUMNS) {
            if (other == column) continue
            val start = deepestRunStart(other)
            if (start >= 0 && hasNoOtherHome(columnCards[other][start], card, column, other)) return true
        }
        if (wasteCount > 0 && hasNoOtherHome(pile[wasteCount - 1], card, column, -1)) return true
        return false
    }

    /** Index into [taps] of the tap an undeviating [ruleset] player takes, or -1 when there are none. */
    fun referenceTapIndex(taps: IntArray, count: Int, ruleset: Ruleset = Ruleset.TRIVIAL): Int {
        var best = -1
        for (index in 0 until count) {
            if (best < 0 || tapRank(taps[index], ruleset) < tapRank(taps[best], ruleset)) best = index
        }
        return best
    }

    /** True when [mover] stacks on [spot] and no column other than [spotColumn] would take it. */
    private fun hasNoOtherHome(mover: Int, spot: Int, spotColumn: Int, moverColumn: Int): Boolean {
        if (!canBuild(spot, mover)) return false
        for (to in 0 until TABLEAU_COLUMNS) {
            if (to == spotColumn || to == moverColumn) continue
            if (canPlaceOnColumn(to, mover)) return false
        }
        return true
    }

    companion object {
        const val MAX_COLUMN = 24
        const val MAX_DEPTH = 256
        const val DECK = 52
        const val SUITS = 4
        const val RANKS = 13

        const val KIND_TABLEAU_FOUNDATION = 0
        const val KIND_TABLEAU_TABLEAU = 1
        const val KIND_WASTE_FOUNDATION = 2
        const val KIND_WASTE_TABLEAU = 3
        const val KIND_FOUNDATION_TABLEAU = 4

        /**
         * Tap categories in the reference player's priority order: bank a card, turn one
         * over, free a column, then bring one down from the pile.
         *
         * Emptying a column sits **between** revealing and playing the pile. It is obvious —
         * a beginner frees a column as readily as they turn a card over — but it is not the
         * same move, so it gets its own rank rather than sharing the reveal's, where an
         * arbitrary column-order tie-break would decide between turning a card over and
         * stripping a column bare.
         */
        const val TAP_FOUNDATION = 0
        const val TAP_REVEAL = 1
        const val TAP_EMPTY_COLUMN = 2
        const val TAP_PILE = 3

        /**
         * Hard's setup move, ranked behind everything that gains something visibly. A tier
         * that reaches for it before taking a free card is not playing a plan, it is
         * fidgeting.
         */
        const val TAP_SETUP = 4

        /**
         * Expert's foundation withdrawal, ranked behind even the setup move. It undoes banked
         * progress, so a tier reaches for it only when nothing else is on offer.
         */
        const val TAP_WITHDRAW = 5

        /** A move a rule wanted to withhold, kept but ordered behind every other. */
        const val TAP_DEFERRED = 6

        /**
         * Ranks are `category * RANK_STEP` plus a within-category tie-break, so a tier can
         * order two moves of the same kind without disturbing the order between kinds. The
         * step exceeds [MAX_COLUMN], the largest tie-break any rule produces.
         */
        const val RANK_STEP = 100

        /** columnSize + columnDown + cards per column, then foundations, pileSize, wasteCount, pile. */
        const val SNAPSHOT_BYTES = TABLEAU_COLUMNS * (2 + MAX_COLUMN) + SUITS + 2 + DECK

        /**
         * Candidate strategy rules, each a hypothesis to be measured rather than assumed.
         * Safety is established the cheap way — by whether a search restricted to them still
         * finds wins as often — never by an argument.
         */
        const val RULE_FOUNDATION_SPREAD = 1
        const val RULE_NO_IDLE_LOW_PILE = 2
        const val RULE_NO_IDLE_SETUP = 4
        const val RULE_KING_WHEN_COLUMNS_SPARE = 8

        /**
         * Easy's "don't rush the foundation" margin, and the default every board starts at.
         * Four is what the tier shipped with; it is a `var` only so an experiment can sweep it,
         * and anything that changes it must put it back.
         */
        var DEFAULT_FOUNDATION_SPREAD = 4

        private const val NO_ACCEPTOR = -2
        private const val EMPTY_ACCEPTOR = -1
        private const val FOUNDATION_DESTINATION = 1 shl TABLEAU_COLUMNS
        private const val TABLEAU_DESTINATIONS = FOUNDATION_DESTINATION - 1
        private const val PILE_BIT = 1 shl TABLEAU_COLUMNS
        private const val FOUNDATION_BIT = 1 shl (TABLEAU_COLUMNS + 1)

        /** Behind every other move: a withheld card goes up only when nothing else is offered. */
        const val RANK_WITHHELD = (TAP_WITHDRAW + 1) * RANK_STEP

        private const val RESOLVE_FOUNDATION = -2
        private const val RESOLVE_NONE = -1

        private const val BASIS = -3750763034362895579L
        private const val PRIME = 1099511628211L

        /** SplitMix64 finaliser: spreads a signature across all 64 bits before it is summed. */
        fun avalanche(value: Long): Long {
            var x = value
            x = (x xor (x ushr 30)) * -0x40a7b892e31b1a47L
            x = (x xor (x ushr 27)) * -0x6b2fb644ecceee15L
            return x xor (x ushr 31)
        }

        fun suitOf(card: Int): Int = card / RANKS
        fun rankOf(card: Int): Int = card % RANKS + 1
        fun isRed(card: Int): Boolean = suitOf(card) == 1 || suitOf(card) == 2

        fun pack(kind: Int, tap: Int, a: Int, b: Int, c: Int): Int =
            kind or (tap shl 3) or ((a + 1) shl 6) or ((b + 1) shl 12) or ((c + 1) shl 18)

        fun kindOf(move: Int): Int = move and 7
        fun tapOf(move: Int): Int = (move shr 3) and 7
        fun fieldA(move: Int): Int = ((move shr 6) and 63) - 1
        fun fieldB(move: Int): Int = ((move shr 12) and 63) - 1
        fun fieldC(move: Int): Int = ((move shr 18) and 63) - 1
    }
}
