package org.finiteplay.holdem.ui

import org.finiteplay.cards.SplitMix64
import org.finiteplay.holdem.opponents.drawOpponents
import org.finiteplay.holdem.rules.Contract

/** The style word shown at a seat (`UI_SPEC.md` "Seats"). */
enum class Style { TIGHT, LOOSE, AGGRESSIVE, PASSIVE }

/** One opponent as the table shows it: a name, a style word and the colour of its drawn avatar. */
data class OpponentProfile(val name: String, val style: Style, val colorArgb: Long)

/**
 * Who sits at seats 1 to 5 for a tournament. A seam: the real opponents bring their own profiles
 * (`DESIGN.md` "Profiles"), and until they land the app draws names and styles itself.
 */
fun interface OpponentRoster {
    fun profiles(tournamentSeed: Long): List<OpponentProfile>
}

/** Plain first names and a style each, drawn from the tournament seed alone, so a tournament always seats the same table. */
object SeededRoster : OpponentRoster {
    private val names = listOf(
        "Ava", "Ben", "Cleo", "Dan", "Elsa", "Finn", "Gus", "Hana", "Ivan", "June", "Kai", "Lena",
        "Milo", "Nora", "Omar", "Pia", "Quinn", "Rosa", "Sam", "Tess", "Uma", "Vic", "Wren", "Zoe",
    )
    private val colors = longArrayOf(
        0xFFE57373, 0xFF64B5F6, 0xFF81C784, 0xFFFFB74D, 0xFFBA68C8, 0xFF4DB6AC, 0xFFF06292, 0xFFA1887F,
    )

    override fun profiles(tournamentSeed: Long): List<OpponentProfile> {
        val random = SplitMix64(tournamentSeed xor 0x0DDB1A5E5BAD5EEDL)
        val pool = names.toMutableList()
        val palette = colors.toMutableList()
        return List(Contract.SEATS - 1) {
            val name = pool.removeAt(random.nextBounded(pool.size.toUInt()).toInt())
            val color = palette.removeAt(random.nextBounded(palette.size.toUInt()).toInt())
            OpponentProfile(name, Style.entries[random.nextBounded(Style.entries.size.toUInt()).toInt()], color)
        }
    }
}

/** The opponents the engine plays against: their names, style words and a colour each, from the tournament seed alone. */
object DrawnRoster : OpponentRoster {
    private val colors = longArrayOf(
        0xFFE57373, 0xFF64B5F6, 0xFF81C784, 0xFFFFB74D, 0xFFBA68C8, 0xFF4DB6AC, 0xFFF06292, 0xFFA1887F,
    )

    override fun profiles(tournamentSeed: Long): List<OpponentProfile> {
        val random = SplitMix64(tournamentSeed xor 0x0DDB1A5E5BAD5EEDL)
        val palette = colors.toMutableList()
        return drawOpponents(tournamentSeed).map {
            OpponentProfile(it.name, Style.valueOf(it.style.name), palette.removeAt(random.nextBounded(palette.size.toUInt()).toInt()))
        }
    }
}
