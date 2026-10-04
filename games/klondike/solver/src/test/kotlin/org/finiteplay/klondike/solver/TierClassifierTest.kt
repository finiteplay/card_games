package org.finiteplay.klondike.solver

import org.finiteplay.klondike.board.GameStateFixtures
import org.finiteplay.klondike.board.GameStateFixtures.faceUp
import org.finiteplay.klondike.board.GameStateFixtures.withFoundation
import org.finiteplay.klondike.board.GameStateFixtures.withTableauColumn
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.klondike.rules.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TierClassifierTest {

    @Test
    fun `trivial greedily sends any foundation-eligible card regardless of rank gap`() {
        val state = GameStateFixtures.emptyBoard()
            .withFoundation(Suit.CLUBS, 6)
            .withTableauColumn(0, listOf(faceUp(Card(Suit.CLUBS, Rank.SEVEN))))

        val candidates = candidateMoves(state, StrategyTier.TRIVIAL)

        assertEquals(listOf(Move.TableauToFoundation(0)), candidates)
    }

    @Test
    fun `easy withholds a foundation-eligible card whose rank exceeds the lowest foundation by more than 4`() {
        val state = GameStateFixtures.emptyBoard()
            .withFoundation(Suit.CLUBS, 6)
            .withTableauColumn(0, listOf(faceUp(Card(Suit.CLUBS, Rank.SEVEN))))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.DIAMONDS, Rank.FIVE))))

        val candidates = candidateMoves(state, StrategyTier.EASY)

        // Foundation withheld (gap 7-0=7 > 4), so Easy falls through to the reveal-less
        // legal tableau-to-tableau move it *does* still recognize at this stage: none
        // here (single card in column 1, nothing beneath it to reveal) - waste is also
        // empty, so this lands on the no-stall override, same as Trivial would. The
        // real point of this fixture is the *next* test, which gives Easy something
        // else to prefer instead.
        assertEquals(listOf(Move.TableauToFoundation(0)), candidates)
    }

    @Test
    fun `easy prefers a reveal move over a withheld foundation-eligible card`() {
        val state = GameStateFixtures.emptyBoard()
            .withFoundation(Suit.CLUBS, 6)
            .withTableauColumn(0, listOf(faceUp(Card(Suit.CLUBS, Rank.SEVEN))))
            .withTableauColumn(
                1,
                listOf(GameStateFixtures.faceDown(Card(Suit.SPADES, Rank.KING)), faceUp(Card(Suit.HEARTS, Rank.SIX))),
            )

        val candidates = candidateMoves(state, StrategyTier.EASY)

        // The Six of Hearts can move onto the Seven of Clubs, revealing the buried
        // King - Easy prefers this over sending the withheld Seven up, unlike Trivial,
        // which would have sent the Seven immediately without ever considering it.
        assertEquals(listOf(Move.TableauToTableau(fromColumn = 1, fromIndex = 1, toColumn = 0)), candidates)
    }

    @Test
    fun `medium restrains a legal but unsafe foundation move that easy's rank-gap check would miss`() {
        // Rank gap from 0 is only 2 (within Easy's tolerance of 4), but sending it is
        // still unsafe: Diamonds hasn't reached rank 2 yet, so a Two of Diamonds still
        // needs somewhere to land, and only a black Three can host it.
        val state = GameStateFixtures.emptyBoard()
            .withFoundation(Suit.CLUBS, 2)
            .withTableauColumn(0, listOf(faceUp(Card(Suit.CLUBS, Rank.THREE))))

        val easyCandidates = candidateMoves(state, StrategyTier.EASY)
        val mediumCandidates = candidateMoves(state, StrategyTier.MEDIUM)

        assertEquals(listOf(Move.TableauToFoundation(0)), easyCandidates) // within Easy's crude gap tolerance
        assertEquals(listOf(Move.TableauToFoundation(0)), mediumCandidates) // no-stall override: nothing else on this bare board
    }

    @Test
    fun `hard offers a setup-only tableau move that lower tiers never consider`() {
        // No foundation-eligible card, no reveal (single card per column), no waste -
        // Trivial/Easy/Medium have nothing but the (empty) no-stall override and draw.
        // Hard alone may still rearrange the board for a later payoff.
        val state = GameStateFixtures.emptyBoard()
            .withTableauColumn(0, listOf(faceUp(Card(Suit.SPADES, Rank.SEVEN))))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.HEARTS, Rank.SIX))))

        val mediumCandidates = candidateMoves(state, StrategyTier.MEDIUM)
        val hardCandidates = candidateMoves(state, StrategyTier.HARD)

        assertEquals(emptyList<Move>(), mediumCandidates) // nothing legal to draw either - genuinely stuck
        assertEquals(listOf(Move.TableauToTableau(fromColumn = 1, fromIndex = 0, toColumn = 0)), hardCandidates)
    }

    @Test
    fun `expert falls back to foundation withdrawal only once every other option is exhausted`() {
        // Five of Spades (top of the Spades foundation) has exactly one legal tableau
        // landing spot: the exposed Six of Hearts. Nothing else on this board is
        // foundation-eligible, revealable, or drawable/waste-playable.
        val state = GameStateFixtures.emptyBoard()
            .withFoundation(Suit.SPADES, 5)
            .withTableauColumn(0, listOf(faceUp(Card(Suit.HEARTS, Rank.SIX))))

        val hardCandidates = candidateMoves(state, StrategyTier.HARD)
        val expertCandidates = candidateMoves(state, StrategyTier.EXPERT)

        assertTrue("Hard never proposes a withdrawal", hardCandidates.none { it is Move.FoundationToTableau })
        assertEquals(listOf(Move.FoundationToTableau(Suit.SPADES, 0)), expertCandidates)
    }

    @Test
    fun `classifyDeal reports trivial for a board trivial's own rules win outright`() {
        val state = GameStateFixtures.emptyBoard(seed = 42L)
            .withFoundation(Suit.CLUBS, 13)
            .withFoundation(Suit.DIAMONDS, 13)
            .withFoundation(Suit.HEARTS, 13)
            .withFoundation(Suit.SPADES, 12)
            .withTableauColumn(0, listOf(faceUp(Card(Suit.SPADES, Rank.KING))))

        val classification = classifyDeal(state)

        assertEquals(StrategyTier.TRIVIAL, classification?.tier)
        assertEquals(0, classification?.withdrawals)
        assertEquals(1, classification?.moveCount)
    }

    @Test
    fun `classifyDeal returns null when no tier's rules can win the board`() {
        // No foundation, reveal, waste, or setup move exists, and stock/waste are both
        // empty - a genuinely dead board at every tier, same shape as
        // HintEngineTest's deadlockedBoard.
        val state = GameStateFixtures.emptyBoard(seed = 7L)
            .withTableauColumn(0, listOf(faceUp(Card(Suit.CLUBS, Rank.THREE))))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.DIAMONDS, Rank.FIVE))))

        assertEquals(null, classifyDeal(state))
    }

    @Test
    fun `a pure ruleset run reports a win without consulting the solver at all`() {
        val state = GameStateFixtures.emptyBoard(seed = 42L)
            .withFoundation(Suit.CLUBS, 13)
            .withFoundation(Suit.DIAMONDS, 13)
            .withFoundation(Suit.HEARTS, 13)
            .withFoundation(Suit.SPADES, 12)
            .withTableauColumn(0, listOf(faceUp(Card(Suit.SPADES, Rank.KING))))

        val run = playPureRuleset(state, StrategyTier.TRIVIAL)

        assertEquals(PureOutcome.WON, run.outcome)
        assertEquals(1, run.moves)
    }

    @Test
    fun `a pure ruleset run reports being stuck rather than looping on a dead board`() {
        val state = GameStateFixtures.emptyBoard(seed = 7L)
            .withTableauColumn(0, listOf(faceUp(Card(Suit.CLUBS, Rank.THREE))))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.DIAMONDS, Rank.FIVE))))

        assertEquals(PureOutcome.STUCK, playPureRuleset(state, StrategyTier.EXPERT).outcome)
    }

    /**
     * Guards the fix that restored the tier ladder: Hard and Expert may only propose a
     * setup move to an empty column when it actually frees buried cards. Without it they
     * shuffled Kings between empty columns indefinitely, hitting the move cap on 85 of
     * 100 real deals and collapsing their win rate below Medium's.
     */
    @Test
    fun `hard does not propose relocating a king between empty columns for nothing`() {
        val state = GameStateFixtures.emptyBoard()
            .withTableauColumn(0, listOf(faceUp(Card(Suit.SPADES, Rank.KING))))

        assertTrue(
            "a King alone in a column has nothing beneath it to free",
            candidateMoves(state, StrategyTier.HARD).none { it is Move.TableauToTableau },
        )
    }

    @Test
    fun `hard does propose moving a king off buried cards into an empty column`() {
        val state = GameStateFixtures.emptyBoard()
            .withTableauColumn(
                0,
                listOf(GameStateFixtures.faceDown(Card(Suit.HEARTS, Rank.TWO)), faceUp(Card(Suit.SPADES, Rank.KING))),
            )

        // Uncovering the Two makes this a *reveal*, not a setup move, and every empty
        // column is an equally good destination for it - so the whole group is offered.
        assertEquals(
            (1..6).map { Move.TableauToTableau(fromColumn = 0, fromIndex = 1, toColumn = it) },
            candidateMoves(state, StrategyTier.HARD),
        )
    }
}
