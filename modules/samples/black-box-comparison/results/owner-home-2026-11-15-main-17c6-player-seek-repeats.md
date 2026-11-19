# Owner-run paired audio player-seek repeats

| Field | Value |
|---|---|
| Run date | 2026-11-15 |
| eXcor main | `17c6febd14d53da67700656dd8daa2030df38796` |
| Code comparison | The first sample used main `b1d020c`; the intervening PR #200 changed documentation only, so the adapter source was unchanged |
| Environment | Signed-out Windows x64, owner's home network |
| Video | `dQw4w9WgXcQ` (direct `audio/webm`) |
| Player | FFplay `9.0.2-full_build-www.gyan.dev` |
| Adapters | eXcor 0.2.0; InnerTubeX 0.11.7 |
| Phase and timeout | Cold; 30 seconds per run |

These are samples 2 and 3 following the [first paired sample](owner-home-2026-11-15-main-b1-player-seek.md). Both adapters used identical metadata within each pair:

| Sample | Comparison ID | Run date metadata |
|---|---|---|
| 2 | `owner-home-shared-player-20261115-repeat` | `2026-11-15T11:28:27Z` |
| 3 | `owner-home-shared-player-20261115-repeat3` | `2026-11-15T11:30:34Z` |

I pressed Right, then Left in each active FFplay window. Both player waveforms visibly refreshed after each key. The next HTTP 206 range matched the forward and backward offsets in all four player runs:

| Sample | Adapter | Initial range | Right key → forward range | Left key → backward range | Result |
|---|---|---|---|---|---|
| 2 | eXcor | byte 0 at 11:28:31.406Z | 11:28:46.467Z → byte 497,307 at 11:28:46.818Z (+351 ms) | 11:28:51.921Z → byte 329,300 at 11:28:52.004Z (+83 ms) | 3/3 HTTP 206; 737,280 bytes |
| 2 | InnerTubeX | byte 0 at 11:29:04.199Z | 11:29:24.144Z → byte 497,307 at 11:29:24.280Z (+136 ms) | 11:29:28.102Z → byte 329,300 at 11:29:28.189Z (+87 ms) | 3/3 HTTP 206; 720,896 bytes |
| 3 | eXcor | byte 0 at 11:30:36.437Z | 11:30:52.592Z → byte 497,307 at 11:30:52.669Z (+77 ms) | 11:30:57.922Z → byte 329,300 at 11:30:57.972Z (+50 ms) | 3/3 HTTP 206; 753,664 bytes |
| 3 | InnerTubeX | byte 0 at 11:31:08.203Z | 11:31:28.372Z → byte 497,307 at 11:31:28.459Z (+87 ms) | 11:31:33.633Z → byte 329,300 at 11:31:33.697Z (+64 ms) | 3/3 HTTP 206; 737,280 bytes |

All four runs reported the expected `player_seek_result=time_limit` at the 30-second limit. The relay byte cap was not exceeded. Across all three samples, including sample 1, each adapter returned nine HTTP 206 responses with the sequence `0 → 497307 → 329300`; eXcor relayed 2,244,608 bytes and InnerTubeX relayed 2,211,840 bytes. Key-to-range times measure arrival of the next request, not audible seek latency.

This is repeatable direct-audio Right/Left seek evidence for one public ID on one network, not cross-ID or cross-network reliability. It does not verify video seeking, muxed playback, ciphered playback, or SABR/UMP. Issue #78 stays open; G4/G5/G11 remain partial, G6 blocked, and the release decision NO-GO.
