package org.finiteplay.holdem.rules

import org.finiteplay.holdem.rules.Fx.checkDown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The deterministic deal contract (`EXECUTION_PLAN.md`): reference vectors, committed. A change is a version change. */
class SeedAndDealContractTest {
    /** SplitMix64's finalizer, written out here so the mix is checked against its definition, not against itself. */
    private fun finalize(x: ULong): Long {
        var z = x
        z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
        z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
        return (z xor (z shr 31)).toLong()
    }

    private val tournaments = listOf(0L, 1L, 42L, 20261006L, -1L, Long.MAX_VALUE)

    private val firstButtons = mapOf(0L to 3, 1L to 2, 42L to 2, 20261006L to 4, -1L to 3, Long.MAX_VALUE to 0)

    private val firstTenHandSeeds = mapOf(
        0L to listOf(7960286522194355700L, 487617019471545679L, -537132696929009172L, 1961750202426094747L, 6038094601263162090L, 3207296026000306913L, -4214222208109204676L, 4532161160992623299L, -884877559730491226L, 7313543279846440201L),
        1L to listOf(-4689498862643123097L, -534904783426661026L, 8196980753821780235L, 8195237237126968761L, -4373826470845021568L, -2262517385565684571L, -8797857673641491083L, 5266705631892356520L, -3800091893662914666L, 7455107161863376737L),
        42L to listOf(2949826092126892291L, 5139283748462763858L, 6349198060258255764L, 701532786141963250L, -2430762948046562554L, 4028864712777624925L, -3677692746721775708L, 6270620877612482005L, -7037763681458882642L, 3779771651426294207L),
        20261006L to listOf(-3461553275080918892L, -121935090718141959L, -739479839461232008L, -6434164657926566281L, -3015733191425161991L, 5386255896105272051L, 282018161716680386L, -7551059914617262818L, -832620825350744318L, -2603447042100315188L),
        -1L to listOf(-1612297016619662647L, 4048727598324417001L, 7862637804313477842L, -5431262886246717010L, -3234237927366542541L, -1058577943711170651L, 4638043754431676516L, -4251777345030058876L, 224706085343030812L, 266333147328794389L),
        Long.MAX_VALUE to listOf(-1005427240264861369L, -1435078927205645936L, 2314904739866303483L, 5990184416167851723L, 2076871689085313299L, 3255033911170563879L, -8354410678317521450L, -3590066590701544131L, -2825049472176592815L, -3020117430252748001L),
    )

    private val decks = mapOf(
        0L to "8S JH 9C 2S 2D QD 6S TC 3D AC JC 7C 9H 8C QS KH 5H 2C 3H 5C AH 6C 4S TD 5D 8H 8D AD QH TS QC JD 3S 4D 2H 9D 3C KD AS 7S 9S 7D JS 6H KS 4C 7H 5S 6D 4H KC TH",
        1L to "7D 9C 4D 8H 8D 5S JD 6D 9S JC 5D KD 3C 2D JH 5H 7H 4H 2C AD 6S KC KH TH TS JS TD 8S QC 4S 6C 7C AS 5C AH KS 6H QS QH QD 9D 3D 3S AC 4C 2H 8C TC 3H 2S 9H 7S",
        42L to "7C 3H KC AS 2S AC AH KD TD 5C 8S JH 2H 4S 6H 5D 2D 5S 6D AD 7D 2C 8H 8C 4D 7H KS TS 4H 3D TH JS 4C 6S 7S 3S 8D QD 9S KH 5H 6C QS 9D JD QC 3C 9H QH 9C JC TC",
        20261006L to "5C 7C 6H 5S QD 3S 6D 2H 8D JS 8H 6C AC 5H QS 5D 9H JH 4D KD 7H 4S TS AD 2D 2C TC KC 7S KH 4H 3C 9D 9S 2S QH KS JD AH 4C 3H 9C 3D 8S QC 7D TD AS JC 8C TH 6S",
        -1L to "JC KS 7S 4C AD JH 5D 2H AH 7D 6D 6S 4D AS 4S 7H 3D 2D 9H QH 8D 3S 5S 9S AC 8S QD TD 7C QS 9C 4H JD KH KC 6H 9D 8H 8C TH 6C KD 2S 3H QC JS 3C 5H TS 2C TC 5C",
        Long.MAX_VALUE to "5C JC 8H AH TD 9H 2C TC 8S QH 3H 3C TS QC 8D AS 2D 7D 7H JS KH 3D 9S QD KC JH 7C 5D 6D 9D AD 6S 6C 2S 4S 9C 5S 6H TH 4D AC 7S 8C KD 3S 4H JD 2H 4C QS 5H KS",
    )

