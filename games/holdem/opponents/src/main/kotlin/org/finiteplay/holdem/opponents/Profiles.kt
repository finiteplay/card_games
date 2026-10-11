package org.finiteplay.holdem.opponents

import org.finiteplay.cards.Animal
import org.finiteplay.cards.PlayStyle
import org.finiteplay.cards.SplitMix64

/**
 * The word shown at an opponent's seat (`DESIGN.md` "Profiles"). An enum rather than a string: the
 * app localizes it, and the names below are the only text this module owns.
 */
enum class Style { TIGHT, LOOSE, AGGRESSIVE, PASSIVE }

/**
 * A fixed set of parameters over the one policy (`DESIGN.md` "Profiles"). [looseness] scales every
 * preflop range (above 1 plays more hands), [aggression] is how often it bets and raises rather than
 * checks and calls, [bluff] how often it bets with nothing, [foldToPressure] how readily it folds to a
 * large bet.
 */
data class Profile(
    val id: String,
    val animal: Animal,
    val style: Style,
    val looseness: Double,
    val aggression: Double,
    val bluff: Double,
    val foldToPressure: Double,
) {
    init {
        require(animal.style == PlayStyle.valueOf(style.name)) { "$animal plays ${animal.style}, not $style" }
    }
}

object Profiles {
    val tag = Profile("tag", Animal.OWL, Style.TIGHT, 0.95, 0.70, 0.20, 0.35)
    val rock = Profile("rock", Animal.TORTOISE, Style.TIGHT, 0.70, 0.30, 0.04, 0.70)
    val station = Profile("station", Animal.PANDA, Style.LOOSE, 1.60, 0.12, 0.03, 0.10)
    val regular = Profile("regular", Animal.GECKO, Style.LOOSE, 1.25, 0.55, 0.18, 0.30)
    val lag = Profile("lag", Animal.FOX, Style.AGGRESSIVE, 1.20, 0.90, 0.35, 0.20)
    val maniac = Profile("maniac", Animal.HONEY_BADGER, Style.AGGRESSIVE, 1.50, 0.95, 0.50, 0.10)
    val caller = Profile("caller", Animal.CAPYBARA, Style.PASSIVE, 1.05, 0.10, 0.03, 0.45)

    val all: List<Profile> = listOf(tag, rock, station, regular, lag, maniac, caller)

    /** The profile the Hint plays (`DESIGN.md` "Hint"): the strongest of the set, which is also what every opponent models its opponents as. */
    val strongest: Profile = tag

    internal val strongPool = listOf(tag, lag, regular)
    internal val exploitablePool = listOf(station, rock, caller, maniac)
}

/** One opponent at the table: an animal, shown by its funny [name] and [style], playing [profile]. */
data class Opponent(val profile: Profile) {
    val animal: Animal get() = profile.animal
    val name: String get() = animal.displayName
    val style: Style get() = profile.style
}

/**
 * The five opponents for seats 1..5, from the tournament's seed alone: distinct animals, distinct
 * profiles, always two the field is weaker for and two it is stronger for, and one more of either,
 * shuffled across the seats.
 */
fun drawOpponents(tournamentSeed: Long): List<Opponent> {
    val rng = SplitMix64(mix(tournamentSeed, 0x0DD5L))
    val strongPick = Profiles.strongPool.shuffledBy(rng).take(2)
    val weakPick = Profiles.exploitablePool.shuffledBy(rng).take(2)
    val fifth = Profiles.all.filter { it !in strongPick && it !in weakPick }.shuffledBy(rng).first()
    val profiles = (strongPick + weakPick + fifth).shuffledBy(rng)
    return profiles.map(::Opponent)
}

private fun <T> List<T>.shuffledBy(rng: SplitMix64): List<T> {
    val out = toMutableList()
    for (i in out.size - 1 downTo 1) {
        val j = rng.nextInt(i + 1)
        val t = out[i]
        out[i] = out[j]
        out[j] = t
    }
    return out
}
