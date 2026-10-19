package org.finiteplay.core.ui.animal

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.finiteplay.cards.Animal
import org.finiteplay.core.ui.R

/**
 * The string resource holding this animal's name in the player's language. Each language has its own playful
 * pun on the animal or its personality; [Animal.displayName] is only the English original, kept
 * for logs and tests.
 */
@StringRes
fun Animal.nameRes(): Int = when (this) {
    Animal.OWL -> R.string.animal_owl
    Animal.TORTOISE -> R.string.animal_tortoise
    Animal.PANDA -> R.string.animal_panda
    Animal.GECKO -> R.string.animal_gecko
    Animal.FOX -> R.string.animal_fox
    Animal.HONEY_BADGER -> R.string.animal_honey_badger
    Animal.CAPYBARA -> R.string.animal_capybara
}

/** The animal's name in the language the app is showing. */
@Composable
fun Animal.localizedName(): String = stringResource(nameRes())
