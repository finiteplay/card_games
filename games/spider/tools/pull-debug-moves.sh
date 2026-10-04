#!/usr/bin/env bash
# Pulls every recorded Spider game transcript off a device running the Spider Dev (debug) build.
# Dev tool, not part of the app; the recorder is `src/debug`'s DebugMoveRecorder.
#
#   games/spider/tools/pull-debug-moves.sh [destination-dir]   (default: ./spider-debug-moves)
#
# Set ANDROID_SERIAL when more than one device is attached. `exec-out` rather than `shell`, which
# can mangle binary output.
set -euo pipefail

dest="${1:-spider-debug-moves}"
adb="${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe"
[ -x "$adb" ] || adb=adb

mkdir -p "$dest"
# Streamed rather than saved first: Git Bash's tar reads a `C:/...` archive path as a remote host.
"$adb" exec-out run-as org.finiteplay.spider.dev tar c -C files debug_moves | tar x -C "$dest"
echo "$(ls "$dest/debug_moves" | wc -l) game(s) in $dest/debug_moves"
