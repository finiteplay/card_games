# Hold’em opponents: localized names for review

These names are in the app as `animal_*` string resources in `core:ui` (English default plus all 30 other languages); before this the seven names were fixed English words in every language. They were written by a model and have **not** been seen by a native speaker; each one needs checking for accidental meanings, regional slang and charm. Changes found in review are made in the `animal_*` strings.

## The roster

| Animal | English name | Play style | Where the English pun comes from |
| --- | --- | --- | --- |
| owl | Hootie | tight | hoot |
| tortoise | Shellby | tight | shell |
| panda | Bamboozle | loose | bamboo + bamboozle |
| gecko | Gizmo | loose | gizmo |
| fox | Foxtrot | aggressive | fox + foxtrot dance |
| honey badger | Badgerino | aggressive | badger + -ino |
| capybara | Chillbara | passive | chill + capybara |

## Rules the names were written to

- One word, at most 10 characters (CJK at most 6, Thai at most 8 base letters, Arabic/Hebrew/Hindi at most 8 visible letters): the seat label is small and shrinks to 8 sp.
- A local pun on the animal and/or the personality, in the spirit of the English names; not a literal translation.
- Family friendly: no brands, real people, film/game/anime characters, slurs, religious or political words; checked for rude or sexual meanings.
- All seven names differ within a language; none contains the play-style word (the style label is shown beside the name).
- The drawn avatars (`AnimalAvatar`) and disc colours stay identical in every language so each animal is recognisable across games. Only real cultural concerns are noted, per language, in the last column of the detail section.

## Overview

| Language | Owl (tight) | Tortoise (tight) | Panda (loose) | Gecko (loose) | Fox (aggressive) | Honey badger (aggressive) | Capybara (passive) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| English (current) | Hootie | Shellby | Bamboozle | Gizmo | Foxtrot | Badgerino | Chillbara |
| Spanish (`es`) | Buhísimo | Tortulenta | Bambulín | Pegatodo | Sonrizorro | Tejonazo | Siestibara |
| French (`fr`) | Hibouquin | Cachecoque | Bambouille | Touchatout | Renarnaque | Bagarmiel | Capépère |
| German (`de`) | Schlaueule | Schildbert | Bambussi | Geckoflitz | Grinsfuchs | Draufdachs | Chillbara |
| Italian (`it`) | Gufosofo | Tartapiano | Bambulone | Giocageco | Furbolpe | Tostotasso | Placibara |
| Portuguese (Brazil) (`pt-BR`) | Corujildo | Jabutimido | Bambuleio | Lagartreco | Sorraposa | Texugão | Calmivara |
| Dutch (`nl`) | Slimuil | Schuilpad | Bamboefje | Gekkerd | Grijnsvos | Durfdas | Rustbara |
| Ukrainian (`uk`) | Пугусь | Панцирко | Бамбуша | Липучка | Хитрулис | Медозавр | Спокібара |
| Polish (`pl`) | Huhulek | Skorupek | Łasuch | Wiercik | Chytrusek | Zadziorek | Luzibara |
| Swedish (`sv`) | Uhuggla | Gömpadda | Bambulle | Klängis | Rävpolka | Kaxling | Lugnbara |
| Norwegian Bokmål (`nb`) | Ugleklok | Gjemmis | Bambusen | Klissen | Revlender | Røffling | Rolibara |
| Danish (`da`) | Huhugle | Skjulius | Pandekage | Klistrup | Rævestreg | Kækling | Hyggebara |
| Finnish (`fi`) | Huhuli | Piilonen | Pampula | Säätäjä | Kettutango | Mesimörkö | Rentobara |
| Turkish (`tr`) | Huhubey | Kabukçu | Bambucuk | Kıpırgeko | Tilkitrot | Porsukino | Keyfibara |
| Greek (`el`) | Γλαυκούλα | Καβουκάς | Παντούλης | Τρεχαμίδι | Αλεποτρότ | Ασβουλίνο | Χαλαμπάρα |
| Czech (`cs`) | Houkálek | Krunýřek | Bambusák | Vrtílek | Liškotrot | Medojedino | Pohodbara |
| Romanian (`ro`) | Bufnache | Carapăcel | Bambusel | Zvârlugă | Vulpotrot | Bursucino | Lejerbara |
| Hungarian (`hu`) | Huhogó | Páncélka | Bambuszka | Fürgekkó | Ravaszdi | Borzino | Nyugibara |
| Bulgarian (`bg`) | Бухчо | Черупко | Бамбучо | Пъргавко | Лисотрот | Язовчино | Кефибара |
| Croatian (`hr`) | Huhić | Oklopko | Bambusko | Gekoslav | Lukavko | Medoboj | Ležibara |
| Slovak (`sk`) | Húhulík | Pancierik | Bambuľko | Gekoslav | Ryško | Medoboj | Pohodbara |
| Arabic (`ar`) | فهمان | سلحوف | بامبولي | شطّور | ثعلوب | مقدام | رايقبارا |
| Hebrew (`he`) | ינשופון | שריוני | במבוקי | שממיתוש | שועליק | גיבורון | סבבארה |
| Hindi (`hi`) | समझू | कवचू | गोलू | जुगाड़ू | दाँवू | शहदवीर | मस्तबारा |
| Indonesian (`id`) | Bijakus | Kurahemat | Bambingung | Cicakrobat | Rubahaha | Madunekat | Santaibara |
| Vietnamese (`vi`) | Cú Khôn | Rùa Rụt Rè | Mê Trúc | Kè Lanh | Cáo Lém | Lửng Gan | Bara Tà Tà |
| Thai (`th`) | ฮูกขรึม | เต่าเหนียว | หมีลุ้น | ตุ๊กซน | จิ้งจอกแสบ | แบดใจถึง | คาปิชิล |
| Japanese (`ja`) | フク先生 | コウラ丸 | ササパン | ペタ吉 | コンニヤリ | イケテル | のんびら |
| Korean (`ko`) | 부엉박사 | 꼭꼭북이 | 다콜판다 | 착붙이 | 능청여우 | 꿀배짱 | 느긋바라 |
| Chinese (Simplified) (`zh-CN`) | 鹰博士 | 壳壳 | 跟跟胖达 | 溜溜虎 | 狐说 | 冲冲獾 | 淡定豚 |
| Chinese (Traditional) (`zh-TW`) | 鷹老師 | 龜毛毛 | 跟跟熊 | 黏黏虎 | 嘻嘻狐 | 敢衝獾 | 悠哉豚 |

