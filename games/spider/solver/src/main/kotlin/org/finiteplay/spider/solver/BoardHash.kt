package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.TABLEAU_COLUMNS

/**
 * Sorts the ten per-column signatures ascending, which is what makes a key blind to column order.
 *
 * Hand-rolled rather than `java.util.Arrays.sort` for the same reason the column-order sort below
 * is: at ten elements the JDK's dual-pivot quicksort runs an insertion sort anyway, but reaches it
 * through a dispatch this cannot inline through — and a search calls this twice per node, at
 * millions of nodes per second. A JFR profile of a real four-suit search put **12% of all samples**
 * inside `Arrays.sort` for these two ten-element arrays alone, second only to the hash functions
 * calling it.
 */
private fun sortColumnSignatures(values: LongArray) {
    for (i in 1 until values.size) {
        val key = values[i]
        var j = i - 1
        while (j >= 0 && values[j] > key) {
            values[j + 1] = values[j]
            j--
        }
        values[j + 1] = key
    }
}

/** Reusable scratch for [canonicalHashOf], so a hot search loop allocates nothing per node. */
internal class HashScratch {
    val signature = LongArray(TABLEAU_COLUMNS)
    val rankSignature = LongArray(TABLEAU_COLUMNS)
    val order = IntArray(TABLEAU_COLUMNS)
    val suitLabel = IntArray(SUITS)

    companion object {
        /** Suit labels a card byte can carry (`suit * 13 + rank`), not how many a deal uses. */
        const val SUITS = 4
    }
}

/**
 * A transposition key that treats both **columns** and **suits** as interchangeable.
 *
 * Two symmetries, and Spider positions spend a large share of their nominal branching on both:
 *
 * **Column order.** Nothing in Spider depends on which column a pile sits in, so sorting the
 * per-column signatures before combining them collapses every permutation of the ten columns onto
 * one key. That was already true of this solver's original hash.
 *
 * **Suit identity.** Every rule reads suits only through *equality*: placement is by rank alone
 * (`FastBoard.canPlace` never looks at a suit), a run lifts only if adjacent cards share a suit,
 * and a sequence banks only if all thirteen share one. No rule asks *which* suit. Each suit also
 * holds the same number of cards as every other by construction, so relabelling the suits by any
 * bijection maps legal positions to legal positions and wins to wins — which makes all 4! = 24
 * relabellings of a four-suit board the same position, and this key collapses them onto one.
 * Solvitaire calls this discarding suit information in the canonical form, and reports it among the
 * techniques behind solving patience games at scale
 * (`docs/games/spider/PUBLIC_STRATEGY_RESEARCH.md` "Sources" has the citation).
 *
 * **The remaining stock is part of the relabelling, and has to be.** A board's future depends on the
 * suits of the cards still to be dealt, so canonicalising the tableau alone would merge two
 * positions whose tableaux agree up to relabelling but whose next row deals do not — silently
 * discarding a real position, which can lose a solution rather than merely cost time. The label walk
 * below therefore covers the tableau *and* `stock[stockPos until size]`.
 *
 * Suits are labelled by first appearance along an order that is itself suit-independent: columns
 * sorted by a rank-only signature (ties broken by column index, which a relabelling cannot move),
 * then the undealt stock in deal order. A relabelling leaves that walk's *positions* untouched, so
 * it assigns label 0 to whichever suit sits first either way — which is what makes the result
 * invariant. Ties between columns with identical ranks but different suits can order arbitrarily;
 * that only costs a collapse the key could have made, never a wrong merge.
 *
 * [scratch] is caller-owned and never mutates the board.
 */