    @Test
    fun `the first ten hand seeds of representative tournaments`() {
        for (t in tournaments) {
            assertEquals("tournament $t", firstTenHandSeeds.getValue(t), (1..10).map { handSeed(t, it) })
        }
    }

    @Test
    fun `the first buttons`() {
        for (t in tournaments) assertEquals("tournament $t", firstButtons.getValue(t), firstButton(t))
    }

    @Test
    fun `the hand seed is the contract's mix and nothing else`() {
        val gamma = 0x9E3779B97F4A7C15uL
        for (t in tournaments) for (n in 0..40) {
            assertEquals(
                "tournament $t hand $n",
                finalize(t.toULong() + (n + 1).toULong() * gamma),
                handSeed(t, n),
            )
        }
    }

    @Test
    fun `hand seeds differ by hand and by tournament, and hand zero is never a hand`() {
        val seeds = tournaments.flatMap { t -> (0..30).map { handSeed(t, it) } }
        assertEquals(seeds.size, seeds.toSet().size)
        assertEquals(1, Tournament.start(5L).handNumber)
        assertNotEquals(handSeed(5L, 0), Tournament.start(5L).handSeed)
    }

    @Test
    fun `seeds map to the full 52-card order`() {
        for ((seed, expected) in decks) assertEquals("seed $seed", expected, Fx.text(deckFor(seed)))
    }

    @Test
    fun `a deal follows the contract, six players`() {
        val t = Tournament.start(42L)
        assertEquals(2, t.button)
        val state = dealHand(t)
        assertEquals(deckFor(handSeed(42L, 1)), state.deck)
        // Seats 3, 4, 5, 0, 1, 2 receive card 0..5, then 6..11.
        assertEquals(
            listOf("9S 7H", "KC 7S", "QD 9C", "TS 5D", "5H TH", "TD 5C"),
            state.holeCards.map(Fx::text),
        )
        val over = state.checkDown()
        assertEquals("6D KD 8D TC 2H", Fx.text(over.board))
    }

    @Test
    fun `a deal follows the contract, three players with the others out`() {
        val t = Tournament(42L, 3, 1, listOf(0, 3000, 0, 2000, 4000, 0), listOf(6, null, 5, null, null, 4))
        val state = dealHand(t)
        // Button 1: the first live seat to its left is 3, then 4, then 1; the board follows six cards.
        assertEquals(
            listOf("", "9D 3D", "", "KS TD", "8H AS", ""),
            state.holeCards.map(Fx::text),
        )
        assertEquals("8C 2D 3H 9H JC", Fx.text(state.checkDown().board))
    }

    @Test
    fun `the deal order is burn, flop, burn, turn, burn, river for every table size`() {
        for (alive in 2..6) {
            val stacks = List(6) { if (it < alive) Contract.TOTAL_CHIPS / alive else 0 }
            val t = Fx.tournament(stacks, button = 0, seed = 9L)
            val state = dealHand(t)
            val deck = state.deck
            val p = 2 * alive
            val over = state.checkDown()
            assertEquals(listOf(deck[p + 1], deck[p + 2], deck[p + 3], deck[p + 5], deck[p + 7]), over.board)
        }
    }

    @Test
    fun `a fold never changes the board a hand deals`() {
        val state = dealHand(Tournament.start(77L))
        val everyoneIn = state.checkDown()
        val first = state.toAct
        val folded = apply(state, first, Action.Fold)!!
        assertEquals(everyoneIn.board, folded.checkDown().board)
    }

    @Test
    fun `the frozen constants`() {
        assertEquals(1, Contract.RULES_VERSION)
        assertEquals(1, Contract.SHUFFLE_VERSION)
        assertEquals(6, Contract.SEATS)
        assertEquals(1_500, Contract.STARTING_STACK)
        assertEquals(10, Contract.HANDS_PER_LEVEL)
        assertTrue(Tournament.start(1L).stacks.all { it == 1_500 })
    }
}
