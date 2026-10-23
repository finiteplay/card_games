# Feedback form and rating dialog strings, batch A

**Status: unreviewed machine translation.** Drafted by a model on 2026-10-22 for 14 new keys in
`core/ui/src/main/res/values-LOC/strings.xml` (es, fr, de, it, pt-BR, nl, uk, pl, sv, nb). No second-model review and
no native-speaker validation has been done.

Source: English block "Feedback and the rating question" in `core/ui/src/main/res/values/strings.xml`.
`feedback_email` is not translated (address, `translatable="false"`). Form of address follows each file's existing
strings: es/it/pl/de/nl/sv/nb informal, fr "vous", pt-BR "você", uk "ви". Terms checked against existing strings
(Cancel, About, Privacy policy, the level-up dialog and its soft "Not yet" button).

Automated checks passed: every edited file parses as XML, each key appears once, `%1$s` matches English, apostrophes
are escaped as `\'` in the resource files (shown unescaped below).

## Needs a native speaker

- **All locales, `enjoy_no`**: soft refusal chosen per language (es "No mucho", de "Eher nicht", it "Non molto",
  pt-BR "Nem tanto", pl "Nie bardzo", nb "Ikke helt"); confirm it reads as polite, not dismissive, on a button.
- **All locales, `feedback_include_info`**: checkbox label is as long as the English; check it wraps acceptably on a
  small screen with large font, or shorten.
- **fr**: "Envoyer des commentaires" vs. "Envoyer un avis" ("avis" could be confused with a store rating); long
  privacy note uses "application" (matches existing app wording) rather than "app".
- **de / sv / it / pt-BR**: anglicism "Feedback" kept (common in German, Swedish, Italian and Brazilian app UIs);
  confirm vs. "Rückmeldung" / "Synpunkter" / "Commenti" / "Comentários".
- **uk**: "відгук" may also suggest a store review; "не виходить в інтернет" for "never goes online" should be checked.
- **pl**: "wersja robocza" for an email draft and capitalized "Twoja"/"Ci" (polite convention) should be checked.
- **nb**: "går aldri på nett" and "si fra om hva vi kan forbedre".
- **nl**: `enjoy_body` was split into a question ("Klopt er iets niet? Vertel ons ...") for natural flow.

## Spanish (es)

| Key | Translation |
|---|---|
| `about_send_feedback` | Enviar comentarios |
| `feedback_title` | Enviar comentarios |
| `feedback_category_problem` | Problema |
| `feedback_category_idea` | Idea |
| `feedback_category_other` | Otra cosa |
| `feedback_message_label` | Tu mensaje |
| `feedback_include_info` | Incluir versión de la app, idioma y modelo del dispositivo |
| `feedback_privacy_note` | Se abrirá tu app de correo con un borrador para %1$s. No se envía nada hasta que lo envíes desde allí; el juego nunca se conecta a internet. |
| `feedback_send` | Enviar |
| `feedback_no_email_app` | No se encontró ninguna app de correo. Puedes escribir a %1$s. |
| `enjoy_title` | ¿Te gusta este juego? |
| `enjoy_body` | Una valoración en Google Play ayuda a otros jugadores a encontrarlo. Si algo no va bien, cuéntanos qué mejorar. |
| `enjoy_yes` | Sí |
| `enjoy_no` | No mucho |

## French (fr)

| Key | Translation |
|---|---|
| `about_send_feedback` | Envoyer des commentaires |
| `feedback_title` | Envoyer des commentaires |
| `feedback_category_problem` | Problème |
| `feedback_category_idea` | Idée |
| `feedback_category_other` | Autre chose |
| `feedback_message_label` | Votre message |
| `feedback_include_info` | Inclure la version de l'application, la langue et le modèle de l'appareil |
| `feedback_privacy_note` | Cela ouvre votre application de messagerie avec un brouillon adressé à %1$s. Rien n'est envoyé tant que vous ne l'envoyez pas depuis celle-ci ; le jeu lui-même ne se connecte jamais à Internet. |
| `feedback_send` | Envoyer |
| `feedback_no_email_app` | Aucune application de messagerie trouvée. Vous pouvez écrire à %1$s. |
| `enjoy_title` | Ce jeu vous plaît ? |
| `enjoy_body` | Une note sur Google Play aide d'autres joueurs à le trouver. Si quelque chose ne va pas, dites-nous ce qu'il faut améliorer. |
| `enjoy_yes` | Oui |
| `enjoy_no` | Pas vraiment |

