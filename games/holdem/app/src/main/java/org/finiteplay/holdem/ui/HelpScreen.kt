package org.finiteplay.holdem.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.core.ui.R as CoreR
import org.finiteplay.core.ui.card.drawCardFace
import org.finiteplay.core.ui.card.rankSpokenLabel
import org.finiteplay.core.ui.card.suitSpokenLabel
import org.finiteplay.core.ui.layout.FullScreenPanel
import org.finiteplay.core.ui.layout.StatTabs
import org.finiteplay.core.ui.theme.LocalAppColors
import org.finiteplay.holdem.R
import org.finiteplay.holdem.rules.HandCategory

/** Which help page is showing. */
private enum class HelpPage { RULES, HANDS, STRATEGY }

/** One hand ranking: its category, strings, and a drawn example (`UI_SPEC.md` "Help"). */
internal data class HandExample(val category: HandCategory, val title: Int, val body: Int, val cards: List<Card>)

private fun c(rank: Rank, suit: Suit) = Card(suit, rank)

/** The nine rankings, strongest first, each with five cards that make exactly that hand and no better. */
internal val HAND_EXAMPLES: List<HandExample> = listOf(
    HandExample(
        HandCategory.STRAIGHT_FLUSH, R.string.help_hand_straight_flush_title, R.string.help_hand_straight_flush,
        listOf(c(Rank.NINE, Suit.HEARTS), c(Rank.EIGHT, Suit.HEARTS), c(Rank.SEVEN, Suit.HEARTS), c(Rank.SIX, Suit.HEARTS), c(Rank.FIVE, Suit.HEARTS)),
    ),
    HandExample(
        HandCategory.FOUR_OF_A_KIND, R.string.help_hand_four_title, R.string.help_hand_four,
        listOf(c(Rank.KING, Suit.SPADES), c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.DIAMONDS), c(Rank.KING, Suit.CLUBS), c(Rank.THREE, Suit.HEARTS)),
    ),
    HandExample(
        HandCategory.FULL_HOUSE, R.string.help_hand_full_house_title, R.string.help_hand_full_house,
        listOf(c(Rank.QUEEN, Suit.SPADES), c(Rank.QUEEN, Suit.HEARTS), c(Rank.QUEEN, Suit.CLUBS), c(Rank.SEVEN, Suit.DIAMONDS), c(Rank.SEVEN, Suit.SPADES)),
    ),
    HandExample(
        HandCategory.FLUSH, R.string.help_hand_flush_title, R.string.help_hand_flush,
        listOf(c(Rank.KING, Suit.CLUBS), c(Rank.TEN, Suit.CLUBS), c(Rank.SEVEN, Suit.CLUBS), c(Rank.FOUR, Suit.CLUBS), c(Rank.TWO, Suit.CLUBS)),
    ),
    HandExample(
        HandCategory.STRAIGHT, R.string.help_hand_straight_title, R.string.help_hand_straight,
        listOf(c(Rank.NINE, Suit.SPADES), c(Rank.EIGHT, Suit.HEARTS), c(Rank.SEVEN, Suit.CLUBS), c(Rank.SIX, Suit.DIAMONDS), c(Rank.FIVE, Suit.HEARTS)),
    ),
    HandExample(
        HandCategory.THREE_OF_A_KIND, R.string.help_hand_three_title, R.string.help_hand_three,
        listOf(c(Rank.EIGHT, Suit.SPADES), c(Rank.EIGHT, Suit.HEARTS), c(Rank.EIGHT, Suit.CLUBS), c(Rank.KING, Suit.DIAMONDS), c(Rank.FOUR, Suit.SPADES)),
    ),
    HandExample(
        HandCategory.TWO_PAIR, R.string.help_hand_two_pair_title, R.string.help_hand_two_pair,
        listOf(c(Rank.ACE, Suit.SPADES), c(Rank.ACE, Suit.HEARTS), c(Rank.SEVEN, Suit.CLUBS), c(Rank.SEVEN, Suit.DIAMONDS), c(Rank.THREE, Suit.SPADES)),
    ),
    HandExample(
        HandCategory.PAIR, R.string.help_hand_pair_title, R.string.help_hand_pair,
        listOf(c(Rank.JACK, Suit.SPADES), c(Rank.JACK, Suit.HEARTS), c(Rank.ACE, Suit.CLUBS), c(Rank.EIGHT, Suit.DIAMONDS), c(Rank.FOUR, Suit.SPADES)),
    ),
    HandExample(
        HandCategory.HIGH_CARD, R.string.help_hand_high_card_title, R.string.help_hand_high_card,
        listOf(c(Rank.ACE, Suit.SPADES), c(Rank.JACK, Suit.HEARTS), c(Rank.EIGHT, Suit.CLUBS), c(Rank.FIVE, Suit.DIAMONDS), c(Rank.THREE, Suit.SPADES)),
    ),
)

