package org.finiteplay.spider.ui.game

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.spider.layout.GameStatus
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.layout.TableauCard
import org.finiteplay.core.storage.DealProgress
import org.finiteplay.core.storage.DealStatus
import org.finiteplay.core.ui.layout.DiscardingAction
import org.finiteplay.spider.game.DealSequence
import org.junit.Assert.assertEquals
import org.junit.Test

private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

/**
 * [SpiderViewModel.hintsUsedThisGame] — the field [SpiderHistoryRecord.hintsUsed]
 * (`SpiderStatistics.kt`) is built from.
 */
class SpiderViewModelTest {

    /** Column 0 a lone Nine of spades, column 1 a lone Ten — the Nine is liftable onto the Ten. */
    private fun boardWithAMovableCard(): SpiderState = SpiderState(
        tableau = (0 until TABLEAU_COLUMNS).map { i ->
            when (i) {
                0 -> listOf(TableauCard(Card(Suit.SPADES, Rank.NINE), faceUp = true))
                1 -> listOf(TableauCard(Card(Suit.SPADES, Rank.TEN), faceUp = true))
                else -> emptyList()
            }
        },
        stock = emptyList(),
        banked = Suit.entries.associateWith { 0 },
        suitCount = SuitCount.ONE,
        seed = 0L,
        versions = VERSIONS,
        moveCount = 0,
        status = GameStatus.IN_PROGRESS,
    )

    @Test
    fun `highlighting movable cards is not a hint use`() {
        val viewModel = SpiderViewModel(initialSeed = 1L)
        viewModel.setHintShowsWinningMove(false)
        viewModel.loadFixtureForDebugging(boardWithAMovableCard())

        viewModel.showHint()

        assertEquals(true, viewModel.hintedCards.isNotEmpty())
        assertEquals(0, viewModel.hintsUsedThisGame)
    }

    @Test
    fun `tapping hint again while it is showing dismisses it`() {
        val viewModel = SpiderViewModel(initialSeed = 1L)
        viewModel.setHintShowsWinningMove(false)
        viewModel.loadFixtureForDebugging(boardWithAMovableCard())

        viewModel.showHint()
        viewModel.showHint()

        assertEquals(0, viewModel.hintsUsedThisGame)
        assertEquals(emptySet<Any>(), viewModel.hintedCards)
        assertEquals(false, viewModel.hintedStock)
    }

    /** Every column holds a lone Ace: no empty column to accept one, and nothing outranks an Ace. */
    private fun boardWithNoMovableCard(): SpiderState = SpiderState(
        tableau = (0 until TABLEAU_COLUMNS).map { listOf(TableauCard(Card(Suit.SPADES, Rank.ACE), faceUp = true)) },
        stock = emptyList(),
        banked = Suit.entries.associateWith { 0 },
        suitCount = SuitCount.ONE,
        seed = 0L,
        versions = VERSIONS,
        moveCount = 0,
        status = GameStatus.IN_PROGRESS,
    )

    @Test
    fun `hint with no movable card lights the stock instead of any card`() {
        val viewModel = SpiderViewModel(initialSeed = 1L)
        viewModel.setHintShowsWinningMove(false)
        viewModel.loadFixtureForDebugging(boardWithNoMovableCard())

        viewModel.showHint()

        assertEquals(emptySet<Any>(), viewModel.hintedCards)
        assertEquals(true, viewModel.hintedStock)
    }

    @Test
    fun `dismissing a stock hint clears it`() {
        val viewModel = SpiderViewModel(initialSeed = 1L)
        viewModel.setHintShowsWinningMove(false)
        viewModel.loadFixtureForDebugging(boardWithNoMovableCard())
        viewModel.showHint()
        assertEquals(true, viewModel.hintedStock)

        viewModel.dismissHint()

        assertEquals(false, viewModel.hintedStock)
    }

    @Test
    fun `a stock hint is not a hint use either`() {
        val viewModel = SpiderViewModel(initialSeed = 1L)
        viewModel.setHintShowsWinningMove(false)
        viewModel.loadFixtureForDebugging(boardWithNoMovableCard())

        viewModel.showHint()
        viewModel.showHint()

        assertEquals(0, viewModel.hintsUsedThisGame)
    }

    /** The status row's suit-count picker deals a new game at the chosen count right away, the
     * same "picking is playing a different hand" call Klondike's own `setDifficulty` makes. */
    @Test
    fun `setSuitCount to a different count deals a new game at that count`() {
        val viewModel = SpiderViewModel(initialSeed = 1L)
        check(viewModel.session.state.suitCount == SuitCount.ONE)

        viewModel.setSuitCount(SuitCount.TWO)

        assertEquals(SuitCount.TWO, viewModel.session.state.suitCount)
    }

    /** Re-picking the count already in force must not become a free reroll of the hand. */
    @Test
    fun `setSuitCount to the count already in force changes nothing`() {
        val viewModel = SpiderViewModel(initialSeed = 1L)
        viewModel.setSuitCount(SuitCount.TWO)
        val seed = viewModel.session.state.seed

        viewModel.setSuitCount(SuitCount.TWO)

        assertEquals(seed, viewModel.session.state.seed)
    }

    /** The choice also sticks for every New Game after this one, not just the immediate redeal. */
    @Test
    fun `setSuitCount also persists as the next New Game's suit count`() {
        val viewModel = SpiderViewModel(initialSeed = 1L)

        viewModel.setSuitCount(SuitCount.FOUR)

        assertEquals(SuitCount.FOUR, viewModel.nextSuitCount)
    }

