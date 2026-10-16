# Text fit: labels and buttons in every language

Translations are longer than English, and phones run at large font scales. A label that does not fit is cut off,
shown with an ellipsis, or broken in the middle of a word. Two audits look for this; both use the same detector
(`TextFitAudit` in `core/ui` test fixtures), which reads the text layout the label was really drawn with.

## What is reported

- `ELLIPSIZED`: the text was replaced by an ellipsis.
- `CLIPPED`: lines were dropped past `maxLines`, a line is wider than its box, or wrapped text is taller than its box.
  A single line that is only taller than its box (line-height padding around glyphs that fit) is not reported.
- `BROKEN_WORD`: a line ends inside a word, which is what a button label that is too narrow for its longest word
  looks like. Scripts that wrap anywhere (Han, kana, Hangul, Thai) are not reported for this.

## The two audits

| | Where | Fonts | Use |
| --- | --- | --- | --- |
| `TextFitTest` (instrumented) | `games/<game>/app/src/androidTest` | the device's Roboto | The authority. Run on an emulator at the font scales a phone has. |
| `TextFitJvmTest` (Robolectric) | `games/<game>/app/src/test` | the JVM's font, about 20% narrower | Advisory early warning with the unit tests; noisy in both directions. |

Both walk all 31 interface languages over the table, Settings, Statistics, Help and a hint (Blackjack also its in-hand
buttons), and write a tab-separated report: `locale, fontScale, screen, kind, tag, w, h, lines, needW, needH, text`.

## Run

Emulator audit (at 1.0 and at 1.15, a typical large setting):

    adb shell settings put system font_scale 1.15
    ./gradlew :games:<game>:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.finiteplay.<game>.TextFitTest

The test fails when anything does not fit, and logs each finding under the `TEXTFIT` tag
(`adb logcat -s TEXTFIT:I`); connected tests remove the app's files afterwards, so read the log.

JVM audit:

    ./gradlew :games:<game>:app:testDebugUnitTest --tests '*TextFitJvmTest'

It writes `games/<game>/app/build/textfit/jvm-<game>.tsv`. It fails the build only when `TEXTFIT_STRICT=1` is set. The JVM
measures narrower text than the device, so it runs at 1.2 and 1.4 as a margin and still shows some labels the device
fits and misses some it does not. Use it to notice a new long string early; settle it on the emulator.

## How labels are kept to fit

- Bottom-bar button labels shrink, down to 72% of their size, until the longest word fits (`BoardActionBar.kt`;
  letter spacing shrinks with the size). A word that still does not fit needs a shorter term in that language.
- The solitaires show moves and time as icons with numbers (`StatusCounters.kt`), keeping the words as the
  accessibility text, so the row does not depend on the language.
- Hold'em's blinds and hand lines shrink to fit.

## When a string does not fit

Prefer a shorter, common word for the button over an abbreviation, rename it everywhere in that language (help text,
hints, dialogs) so the app uses one name, and re-run the emulator audit. Note the change for a native speaker.
