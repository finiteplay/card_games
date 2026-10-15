# Google Play listing review, group 1 (es-ES, fr-FR, de-DE, it-IT, pt-BR, nl-NL)

Scope: `blackjack/<code>.md` and `holdem/<code>.md` against `source/*.en-US.md`. The terms were checked against the reviewed in-app strings (`values-es`, `-fr`, `-de`, `-it`, `-pt-rBR`, `-nl`).

Limits were checked after the edits with `listing_limits.py`. Every file passes: TITLE 23 (Blackjack) / 27 (Hold'em), SHORT 67-79, FULL 1130-1674.

All 12 FULL texts keep every safety and policy claim from the source:
- offline play
- virtual chips with no monetary value
- a computer dealer (Blackjack) or computer opponents only (Hold'em)
- no ads and no in-app purchases
- no real-money betting, deposits, cash-outs or prizes
- no online play and no matches against real people
- no account (Blackjack); no account or tracking, with progress and statistics kept on the device (Hold'em)

All 6 Hold'em feature lists keep 10 `•` bullets that match the source items. The titles are unchanged ("Blackjack by FinitePlay" and "Texas Hold’em by FinitePlay").

## es-ES

**Blackjack** (rating 4/5)
- "la mirada del crupier" became "la comprobación de la carta oculta del crupier". The old phrase means "the dealer's gaze", which is not blackjack language.
- "Sigue tu banca virtual" became "Lleva el control de tu banca virtual". "Sigue" read as "follow".
- SHORT compromise: no change. "multijugador" or "online" cannot fit, because the best candidate was 88 characters. The line keeps offline, virtual chips, no ads, no purchases and no real money.

**Hold'em** (rating 4/5)
- "subir y all in" became "subir e ir all in". The list is made of verbs, so the last item now reads as a verb too.
- SHORT compromise: "online players" was dropped. "ni rivales online" gives 93 characters. "ni online" would fit at 79 but is too vague, so I left it out. All the strongest claims are kept.

## fr-FR

**Blackjack** (rating 4/5)
- "le coup d'œil du croupier" became "la vérification de la carte cachée par le croupier". The old phrase is not standard blackjack language.
- "quel risque présente une carte pour vous" became "à quel point tirer est risqué pour vous". This is closer to "how risky a hit is" and uses the in-app verb "Tirer".
- "jeux de cartes ciblés" became "jeux de cartes épurés". "Ciblés" means "targeted".
- SHORT compromise: "multijoueur" was dropped. The best candidate was 82 characters. The line keeps the five strongest claims.

**Hold'em** (rating 4/5)
- Meaning error fixed: "visez la dernière place à la table" said "aim for last place". It now reads "visez à être le dernier joueur à la table".
- The bullet "Se coucher (Fold), check, suivre (Call), miser, relancer et tapis (All in)" became "Se coucher, checker, suivre, miser, relancer et faire tapis". It now uses the in-app terms consistently and reads naturally.
- SHORT compromise: "joueurs en ligne" was dropped. The best candidate was 82 characters. The line keeps the five strongest claims.

## de-DE

**Blackjack** (rating 4/5)
- SHORT: the multiplayer claim is restored. The line is now "Offline-Blackjack, virtuelle Chips. Ohne Werbung, Käufe, Echtgeld, Mehrspieler." (79 characters).
- "stehen" became "halten", to match the in-app action "Halten".
- "den Blick des Gebers" became "die Prüfung der verdeckten Karte durch den Geber". The old phrase means "the dealer's look", which is not blackjack language.
- "Hinweis" became "Tipp" in 3 places, to match the in-app feature name ("Tipp-Taste", "Rückblick auf den Tipp").

**Hold'em** (rating 4/5)
- SHORT: the online claim is restored. The line is now "Offline-Poker, virtuelle Chips. Ohne Werbung, Käufe, Echtgeld, Online-Spieler." (78 characters).
- "Coaching-Hinweis" and "mit Hinweis" / "der Hinweis" became "Tipp", to match the app.
- "Spiele durch steigende Blinds" became "Spiele mit steigenden Blinds", which is more natural.

## it-IT

**Blackjack** (rating 4/5)
- "controllo del banco" became "controllo della carta coperta da parte del banco". The old phrase was too vague to read as dealer peek.
- "giochi di carte mirati" became "giochi di carte essenziali". "Mirati" means "targeted".
- SHORT compromise: no change. The line is already 79 characters, and "multiplayer" needs 87 or more. The five strongest claims are kept.

**Hold'em** (rating 4/5)
- "punta a essere l'ultimo giocatore" became "cerca di essere l'ultimo giocatore rimasto". This removes the clash with "decisioni di puntata" right before it and keeps the meaning clear.
- SHORT compromise: "giocatori online" was dropped. The best candidate was 84 characters. The five strongest claims are kept.

## pt-BR

**Blackjack** (rating 4/5)
- "a espiada do dealer" became "a conferência de blackjack pelo dealer". "Espiada" is not standard blackjack language.
- SHORT compromise: "multiplayer" was dropped. The best candidate was 88 characters. The five strongest claims are kept.

**Hold'em** (rating 5/5)
- No changes needed. The terms match the app (Fold, check, pagar, apostar, aumentar, all in, equity, pot odds, dica).
- SHORT compromise: "jogadores/rivais online" was dropped. The best candidate was 89 characters. The five strongest claims are kept.

## nl-NL

**Blackjack** (rating 4/5)
- "klassieke blackjack" became "klassiek blackjack", because the noun is neuter ("het blackjack").
- "bevat een kaart nemen, …, de blik van de deler" became "bevat kaart nemen, …, controle van de gedekte kaart door de deler". This uses the in-app term "gedekte kaart", and "de blik van de deler" is not blackjack language.
- "gerichte kaartspellen" became "gefocuste kaartspellen". "Gerichte" means "targeted".
- SHORT compromise: "multiplayer" was dropped. The best candidate was 83 characters, even with "reclame". The five strongest claims are kept.

**Hold'em** (rating 4/5)
- "Speel door oplopende blinds" became "Speel met oplopende blinds", which is more natural.
- SHORT compromise: "online spelers" was dropped. The best candidate was 82 characters. The five strongest claims are kept.

## Needs a native speaker

- de-DE: the SHORT lines are comma lists with no "oder" ("Ohne Werbung, Käufe, Echtgeld, Mehrspieler"). Confirm the telegraphic style reads well.
- de-DE: "die Prüfung der verdeckten Karte durch den Geber". Check this is the most natural wording for dealer peek, or whether "Peek" or "Blackjack-Check" is more common.
- es-ES: "la comprobación de la carta oculta del crupier" (dealer peek).
- fr-FR: "la vérification de la carte cachée par le croupier", "checker" in the bullet list, and "épurés" for "focused".
- fr-FR (both games): "gains" is used for "prizes" in the policy lines. It reads as "winnings", which keeps the policy sense, but a native should confirm.
- it-IT: "controllo della carta coperta da parte del banco" and "essenziali" for "focused".
- pt-BR: "conferência de blackjack pelo dealer" (dealer peek).
- nl-NL: "klassiek blackjack" (gender), "gefocuste" (an anglicism) and "controle van de gedekte kaart door de deler".
- All languages: decide whether a vaguer "ni online" / "o online" in SHORT would be acceptable to restore the online claim where it fits (es-ES Hold'em, nl-NL Blackjack). For now I left it out in favour of clear wording.

## Summary

All 12 listings in this group are within the limits, keep every safety claim in FULL, and keep the 10-bullet Hold'em list and the Hint copy.
I made 25 small fixes: 1 meaning error (fr Hold'em "dernière place"), dealer-peek wording in 5 languages, in-app term alignment (de "Tipp" / "halten"), and a few unnatural phrases.
The multiplayer/online claim is restored in both de-DE SHORT lines. For the other 10 SHORT lines it could not fit within 80 characters, so they keep offline, virtual chips, no ads, no purchases and no real money.