## German (de)

| Key | Translation |
|---|---|
| `about_send_feedback` | Feedback senden |
| `feedback_title` | Feedback senden |
| `feedback_category_problem` | Problem |
| `feedback_category_idea` | Idee |
| `feedback_category_other` | Etwas anderes |
| `feedback_message_label` | Deine Nachricht |
| `feedback_include_info` | App-Version, Sprache und Gerätemodell mitsenden |
| `feedback_privacy_note` | Damit öffnet sich deine E-Mail-App mit einem Entwurf an %1$s. Es wird nichts gesendet, bis du ihn dort abschickst; das Spiel selbst geht nie online. |
| `feedback_send` | Senden |
| `feedback_no_email_app` | Keine E-Mail-App gefunden. Du kannst an %1$s schreiben. |
| `enjoy_title` | Gefällt dir das Spiel? |
| `enjoy_body` | Eine Bewertung bei Google Play hilft anderen Spielern, es zu finden. Wenn etwas nicht passt, sag uns, was wir verbessern können. |
| `enjoy_yes` | Ja |
| `enjoy_no` | Eher nicht |

## Italian (it)

| Key | Translation |
|---|---|
| `about_send_feedback` | Invia feedback |
| `feedback_title` | Invia feedback |
| `feedback_category_problem` | Problema |
| `feedback_category_idea` | Idea |
| `feedback_category_other` | Altro |
| `feedback_message_label` | Il tuo messaggio |
| `feedback_include_info` | Includi versione dell'app, lingua e modello del dispositivo |
| `feedback_privacy_note` | Si aprirà la tua app email con una bozza indirizzata a %1$s. Non viene inviato nulla finché non la invii da lì; il gioco non si connette mai a internet. |
| `feedback_send` | Invia |
| `feedback_no_email_app` | Nessuna app email trovata. Puoi scrivere a %1$s. |
| `enjoy_title` | Ti piace questo gioco? |
| `enjoy_body` | Una valutazione su Google Play aiuta altri giocatori a trovarlo. Se qualcosa non va, dicci cosa migliorare. |
| `enjoy_yes` | Sì |
| `enjoy_no` | Non molto |

## Brazilian Portuguese (pt-BR)

| Key | Translation |
|---|---|
| `about_send_feedback` | Enviar feedback |
| `feedback_title` | Enviar feedback |
| `feedback_category_problem` | Problema |
| `feedback_category_idea` | Ideia |
| `feedback_category_other` | Outra coisa |
| `feedback_message_label` | Sua mensagem |
| `feedback_include_info` | Incluir versão do app, idioma e modelo do aparelho |
| `feedback_privacy_note` | Isso abre seu app de e-mail com um rascunho para %1$s. Nada é enviado até você enviar por lá; o jogo em si nunca fica on-line. |
| `feedback_send` | Enviar |
| `feedback_no_email_app` | Nenhum app de e-mail encontrado. Você pode escrever para %1$s. |
| `enjoy_title` | Está gostando deste jogo? |
| `enjoy_body` | Uma avaliação no Google Play ajuda outros jogadores a encontrá-lo. Se algo não estiver bom, conte para a gente o que melhorar. |
| `enjoy_yes` | Sim |
| `enjoy_no` | Nem tanto |

## Dutch (nl)

| Key | Translation |
|---|---|
| `about_send_feedback` | Feedback sturen |
| `feedback_title` | Feedback sturen |
| `feedback_category_problem` | Probleem |
| `feedback_category_idea` | Idee |
| `feedback_category_other` | Iets anders |
| `feedback_message_label` | Je bericht |
| `feedback_include_info` | App-versie, taal en apparaatmodel meesturen |
| `feedback_privacy_note` | Hiermee open je je e-mailapp met een concept aan %1$s. Er wordt niets verstuurd totdat je het daar verstuurt; het spel zelf gaat nooit online. |
| `feedback_send` | Versturen |
| `feedback_no_email_app` | Geen e-mailapp gevonden. Je kunt mailen naar %1$s. |
| `enjoy_title` | Vind je dit spel leuk? |
| `enjoy_body` | Een beoordeling op Google Play helpt andere spelers het te vinden. Klopt er iets niet? Vertel ons wat we kunnen verbeteren. |
| `enjoy_yes` | Ja |
| `enjoy_no` | Niet echt |

