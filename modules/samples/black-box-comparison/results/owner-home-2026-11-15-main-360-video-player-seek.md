# Exact-main video player-seek comparison attempts — 2026-11-15

## Scope

- eXcor main: `360c38cd77e344a801fc0fcf1a292bfcca9eea56`
- Network category: signed-out Windows home network; one public video, `dQw4w9WgXcQ`
- eXcor adapter from this main; InnerTubeX `0.11.7`; FFplay `9.0.2`
- Two cold attempts on one video and network, each with the 30-second limit and 256 MiB FFplay relay cap.
- Attempt 1: `owner-home-main-360-video-player-seek-2026-11-15-repeat`, timestamp `2026-11-15T05:35:12Z`, sample 2.
- Attempt 2: `owner-home-main-360-video-player-seek-2026-11-15-repeat2`, timestamp `2026-11-15T05:47:22Z`, sample 3.

Both adapters in each pair used the same arguments, except the comparison ID, timestamp and sample index:

```text
Attempt 1: --video-id dQw4w9WgXcQ --comparison-id owner-home-main-360-video-player-seek-2026-11-15-repeat --main-commit 360c38cd77e344a801fc0fcf1a292bfcca9eea56 --run-date-utc 2026-11-15T05:35:12Z --network-category home --content-category music_video --phase cold --sample-index 2 --timeout-seconds 30 --player-seek-video
Attempt 2: --video-id dQw4w9WgXcQ --comparison-id owner-home-main-360-video-player-seek-2026-11-15-repeat2 --main-commit 360c38cd77e344a801fc0fcf1a292bfcca9eea56 --run-date-utc 2026-11-15T05:47:22Z --network-category home --content-category music_video --phase cold --sample-index 3 --timeout-seconds 30 --player-seek-video
```

## Results

| Attempt | Adapter | Outcome | Range starts | Statuses | Bytes relayed |
|---|---|---|---|---|---:|
| 1 | eXcor | `time_limit` | `0, 2900, 2591189, 3061618, 448526` | HTTP 206 × 5 | 786,432 |
| 1 | InnerTubeX | `stream_resolution_failed` before FFplay opened | — | — | — |
| 2 | eXcor | `time_limit` | `0, 2900, 1322634, 2161318, 201100` | HTTP 206 × 5 | 786,432 |
| 2 | InnerTubeX | `stream_resolution_failed` before FFplay opened | — | — | — |

In both eXcor attempts, the FFplay window was selected and received Right then Left input. Each adapter run later reported five valid range responses and stayed below its byte cap. The output does not timestamp each range against the key input or report decoded frames, so these attempts do not prove which request followed each key or confirm rendered playback.

The InnerTubeX adapter stopped with `stream_resolution_failed` before opening FFplay in both attempts, so it did not receive seek input. This is two repetitions of one video on one network; it is not evidence of general InnerTubeX incompatibility or broad parity. There is no paired successful video-seek result, so G4, G5 and G11 remain partial and the release decision remains NO-GO.
