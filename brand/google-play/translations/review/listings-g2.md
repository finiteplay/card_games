# Store listing review, group 2: uk, pl-PL, sv-SE, no-NO, da-DK, fi-FI

Scope: `blackjack/<code>.md` and `holdem/<code>.md`, compared with `source/*.en-US.md` and checked against the in-app strings that were just reviewed (`values-uk/pl/sv/nb/da/fi`, plus `core/ui` for the Hint label).

Limits were checked with `listing_limits.py` after the edits. Every file passes:

| Lang | BJ title / short / full | HE title / short / full |
|---|---|---|
| uk | 23 / 80 / 1096 | 27 / 77 / 1435 |
| pl-PL | 23 / 79 / 1150 | 27 / 78 / 1466 |
| sv-SE | 23 / 79 / 1061 | 27 / 80 / 1411 |
| no-NO | 23 / 74 / 1077 | 27 / 80 / 1410 |
| da-DK | 23 / 79 / 1075 | 27 / 79 / 1468 |
| fi-FI | 23 / 75 / 1205 | 27 / 79 / 1496 |

These checks held in every file, so nothing needed fixing:
- Titles are exactly `Blackjack by FinitePlay` and `Texas Hold’em by FinitePlay`.
- The FULL text keeps every policy claim: offline; virtual chips with no monetary value; computer dealer or computer opponents only; no ads; no in-app purchases; no real-money betting, deposits, cash-outs or prizes; no online play or matches against real people; no accounts. Hold'em also keeps "no tracking" and "progress and statistics stay on the device".
- Every Hold'em feature list has the same 10 `•` items as the source.

---

## uk (Ukrainian), rating 4/5

**Blackjack**
- SHORT: `…без грошей і мультиплеєра` became `…Без реклами, покупок, гри на гроші й онлайну.` (80). "Без грошей" did not carry the meaning "real money". "Гри на гроші" (playing for money) does, and the online claim stays.
- Dealer peek: `перевірку закритої карти дилера` became `перевірку дилером прихованої карти`. This uses the in-app term "прихована карта" and makes the dealer the one who checks.
- Hint: `й просто пояснює, чому` became `і простими словами пояснює чому`, because "просто" can read as "merely". `як часто дилер програє перебором з відкритою картою` became `як часто дилер отримує перебір за такої відкритої карти`. This matches the in-app "Перебір дилера" and the meaning "with the card showing".

**Hold'em**
- SHORT: `…грошей і онлайн-гравців` became `…гри на гроші й онлайну.` (77). This restores "real money" and keeps it consistent with Blackjack.
- `Проходьте через зростання блайндів` became `Грайте, поки зростають блайнди`, which is more natural.
- The Hint bullet now says `простими словами пояснює` (same reason as in Blackjack).

## pl-PL (Polish), rating 4/5

**Blackjack**
- SHORT: `…Bez reklam, zakupów, prawdziwych pieniędzy.` became `…Bez reklam, zakupów, hazardu i gry online.` (79). The online claim had been dropped, so it is back. "Prawdziwych pieniędzy i gry online" does not fit (88), so "real money" is now "hazardu" (gambling, which in Polish means games for money).
- Dealer peek: `sprawdzanie karty krupiera` became `sprawdzanie zakrytej karty przez krupiera`, to match the in-app "zakryta karta".
- Hint: `razem ze statystykami: jak często krupier przegrywa…` became `podając stojące za tym szanse: jak często krupier przekracza 21 przy danej odkrytej karcie…`. "Przegrywa" (loses) was wrong for "busts". "Przekracza 21" is the in-app term.

**Hold'em**
- SHORT: became `…Bez reklam, zakupów, hazardu i graczy online.` (78). This restores "online players".
- `podejmuj decyzje licytacyjne` became `podejmuj decyzje o zakładach`, because "licytacyjne" suggests auction or bridge bidding.
- `pomoc ze strategii` became `porady strategiczne`, which is more idiomatic.

## sv-SE (Swedish), rating 4/5

**Blackjack**
- SHORT: `Inga annonser, köp, riktiga pengar, nätspel` had no final period and odd agreement after "inga". It is now `Utan reklam, köp, riktiga pengar, nätspel.` (79), with every claim kept.
- Hint name: the app's Hint button is **"Tips"** in Swedish, but the listing said "Hint" or "hintad". It now reads `den valfria Tips-funktionen` and `Efter en runda med Tips … vad Tips sa`.
- Dealer peek: `givarens kontroll av kortet` became `kontroll av givarens dolda kort`, to match the in-app "dolda kort".

**Hold'em**
- SHORT: became `…Utan reklam, köp, riktiga pengar eller nätspel.` (80).
- Hint bullets now say `En coachande Tips-funktion som…` and `efter varje hand med Tips: vad Tips sa`, to match the in-app term.

## no-NO (Norwegian Bokmål), rating 4/5

**Blackjack**
- SHORT: left unchanged. This is a compromise (see below).
- Dealer peek: `dealerens kikk på kortet` became `dealerens sjekk av det skjulte kortet`, to match the in-app "skjulte kort".
- Hint: `den valgfrie Hint. Den foreslår trekket` became `den valgfrie Hint-funksjonen. Den foreslår det grunnstrategien anbefaler`. "Trekket" collides with the Hit button "Trekk", and "Hint" is neuter, so "den … Hint" did not agree.
- Grammar: `en kort tilbakeblikk` became `et kort tilbakeblikk`, because the noun is neuter.

