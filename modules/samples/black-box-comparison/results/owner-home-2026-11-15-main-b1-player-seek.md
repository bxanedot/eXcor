# Owner-run paired audio player-seek check

| Field | Value |
|---|---|
| Run date | 2026-11-15 |
| eXcor main | `b1d020cbe2eeea9be31687ace29da3ddefb5c2f7` |
| Comparison ID | `owner-home-shared-player-20261115` |
| Run date metadata | `2026-11-15T11:09:19Z` |
| Environment | Signed-out Windows x64, owner's home network |
| Video | `dQw4w9WgXcQ` (direct audio, `audio/webm`) |
| Player | FFplay `9.0.2-full_build-www.gyan.dev` |
| Adapters | eXcor 0.2.0; InnerTubeX 0.11.7 |
| Sample | One cold run per adapter; 30-second maximum |

Each adapter opened the audio in FFplay. I pressed Right and then Left while the player was active. The waveform visibly refreshed after each key in both windows. The sanitized relay logs confirm the corresponding forward then backward media reads:

| Adapter | Initial range | Right key → next HTTP 206 range | Left key → next HTTP 206 range | Result |
|---|---|---|---|---|
| eXcor | byte 0 at 11:09:20.539Z | 11:09:36.328Z → byte 497,307 at 11:09:36.401Z (+73 ms) | 11:09:39.191Z → byte 329,300 at 11:09:39.268Z (+77 ms) | 3/3 HTTP 206; 753,664 bytes relayed |
| InnerTubeX | byte 0 at 11:09:52.662Z | 11:10:04.850Z → byte 497,307 at 11:10:04.924Z (+74 ms) | 11:10:09.213Z → byte 329,300 at 11:10:09.284Z (+71 ms) | 3/3 HTTP 206; 753,664 bytes relayed |

Both adapters ended with `player_seek_result=time_limit`, the expected result at the configured 30-second cap. Neither hit the 256 MiB relay byte limit. The key-to-range intervals measure when the next range request arrived; they do not measure audible seek latency. The adapters kept signed media URLs inside their processes, and none were recorded here.

This confirms forward and backward seeking for one direct-audio stream per adapter on one signed-out Windows home network. It does not establish repeatability across IDs or networks, video seeking, muxed playback, ciphered playback, or SABR/UMP compatibility. Issue #78 remains open; G4, G5 and G11 remain partial, G6 remains blocked, and the release decision remains NO-GO.