## Detail by language

Each row: the name, the local common word for the animal, and a one-line English explanation (romanisation included for non-Latin scripts).

### Spanish (`es`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Buhísimo** | búho | búho (owl) + superlative -ísimo: 'the most owl-ish', a wise, careful know-it-all. |
| tortoise | **Tortulenta** | tortuga | tortuga + lenta (slow): the slow, cautious one who waits for a monster hand. |
| panda | **Bambulín** | panda | bambú (bamboo) + cute -ín, echoing 'bambolear' (to wobble): a wobbly, easy-going caller. |
| gecko | **Pegatodo** | geco / salamanquesa | pega (sticks) + todo (everything): gecko feet stick to anything, and it sticks around in every hand. |
| fox | **Sonrizorro** | zorro | sonrisa (smile) + zorro (fox): the grinning fox who bluffs. Avoids plain 'Zorro' (famous film character) and 'zorra' (an insult). |
| honey badger | **Tejonazo** | tejón melero | tejón (badger) + -azo, a suffix that also means a big hit: a badger that charges in hard. |
| capybara | **Siestibara** | capibara (carpincho / chigüire) | siesta + capibara: a nap-loving capybara who just goes along. |

Avatar note: Mostly the same. Honey badger (tejón melero) is not well known, so the drawing has to carry it. Capybara has regional names (carpincho in Argentina/Uruguay, chigüire in Venezuela) but is widely recognised. In parts of Latin America the 'lechuza' (barn owl) is linked to witch folklore, so in text use 'búho' (as here), which has no such link.

### French (`fr`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Hibouquin** | hibou / chouette | hibou (owl) + bouquin (informal for book): a bookish, studious owl who plays by the book. |
| tortoise | **Cachecoque** | tortue | cache (hides) + coque (shell), echoing 'cache-cache' (hide-and-seek): ducks into its shell and folds. |
| panda | **Bambouille** | panda | bambou (bamboo) + bouille (cute chubby face, as in 'bonne bouille'): a round, good-natured panda. |
| gecko | **Touchatout** | gecko | From 'touche-à-tout' (a dabbler who tries everything): sticky gecko fingers in every hand. |
| fox | **Renarnaque** | renard | renard (fox) + arnaque (con, swindle; playful): the crafty fox who bluffs you. |
| honey badger | **Bagarmiel** | ratel | bagarre (scuffle) + miel (honey): a honey-loving little brawler. |
| capybara | **Capépère** | capybara (cabiai) | capybara + pépère (laid-back, cosy, taking it easy): the comfortable, unhurried one. |

Avatar note: Honey badger: avoid 'blaireau' in French text (slang for a dim, tacky guy); 'ratel' is correct but not widely known, so the drawing has to carry the animal. Everything else the same.

### German (`de`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Schlaueule** | Eule | schlau (clever) + Eule (owl): the clever owl. Avoids 'Uhu', which is also a well-known glue brand. |
| tortoise | **Schildbert** | Schildkröte | Schild (shield, from Schildkröte) + Bert (a friendly name), like Shellby: a shielded, defensive player. |
| panda | **Bambussi** | Panda | Bambus (bamboo) + cuddly '-i' ending ('Bussi' is also a southern German word for a peck on the cheek, harmless and sweet). |
| gecko | **Geckoflitz** | Gecko | Gecko + Flitz (from flitzen, to dart around): a quick gecko darting into lots of hands. |
| fox | **Grinsfuchs** | Fuchs | grinsen (to grin) + Fuchs (fox), echoing 'Schlaufuchs' (clever clogs): the grinning bluffer. |
| honey badger | **Draufdachs** | Honigdachs | drauf (as in 'drauf los!', charge at it) + Dachs (badger): the badger that always goes for it. |
| capybara | **Chillbara** (kept from English) | Capybara (Wasserschwein) | Kept from English: 'chillen' is everyday German slang, so the pun works as is. |

Avatar note: same

### Italian (`it`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Gufosofo** | gufo | gufo (owl) + filosofo (philosopher): a thoughtful owl who weighs every hand. |
| tortoise | **Tartapiano** | tartaruga | tarta(ruga) + piano (slowly, as in 'piano piano', step by step): slow and careful. |
| panda | **Bambulone** | panda | bambù (bamboo) + bambolone (a big, chubby, good-natured lump): a soft-hearted panda who calls along. |
| gecko | **Giocageco** | geco | gioca (plays) + geco (gecko), a sing-song rhyme: the gecko that wants to play every hand. |
| fox | **Furbolpe** | volpe | furbo (crafty, sly) + volpe (fox): the cunning fox with a cheeky grin. |
| honey badger | **Tostotasso** | tasso del miele | tosto (tough, gutsy) + tasso (badger), alliterative: the fearless tough guy. |
| capybara | **Placibara** | capibara | placido (placid, calm) + capibara: the calm, easy-going one. |

Avatar note: Owl: in Italian 'gufo'/'gufare' is linked to jinxing and bad luck, and 'civetta' also means a flirt. A cute, wise cartoon owl is fine, but keep 'civetta' out of the UI. Everything else the same.

### Portuguese (Brazil) (`pt-BR`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Corujildo** | coruja | coruja (owl) + an old-fashioned name ending like Hermenegildo: a funny, proper, careful old owl. |
| tortoise | **Jabutimido** | jabuti (tartaruga) | jabuti (the Brazilian land tortoise) + tímido (shy): hides in its shell and folds. |
| panda | **Bambuleio** | panda | bambu (bamboo) + bamboleio (swaying, wobbling): a relaxed panda that rolls along with everything. |
| gecko | **Lagartreco** | lagartixa | lagartixa (the common house gecko) + treco (thingamajig, a gizmo): a busy little gadget-gecko. |
| fox | **Sorraposa** | raposa | sorriso (smile) + raposa (fox): the smiling fox who bluffs. |
| honey badger | **Texugão** | texugo-do-mel | texugo (badger) + augmentative -ão (big and tough): the fearless bruiser. |
| capybara | **Calmivara** | capivara | calma (calm) + capivara: the famously chill Brazilian capybara. |

Avatar note: Honey badger (texugo-do-mel) is not native and many players won't know it; the drawing has to carry it. Capybara (capivara) is native and much loved in Brazil, a great fit. Everything else the same.

