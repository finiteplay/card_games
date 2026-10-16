package org.finiteplay.cards

/** How a table companion plays, as the one word shown beside its name. */
enum class PlayStyle { TIGHT, LOOSE, AGGRESSIVE, PASSIVE }

/**
 * The animals that sit at our card tables. Each one is a fixed character: the same name, colour and
 * way of playing in every game that seats opponents, so "Foxtrot" is always the sneaky aggressive
 * one. [displayName] is the English original of a made-up name; the player sees it in their own language
 * (`animal_*` strings in `core:ui`, `Animal.localizedName`); [style] is how the animal plays;
 * [colorArgb] is the disc its drawn icon sits on (the art is `AnimalAvatar` in `core:ui`).
 */
enum class Animal(val displayName: String, val style: PlayStyle, val colorArgb: Long) {
    /** Plays few hands and plays them well. */
    OWL("Hootie", PlayStyle.TIGHT, 0xFFB39DDB),

    /** Folds almost everything and bets only a monster. */
    TORTOISE("Shellby", PlayStyle.TIGHT, 0xFF80CBC4),

    /** Calls everything, hopes for bamboo. */
    PANDA("Bamboozle", PlayStyle.LOOSE, 0xFFA5D6A7),

    /** Plays a lot of hands, mixes bets and calls. */
    GECKO("Gizmo", PlayStyle.LOOSE, 0xFFE6EE9C),

    /** Raises often and bluffs with a grin. */
    FOX("Foxtrot", PlayStyle.AGGRESSIVE, 0xFFFFCC80),

    /** Fearless: bets and raises with anything. */
    HONEY_BADGER("Badgerino", PlayStyle.AGGRESSIVE, 0xFFB0BEC5),

    /** Chills out and calls along, rarely raises. */
    CAPYBARA("Chillbara", PlayStyle.PASSIVE, 0xFFFFAB91),
}
