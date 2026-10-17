#!/bin/bash
# Archives the localized store screenshots and their manifest in the private R2 bucket.
#   tools/store-screenshots/upload-r2.sh [--apply]      (without --apply: lists what would be uploaded)
# Needs `wrangler login` as the account that owns the bucket (no credentials are read or stored here).
# Keys: <versionName>/<date>/<game>/<language>/<file>.png and <versionName>/<date>/manifest.json.
# Wrangler runs from a scratch directory so it does not leave a .wrangler/ folder in the repository.
set -eu
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
BUCKET="${BUCKET:-finiteplay-store-screenshots}"
JOBS="${JOBS:-4}"
APPLY=0; [ "${1:-}" = "--apply" ] && APPLY=1
MANIFEST="$ROOT/brand/google-play/screenshots-manifest.json"
[ -f "$MANIFEST" ] || { echo "run manifest.py first"; exit 1; }
VERSION="$(python3 -c "import json,sys;print(json.load(open(sys.argv[1]))['versionName'])" "$MANIFEST")"
DATE="$(python3 -c "import json,sys;print(json.load(open(sys.argv[1]))['generated'])" "$MANIFEST")"
PREFIX="$VERSION/$DATE"
WORK="$(mktemp -d)"
cd "$WORK"

LIST="$WORK/files.txt"
( cd "$ROOT/brand/google-play" && find . -path '*/screenshots-localized/*' -name '*.png' | sed 's#^\./##' | sort ) > "$LIST"
echo "$(wc -l < "$LIST") screenshots -> r2://$BUCKET/$PREFIX/ (plus manifest.json)"
if [ "$APPLY" -ne 1 ]; then head -3 "$LIST"; echo "dry run; pass --apply to upload"; exit 0; fi

put() { # path-relative-to-brand/google-play
  rel="$1"
  game="${rel%%/*}"; rest="${rel#*/screenshots-localized/}"; lang="${rest%%/*}"; file="${rest#*/}"
  npx --yes wrangler r2 object put "$BUCKET/$PREFIX/$game/$lang/$file" --file "$ROOT/brand/google-play/$rel" \
    --content-type image/png --remote >/dev/null 2>"$WORK/err.$$.txt" || { echo "FAILED $rel"; cat "$WORK/err.$$.txt" | tail -3; return 1; }
}
export -f put; export BUCKET PREFIX ROOT WORK
npx --yes wrangler r2 object put "$BUCKET/$PREFIX/manifest.json" --file "$MANIFEST" --content-type application/json --remote >/dev/null
xargs -P "$JOBS" -I{} bash -c 'put "$@"' _ {} < "$LIST"
echo "uploaded to r2://$BUCKET/$PREFIX/"
