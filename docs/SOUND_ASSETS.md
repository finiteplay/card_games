# Sound assets

The short cues in `core/ui/src/main/res/raw/` form one quiet, game-neutral library shared by
Klondike, Spider, FreeCell, and Blackjack. Physical actions use recorded card and chip Foley;
semantic feedback uses generated or procedural audio. All files are CC0 1.0 and may be shipped,
modified, and used commercially without attribution.

## Manifest

| Resource | Event | Source file | SHA-256 |
|---|---|---|---|
| `sound_move.ogg` | Player card move | Kenney Casino Audio 1.1 `card-slide-4.ogg` | `b9c82bcdbe6b7d00a06e2546bd2683a9ab082ca5fd090a42882f33eddd925b7f` |
| `sound_automove.ogg` | Automatic card transfer | Kenney Casino Audio 1.1 `card-place-1.ogg` | `d8f26c33a665dcbdcc6f632f6050bc9dd548feb0d4942d4547615926d3926810` |
| `sound_undo.ogg` | Undo a move | Kenney Casino Audio 1.1 `card-slide-2.ogg` | `e674a1679ec87b507047fe012d068ad2c412125f56186f0e732684705c2a6888` |
| `sound_shuffle.wav` | Beginning of a new game (~0.5 s card riffle) | BMacZero Playing Card Sounds `shuffle.wav`, trimmed | `dce8236feed0dedfa82dac5af2dc966328c9be2e38fd6b293d6554e3122bf719` |
| `sound_hint.ogg` | Hint request | SFXMint `ui-chime-15.ogg` | `67488b8f83e44a634c8778acd514f8fb1664f4ddf3fbdc3c8f6511c3af0da82e` |
| `sound_deal.ogg` | Card deal or row deal | Kenney Casino Audio 1.1 `card-shove-2.ogg` | `8e95c1d3ae94b5b91bc663432ad7c3bf4e03a541c22ec10fa51c65fe0e29054b` |
| `sound_chip.ogg` | Chip adjustment | Kenney Casino Audio 1.1 `chip-lay-2.ogg` | `8ca8b2d450741c04ed7b4ba51ed3f73c2400c8b3dbe83b974be4fe4774a76712` |
| `sound_card_draw.ogg` | Draw-card decision | Kenney Casino Audio 1.1 `card-place-3.ogg` | `1d988631a6e5201ed1bb0d728f2e43a3ec79401258426858793598044ac95841` |
| `sound_action_confirm.ogg` | Hold or decline decision | SFXMint `ui-click-08.ogg` | `952a4c6f0f6ee35558d3bc79a0c39c08dabba55a47b735e129bbc3a76891c546` |
| `sound_wager_commit.ogg` | Increased-wager decision | Kenney Casino Audio 1.1 `chips-stack-3.ogg` | `48cc0f01e2b62be7677f2e8c02177906cab80e96fa8176fa05b3ced35bc2a9c2` |
| `sound_card_split.ogg` | Divide cards into separate hands | Kenney Casino Audio 1.1 `card-fan-2.ogg` | `5743373c9dde00c6eee949a2c8e65d75946522768999167e1f5632451630e848` |
| `sound_sequence_complete.ogg` | Completed card sequence | Kenney Casino Audio 1.1 `card-fan-1.ogg` | `7a9758ccad8899baef1b9f60b4064e4baa724a548293832fdaea07e6e07e638d` |
| `sound_bust.ogg` | Blackjack hand bust | Kenney Casino Audio 1.1 `card-shove-4.ogg` | `0a1ad3a2f16bd2ed73349e6342426ca7a59a6e26b7e26e36d51d51c2c2349d6f` |
| `sound_natural_win.ogg` | Natural Blackjack celebration | Kenney Casino Audio 1.1 `chips-stack-5.ogg` | `42dd800e3e24bbd07d3ebfc86c44b6500d376641e30270dc363d95a73e90b56e` |
| `sound_round_loss.ogg` | Losing Blackjack round | SFXMint `feedback-fail-56.ogg` | `f8aac3ba217f71d1e8a37425c6c9d74225f46c8331af71a4091dc4ef6214d1c4` |
| `sound_bankroll_lost.ogg` | Blackjack bankroll below the minimum bet | Kenney Casino Audio 1.1 `chips-handle-5.ogg` | `589753ea21b1955626e82975be8979f42140f5b0645a8c33c2d1f7e33fba68cb` |
| `sound_invalid.ogg` | Restrained invalid-action tap | SFXMint `backlog-adapt-editorial-tap.ogg` | `b672ca2ee8b43c8883259448dc340995da216b208377f5de53cefe48b4197d0d` |
| `sound_win.ogg` | Shared game-win jingle | Fupi `winfretless.ogg` | `066d704a58af2bbd293cb9a32488b832be7803e365d3f15a264aa803c238c944` |

## Sources and licenses

- Kenney, [54 Casino sound effects](https://opengameart.org/content/54-casino-sound-effects-cards-dice-chips),
  downloaded 2026-10-06. The archive's `License.txt` dedicates the pack to the public domain under
  [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) and identifies Kenney Vleugels
  (Kenney.nl) as the author. The files above are unmodified Ogg Vorbis originals.
- Brian MacIntosh (BMacZero), [Playing Card Sounds](https://opengameart.org/content/playing-card-sounds),
  downloaded 2026-10-06 under CC0 1.0. The source `shuffle.wav` SHA-256 is
  `eedc3d3112c593f0a9bc3697dcb496e1b03ac5423b0f45f89e412f7b62818729`. The shipped cue is the
  highest-RMS 0.500-second window (starting at 0.040 seconds), with a 5 ms linear fade at each edge;
  it remains mono, 16-bit PCM at 44.1 kHz.
- Fupi, [Win Jingle](https://opengameart.org/content/win-jingle), downloaded 2026-10-06 under
  CC0 1.0. The shipped `winfretless.ogg` is the unmodified Ogg Vorbis original.
- SFXMint, [Restrained Short Wood Tap](https://sfxmint.com/sounds/backlog-adapt-editorial-tap),
  [Warm Notification Chime 15](https://sfxmint.com/sounds/ui-chime-15),
  [Wooden User Interface Click 08](https://sfxmint.com/sounds/ui-click-08), and
  [Sad Slow Failure Descending Tone 56](https://sfxmint.com/sounds/feedback-fail-56), downloaded
  2026-10-06. SFXMint's [license](https://sfxmint.com/license) dedicates every library sound to the
  public domain under CC0 1.0 and states that its library is produced with AI generation or
  procedural synthesis. The files above are unmodified Ogg Vorbis downloads.

Keep this manifest current whenever a cue is replaced. Preserve the exact downloaded bytes when
possible; if a file is processed, record the processing recipe and checksum of the shipped result.