/**
 * How Hold'em is played (`UI_SPEC.md` "Help"): the rules in short sections, the nine hand rankings
 * each with five drawn cards, and a simple strategy page. The contract is `RULES.md`; this is its
 * plain-language summary.
 */
@Composable
fun HelpScreen(onClose: () -> Unit) {
    var page by remember { mutableStateOf(HelpPage.RULES) }
    FullScreenPanel(
        title = stringResource(R.string.help_title),
        onClose = onClose,
        testTag = "help_screen",
        scrollable = false,
    ) {
        StatTabs(
            options = HelpPage.entries,
            selected = page,
            label = {
                stringResource(
                    when (it) {
                        HelpPage.RULES -> R.string.help_tab_rules
                        HelpPage.HANDS -> R.string.help_tab_hands
                        HelpPage.STRATEGY -> R.string.help_tab_strategy
                    },
                )
            },
            onSelect = { page = it },
            tagPrefix = "help_tab",
        )
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 8.dp).testTag("help_page_${page.name.lowercase()}"),
        ) {
            when (page) {
                HelpPage.RULES -> {
                    HelpSection(R.string.help_goal_title, R.string.help_goal)
                    HelpSection(R.string.help_hand_title, R.string.help_hand_cards, R.string.help_hand_flop)
                    HelpSection(
                        R.string.help_actions_title,
                        R.string.help_actions_check, R.string.help_actions_call, R.string.help_actions_bet,
                        R.string.help_actions_fold, R.string.help_actions_all_in,
                    )
                    HelpSection(R.string.help_button_title, R.string.help_button)
                    HelpSection(R.string.help_allin_title, R.string.help_allin)
                    HelpSection(R.string.help_showdown_title, R.string.help_showdown)
                    HelpSection(R.string.help_tournament_title, R.string.help_tournament)
                    HelpSection(R.string.help_leave_title, R.string.help_leave)
                }
                HelpPage.HANDS -> {
                    Text(
                        text = stringResource(R.string.help_hands_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    for (example in HAND_EXAMPLES) HandExampleRow(example)
                    HelpSection(R.string.help_ties_title, R.string.help_ties)
                }
                HelpPage.STRATEGY -> {
                    Text(
                        text = stringResource(R.string.help_strategy_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    for (tip in listOf(
                        R.string.help_strategy_1, R.string.help_strategy_2, R.string.help_strategy_3, R.string.help_strategy_4,
                        R.string.help_strategy_5, R.string.help_strategy_6, R.string.help_strategy_7,
                    )) {
                        Text(
                            text = stringResource(tip),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
            Text(
                text = stringResource(CoreR.string.company_byline),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun HelpSection(title: Int, vararg bodies: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
    for ((i, body) in bodies.withIndex()) {
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = if (i == 0) 0.dp else 8.dp),
        )
    }
}

/** A ranking's title, its five cards drawn with the shared card face, and its one line. */
@Composable
private fun HandExampleRow(example: HandExample) {
    val title = stringResource(example.title)
    val cards = example.cards.map {
        stringResource(R.string.cd_example_card, rankSpokenLabel(it.rank), suitSpokenLabel(it.suit))
    }.joinToString(", ")
    val description = stringResource(R.string.cd_example_hand, title, cards)
    val colors = LocalAppColors.current.card
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
    Row(modifier = Modifier.fillMaxWidth().testTag("help_hand_${example.category.name.lowercase()}")) {
        Canvas(
            modifier = Modifier
                .width(CARD_WIDTH * 0.8f * 4 + CARD_WIDTH)
                .height(CARD_HEIGHT)
                .semantics { contentDescription = description },
        ) {
            val size = Size(CARD_WIDTH.toPx(), CARD_HEIGHT.toPx())
            val step = CARD_WIDTH.toPx() * 0.8f
            example.cards.forEachIndexed { i, card -> drawCardFace(Offset(i * step, 0f), size, card, colors) }
        }
    }
    Text(
        text = stringResource(example.body),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

private val CARD_WIDTH = 48.dp
private val CARD_HEIGHT = 67.dp
