# Sound assets

The short cues in `core/ui/src/main/res/raw/` form one quiet, game-neutral library shared by
Klondike, Spider, FreeCell, and Blackjack. Physical actions use recorded card and chip Foley;
semantic feedback uses generated or procedural audio. The library uses CC0 1.0 assets plus one
locally generated voice announcement made with the Apache-2.0-licensed Kokoro model. The applicable
license and provenance of every source are recorded below.

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
| `sound_bust.ogg` | Blackjack hand bust | SFXMint `short-ui-error-05.ogg` | `63d48098ca55bf65fc530eac9fe3fe84cd820362273d6e6c732804d572307e54` |
| `sound_round_win.ogg` | Short Blackjack round-win cue | SFXMint `ready-success-05.ogg` | `2e01aa63835222884e12b1fa2511d5fbdc0ba49b459227ada1b3805869b52a69` |
| `sound_natural_win.ogg` | Natural Blackjack celebration | SFXMint `short-video-ding-04.ogg` | `15dee552cce494cc3fc9c137fd27c227bf3f2794207ae316cc21132ef7eb248b` |
| `sound_voice_announcement.wav` | Spoken natural-win announcement | Kokoro-82M `af_nicole`, speed `1.50` | `295ede53f144a45d3a4d779d152d1e89f285029980a5fc055afea5cdc0667698` |
| `sound_round_loss.ogg` | Losing Blackjack round | SFXMint `feedback-error-01.ogg` | `ff0cfe44740559b0851c50adbb2953a193cb65e5cabaf130aa8806c9575ddaa9` |
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
  [Soft Rewarding Success Chime 54](https://sfxmint.com/sounds/feedback-success-54), downloaded
  2026-10-06.
- SFXMint, [Short UI Error 05](https://sfxmint.com/sounds/short-ui-error-05) and
  [Descending Sad Error Buzz 01](https://sfxmint.com/sounds/feedback-error-01),
  [Success Cue 05](https://sfxmint.com/sounds/ready-success-05), and
  [Short Video Ding 04](https://sfxmint.com/sounds/short-video-ding-04), downloaded
  2026-10-07. SFXMint's [license](https://sfxmint.com/license) dedicates every library sound to the
  public domain under CC0 1.0 and states that its library is produced with AI generation or
  procedural synthesis. The SFXMint files above are unmodified Ogg Vorbis downloads.
- Hexgrad, [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M), downloaded and run locally on
  2026-10-07 under Apache License 2.0. `sound_voice_announcement.wav` was synthesized from
  `Blackjack!` with Kokoro 0.9.4, voice `af_nicole`, and speed `1.50`. Quiet model padding below
  amplitude 0.002 was trimmed with a 25 ms margin, then the peak was normalized to 0.89. The result
  is 0.636 seconds of mono, 16-bit PCM audio at 24 kHz. No hosted synthesis service was used.

Keep this manifest current whenever a cue is replaced. Preserve the exact downloaded bytes when
possible; if a file is processed, record the processing recipe and checksum of the shipped result.
