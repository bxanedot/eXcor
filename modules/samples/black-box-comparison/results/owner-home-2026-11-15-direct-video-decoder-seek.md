# Direct video decoder seek comparison

| Field | Value |
|---|---|
| UTC start | 2026-11-15T17:35:40Z |
| eXcor source | main `df65af48d96354b87f8976c198a9793460bc17d0` |
| Adapter source | `f96687f55e10cde1aa6a256198d8964260710db6` |
| Environment | Windows x64, one home network, signed out, JVM 25.0.4.1 |
| Content | Public music video `dQw4w9WgXcQ`, locale `en-US` |
| Adapters and decoder | eXcor 0.2.0, InnerTubeX 0.11.7, FFprobe `9.0.2-full_build-www.gyan.dev` |
| Command | Paired [JVM adapter commands](../README.md#direct-video-decoder-seeks) with `--decoder-seek-video --comparison-id owner-home-decoder-seek-repeat --phase cold --timeout-seconds 30`, sample indices 1–3 |

| Sample | eXcor first frame in each interval (seconds) | eXcor HTTP 206 reads | eXcor bytes relayed | InnerTubeX outcome |
|---:|---|---:|---:|---|
| 1 | 0.0 → 24.6 → 5.24 | 3 | 589,824 | `UNSUPPORTED/UNSUPPORTED_TRANSPORT` before media |
| 2 | 0.0 → 24.6 → 5.24 | 3 | 606,208 | `UNSUPPORTED/UNSUPPORTED_TRANSPORT` before media |
| 3 | 0.0 → 24.6 → 5.24 | 3 | 606,208 | `UNSUPPORTED/UNSUPPORTED_TRANSPORT` before media |

Each eXcor MP4 video run used one FFprobe session and read frames from the start, then after seeks to 30 and 10 seconds. The timestamps show the earlier keyframes FFprobe reached. Each run requested offsets `0 → 3064703 → 676582` and stayed below the 256 MiB relay cap. An earlier diagnostic build used a five-second allowance before the target and returned `seek_unverified`. A separate diagnostic confirmed the 24.6-second keyframe landed 5.4 seconds before the target. The run above used a six-second allowance.

The InnerTubeX adapter requested direct video with HLS and SABR disabled. Its outcome applies to that profile on this ID. This verifies repeatable eXcor decoder seeks on one video and network. Paired direct video decode and seek, interactive video controls, and synchronized audio/video seeking remain unverified. Issue [#78](https://github.com/bxanedot/eXcor/issues/78) remains open. G5 and G11 stay partial.
