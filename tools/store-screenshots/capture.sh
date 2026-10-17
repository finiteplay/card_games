#!/bin/bash
# Captures the localized Play Store phone screenshots of the five games on an emulator.
# See README.md for the setup this expects. Usage:
#   tools/store-screenshots/capture.sh [PLAYCODE:ANDROIDTAG ...]   (default: every line of locales.txt)
# Output: brand/google-play/<game>/screenshots-localized/<PLAYCODE>/NN-name.png (git-ignored).
# GAMES="holdem blackjack" limits the run to some games (default: all five).
set -u
export MSYS_NO_PATHCONV=1
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
OUT="${OUT:-$ROOT/brand/google-play}"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
ADB="${ADB:-adb}"
A="$ADB -s $SERIAL"
# A hung emulator makes adb block forever; a bounded call lets the run fail loudly instead.
T="timeout 60"
GAMES="${GAMES:-klondike spider freecell blackjack holdem}"
want() { [[ " $GAMES " == *" $1 "* ]]; }

if [ "$#" -eq 0 ]; then
  set -- $(grep -v '^#' "$HERE/locales.txt" | awk 'NF { print $1 ":" $2 }')
fi

shot() { $T $A exec-out screencap -p > "$1"; }
fresh() { # game tag: a clean app, in the language
  $T $A shell am force-stop "org.finiteplay.$1"
  $T $A shell pm clear "org.finiteplay.$1" >/dev/null
  $T $A shell cmd locale set-app-locales "org.finiteplay.$1" --user 0 --locales "$2" >/dev/null
}
launch() { $T $A shell am force-stop "org.finiteplay.$1"; $T $A shell am start -n "org.finiteplay.$1/.MainActivity" >/dev/null; sleep "${2:-5}"; }
# Taps are written for a left-to-right bar; a right-to-left language mirrors the bar, so the x is mirrored.
tap() { local x=$1; [ "$MIRROR" = 1 ] && x=$((1080 - x)); $T $A shell input tap "$x" "$2"; sleep "${3:-2}"; }
demo() { $T $A shell am broadcast -a com.android.systemui.demo "$@" >/dev/null; }

# Clean status bar: fixed clock, full battery, wifi, no notifications.
demo -e command enter; demo -e command clock -e hhmm 0941; demo -e command battery -e level 100 -e plugged false
demo -e command network -e wifi show -e level 4; demo -e command network -e mobile hide; demo -e command notifications -e visible false
$T $A shell settings put system font_scale 1.0

for pair in "$@"; do
  code=${pair%%:*}; tag=${pair##*:}
  case "$tag" in ar* | he* | fa* | ur*) MIRROR=1 ;; *) MIRROR=0 ;; esac
  echo "== $code ($tag) mirror=$MIRROR"
  # Solitaires: the opening deal, the hint's highlighted card, Settings. Bar slots: Settings 127, Hint 748 (of 1080).
  for g in klondike spider freecell; do
    want "$g" || continue
    dir="$OUT/$g/screenshots-localized/$code"; mkdir -p "$dir"
    fresh "$g" "$tag"; launch "$g" 6
    shot "$dir/01-gameplay.png"
    tap 748 1719 3; shot "$dir/02-hint.png"
    launch "$g" 5; tap 127 1719 2; shot "$dir/03-settings.png"
    $T $A shell am force-stop "org.finiteplay.$g"
  done
  # Blackjack: the bet screen, Deal (last slot), a hand, Hint (last slot), Settings (first slot).
  if want blackjack; then
  g=blackjack; dir="$OUT/$g/screenshots-localized/$code"; mkdir -p "$dir"
  fresh "$g" "$tag"; launch "$g" 6
  shot "$dir/01-betting.png"
  tap 1000 1719 5; shot "$dir/02-gameplay.png"
  tap 1000 1719 3; shot "$dir/03-hint.png"
  launch "$g" 5; tap 110 1719 2; shot "$dir/04-settings.png"
  $T $A shell am force-stop "org.finiteplay.$g"
  fi
  # Hold'em: the opening hand, Hint (last slot), Settings (first slot).
  if want holdem; then
  g=holdem; dir="$OUT/$g/screenshots-localized/$code"; mkdir -p "$dir"
  fresh "$g" "$tag"; launch "$g" 8
  shot "$dir/01-gameplay.png"
  tap 1000 1719 4; shot "$dir/02-hint.png"
  launch "$g" 6; tap 110 1719 2; shot "$dir/03-settings.png"
  $T $A shell am force-stop "org.finiteplay.$g"
  fi
done

demo -e command exit
echo "done: $OUT"