### Dutch (`nl`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Slimuil** | uil | slim (clever) + uil (owl): the clever owl. Avoids 'uilskuiken', which means a fool. |
| tortoise | **Schuilpad** | schildpad | schuilen (to take shelter, hide) + pad (from schildpad): hides in its shell and folds. |
| panda | **Bamboefje** | panda | bamboe (bamboo) + boefje (little rascal, affectionate): a cheeky panda who joins every hand. |
| gecko | **Gekkerd** | gekko | gekko + gekkerd (an affectionate 'silly-billy'): a playful, slightly wild gecko. Mild teasing, not an insult. |
| fox | **Grijnsvos** | vos | grijns (grin) + vos (fox): the grinning, sly bluffer. |
| honey badger | **Durfdas** | honingdas | durf (daring, guts) + das (badger), echoing 'durfal' (daredevil): a fearless badger. |
| capybara | **Rustbara** | capibara | rust (calm, as in 'rustig aan' = take it easy) + capibara. 'Chillbara' would also work in Dutch. |

Avatar note: same

### Ukrainian (`uk`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Пугусь** | сова | From 'пугу' (the Ukrainian owl call, as in 'пугу-пугу') + cuddly diminutive -усь; a careful little hooter. |
| tortoise | **Панцирко** | черепаха | From 'панцир' (shell/armour) + friendly -ко; always hiding in its armour, fits a tight player. |
| panda | **Бамбуша** | панда | From 'бамбук' (bamboo) + soft pet ending -уша; a cuddly bamboo-lover who calls hoping for bamboo. |
| gecko | **Липучка** | гекон | 'Липучка' = velcro / sticky thing; geckos stick to walls, this one sticks with every hand (loose). |
| fox | **Хитрулис** | лисиця | 'Хитрий' (sly, cunning) + 'лис' (fox); a sly fox who bluffs with a grin. |
| honey badger | **Медозавр** | медоїд | From 'медоїд' (honey badger) + '-завр' (as in dinosaur); a fearless little honey monster. |
| capybara | **Спокібара** | капібара | 'Спокій' (calm) + 'капібара'; direct equivalent of Chillbara, calm and passive. |

Avatar note: same

