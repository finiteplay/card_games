"""Checks the captured store screenshots and writes their per-language upload order.

    python tools/store-screenshots/check.py [--write-order] [--sheets DIR]

Checks: every file decodes and is exactly 1080 x 1920; none is nearly blank; no language's Settings
screen is byte-identical to English (the language did not apply); no hint scene is identical to its
gameplay scene (the tap missed, as it does on a mirrored bar if the mirroring is wrong).
--write-order writes screenshot-order.txt in each language folder (hints and gameplay first).
--sheets DIR writes one contact sheet per language, to look at.
"""
import argparse
import glob
import hashlib
import os
import sys

from PIL import Image, ImageStat

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
PLAY = os.path.join(ROOT, 'brand', 'google-play')
GAMES = ['klondike', 'spider', 'freecell', 'blackjack', 'holdem']
SCENES = {
    'klondike': ['01-gameplay', '02-hint', '03-settings'],
    'spider': ['01-gameplay', '02-hint', '03-settings'],
    'freecell': ['01-gameplay', '02-hint', '03-settings'],
    'blackjack': ['01-betting', '02-gameplay', '03-hint', '04-settings'],
    'holdem': ['01-gameplay', '02-hint', '03-settings'],
}
ORDER = {
    'klondike': ['02-hint', '01-gameplay', '03-settings'],
    'spider': ['02-hint', '01-gameplay', '03-settings'],
    'freecell': ['02-hint', '01-gameplay', '03-settings'],
    'blackjack': ['03-hint', '02-gameplay', '01-betting', '04-settings'],
    'holdem': ['02-hint', '01-gameplay', '03-settings'],
}
HINT_VS_GAMEPLAY = {'klondike': ('01-gameplay', '02-hint'), 'spider': ('01-gameplay', '02-hint'),
                    'freecell': ('01-gameplay', '02-hint'), 'blackjack': ('02-gameplay', '03-hint'),
                    'holdem': ('01-gameplay', '02-hint')}


def digest(path):
    with open(path, 'rb') as f:
        return hashlib.md5(f.read()).hexdigest()


def folder(game, code):
    return os.path.join(PLAY, game, 'screenshots-localized', code)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--write-order', action='store_true')
    ap.add_argument('--sheets')
    args = ap.parse_args()
    problems = []
    codes = sorted({os.path.basename(d) for g in GAMES for d in glob.glob(folder(g, '*'))})
    for g in GAMES:
        english = {n: digest(os.path.join(folder(g, 'en-US'), n + '.png'))
                   for n in SCENES[g] if os.path.exists(os.path.join(folder(g, 'en-US'), n + '.png'))}
        for code in codes:
            d = folder(g, code)
            if not os.path.isdir(d):
                problems.append((g, code, 'missing folder'))
                continue
            for n in SCENES[g]:
                p = os.path.join(d, n + '.png')
                if not os.path.exists(p):
                    problems.append((g, code, n + ' missing'))
                    continue
                try:
                    im = Image.open(p)
                    im.load()
                except Exception as e:  # noqa: BLE001 - any decode failure is the finding
                    problems.append((g, code, n + ' does not decode: ' + str(e)))
                    continue
                if im.size != (1080, 1920):
                    problems.append((g, code, '%s is %dx%d, not 1080x1920' % (n, im.size[0], im.size[1])))
                    continue
                if ImageStat.Stat(im.convert('L')).stddev[0] < 8:
                    problems.append((g, code, n + ' is nearly blank'))
                if code != 'en-US' and n.endswith('settings') and english.get(n) == digest(p):
                    problems.append((g, code, n + ' is identical to English: the language did not apply'))
            a, b = HINT_VS_GAMEPLAY[g]
            pa, pb = os.path.join(d, a + '.png'), os.path.join(d, b + '.png')
            if os.path.exists(pa) and os.path.exists(pb) and digest(pa) == digest(pb):
                problems.append((g, code, b + ' is identical to ' + a + ': the hint tap missed'))
            if args.write_order and os.path.isdir(d):
                names = [x + '.png' for x in ORDER[g] if os.path.exists(os.path.join(d, x + '.png'))]
                with open(os.path.join(d, 'screenshot-order.txt'), 'w', encoding='utf-8', newline='\n') as f:
                    f.write('\n'.join(names) + '\n')
    if args.sheets:
        os.makedirs(args.sheets, exist_ok=True)
        w, h = 216, 384
        for code in codes:
            sheet = Image.new('RGB', (w * 4, h * len(GAMES)), 'white')
            for r, g in enumerate(GAMES):
                for c, n in enumerate(SCENES[g]):
                    p = os.path.join(folder(g, code), n + '.png')
                    if os.path.exists(p):
                        try:
                            sheet.paste(Image.open(p).convert('RGB').resize((w, h)), (c * w, r * h))
                        except Exception:  # noqa: BLE001 - a bad file is reported above
                            pass
            sheet.save(os.path.join(args.sheets, code + '.png'))
    total = sum(len(glob.glob(os.path.join(folder(g, '*'), '*.png'))) for g in GAMES)
    print('%d languages, %d screenshots, %d problems' % (len(codes), total, len(problems)))
    for p in problems:
        print('  ', *p)
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
