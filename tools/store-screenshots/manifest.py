"""Records which store screenshots were produced, from which builds, as a small file git can hold.

    python tools/store-screenshots/manifest.py [--uploaded YYYY-MM-DD]

The PNGs are git-ignored (about 900 MB for 31 languages, and a regeneration adds that much to history),
so the repository keeps only this manifest: per game the versionName and versionCode the shots came from,
and the SHA-256 of every file. It answers "what is live, and is it stale" without the images. Run it after
a capture, and again with --uploaded after the set is uploaded to Play Console.
Writes brand/google-play/screenshots-manifest.json.
"""
import argparse
import datetime
import glob
import hashlib
import json
import os
import re

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
PLAY = os.path.join(ROOT, 'brand', 'google-play')
GAMES = ['klondike', 'spider', 'freecell', 'blackjack', 'holdem']
PATH = os.path.join(PLAY, 'screenshots-manifest.json')


def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def version_name():
    with open(os.path.join(ROOT, 'gradle.properties'), encoding='utf-8') as f:
        m = re.search(r'^finiteplay\.versionName\s*=\s*(\S+)', f.read(), re.M)
    return m.group(1) if m else None


def version_code(game):
    with open(os.path.join(ROOT, 'games', game, 'app', 'build.gradle.kts'), encoding='utf-8') as f:
        m = re.search(r'^\s*versionCode\s*=\s*(\d+)', f.read(), re.M)
    return int(m.group(1)) if m else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--uploaded', help='the date this set was uploaded to Play Console')
    args = ap.parse_args()
    previous = {}
    if os.path.exists(PATH):
        with open(PATH, encoding='utf-8') as f:
            previous = json.load(f)
    manifest = {
        'generated': datetime.date.today().isoformat(),
        'uploaded': args.uploaded or previous.get('uploaded'),
        'versionName': version_name(),
        'recipe': 'tools/store-screenshots (1080x1920 emulator, font scale 1.0, release builds)',
        'games': {},
    }
    for g in GAMES:
        entry = {'versionCode': version_code(g), 'languages': {}}
        for d in sorted(glob.glob(os.path.join(PLAY, g, 'screenshots-localized', '*'))):
            files = sorted(glob.glob(os.path.join(d, '*.png')))
            if files:
                entry['languages'][os.path.basename(d)] = {os.path.basename(p): sha256(p) for p in files}
        manifest['games'][g] = entry
    with open(PATH, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(manifest, f, indent=1, sort_keys=True)
        f.write('\n')
    count = sum(len(v) for e in manifest['games'].values() for v in e['languages'].values())
    print('wrote %s: %d files' % (os.path.relpath(PATH, ROOT), count))


if __name__ == '__main__':
    main()