### Polish (`pl`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Huhulek** | sowa | From owl call 'hu-hu' + diminutive -lek; a careful little hooter. Deliberately avoids 'Puchatek' (Polish Winnie-the-Pooh). |
| tortoise | **Skorupek** | żółw | From 'skorupa' (shell) + -ek, sounds like a cute nickname; hides in its shell (tight). |
| panda | **Łasuch** | panda | 'Łasuch' = greedy little snacker / sweet tooth; gobbles bamboo and calls everything (loose). Bamboo pun avoided because 'Bambo' evokes a dated racially-coded Polish children's poem. |
| gecko | **Wiercik** | gekon | From 'wiercić się' (to fidget, can't sit still) + -ik; a restless gecko in on every hand (loose). |
| fox | **Chytrusek** | lis | 'Chytry' (sly); 'lisek chytrusek' is the classic Polish nursery phrase for a sly little fox; bluffs with a grin. |
| honey badger | **Zadziorek** | ratel miodożerny (miodożer) | From 'zadziorny' (feisty, scrappy) + -ek; a fearless little scrapper (aggressive). |
| capybara | **Luzibara** | kapibara | 'Luz' (chill, easygoing) + 'kapibara'; direct equivalent of Chillbara. |

Avatar note: same

### Swedish (`sv`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Uhuggla** | uggla | 'Uhu' (owl hoot) + 'uggla' (owl); a hooting owl that plays carefully. |
| tortoise | **Gömpadda** | sköldpadda | 'Göm' (hide) + '-padda' from sköldpadda; the hide-away tortoise (tight). |
| panda | **Bambulle** | panda | 'Bambu' (bamboo) + 'bulle' (bun, cosy pet word); a soft bamboo bun. Kept clear of 'Bamse' (famous Swedish comic bear). |
| gecko | **Klängis** | geckoödla | From 'klänga' (to cling/climb) + slangy -is (as in kompis); clings to every hand (loose). |
| fox | **Rävpolka** | räv | 'Räv' (fox) + 'polka'; local dance pun mirroring Foxtrot, a lively bluffing fox. |
| honey badger | **Kaxling** | honungsgrävling | 'Kaxig' (cocky, bold) + 'grävling' (badger); a cocky, fearless badger (aggressive). |
| capybara | **Lugnbara** | kapybara | 'Lugn' (calm) + 'kapybara'; direct equivalent of Chillbara. |

Avatar note: same

### Norwegian Bokmål (`nb`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Ugleklok** | ugle | 'Ugle' (owl) + 'klok' (wise); a wise owl that picks hands carefully (tight). |
| tortoise | **Gjemmis** | skilpadde | From 'gjemme seg' (to hide) + -is; always hiding in its shell (tight). |
| panda | **Bambusen** | panda | 'Bambus' (bamboo) used as a nickname in definite form ('the Bamboo one'), a common Norwegian nickname style. Avoids 'Bamse' (brand/character associations). |
| gecko | **Klissen** | gekko | From 'klissete' (sticky); sticks to every hand like a gecko on a wall (loose). |
| fox | **Revlender** | rev | 'Rev' (fox) + 'reinlender' (traditional Norwegian dance); local dance pun mirroring Foxtrot. |
| honey badger | **Røffling** | honninggrevling | 'Røff' (rough, tough) + 'grevling' (badger); a tough, fearless badger (aggressive). |
| capybara | **Rolibara** | kapybara | 'Rolig' (calm) + 'kapybara'; direct equivalent of Chillbara. |

Avatar note: same

### Danish (`da`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Huhugle** | ugle | 'Hu-hu' (owl hoot) + 'ugle' (owl); a hooting, careful owl. |
| tortoise | **Skjulius** | skildpadde | 'Skjul' (hide/hideout) + playful Latin-style -ius; the tortoise that hides in its shell (tight). |
| panda | **Pandekage** | panda | 'Pandekage' = pancake, sounds like panda; soft and flat, and 'flat calling' everything fits loose play. |
| gecko | **Klistrup** | gekko | 'Klister' (glue/sticky) + -rup, the ending of many Danish town names; 'Sticky-town', sticks to every hand (loose). |
| fox | **Rævestreg** | ræv | Real Danish idiom 'rævestreg' = a sly fox trick; perfect for a grinning bluffer. |
| honey badger | **Kækling** | honninggrævling | 'Kæk' (bold, plucky) + 'grævling' (badger); a bold little badger (aggressive). Avoided 'fræk' (can read as naughty/risqué). |
| capybara | **Hyggebara** | kapivar (also 'capybara') | 'Hygge' (cosy Danish comfort) + capybara; the most relaxed seat at the table (passive). |

Avatar note: same

### Finnish (`fi`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Huhuli** | pöllö | From 'huhuu' (Finnish owl hoot) + cute -li; avoids 'pöllö' itself, which is also slang for a dimwit. |
| tortoise | **Piilonen** | kilpikonna | From 'piilo' (hiding place) + -nen (common name ending); the tortoise always in hiding (tight). |
| panda | **Pampula** | panda | 'Pampula' = pom-pom (on a knit hat), echoes 'bambu'/'panda'; a fluffy, carefree ball (loose). |
| gecko | **Säätäjä** | gekko | 'Säätäjä' = a tinkerer, someone fiddling with everything; the Finnish 'Gizmo', in on lots of hands. |
| fox | **Kettutango** | kettu | 'Kettu' (fox) + 'tango' (Finland's beloved dance); local dance pun mirroring Foxtrot. |
| honey badger | **Mesimörkö** | mesimäyrä | 'Mesi' (honey, from mesimäyrä = honey badger) + 'mörkö' (friendly bogeyman); a fearless honey monster. |
| capybara | **Rentobara** | kapybara | 'Rento' (relaxed) + 'kapybara'; direct equivalent of Chillbara. |

Avatar note: same

### Turkish (`tr`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Huhubey** | baykuş | 'Huhu' is the Turkish owl call, plus 'bey' (Mr.), so 'Mr. Hoot': a polite, careful gentleman who picks his hands. |
| tortoise | **Kabukçu** | kaplumbağa | From 'kabuk' (shell) + '-çu' (the one who...): 'the shell guy', always hiding in his shell, folding. |
| panda | **Bambucuk** | panda | 'Bambu' (bamboo) + cute diminutive '-cuk': 'little bamboo', a sweet panda who calls hoping for bamboo. |
| gecko | **Kıpırgeko** | geko (kertenkele = lizard) | 'Kıpır kıpır' means fidgety/restless + 'geko': a gecko that can't sit still and plays lots of hands. |
| fox | **Tilkitrot** | tilki | 'Tilki' (fox) + 'fokstrot' (the foxtrot dance, known in Turkish): same fox+dance pun as the English. |
| honey badger | **Porsukino** | bal porsuğu | 'Porsuk' (badger) + Italian-ish '-ino', mirroring Badgerino; a bold, swaggering little badger. |
| capybara | **Keyfibara** | kapibara | 'Keyif/keyfi' (enjoyment, taking it easy, a very Turkish idea of chilling) + 'kapibara'. |

Avatar note: Mostly same. Folk belief links the owl (baykuş) to ruins and bad luck, but a wise, friendly cartoon owl is common in Turkish kids' media; keep the owl and keep its face friendly, not spooky.

### Greek (`el`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Γλαυκούλα** | κουκουβάγια | Diminutive of 'γλαύκα', the classic Athenian owl of wisdom (Athena's owl): 'little wise owl', a thoughtful careful player. Feminine-sounding ending. |
| tortoise | **Καβουκάς** | χελώνα | From 'καβούκι' (shell); the idiom 'μπαίνω στο καβούκι μου' means 'withdraw into my shell', exactly a player who folds almost everything. |
| panda | **Παντούλης** | πάντα (panda) | 'Πάντα' (panda) also means 'always' in Greek; + friendly '-ούλης': the panda who always calls. |
| gecko | **Τρεχαμίδι** | σαμιαμίδι | 'Τρέχω' (to run) + 'σαμιαμίδι' (gecko): a gecko darting everywhere, in on lots of hands. |
| fox | **Αλεποτρότ** | αλεπού | 'Αλεπού' (fox) + 'φοξτρότ' (foxtrot): keeps the English fox+dance pun with the Greek fox word. |
| honey badger | **Ασβουλίνο** | μελοασβός | 'Ασβός' (badger) + Italian-ish '-ουλίνο', mirroring Badgerino: a cocky little badger. |
| capybara | **Χαλαμπάρα** | καπιμπάρα | 'Χαλαρά' (relaxed, chill) blended with 'καπιμπάρα': the laid-back capybara. |

Avatar note: same (the owl is a positive symbol of wisdom in Greece)

### Czech (`cs`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Houkálek** | sova | From 'houkat' (to hoot) + cute '-álek': 'little hooter', direct counterpart of Hootie. |
| tortoise | **Krunýřek** | želva | Diminutive of 'krunýř' (shell, carapace): stays inside his armour and rarely comes out to bet. |
| panda | **Bambusák** | panda | 'Bambus' (bamboo) + friendly '-ák' (a guy who is all about...): the bamboo guy, hoping for bamboo. |
| gecko | **Vrtílek** | gekon | From 'vrtět se' (to fidget, wriggle): a restless little fidgeter who jumps into many hands. |
| fox | **Liškotrot** | liška | 'Liška' (fox) + 'foxtrot' (same word in Czech): keeps the fox+dance pun. |
| honey badger | **Medojedino** | medojed | 'Medojed' (honey badger, literally 'honey-eater') + Italian-ish '-ino', mirroring Badgerino. Exactly 10 chars. |
| capybara | **Pohodbara** | kapybara | 'Pohoda' (chill, easy-going, a key Czech word) + 'kapybara': the chill capybara. |

Avatar note: same

### Romanian (`ro`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Bufnache** | bufniță | 'Bufniță' (owl) + playful Romanian name ending '-ache' (as in Mitache): a funny old-fashioned gentleman owl. 'A bufni' also means to burst out laughing. |
| tortoise | **Carapăcel** | țestoasă (broască țestoasă) | Diminutive of 'carapace' (shell): 'little shell', always tucked in. |
| panda | **Bambusel** | panda | 'Bambus' (bamboo) + cute diminutive '-el': 'little bamboo', the hopeful calling panda. |
| gecko | **Zvârlugă** | gecko / șopârlă (lizard) | A real word for a restless, wriggly, quick child (also a small darting fish): fits a lively gecko that plays lots of hands. |
| fox | **Vulpotrot** | vulpe | 'Vulpe' (fox) + 'foxtrot': keeps the English fox+dance pun. |
| honey badger | **Bursucino** | bursuc (honey badger: bursuc de miere / ratel) | 'Bursuc' (badger) + Italian-ish '-ino', mirroring Badgerino. |
| capybara | **Lejerbara** | capibara | 'Lejer' (relaxed, easy-going, common colloquial) + 'capibara': the chill capybara. |

Avatar note: Mostly same. In folk belief the screech owl (cucuvea) is an omen of misfortune, but 'bufniță' as a wise cartoon owl is common in children's books; keep the owl friendly-looking.

### Hungarian (`hu`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Huhogó** | bagoly | From 'huhog' (to hoot): 'the hooting one', the direct counterpart of Hootie. |
| tortoise | **Páncélka** | teknős | 'Páncél' (armour, shell) + cute '-ka': 'little armour', safely shut in his shell. |
| panda | **Bambuszka** | panda | 'Bambusz' (bamboo) + cute '-ka': 'little bamboo', the hopeful panda. |
| gecko | **Fürgekkó** | gekkó | 'Fürge' (nimble, quick) merged with 'gekkó' (shared 'ge' sound): a quick gecko that jumps into lots of hands. |
| fox | **Ravaszdi** | róka | A real colloquial word for a sly, cunning fellow (from 'ravasz', sly), classic for a fox: bluffs with a grin. |
| honey badger | **Borzino** | méhészborz | 'Borz' (badger) + Italian-ish '-ino', mirroring Badgerino. |
| capybara | **Nyugibara** | kapibara | 'Nyugi' (colloquial 'chill, relax') + 'kapibara'. Avoided 'laza' (relaxed) since it may be the label for 'loose'. |

Avatar note: same

### Bulgarian (`bg`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Бухчо** | бухал / сова | 'Бухал' (eagle owl) + typical friendly male name ending '-чо' (as in Пенчо): a cosy owl gent; 'бух' also echoes the hoot. |
| tortoise | **Черупко** | костенурка | From 'черупка' (shell) + name-like '-ко': 'Shelly', always in his shell. |
| panda | **Бамбучо** | панда | 'Бамбук' (bamboo) + friendly '-чо': the bamboo-hoping panda. |
| gecko | **Пъргавко** | гекон | From 'пъргав' (nimble, agile) + '-ко': a lively, quick gecko in lots of hands. |
| fox | **Лисотрот** | лисица | 'Лиса' (fox) + 'фокстрот' (foxtrot): keeps the English fox+dance pun. |
| honey badger | **Язовчино** | медояд (медоед) | 'Язовец' (badger) + Italian-ish '-ино', mirroring Badgerino. |
| capybara | **Кефибара** | капибара | 'Кеф' (fun, chilling, very common slang: 'кеф ми е') + 'капибара': the chill capybara. |

Avatar note: Mostly same. Folk belief treats owls (especially кукумявка) as an ill omen, but a wise cartoon owl is familiar in kids' media; keep the owl friendly, not spooky.

### Croatian (`hr`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Huhić** | sova | From the owl call 'hu-hu' plus the -ić surname ending, so it sounds like a wise little gentleman's surname; quiet, selective player. |
| tortoise | **Oklopko** | kornjača | 'oklop' = shell/armour + friendly -ko name ending: the guy who stays inside his shell, i.e. folds a lot. |
| panda | **Bambusko** | panda | 'bambus' = bamboo + -ko: the bamboo-dreamer who calls everything hoping his bamboo shows up. |
| gecko | **Gekoslav** | gekon | 'gekon' blended with classic Slavic names like Tomislav/Vjekoslav: a cheeky, inventive all-rounder. |
| fox | **Lukavko** | lisica | 'lukav' = sly/cunning + -ko: the sly fox of folk tales who bluffs with a grin. |
| honey badger | **Medoboj** | medojed | 'med' = honey + 'boj' = battle, echoing the animal's name 'medojed' (honey-eater): honey-fighter who charges in fearlessly. |
| capybara | **Ležibara** | kapibara | 'leži' = lies down + '-bara' from kapibara (and 'bara' = pond): the lounging chiller, direct parallel to Chillbara. |

Avatar note: same

### Slovak (`sk`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Húhulík** | sova | Owl call 'hú-hú' + diminutive -lík (as in Kubík): cute, careful little owl. |
| tortoise | **Pancierik** | korytnačka | 'pancier' = armour/shell + diminutive -ik: Little Armour, never leaves his shell unless he has a monster. |
| panda | **Bambuľko** | panda | Mix of 'bambus' (bamboo) and 'bambuľa' (a fluffy pompom): a round, fluffy bamboo-hoper. |
| gecko | **Gekoslav** | gekón | 'gekón' blended with Slavic names like Miroslav/Stanislav: a playful tinkerer who mixes it up. |
| fox | **Ryško** | líška | From 'ryšavý' (ginger/red-haired), a folk-tale style nickname for the fox; sounds like a cheeky boy's nickname. |
| honey badger | **Medoboj** | medojed | 'med' = honey + 'boj' = fight, echoing 'medojed' (honey-eater): a fearless honey-fighter. |
| capybara | **Pohodbara** | kapybara | 'pohoda' = ease/chill (very Slovak: 'v pohode' = all cool) + '-bara' from kapybara: direct parallel to Chillbara. |

Avatar note: same

### Arabic (`ar`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **فهمان** | بومة | Fahmān: colloquial 'the one who gets it / smarty' from fahm (understanding); avoids the word 'būma', which is linked to bad luck. |
| tortoise | **سلحوف** | سلحفاة | Salḥūf: cute pet-form of sulḥafāh (tortoise); slow, careful, hides in its shell. |
| panda | **بامبولي** | باندا | Bāmbūlī: playful invented name from 'bāmbū' (bamboo); sounds bouncy and carefree for a loose caller. |
| gecko | **شطّور** | أبو بريص | Shaṭṭūr: diminutive of shāṭir (clever, handy); the gadget-like quick little trickster. Avoids gecko words 'wazagh'/'abu brais' (religious connotations / root 'baraṣ' = leprosy). |
| fox | **ثعلوب** | ثعلب | Thaʿlūb: affectionate pet-form of thaʿlab (fox), the sly trickster of Arabic fables. |
| honey badger | **مقدام** | غرير العسل | Miqdām: 'daring, bold, always charging forward'; fits the fearless raiser. Also a rare male given name, harmless. |
| capybara | **رايقبارا** | كابيبارا | Rāyiqbāra: colloquial 'rāyiq' (chilled, relaxed, in a good mood) + '-bāra' from kābībāra; a direct parallel to Chillbara. |

Avatar note: Owl: in Arab folk culture the owl (būma) is seen as a bad omen; keep the avatar clearly cute/friendly (big eyes, maybe glasses) and never call it 'būma' in text. Gecko: the house gecko (wazagh) has negative religious connotations in Islamic tradition; the cartoon avatar is acceptable but consider making it a bright, clearly cartoon lizard. Others same.

### Hebrew (`he`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **ינשופון** | ינשוף | Yanshufon: yanshuf (owl) + diminutive -on: a little owl, wise and picky about its hands. |
| tortoise | **שריוני** | צב | Shiryoni: from shiryon (armour/shell) + -i: Mr. Armoured, stays inside his shell. |
| panda | **במבוקי** | פנדה | Bambuki: from bambuk (bamboo) + -i: the panda who keeps calling, waiting for bamboo. |
| gecko | **שממיתוש** | שממית | Smamitush: smamit (gecko) + playful diminutive -ush; a quick, inventive mix-it-up player. |
| fox | **שועליק** | שועל | Shualik: shu'al (fox) + cheeky diminutive -ik: the grinning bluffer. |
| honey badger | **גיבורון** | גירית הדבש | Giboron: gibor (hero) + -on: Little Hero, fearless and bets with anything. |
| capybara | **סבבארה** | קפיברה | Sababara: slang 'sababa' (cool, all good) + '-bara' from kapibara: direct parallel to Chillbara. |

Avatar note: same

### Hindi (`hi`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **समझू** | उल्लू | Samjhu: from 'samajh' (understanding/sense), a nickname for the sensible one. Deliberately avoids 'ullu', the Hindi word for owl, which is a common insult meaning 'fool'. |
| tortoise | **कवचू** | कछुआ | Kavchu: from 'kavach' (armour/shield) + nickname ending -u: the shell-hider who rarely comes out. |
| panda | **गोलू** | पांडा | Golu: the classic affectionate nickname for a chubby, round, easy-going kid; joins every hand. Avoids 'Pandu' (epic figure / Mumbai slang for police) and 'bamboo' (crude slang use). |
| gecko | **जुगाड़ू** | छिपकली | Jugaadu: from 'jugaad' (clever improvised fix), the perfect Indian equivalent of Gizmo; mixes bets and calls. |
| fox | **दाँवू** | लोमड़ी | Daanvu: from 'daanv' (a bet/stake, a tricky move, as in daanv lagana 'to place a bet'): the bold bettor and bluffer. |
| honey badger | **शहदवीर** | बिज्जू | Shahadveer: 'shahad' (honey) + 'veer' (brave, as in names like Ranveer): the brave honey-warrior. |
| capybara | **मस्तबारा** | कैपीबारा | Mastbara: 'mast' (carefree, happy, 'cool', as in 'mast hai!') + '-bara' from capybara: direct parallel to Chillbara. |

Avatar note: Owl: 'ullu' (owl) is an everyday insult for 'fool', and the owl is also Lakshmi's mount; the name avoids the word, and the avatar should look clearly wise (e.g. glasses). Gecko: the house lizard (chhipkali) is disliked by many and tied to omen beliefs; a bright cartoon style is fine but avoid realistic looks. Fox: 'lomdi' is used for a cunning person, mild. Others same.

### Indonesian (`id`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Bijakus** | burung hantu | 'bijak' = wise + playful Latin-ish -us: the wise owl who picks hands carefully. |
| tortoise | **Kurahemat** | kura-kura | 'kura' (tortoise) + 'hemat' (thrifty, saves money): spends chips only on a monster. |
| panda | **Bambingung** | panda | 'bambu' (bamboo) + 'bingung' (confused/bewildered): a local echo of Bamboozle; calls everything in happy confusion. |
| gecko | **Cicakrobat** | cicak / tokek | 'cicak' (house gecko) + 'akrobat' (acrobat): the nimble gecko who mixes up its moves. |
| fox | **Rubahaha** | rubah | 'rubah' (fox) + 'haha': the fox who laughs while bluffing. |
| honey badger | **Madunekat** | musang madu | 'madu' (honey) + 'nekat' (daring, reckless): fearless honey-hunter who bets with anything. |
| capybara | **Santaibara** | kapibara | 'santai' (relaxed, chill) + '-bara' from kapibara: direct parallel to Chillbara. |

Avatar note: Owl: 'burung hantu' literally means 'ghost bird' and some folk beliefs see it as an omen; a cute, friendly owl avatar is fine for most audiences. Others same.

### Vietnamese (`vi`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **Cú Khôn** | cú mèo | 'Cú Khôn' = 'clever owl' (cu khon); a wise bird that only plays the hands it is sure of. Plain, friendly, no tone traps. |
| tortoise | **Rùa Rụt Rè** | rùa | 'Rùa Rụt Rè' (rua rut re) = 'shy tortoise'; alliterative R-R-R, and 'rut' also evokes pulling the head into the shell, so it folds almost everything. Exactly 10 characters. |
| panda | **Mê Trúc** | gấu trúc | 'Mê Trúc' (me truc) = 'bamboo-crazy'; puns on gau truc (panda = 'bamboo bear'); it calls everything hoping the bamboo shows up. |
| gecko | **Kè Lanh** | tắc kè | 'Kè Lanh' (ke lanh) = 'nimble gecko'; 'ke' from tac ke, 'lanh' = quick/eager, so it jumps into lots of hands. Deliberately avoids 'tac ke hoa' (chameleon), which is an idiom for a turncoat. |
| fox | **Cáo Lém** | cáo | 'Cáo Lém' (cao lem) = 'cheeky fox'; from 'lem linh' (cheeky, playful), fits a fox that bluffs with a grin. Avoids 'cao gia' (old fox), which can sound like an insult for a scheming person. |
| honey badger | **Lửng Gan** | lửng mật | 'Lửng Gan' (lung gan) = 'gutsy badger'; 'gan' as in 'gan da' (brave), it fears nothing and raises with anything. |
| capybara | **Bara Tà Tà** | chuột lang nước (capybara) | 'Bara Tà Tà' (bara ta ta) = 'easy-going Bara'; 'ta ta' is a colloquial (mostly southern) word for strolling slowly, unhurried; 'Bara' from capybara, which Vietnamese internet already knows as the calm meme animal. Reviewer: confirm 'ta ta' reads as relaxed, not as 'ta' (evil) on its own. |

Avatar note: Owl: in Vietnamese folklore the owl's call is a bad omen ('cu keu ra ma'). A big-eyed cute cartoon owl is normal in kids' media, so keep it, but keep the drawing friendly (no dark/night look). Others: same.

### Thai (`th`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **ฮูกขรึม** | นกฮูก | 'Huk Khruem' (RTGS: huk khruem) = 'serious, reserved owl'; 'khruem' means quiet and composed, hinting it only plays few, careful hands. |
| tortoise | **เต่าเหนียว** | เต่า | 'Tao Niao' (RTGS: tao niao) = 'sticky/tight tortoise'; 'niao' (sticky) is also the slang in 'khi niao' = stingy, so it is a pun on a tight player who will not give up chips. |
| panda | **หมีลุ้น** | แพนด้า | 'Mi Lun' (RTGS: mi lun) = 'hoping bear'; 'lun' means to root for / keep hoping, it calls every hand hoping for its bamboo. Avoided 'mua' (random), which can carry a promiscuity sense. |
| gecko | **ตุ๊กซน** | ตุ๊กแก | 'Tuk Son' (RTGS: tuk son) = 'naughty little gecko'; 'tuk' from tukkae (gecko, named after its call), 'son' = playful/mischievous, so it plays lots of hands. |
| fox | **จิ้งจอกแสบ** | หมาจิ้งจอก (สุนัขจิ้งจอก) | 'Chingchok Saep' (RTGS: chingchok saep) = 'cheeky fox'; 'saep' is the friendly word for a mischievous rascal (dek saep), fits a grinning bluffer. 8 base letters. |
| honey badger | **แบดใจถึง** | ฮันนีแบดเจอร์ | 'Baet Chai Thueng' (RTGS: baet chai thueng) = 'gutsy badger'; 'chai thueng' = daring, game for anything. Reviewer: 'baet' is from 'badger' but may also be read as English 'bad'; alternative 'แบดลุย' (Baet Lui, 'charging badger'). |
| capybara | **คาปิชิล** | คาปิบารา | 'Khapi Chil' (RTGS: khapi chin, said 'chill') = 'chill capy'; 'chil' is everyday Thai slang for relaxed. Same idea as the English Chillbara, localized form, so not marked kept. |

Avatar note: Owl: traditional Thai belief treats owls (especially the barn owl, nok saek) entering a house as a bad omen; a round cartoon owl is fine and common in kids' media, but avoid a pale barn-owl face. Gecko: folk tale 'tukkae eats your liver' is a playful scare, not a concern. Others: same.

### Japanese (`ja`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **フク先生** | フクロウ | 'Fuku-sensei' (fuku sensei) = 'Professor Owl'; 'fuku' from fukurou, also sounds like 福 (luck); 'sensei' suggests a wise, careful player who picks few hands. |
| tortoise | **コウラ丸** | カメ (リクガメ) | 'Kouramaru' (koura-maru) = 'Shell-maru'; 'koura' = shell, '-maru' is a classic cute name ending; it hides in its shell and folds almost everything. |
| panda | **ササパン** | パンダ | 'Sasapan' (sasa-pan) = 'bamboo-grass panda'; sasa = the bamboo grass pandas eat, a cute clipped nickname; hints it keeps calling for its bamboo. Avoids real zoo panda names (Shan Shan, Ri Ri, Shin Shin, etc.). |
| gecko | **ペタ吉** | ヤモリ | 'Petakichi' (peta-kichi) = 'Sticky-kichi'; 'peta-peta' is the sound of sticky gecko feet, '-kichi' a folksy boy-name ending; it sticks to lots of hands. |
| fox | **コンニヤリ** | キツネ | 'Kon-niyari' (kon niyari) = fox cry 'kon' + 'niyari' (a sly grin); a grinning bluffer. Avoided 'Niyakon' because '-kon' also reads as 'complex' (as in unwanted slang compounds). |
| honey badger | **イケテル** | ラーテル | 'Iketeru' (iketeru) = pun on raateru (honey badger) + 'ike-ike' (go-go, pushy) and slang 'iketeru' (cool, on fire); a fearless raiser. |
| capybara | **のんびら** | カピバラ | 'Nonbira' (nonbira) = 'nonbiri' (laid-back) + kapibara; capybaras are already Japan's favourite relaxed animal, so this reads naturally. |

Avatar note: same (owl is a lucky symbol in Japan via fukurou = 不苦労 'no hardship'; tortoise = long life; fox is linked to folklore but a cartoon fox is a common mascot).

### Korean (`ko`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **부엉박사** | 부엉이 | 'Bueong-baksa' (bueong baksa) = 'Dr. Owl'; 'bueong' from bueongi (owl, also its hoot), 'baksa' = doctor/expert: a wise player picking few hands. |
| tortoise | **꼭꼭북이** | 거북이 | 'Kkokkok-bugi' (kkokkok bugi) = 'tightly-tucked turtle'; 'kkokkok' as in the hide-and-seek chant 'kkokkok sumeora' (hide well!), 'bugi' from geobugi; it stays hidden and folds almost everything. |
| panda | **다콜판다** | 판다 | 'Dakol-panda' (da kol panda) = 'calls-everything panda'; 'da' = all, 'kol' = call (also everyday 'deal!'), and 'panda' also means 'sells' in Korean, which adds a light joke. |
| gecko | **착붙이** | 도마뱀붙이 (게코) | 'Chakbuti' (chak-buchi) = pun on domabaembuchi (gecko) + 'chak butda' (to stick right on, slang 'chakbut' = perfect fit); it sticks to lots of hands. |
| fox | **능청여우** | 여우 | 'Neungcheong-yeou' (neungcheong yeou) = 'poker-faced fox'; 'neungcheong' means acting innocent while up to tricks, ideal for a grinning bluffer. |
| honey badger | **꿀배짱** | 벌꿀오소리 | 'Kkul-baejjang' (kkul baejjang) = 'honey + guts'; 'kkul' from beolkkul-osori (honey badger, a famous fearless-animal meme in Korea), 'baejjang' = nerve/boldness. |
| capybara | **느긋바라** | 카피바라 | 'Neugeut-bara' (neugeut bara) = 'relaxed capybara'; 'neugeut' = easy-going, unhurried, + 'bara' from kapibara. |

Avatar note: same (minor: '여우' fox is also slang for a sly/flirty person, but a cartoon fox mascot is common and fine; owl is today seen as a luck/wealth charm).

### Chinese (Simplified) (`zh-CN`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **鹰博士** | 猫头鹰 | 'Ying Boshi' (ying boshi) = 'Dr. Owl'; ying from maotouying (owl), boshi = doctor/expert, the classic wise-owl image of Chinese kids' books; picks few hands. Avoided 'gugu' (hoot) because internet slang 'gugugu' means flaking out. |
| tortoise | **壳壳** | 乌龟 (陆龟) | 'Keke' (ke ke) = 'Shell-Shell'; a cute reduplicated nickname for an animal that never leaves its shell. Deliberately avoids 龟 combos like 缩头乌龟 (suotou wugui, 'turtle pulling its head in'), which is an insult for a coward. |
| panda | **跟跟胖达** | 熊猫 | 'Gengen Pangda' (gengen pangda) = 'Call-call Panda'; 跟 gen = 'call' in Chinese poker (跟注), and 胖达 pangda ('chubby-da') is the popular affectionate pun on panda. |
| gecko | **溜溜虎** | 壁虎 | 'Liuliu Hu' (liuliu hu) = 'darting little gecko'; liuliu = slipping and sliding around, hu from bihu (gecko, literally 'wall tiger'); it slips into many hands. |
| fox | **狐说** | 狐狸 | 'Hushuo' (hu shuo) = 'the fox says', a homophone of 胡说 hushuo ('talking nonsense'), a gentle pun for a bluffer. Avoids 狐狸精 (seductress). |
| honey badger | **冲冲獾** | 蜜獾 | 'Chongchong Huan' (chongchong huan) = 'charge-charge badger'; huan (badger) also sounds like 欢 (joy). Avoided the famous meme nickname 平头哥 because it is also a company brand name. |
| capybara | **淡定豚** | 水豚 (卡皮巴拉) | 'Danding Tun' (danding tun) = 'unflappable capybara'; danding = calm, the word Chinese internet uses for the capybara's famously stable mood; tun from shuitun. Avoided 佛系 (religious) and 躺平 (political overtone). |

Avatar note: Owl: older folk belief treats owls as unlucky ('夜猫子进宅'), but cartoon owls are standard for 'wise' in kids' media, so keep it friendly. Tortoise: 乌龟/王八 can be insults, so never pair the tortoise with names or captions using 乌龟, 王八 or 缩头; the cute drawing itself is fine.

### Chinese (Traditional) (`zh-TW`)

| Animal | Name | Local animal word | Why |
| --- | --- | --- | --- |
| owl | **鷹老師** | 貓頭鷹 | 'Ying Laoshi' (ying laoshi, zhuyin: ㄧㄥ ㄌㄠˇ ㄕ) = 'Teacher Owl'; laoshi is the everyday Taiwan title for a wise teacher; bonus: 鷹 ying sounds like 贏 ying (win). |
| tortoise | **龜毛毛** | 烏龜 (陸龜) | 'Gui Maomao' (gui maomao, zhuyin: ㄍㄨㄟ ㄇㄠˊ ㄇㄠˊ) = pun on Taiwan slang 龜毛 guimao ('fussy, overly picky'), perfect for a player who folds almost everything; reduplicated maomao keeps it cute. |
| panda | **跟跟熊** | 貓熊 | 'Gengen Xiong' (gengen xiong, zhuyin: ㄍㄣ ㄍㄣ ㄒㄩㄥˊ) = 'Call-call Bear'; 跟 gen = 'call' in poker; xiong from 貓熊, the Taiwan word for panda. Avoids the famous Taipei Zoo panda names 團團, 圓圓, 圓仔. |
| gecko | **黏黏虎** | 壁虎 | 'Nian Nian Hu' (niannian hu, zhuyin: ㄋㄧㄢˊ ㄋㄧㄢˊ ㄏㄨˇ) = 'sticky-sticky gecko'; niannian = sticky/clingy, hu from bihu; it sticks to lots of hands. 黏 is the standard Taiwan form. |
| fox | **嘻嘻狐** | 狐狸 | 'Xixi Hu' (xixi hu, zhuyin: ㄒㄧ ㄒㄧ ㄏㄨˊ) = 'giggling fox'; a cheeky grinning bluffer. Avoids 狐狸精 (seductress). |
| honey badger | **敢衝獾** | 蜜獾 | 'Gan Chong Huan' (ganchong huan, zhuyin: ㄍㄢˇ ㄔㄨㄥ ㄏㄨㄢ) = 'dare-to-charge badger'; 敢衝 gan chong is a common Taiwan expression for bold, go-for-it people. |
| capybara | **悠哉豚** | 水豚 | 'Youzai Tun' (youzai tun, zhuyin: ㄧㄡ ㄗㄞ ㄊㄨㄣˊ) = 'leisurely capybara'; 悠哉 youzai ('taking it easy') is very common in Taiwan speech. |

Avatar note: Tortoise: 烏龜 can be an insult, so never caption it with 烏龜/王八/縮頭; the cute drawing is fine. Owl: generally a positive, popular motif in Taiwan (鷹 ying ~ 贏 win). Otherwise same.

## Things to look at first

- Same name in more than one language: Pohodbara (cs, sk); Gekoslav (hr, sk); Medoboj (hr, sk). Allowed, but you may want them to differ.
- At the 10-character limit: Rùa Rụt Rè (vi), Bara Tà Tà (vi), เต่าเหนียว (th), จิ้งจอกแสบ (th), Jabutimido (pt-BR), Lagartreco (pt-BR), Tartapiano (it), Tostotasso (it), Bambingung (id), Cicakrobat (id), Santaibara (id), Cachecoque (fr), Bambouille (fr), Touchatout (fr), Renarnaque (fr), Kettutango (fi), Tortulenta (es), Sonrizorro (es), Siestibara (es), Schildbert (de), Schlaueule (de), Draufdachs (de), Geckoflitz (de), Grinsfuchs (de), Medojedino (cs).
- Kept from English: de Chillbara.
- Owl: several languages treat it as a bad omen (see the avatar notes); a friendly drawing is the usual answer.
- Names the drafters flagged for a closer look are explained in the "Why" column; the reviewer notes are in the table rows.

## What adopting them changed

- The seven names moved from `Animal.displayName` (now only the English original) to `animal_owl` ... `animal_capybara` in `core:ui`; seats, spoken seat descriptions and announcements read them through `Animal.localizedName`.
- `docs/games/holdem/DESIGN.md` and the `Animal.kt` comment no longer say names are not translated.
- Still to do: native-speaker review per language, and re-checking the longest names on a seat at the largest font scale.
