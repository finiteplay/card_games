package org.finiteplay.spider.layout

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.cards.shuffleDeckIndices

/** Cards dealt to the tableau; the four leftmost columns take six, the rest five. */
private const val TABLEAU_CARDS = 54

/** Columns dealt the deeper of the two counts. */
private const val DEEP_COLUMNS = 4

/**
 * The dealt layout for [seed] at [suitCount], per the deterministic deal contract: a
 * Fisher-Yates shuffle over 104 indices, dealt index 0 first, left to right, round by round,
 * with each column's last card face up.
 *
 * The shuffle runs over deck *positions*, and [suitCount] decides what card each position
 * holds — so the one-suit and four-suit deals of the same seed are the same arrangement of
 * different cards, and a seed names a shape rather than a specific board. That is deliberate:
 * it makes the three difficulties comparable, and it means a catalog can eventually certify a
 * seed at one suit count without implying anything about the others.
 */
fun dealGame(seed: Long, versions: GameVersions, suitCount: SuitCount = SuitCount.FOUR): SpiderState {
    val deck = buildDeck(suitCount)
    val shuffled = shuffleDeckIndices(seed, DECK_CARDS).map { deck[it] }

    val tableau = List(TABLEAU_COLUMNS) { mutableListOf<TableauCard>() }
    var cursor = 0
    // Round by round rather than column by column, so a column's depth is a property of the
    // deal's shape and the last card dealt to each is the one that ends face up.
    for (round in 0 until 6) {
        for (column in 0 until TABLEAU_COLUMNS) {
            if (round == 5 && column >= DEEP_COLUMNS) continue
            tableau[column].add(TableauCard(shuffled[cursor], faceUp = false))
            cursor++
        }
    }
    check(cursor == TABLEAU_CARDS) { "expected $TABLEAU_CARDS tableau cards, dealt $cursor" }
    for (column in tableau) column[column.lastIndex] = column.last().copy(faceUp = true)

    return SpiderState(
        seed = seed,
        versions = versions,
        suitCount = suitCount,
        tableau = tableau.map { it.toList() },
        stock = shuffled.subList(cursor, shuffled.size).toList(),
        banked = Suit.entries.associateWith { 0 },
        status = GameStatus.IN_PROGRESS,
    )
}

/**
 * The 104 cards a [suitCount] game is played with, in canonical order: each suit in play
 * repeated until the two decks are full, Ace through King.
 *
 * Position `i` of this list is what shuffled index `i` names, so the deck is a function of the
 * suit count alone and never of the seed.
 */
internal fun buildDeck(suitCount: SuitCount): List<Card> = buildList {
    repeat(suitCount.setsPerSuit) {
        for (suit in suitCount.suits) for (rank in Rank.entries) add(Card(suit, rank))
    }
}