    @Test
    fun `a game with moves behind it is marked played, and a won one won`() {
        val viewModel = SpiderViewModel(initialSeed = 1L)
        val played = boardWithAMovableCard().copy(moveCount = 3)
        viewModel.loadFixtureForDebugging(played)
        assertEquals(DealProgress(DealStatus.PLAYED, 3), viewModel.dealProgress[played.seed])

        viewModel.loadFixtureForDebugging(played.copy(status = GameStatus.WON))
        assertEquals(DealProgress(DealStatus.WON, 3), viewModel.dealProgress[played.seed])
    }

    @Test
    fun `a deal keeps the moves of the game last played, then the fewest moves of any win`() {
        val viewModel = SpiderViewModel(initialSeed = 1L)
        val board = boardWithAMovableCard()

        viewModel.loadFixtureForDebugging(board.copy(moveCount = 30))
        viewModel.loadFixtureForDebugging(board.copy(moveCount = 12))
        assertEquals("the last game played, not the longest", DealProgress(DealStatus.PLAYED, 12), viewModel.dealProgress[board.seed])

        viewModel.loadFixtureForDebugging(board.copy(moveCount = 150, status = GameStatus.WON))
        viewModel.loadFixtureForDebugging(board.copy(moveCount = 180, status = GameStatus.WON))
        viewModel.loadFixtureForDebugging(board.copy(moveCount = 5))
        assertEquals("a worse win and a later unfinished game leave the best win", DealProgress(DealStatus.WON, 150), viewModel.dealProgress[board.seed])

        viewModel.loadFixtureForDebugging(board.copy(moveCount = 140, status = GameStatus.WON))
        assertEquals(DealProgress(DealStatus.WON, 140), viewModel.dealProgress[board.seed])
    }

    @Test
    fun `an untouched board has no progress`() {
        val viewModel = SpiderViewModel(initialSeed = 1L)
        viewModel.loadFixtureForDebugging(boardWithAMovableCard())
        assertEquals(emptyMap<Long, DealProgress>(), viewModel.dealProgress)
    }

    @Test
    fun `picking a deal on an untouched board starts it at the suit count in play`() {
        val viewModel = SpiderViewModel(initialSeed = 1L)
        val suitCount = viewModel.session.state.suitCount

        viewModel.requestSelectDeal(5)

        assertEquals(5, viewModel.dealNumber)
        assertEquals(DealSequence.seedFor(suitCount, 5), viewModel.session.state.seed)
        assertEquals(suitCount, viewModel.session.state.suitCount)
        assertEquals(null, viewModel.pendingAction)
    }

    @Test
    fun `picking a deal while a played game is unfinished asks first`() {
        val viewModel = SpiderViewModel(initialSeed = 1L)
        viewModel.loadFixtureForDebugging(boardWithAMovableCard().copy(moveCount = 3))
        val before = viewModel.session.state.seed

        viewModel.requestSelectDeal(5)
        assertEquals(DiscardingAction.NEW_GAME, viewModel.pendingAction)
        assertEquals(before, viewModel.session.state.seed)

        viewModel.confirmPendingAction()
        assertEquals(5, viewModel.dealNumber)
        assertEquals(null, viewModel.pendingAction)
    }

    @Test
    fun `cancelling the confirmation forgets the pick`() {
        val viewModel = SpiderViewModel(initialSeed = 1L)
        viewModel.loadFixtureForDebugging(boardWithAMovableCard().copy(moveCount = 3))
        val before = viewModel.session.state.seed

        viewModel.requestSelectDeal(5)
        viewModel.dismissPendingAction()
        viewModel.confirmPendingAction()

        assertEquals(before, viewModel.session.state.seed)
    }

    @Test
    fun `choosing another suit count in Settings starts a new game at it`() {
        val viewModel = SpiderViewModel(initialSeed = 1L)
        val before = viewModel.session.state.suitCount
        val other = SuitCount.entries.first { it != before }

        viewModel.requestSuitCount(other)

        assertEquals(other, viewModel.session.state.suitCount)
        assertEquals(null, viewModel.pendingAction)
    }

    @Test
    fun `choosing another suit count mid-game asks first, and cancelling keeps the game`() {
        val viewModel = SpiderViewModel(initialSeed = 1L)
        viewModel.loadFixtureForDebugging(boardWithAMovableCard().copy(moveCount = 3))
        val before = viewModel.session.state.suitCount
        val other = SuitCount.entries.first { it != before }

        viewModel.requestSuitCount(other)
        assertEquals(DiscardingAction.NEW_GAME, viewModel.pendingAction)
        assertEquals(before, viewModel.session.state.suitCount)

        viewModel.dismissPendingAction()
        viewModel.confirmPendingAction()
        assertEquals(before, viewModel.session.state.suitCount)

        viewModel.requestSuitCount(other)
        viewModel.confirmPendingAction()
        assertEquals(other, viewModel.session.state.suitCount)
    }

    @Test
    fun `choosing the suit count already in play starts nothing`() {
        val viewModel = SpiderViewModel(initialSeed = 1L)
        viewModel.loadFixtureForDebugging(boardWithAMovableCard().copy(moveCount = 3))
        viewModel.requestSuitCount(viewModel.session.state.suitCount)
        assertEquals(null, viewModel.pendingAction)
    }
}
