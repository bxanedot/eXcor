# Paired direct-audio seek check — 2026-11-13

## Setup

- Same public video: `dQw4w9WgXcQ`.
- Same signed-out Windows home network; no cookies, browser tokens, or challenge bypass.
- eXcor used the current dirty worktree's `0.2.0` local Maven artifact and the quickstart's loopback-only media proxy with FFplay 9.0.2.
- InnerTubeX used the published [v0.11.6](https://github.com/MetrolistGroup/innertubex/releases/tag/v0.11.6) harness at exact tag commit [`64c7df4d88d27e6964157b3b341f7ec70f88ea6e`](https://github.com/MetrolistGroup/innertubex/tree/64c7df4d88d27e6964157b3b341f7ec70f88ea6e), with `--auth anonymous --tokens off`. The [v0.11.6 README documents the harness](https://github.com/MetrolistGroup/innertubex/blob/v0.11.6/README.md#standalone-playback-diagnostics). The run used `AUTO`, high audio quality, one repetition, and its default 500 ms PCM pacing.

## Results

The InnerTubeX harness completed one cold and one warm 90-second paced PCM attempt. Both attempts passed all stages:

| Phase | Stage | Requested position | Observed position | PCM bytes | Media bytes | Result |
|---|---|---:|---:|---:|---:|---|
| Cold | Initial playback | 0 s | 70.021 s | 1,120,000 | 1,572,864 | Pass; 71,998 ms elapsed |
| Cold | Backward seek | 50 s | 60 s | 160,000 | 1,048,576 | Pass; 10,209 ms elapsed |
| Cold | Forward seek | 90 s | 100 s | 160,000 | 1,048,576 | Pass; 10,181 ms elapsed |
| Warm | Initial playback | 0 s | 70.021 s | 1,120,000 | 1,572,864 | Pass; 70,722 ms elapsed |
| Warm | Backward seek | 50 s | 60 s | 160,000 | 1,048,576 | Pass; 10,189 ms elapsed |
| Warm | Forward seek | 90 s | 100 s | 160,000 | 1,048,576 | Pass; 10,196 ms elapsed |

The eXcor quickstart started FFplay at 30 seconds. Right then Left key presses changed the waveform and produced four upstream HTTP 206 responses; the loopback proxy recorded ordered range starts `0`, `329300`, `980764`, and `824084`. The player reached its configured 60-second session limit (`time_limit`), relayed 1,294,336 bytes, and did not exceed its byte cap.

## Limits

Both implementations demonstrated backward and forward direct-audio seeking on the same video and network. Their player harnesses differ: eXcor used FFplay with key-driven seeks; InnerTubeX v0.11.6 used its PCM transport harness with programmed stages. This is behavior evidence, not a same-harness timing comparison. Only one video and one network were checked; video seeking and repeatability across IDs/networks remain open. The eXcor worktree was dirty, so this owner-run diagnostic does not qualify it for public readiness credit.