**Hold'em**
- Hint bullet: `En coachende Hint` became `En coachende Hint-funksjon`, for gender agreement and consistency with Blackjack.
- SHORT was already complete at 80 characters, so it is unchanged.

## da-DK (Danish), rating 4/5

**Blackjack**
- SHORT: `…Uden reklamer, køb og rigtige penge.` became `…Uden reklame, køb, pengespil, onlinespil.` (79). This restores the online claim. "Rigtige penge" plus "onlinespil" does not fit (83), so it is now "pengespil" (gambling for money).
- `fair deling` (sharing or splitting) became `fair kortgivning` (dealing cards).
- Dealer peek: `dealerens kig på kortet` became `dealerens tjek af det skjulte kort`, to match the in-app "skjulte kort".
- Hint: `den valgfrie Hint. Den foreslår trækket` became `den valgfrie Hint-funktion. Den foreslår det, grundstrategien anbefaler,`. "Trækket" collides with the Hit button "Træk", and the gender did not agree.

**Hold'em**
- SHORT: became `…Uden reklame, køb, rigtige penge, onlinespil.` (79). This restores the online claim.
- Hint bullet: `En coachende Hint` became `En coachende Hint-funktion`.

## fi-FI (Finnish), rating 4/5

**Blackjack**
- SHORT: left unchanged. This is a compromise (see below).
- Dealer peek: `jakajan kortin tarkistuksen` became `jakajan piilokortin tarkistuksen`, to match the in-app "piilokortti".
- Hint: `selittää sen … sekä kertoo todennäköisyydet` became `selittää yksinkertaisin sanoin miksi sekä kertoo taustalla olevat todennäköisyydet`. The draft had lost "why" and "behind it".
- `Vihjeellisen kierroksen jälkeen` became `Kun olet käyttänyt vihjettä, kierroksen jälkeen`. "Vihjeellinen" means "suggestive" and does not mean "hinted".

**Hold'em**
- SHORT: `…Ei mainoksia, ostoja eikä oikeaa rahaa.` became `…Ei mainoksia, ostoja, rahapelejä, verkkopeliä.` (79). This restores the online claim. "Oikeaa rahaa" plus "verkkopeliä" does not fit (81), so it is now "rahapelejä" (money games or gambling).
- `vihjeellisen käden jälkeen` became `jokaisen käden jälkeen, jossa käytit vihjettä`.
- `laitteen tilastot` (the device's statistics) became `tilastot laitteella` (statistics on the device).

---

## SHORT compromises

| File | Current SHORT | What is missing | Why |
|---|---|---|---|
| blackjack/no-NO | `Blackjack offline, virtuelle sjetonger. Uten reklame, kjøp og ekte penger.` | online/multiplayer | Every complete version is 82–85 characters. The only version that fits removes the word "Blackjack" (`Offline, virtuelle sjetonger. Ingen reklame, kjøp, ekte penger eller nettspill.`, 79), so I kept the game name and the four strongest claims. If the product owner wants all claims instead, this 79-character line is ready to use. |
| blackjack/fi-FI | `Blackjack offline, virtuaalimerkit. Ei mainoksia, ostoja eikä oikeaa rahaa.` | online/multiplayer | The shortest complete version, `…Ei mainoksia, ostoja, rahapelejä, nettipeliä.`, is 81 characters. It fits only without the final period (80), so I kept it unchanged. |

These short lines keep every claim, but with a substituted word:
- pl-PL (both games): "real money" became "hazardu" (gambling).
- da-DK Blackjack: "real money" became "pengespil".
- fi-FI Hold'em: "real money" became "rahapelejä".
- uk (both games): "real money" became "гри на гроші" (playing for money).
- Hold'em sv-SE, no-NO, uk, da-DK, fi-FI: "online players" became "online play" (nätspel, nettspill, онлайну, onlinespil, verkkopeliä). pl-PL keeps "graczy online".

## Needs a native speaker
- **sv-SE**: "Tips-funktionen" and "en runda med Tips" as the name of the Hint feature. Please also confirm the terse list `Utan reklam, köp, riktiga pengar, nätspel.`
- **no-NO**: "dealerens sjekk av det skjulte kortet" for dealer peek; the "Hint-funksjonen" wording; `pengespill` as an alternative SHORT term.
- **da-DK**: whether "pengespil" in the SHORT is acceptable store wording for "no real money"; "dealerens tjek af det skjulte kort"; "fair kortgivning".
- **fi-FI**: "rahapelejä" in the Hold'em SHORT; the reworded Hint sentence "Kun olet käyttänyt vihjettä, kierroksen jälkeen…"; "etkä vain tuloksia" (unchanged, slightly colloquial).
- **pl-PL**: "hazardu" in a blackjack or poker SHORT. Readers could take it as "this is not a gambling game", which is the intended message, but a native speaker should confirm it reads well. Also "sprawdzanie zakrytej karty przez krupiera".
- **uk**: "онлайну" as a noun in the SHORT; "перевірку дилером прихованої карти".

## Summary
All 12 listings for uk, pl-PL, sv-SE, no-NO, da-DK and fi-FI pass the limits. Every policy claim survives in the FULL text, and the Hold'em feature lists match the source.
The online/multiplayer claim is back in 10 of 12 SHORT lines. no-NO and fi-FI Blackjack still omit it because the full wording does not fit in 80 characters (alternatives are listed above).
Main fixes: dealer-peek wording now uses each app's hole-card term, Swedish now uses the in-app name "Tips", Hint copy was corrected (uk, pl, fi "why/odds"; no and da "move" no longer collides with Hit), and some grammar and gender errors were fixed.
