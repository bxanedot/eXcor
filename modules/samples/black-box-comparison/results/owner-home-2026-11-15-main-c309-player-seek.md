# Owner home-network paired FFplay audio-seek check — 2026-11-15

- eXcor source: main `c3091d31920bd6769402322fb2ee83d521fda3cc`, version `0.2.0`.
- Comparator: InnerTubeX `0.11.7`.
- Video: `dQw4w9WgXcQ`; signed-out Windows x64; one home network; `music_video`; locale `en-US`.
- Track and player: direct audio in the same installed FFplay `9.0.2` build; cold sample 1; 30-second limit; 256 MiB relay cap.
- The runs were back-to-back and used the same video, host, category, timeout and player build. Their comparison IDs were adapter-specific (`owner-home-2026-11-15-excor-seek-confirm` and `owner-home-2026-11-15-innertubex-seek-confirm`), so this is a manual paired check rather than a single shared-metadata batch.

| Adapter | Key input | Range starts | Range times (UTC) | HTTP results | Requests | Bytes relayed | End state |
|---|---|---|---|---|---:|---:|---|
| eXcor | Right observed; Left attempted | `0`, `666109`, `497307` | `09:59:21.516`, `09:59:44.446`, `09:59:50.428` | `206` × 3 | 3 | 753,664 | `time_limit`; cap not exceeded; FFplay closed before final UI refresh |
| InnerTubeX | Right, then Left | `0`, `497307`, `329300` | `10:00:34.209`, `10:00:48.512`, `10:00:53.860` | `206` × 3 | 3 | 720,896 | `time_limit`; cap not exceeded; post-Left waveform captured |

Each relay recorded an initial read followed by two different offsets. The InnerTubeX waveform visibly changed after both key presses, and its final offset was lower than the preceding one. For eXcor, the waveform visibly changed after Right; the final offset was lower than the preceding one after the Left attempt, but the player closed before a final screenshot could confirm that input. The adapter does not timestamp key events separately, so the eXcor backward-seek interpretation is indicative rather than confirmed per key; neither trace establishes per-key latency or a one-to-one causal mapping from each key to each range.

This is one attempt per adapter on one audio ID and one network. It does not measure audible output quality, ciphered formats, direct-video seeking, muxed audio/video synchronization, SABR/UMP, cross-network behavior, or broad reliability/performance parity. G4, G5 and G11 remain partial; G6 remains blocked.
