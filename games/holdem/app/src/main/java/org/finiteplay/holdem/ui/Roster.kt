package org.finiteplay.holdem.ui

import org.finiteplay.cards.Animal
import org.finiteplay.cards.SplitMix64
import org.finiteplay.holdem.opponents.drawOpponents
import org.finiteplay.holdem.rules.Contract

/** The style word shown at a seat (`UI_SPEC.md` "Seats"). */
enum class Style { TIGHT, LOOSE, AGGRESSIVE, PASSIVE }

/**
 * One opponent as the table shows it: an animal, whose made-up name, style word and drawn icon are
 * the same in every game (see [Animal]).
 */
data class OpponentProfile(val animal: Animal) {
    val name: String get() = animal.displayName
    val style: Style get() = Style.valueOf(animal.style.name)
}

/**
 * Who sits at seats 1 to 5 for a tournament. A seam, so previews and tests can seat a fixed table.
 */
fun interface OpponentRoster {
    fun profiles(tournamentSeed: Long): List<OpponentProfile>
}

/** Five animals drawn from the tournament seed alone, so a tournament always seats the same table. */
object SeededRoster : OpponentRoster {
    override fun profiles(tournamentSeed: Long): List<OpponentProfile> {
        val random = SplitMix64(tournamentSeed xor 0x0DDB1A5E5BAD5EEDL)
        val pool = Animal.entries.toMutableList()
        return List(Contract.SEATS - 1) { OpponentProfile(pool.removeAt(random.nextBounded(pool.size.toUInt()).toInt())) }
    }
}

/** The opponents the engine plays against: each profile's animal, from the tournament seed alone. */
object DrawnRoster : OpponentRoster {
    override fun profiles(tournamentSeed: Long): List<OpponentProfile> =
        drawOpponents(tournamentSeed).map { OpponentProfile(it.animal) }
}
