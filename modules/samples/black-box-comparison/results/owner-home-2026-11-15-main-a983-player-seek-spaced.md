# Owner home-network paired FFplay audio-seek check — 2026-11-15

- eXcor main: `a983599f1256209a78a1d30140fdda23a521b328` (`eXcor` 0.2.0; adapter 0.1.4).
- Comparator: InnerTubeX v0.11.6 (adapter 0.1.3).
- Video: `dQw4w9WgXcQ`; signed-out Windows x64; home network; `music_video`; locale `en-US`.
- Shared comparison metadata: `owner-home-2026-11-15-main-a983-player-seek-spaced`, `2026-11-15T01:49:24Z`, cold sample 1, 30-second limit.
- Both ran through the same FFplay 9.0.2 build. Each received Right, a 1.5-second pause, then Left. No cookies, account credentials, or token provider were supplied.

| Adapter | Range starts (initial, forward, backward) | HTTP results | Requests | Bytes relayed | End state |
|---|---|---|---:|---:|---|
| eXcor | `0`, `666109`, `497307` | `206` × 3 | 3 | 770,048 | `time_limit`; 256 MiB cap not exceeded |
| InnerTubeX | `0`, `497307`, `329300` | `206` × 3 | 3 | 753,664 | `time_limit`; 256 MiB cap not exceeded |

Both adapters produced a distinct forward and backward HTTP range after the matching key sequence. `time_limit` is the harness's configured 30-second stop, not a failed HTTP response. Each adapter had one attempt on one audio ID and network; this does not verify video seeking, ciphered playback, cross-network behavior, or broad reliability/performance parity.

Commands, with the same captured metadata:

```powershell
& '.\modules\samples\black-box-comparison\excor-jvm\build\install\excor-black-box-adapter\bin\excor-black-box-adapter.bat' --video-id dQw4w9WgXcQ --comparison-id owner-home-2026-11-15-main-a983-player-seek-spaced --main-commit a983599f1256209a78a1d30140fdda23a521b328 --run-date-utc 2026-11-15T01:49:24Z --network-category home --content-category music_video --phase cold --sample-index 1 --timeout-seconds 30 --player-seek
& '.\modules\samples\black-box-comparison\innertubex-jvm\build\install\innertubex-black-box-adapter\bin\innertubex-black-box-adapter.bat' --video-id dQw4w9WgXcQ --comparison-id owner-home-2026-11-15-main-a983-player-seek-spaced --main-commit a983599f1256209a78a1d30140fdda23a521b328 --run-date-utc 2026-11-15T01:49:24Z --network-category home --content-category music_video --phase cold --sample-index 1 --timeout-seconds 30 --player-seek
```
