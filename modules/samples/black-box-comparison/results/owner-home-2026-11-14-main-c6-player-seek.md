# Owner home-network paired FFplay audio-seek check — 2026-11-14

- eXcor main: `c6faa41e8eb817210bbdf796bacca710be08653a` (`eXcor` 0.2.0; adapter 0.1.4).
- Comparator: InnerTubeX v0.11.6 (adapter 0.1.3).
- Video: `dQw4w9WgXcQ`; signed-out Windows x64; home network; `music_video`; locale `en-US`.
- Shared comparison metadata: `owner-home-shared-player-2026-11-14-c6-repeat`, `2026-11-14T14:56:31Z`, cold sample 3, 30-second limit.
- Both ran through the same installed FFplay 9.0.2 build. No cookies, account credentials, or token provider were supplied.

| Adapter | Key input | Range starts | HTTP results | Requests | Bytes relayed | End state |
|---|---|---|---|---:|---:|---|
| eXcor | Right, then Left | `0`, `497307`, `329300` | `206` × 3 | 3 | 720,896 | `time_limit`; 256 MiB cap not exceeded |
| InnerTubeX | Right, then Left | `0`, `497307`, `329300` | `206` × 3 | 3 | 720,896 | `time_limit`; 256 MiB cap not exceeded |

FFplay's audio waveform changed after both keys in each run. The adapter logs show the initial read followed by the same forward-offset and backward-offset requests for both extractors. `time_limit` is the harness's configured 30-second stop, not a failed HTTP response. Each adapter had one attempt on one audio ID and network; this does not test video seeking, ciphered playback, cross-network behavior, or broad reliability/performance parity.

Commands, with the same captured metadata:

```powershell
.\gradlew.bat -p modules/samples/black-box-comparison/excor-jvm run --args="--video-id dQw4w9WgXcQ --comparison-id owner-home-shared-player-2026-11-14-c6-repeat --main-commit c6faa41e8eb817210bbdf796bacca710be08653a --run-date-utc 2026-11-14T14:56:31Z --network-category home --content-category music_video --phase cold --sample-index 3 --timeout-seconds 30 --player-seek"
.\gradlew.bat -p modules/samples/black-box-comparison/innertubex-jvm run --args="--video-id dQw4w9WgXcQ --comparison-id owner-home-shared-player-2026-11-14-c6-repeat --main-commit c6faa41e8eb817210bbdf796bacca710be08653a --run-date-utc 2026-11-14T14:56:31Z --network-category home --content-category music_video --phase cold --sample-index 3 --timeout-seconds 30 --player-seek"
```
