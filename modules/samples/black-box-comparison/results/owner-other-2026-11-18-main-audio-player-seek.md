# Current-main owner-run audio player seek

| Field | Value |
|---|---|
| eXcor main | `2cd94171959c9c0fcb58622396e9c0a7c61be7f7` |
| Run date | 2026-11-18 UTC |
| Environment | Signed-out Windows x64; one owner connection, network category `other` |
| Video / track | `dQw4w9WgXcQ`; direct `audio/webm` |
| Player | FFplay 9.0.2 |
| Comparison ID / sample | `owner-other-main-2026-11-18-audio-seek-repeat3`; cold sample 3; 30-second limit |

The implementation source tree matched merged `main`. I focused FFplay and sent Right, then Left. The player display refreshed after Right. A screenshot could not be captured after Left, but the adapter recorded the ordered initial, forward, and backward media requests:

| Request | Range start | UTC timestamp | HTTP status |
|---|---:|---|---:|
| Initial | 0 | 2026-11-18 17:44:12.336 | 206 |
| After Right | 666,109 | 2026-11-18 17:44:34.706 | 206 |
| After Left | 497,307 | 2026-11-18 17:44:41.625 | 206 |

The run relayed 720,896 bytes, did not reach the 256 MiB cap, and ended at the configured 30-second limit. The forward and backward range changes verify one current-main direct-audio player-seek pass. They do not measure audible seek latency.

This is one eXcor run on one video and one network. No InnerTubeX comparison, second network, interactive video seek, or eligible ciphered playback was verified. Issue [#78](https://github.com/lylicht/eXcor/issues/78) remains open; G4/G5 remain partial.