## Ukrainian (uk)

| Key | Translation |
|---|---|
| `about_send_feedback` | Надіслати відгук |
| `feedback_title` | Надіслати відгук |
| `feedback_category_problem` | Проблема |
| `feedback_category_idea` | Ідея |
| `feedback_category_other` | Інше |
| `feedback_message_label` | Ваше повідомлення |
| `feedback_include_info` | Додати версію застосунку, мову та модель пристрою |
| `feedback_privacy_note` | Відкриється ваш поштовий застосунок із чернеткою листа на адресу %1$s. Нічого не надсилається, доки ви не надішлете лист звідти; сама гра ніколи не виходить в інтернет. |
| `feedback_send` | Надіслати |
| `feedback_no_email_app` | Поштовий застосунок не знайдено. Ви можете написати на %1$s. |
| `enjoy_title` | Подобається ця гра? |
| `enjoy_body` | Оцінка в Google Play допомагає іншим гравцям знайти гру. Якщо щось не так, розкажіть, що нам покращити. |
| `enjoy_yes` | Так |
| `enjoy_no` | Не дуже |

## Polish (pl)

| Key | Translation |
|---|---|
| `about_send_feedback` | Wyślij opinię |
| `feedback_title` | Wyślij opinię |
| `feedback_category_problem` | Problem |
| `feedback_category_idea` | Pomysł |
| `feedback_category_other` | Coś innego |
| `feedback_message_label` | Twoja wiadomość |
| `feedback_include_info` | Dołącz wersję aplikacji, język i model urządzenia |
| `feedback_privacy_note` | Otworzy się Twoja aplikacja pocztowa z wersją roboczą wiadomości do %1$s. Nic nie zostanie wysłane, dopóki nie wyślesz jej stamtąd; sama gra nigdy nie łączy się z internetem. |
| `feedback_send` | Wyślij |
| `feedback_no_email_app` | Nie znaleziono aplikacji pocztowej. Możesz napisać na adres %1$s. |
| `enjoy_title` | Podoba Ci się ta gra? |
| `enjoy_body` | Ocena w Google Play pomaga innym graczom ją znaleźć. Jeśli coś jest nie tak, napisz nam, co poprawić. |
| `enjoy_yes` | Tak |
| `enjoy_no` | Nie bardzo |

## Swedish (sv)

| Key | Translation |
|---|---|
| `about_send_feedback` | Skicka feedback |
| `feedback_title` | Skicka feedback |
| `feedback_category_problem` | Problem |
| `feedback_category_idea` | Idé |
| `feedback_category_other` | Något annat |
| `feedback_message_label` | Ditt meddelande |
| `feedback_include_info` | Bifoga appversion, språk och enhetsmodell |
| `feedback_privacy_note` | Din e-postapp öppnas med ett utkast till %1$s. Inget skickas förrän du skickar det därifrån; själva spelet går aldrig ut på internet. |
| `feedback_send` | Skicka |
| `feedback_no_email_app` | Ingen e-postapp hittades. Du kan skriva till %1$s. |
| `enjoy_title` | Gillar du spelet? |
| `enjoy_body` | Ett betyg på Google Play hjälper andra spelare att hitta det. Om något inte känns rätt, berätta vad vi kan förbättra. |
| `enjoy_yes` | Ja |
| `enjoy_no` | Inte riktigt |

## Norwegian Bokmal (nb)

| Key | Translation |
|---|---|
| `about_send_feedback` | Send tilbakemelding |
| `feedback_title` | Send tilbakemelding |
| `feedback_category_problem` | Problem |
| `feedback_category_idea` | Idé |
| `feedback_category_other` | Noe annet |
| `feedback_message_label` | Din melding |
| `feedback_include_info` | Ta med appversjon, språk og enhetsmodell |
| `feedback_privacy_note` | E-postappen din åpnes med et utkast til %1$s. Ingenting sendes før du sender det derfra; selve spillet går aldri på nett. |
| `feedback_send` | Send |
| `feedback_no_email_app` | Fant ingen e-postapp. Du kan skrive til %1$s. |
| `enjoy_title` | Liker du spillet? |
| `enjoy_body` | En vurdering på Google Play hjelper andre spillere med å finne det. Hvis noe ikke er som det skal, si fra om hva vi kan forbedre. |
| `enjoy_yes` | Ja |
| `enjoy_no` | Ikke helt |
