package org.finiteplay.cards

/**
 * Canonical deck order fixed by the deal contract: Clubs, Diamonds, Hearts, Spades;
 * Ace through King. Index `i` is suit `i / 13` (in that order) and rank `i % 13`
 * (Ace = 0 .. King = 12).
 */
const val DECK_SIZE = 52

/**
 * Fisher-Yates shuffle over canonical deck indices `0..<size`, seeded by [SplitMix64],
 * frozen by the deterministic deal contract. Index `i` of the result is the deck index
 * dealt `i`-th; which indices then form which pile is the game's own rule.
 *
 * [size] exists because a game may deal more than one deck — where it does, index `i`
 * names the card `i % DECK_SIZE` and the copies are interchangeable. It does not change
 * the algorithm: the iteration still runs from the last index down, drawing one bounded
 * value per step, so a 52-card shuffle produces exactly what it always did and every
 * existing save and certified deal still replays. A change to *that* would need a new
 * shuffle version (`docs/PLATFORM.md` "Deterministic Shuffle").
 */
fun shuffleDeckIndices(seed: Long, size: Int = DECK_SIZE): IntArray {
    require(size > 0 && size % DECK_SIZE == 0) { "size must be a whole number of decks, was $size" }
    val random = SplitMix64(seed)
    val indices = IntArray(size) { it }
    for (i in size - 1 downTo 1) {
        val j = random.nextBounded((i + 1).toUInt()).toInt()
        val tmp = indices[i]
        indices[i] = indices[j]
        indices[j] = tmp
    }
    return indices
}