internal fun canonicalHashOf(board: FastBoard, scratch: HashScratch): Long {
    val rankSignature = scratch.rankSignature
    val order = scratch.order
    // Reused from the board rather than rebuilt from its cards: a move changes one or two columns,
    // and the rest are still what they were ([FastBoard.rankSignatureOf]).
    for (c in 0 until TABLEAU_COLUMNS) {
        rankSignature[c] = board.rankSignatureOf(c)
        order[c] = c
    }
    // Insertion sort of ten entries by (rank signature, column index): cheaper than a comparator
    // over boxed indices, and the index tie-break is what keeps the order deterministic.
    for (i in 1 until TABLEAU_COLUMNS) {
        val candidate = order[i]
        val key = rankSignature[candidate]
        var j = i - 1
        while (j >= 0 && (rankSignature[order[j]] > key || (rankSignature[order[j]] == key && order[j] > candidate))) {
            order[j + 1] = order[j]
            j--
        }
        order[j + 1] = candidate
    }

    val suitLabel = scratch.suitLabel
    // Four stores, not `Arrays.fill`: same reason as [sortColumnSignatures] — the call showed up in
    // the same profile for an array this small.
    suitLabel[0] = -1; suitLabel[1] = -1; suitLabel[2] = -1; suitLabel[3] = -1
    var nextLabel = 0
    // Stops the moment every suit has a label: from there on each remaining card would only find
    // its suit already labelled, so the walk's result is fixed and the rest of it is pure cost —
    // and on a four-suit board all four suits usually turn up within the first column, against the
    // hundred-odd tableau cards and fifty stock cards this would otherwise read at every node.
    // Fewer-suit games simply never trip it and walk exactly as far as they did before.
    labelWalk@ for (index in 0 until TABLEAU_COLUMNS) {
        val c = order[index]
        val col = board.cards[c]
        for (i in 0 until board.len[c]) {
            val suit = board.suitOf(col[i])
            if (suitLabel[suit] < 0) {
                suitLabel[suit] = nextLabel++
                if (nextLabel == HashScratch.SUITS) break@labelWalk
            }
        }
    }
    if (nextLabel < HashScratch.SUITS) {
        for (k in board.stockPos until board.stock.size) {
            val suit = board.suitOf(board.stock[k])
            if (suitLabel[suit] < 0) {
                suitLabel[suit] = nextLabel++
                if (nextLabel == HashScratch.SUITS) break
            }
        }
    }

    val signature = scratch.signature
    for (c in 0 until TABLEAU_COLUMNS) {
        var h = 0x9E3779B97F4A7C15uL.toLong() + board.faceDown[c] * 0x100000001B3L
        val col = board.cards[c]
        for (i in 0 until board.len[c]) {
            val card = col[i]
            val relabelled = suitLabel[board.suitOf(card)] * FastBoard.RANKS + board.rankOf(card)
            h = h * 0x100000001B3L xor (relabelled.toLong() + 1L)
        }
        signature[c] = h
    }
    sortColumnSignatures(signature)

    var combined = board.banked * 0x9E3779B1L + board.stockPos * 0x85EBCA77L
    for (h in signature) combined = combined * 0x100000001B3L xor h

    // The undealt stock's *relabelled* contents, in deal order, and not merely its position.
    //
    // Position alone was enough before suits were canonicalised — every board in one solve shares
    // the same stock array and only advances into it, so `stockPos` determined the rest exactly.
    // Under relabelling it is not: two boards whose tableaux are relabellings of each other carry
    // *different* relabelled stocks, and hashing only the position would merge them, making the
    // search skip a position it had never looked at. `CanonicalHashTest` pins this case; it failed
    // against an earlier version of this function that folded the stock into the label walk but not
    // into the key.
    for (k in board.stockPos until board.stock.size) {
        val card = board.stock[k]
        val relabelled = suitLabel[board.suitOf(card)] * FastBoard.RANKS + board.rankOf(card)
        combined = combined * 0x100000001B3L xor (relabelled.toLong() + 1L)
    }
    return combined
}

/**
 * A transposition key that discards suit **entirely** rather than merely relabelling it — two cards
 * of the same rank hash identically whatever suit either actually carries. This is the streamliner
 * Solvitaire uses for any-suit-build games (their `is_suit_symmetry` path maps every card to
 * `card(0, rank)`), not a strengthening of [canonicalHashOf]: it is **unsound**, since it merges
 * positions that differ in whether a run is liftable (lifting a run requires the cards to actually
 * share a suit, which this key cannot see). `docs/games/spider/EXECUTION_PLAN.md` "S6" is why it
 * exists — plain DFS with this reduction is most of what closes the gap between our solver and a
 * published ~97%-winnable result on the identical game.
 *
 * **The correctness contract that makes an unsound key safe to use at all:** a search built on this
 * key may only ever report finding a line, never that it looked everywhere and found none. A merge
 * can make the search skip a position that was actually reachable and skip past a win it never
 * looked at — but it can never invent moves, so any line it *does* find is a real, legal sequence
 * from the starting board, and every caller of this key already independently replays what it finds
 * through the canonical reducer before trusting it (`SpiderSolver.certify`'s own doc). What a search
 * over this key must never do is report a board unsolvable: `SpiderSolver.certifyStreamlined`
 * enforces that structurally by never returning [SolveResult.EXHAUSTED] at all, so the constraint
 * cannot be violated by a future call site forgetting to check.
 *
 * Column order is still collapsed (unchanged from [canonicalHashOf]), since that reduction is sound
 * on its own and free to keep. [scratch] is caller-owned and never mutates the board; only its
 * [HashScratch.rankSignature]/[HashScratch.order] fields are used; the suit-labelling fields are
 * [canonicalHashOf]'s alone.
 */
internal fun streamlinedHashOf(board: FastBoard, scratch: HashScratch): Long {
    // Identical per-column signatures to [canonicalHashOf]'s, and from the same cache — this key is
    // that one without the relabelling pass, so with the signatures reused there is no card read
    // left in it at all on a node whose columns the last move did not touch.
    val rankSignature = scratch.rankSignature
    for (c in 0 until TABLEAU_COLUMNS) rankSignature[c] = board.rankSignatureOf(c)
    sortColumnSignatures(rankSignature)

    var combined = board.banked * 0x9E3779B1L + board.stockPos * 0x85EBCA77L
    for (h in rankSignature) combined = combined * 0x100000001B3L xor h
    // The undealt stock needs no pass of its own here, unlike in [canonicalHashOf]. Every board in
    // one search shares one `stock` array and only advances into it, so `stock[stockPos until
    // size]` is a function of `stockPos` — already mixed in above — and this key relabels nothing
    // that could make two boards at the same position carry different remaining cards. (That is
    // exactly what does happen under [canonicalHashOf]'s relabelling, which is why its own stock
    // pass is load-bearing and documented as such; discarding suit outright removes the need.)
    // Hashing the tail as well would cost up to fifty reads per node to distinguish nothing.
    return combined
}

/**
 * A transposition key for positions of **one deal**, for [PhaseSearch]: blind to column order (the
 * per-column signatures are mixed and then summed, and a sum does not care about order) but exact
 * about suits, and keyed on the stock by position alone — within one search the undealt stock is the
 * same array throughout, so where it stands is all that can differ.
 *
 * Far cheaper than [canonicalHashOf], which re-walks every card and the stock to relabel suits at
 * every node (a profile put it at two thirds of a phase search's time): this rehashes only the
 * columns a move touched ([FastBoard.cardSignatureOf]). The suit relabelling it gives up is a
 * constant-factor collapse a hint search has no use for; and it must never be mixed into the same
 * set as either other key.
 */
internal fun dealKeyOf(board: FastBoard): Long {
    var sum = board.banked * -0x61c8864680b583ebL + board.stockPos * 0x2545F4914F6CDD1DL
    for (c in 0 until TABLEAU_COLUMNS) {
        var z = board.cardSignatureOf(c)
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        sum += z xor (z ushr 31)
    }
    return sum
}
