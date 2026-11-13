# eXcor

[![eXcor evidence readiness](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fbxanedot%2FeXcor%2Fautomation%2Fexcor-competitiveness%2Fscore%2Fbadge.json)](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md)

**Live readiness report:** [current score, missing evidence, release gates and CI state](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md) · [scoring method](docs/COMPETITIVENESS_CHECKER.md) · [automated report runs](https://github.com/bxanedot/eXcor/actions/workflows/competitiveness-score.yml). The score is evidence-weighted engineering readiness. It is not a playback success rate or a measured head-to-head score against InnerTubeX.

**eXcor** is a [GPL-3.0-only](LICENSE) Kotlin Multiplatform library for resolving YouTube audio streams and, experimentally, HLS manifests from video IDs. This clean-room implementation is a draft SDK, not a production-ready release.

## eXcor compared with InnerTubeX

**Paired direct-audio seek check (2026-11-13):** on `dQw4w9WgXcQ`, eXcor's FFplay session recorded forward and backward key-driven ranges, and InnerTubeX v0.11.6's PCM harness passed backward and forward transport stages in both cold and warm runs at exact tag commit `64c7df4d88d27e6964157b3b341f7ec70f88ea6e`. Both implementations used the same signed-out workstation network. The harnesses differ, so this is seek-behavior evidence, not a same-harness timing comparison. See the [run report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-paired-player-seek.md).

**Earlier post-profile-order stable-video rerun (2026-11-13; before the lazy-watch follow-up):** on `aqz-KE-bpKQ`, InnerTubeX passed full decode and both HTTP byte-range probes in 3/3 cold samples; eXcor was classified `ACCESS_DENIED` before media in 3/3. This was an access differential, not an eXcor decoder failure. See the [normalized report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-post-profile-aqz.md) and [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-13-post-profile-aqz.jsonl); this one-network dirty-worktree evidence is excluded from public readiness claims.

**Evidence reviewed: 2026-11-13.** The latest published InnerTubeX release is [v0.11.6](https://github.com/MetrolistGroup/innertubex/releases/tag/v0.11.6), released November 7. Its [README](https://github.com/MetrolistGroup/innertubex) documents Kotlin Multiplatform targets, standard InnerTube operations and an extraction stack with direct, HLS and SABR/UMP transports. The README's JitPack example still pins v0.11.5; this comparison does not assume that v0.11.6 resolves through JitPack. eXcor's evidence is linked to its [feature matrix](docs/FEATURE_MATRIX.md), [competitor matrix](docs/COMPETITOR_MATRIX.md) and [release gates](docs/release-gates.json).

**Latest exact-main matched direct-audio run (2026-11-13; eXcor main `4e5e67d`):** 24 signed-out cold/warm samples across two music videos on one Windows home network. On `dQw4w9WgXcQ`, both adapters passed full decode and forward/backward HTTP byte-range probes in all six attempts; first-media p50/p95 was 379/406 ms and 118/139 ms for eXcor (cold/warm), versus 1,524/1,645 ms and 185/189 ms for InnerTubeX. On `kJQP7kiw5Fk`, eXcor was `ACCESS_DENIED` before media in all six attempts, while InnerTubeX passed full decode and both ranges in all six. This is direct-audio diagnostic evidence from one network, not a general reliability or performance ranking; extraction paths differ, InnerTubeX request counts are incomplete, and HTTP ranges are not player seeks. No video-seek, HLS, SABR/UMP, ciphered-playback or signed-in result is implied. See the per-video [report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-main-4e5e67d.md), [JSON summary](modules/samples/black-box-comparison/results/owner-home-2026-11-13-main-4e5e67d-report.json) and [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-13-main-4e5e67d.jsonl).

**Earlier second shared-video follow-up (2026-11-13; dirty worktree):** on signed-out `aqz-KE-bpKQ`, eXcor passed full 10,332,653-byte decode and both HTTP byte-range probes in 1/3 cold attempts; its other two attempts were blocked before media. InnerTubeX passed the same decode and probes in 3/3. This is one shared success with inconsistent eXcor access, not 3/3 repeatability. See the [report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-lazy-watch-aqz.md), [JSON summary](modules/samples/black-box-comparison/results/owner-home-2026-11-13-lazy-watch-aqz-report.json), and [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-13-lazy-watch-aqz.jsonl).

| Area | eXcor | InnerTubeX | What the evidence says |
|---|---|---|---|
| Metadata and music catalogs | YouTube and YouTube Music metadata, search, channels, playlists, comments, chapters and radio. Signed-out public search continuation and channel details/uploads passed live JVM checks on the owner's Windows workstation on 2026-11-13; artist album-discography pagination was [live-verified](modules/cleanroom/jvmTest/kotlin/com/excor/LiveVideoInfoTest.kt) the same day. Spotify, Apple Music and Deezer metadata and matching. | README documents search, browse, account, playlist and player operations, plus channel discovery and music uploads. | These live checks confirm eXcor's flows on one workstation/network; they are not paired InnerTubeX measurements and do not establish that InnerTubeX lacks features absent from its README. |
| Direct audio and video | On 2026-11-13, eXcor and the pinned InnerTubeX adapter both fully decoded the same 3,507,483-byte Opus stream in 3/3 cold attempts and passed forward/backward HTTP byte-range probes in 3/3. | README documents direct extraction and bounded media probing. InnerTubeX v0.11.6 was tested through adapter 0.1.3. | In an earlier matched run, cold first-media medians were close (1,146 ms for eXcor; 1,169 ms for InnerTubeX), while eXcor's median decode was 58 ms faster (307 ms vs 365 ms). In that earlier warm run, InnerTubeX reached first media sooner (135 ms vs 869 ms), while eXcor decoded 50 ms faster (315 ms vs 365 ms). Earlier runs on four additional IDs showed InnerTubeX full decode and both HTTP byte-range probes in 3/3 while eXcor was ACCESS_DENIED before media in 3/3; a later rerun of `aqz-KE-bpKQ` passed for eXcor in 1/3 and for InnerTubeX in 3/3. These are access/repeatability outcomes, not evidence of decoder failure. On `M7lc1UVf-VE`, eXcor was blocked before media in 3/3 while InnerTubeX passed both HTTP byte-range probes but timed out during full decode in 3/3 after reading 73,728 bytes per sample. On `aqz-KE-bpKQ`, InnerTubeX decoded 10,332,653 bytes (earlier first-media p50/p95 1,243/1,319 ms; decode p50/p95 1,206/1,279 ms); eXcor's later shared PASS decoded the same amount. On music-video ID `kJQP7kiw5Fk`, InnerTubeX decoded 4,678,808 bytes (first-media p50/p95 1,377/1,437 ms; decode p50/p95 466/495 ms); eXcor was `ACCESS_DENIED` before media in 3/3. |
| HLS, DASH and SABR/UMP | HLS is experimental. Android and JVM attempts on `dQw4w9WgXcQ` returned `UNPLAYABLE` before a manifest; two more JVM attempts hit bot checks, including on a public NASA ISS live stream. FFmpeg never started, and no InnerTubeX HLS run was possible. DASH-manifest and SABR/UMP playback are not implemented. See the [sanitized JVM attempts](modules/samples/black-box-comparison/results/owner-home-2026-11-13-jvm-hls-attempt.md). | README documents HLS and seekable SABR/UMP audio and video, including video representation selection. | **Largest parity gap:** eXcor has no SABR/UMP transport and no successful live HLS playback. InnerTubeX's capabilities here are upstream documentation claims, not an independent benchmark. |
| Player cipher | Signature and `n` solving are implemented; successful transformed-media decode and seek remain unverified. | README describes Kotlin cipher handling via QuackJS. The latest v0.11.6 release fixes iOS cipher work on large-stack threads. | A corrected signed-out recheck parsed four playable pages and found no `signatureCipher` or direct-URL `n` challenge, so it skipped before fetching player JavaScript or media. The forced-TV playback check was separately refused before extraction. See the [recheck](modules/samples/black-box-comparison/results/owner-home-2026-11-13-cipher-recheck.md) and earlier [TVHTML5 attempt](modules/samples/black-box-comparison/results/owner-home-2026-11-13-tvhtml5-only.md). |
| Reliability and security | URL allowlisting, bounded responses, host-cookie isolation on JVM/Android, Android Keystore-encrypted preview cookies, session-only desktop preview cookies, session-generation invalidation and diagnostic redaction are implemented; Android cookie migration, tamper and sign-out checks pass on API 26 and 36. One live signed-out extraction recovered after a locally injected 503 and passed full decode plus a midpoint range read; live upstream fault coverage and the full release-security audit remain partial. | README documents bounded bodies and protocol state, strict media URL validation and isolated sessions; the [changelog](https://github.com/MetrolistGroup/innertubex/blob/main/CHANGELOG.md) records additional timeout and redirect hardening. | The designs overlap, but there is no common independent stress-test result. |
| JVM compatibility | Java 17 bytecode, built with a JDK 21 toolchain; both comparison adapters completed a paired live full-decode/range run on Temurin JDK 17.0.20.1. | v0.11.6 targets JVM 17 bytecode and uses a JDK 21 toolchain in its [build](https://github.com/MetrolistGroup/innertubex/blob/v0.11.6/build.gradle.kts). | Both adapters ran under JDK 17 on one signed-out video/network; see the [12-sample report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-jdk17-direct-audio.md). This does not establish broad runtime or playback parity. |
| Distribution, adoption and license | GPL-3.0-only; `com.excor:eXcor:0.2.0` currently installs from `mavenLocal`; no remote release or known external consumers. | GPL-3.0; the README provides a JitPack dependency example and lists 18 projects using or adapting InnerTubeX (non-exhaustive). | License parity is documented; InnerTubeX leads on availability and adoption. eXcor still needs a verified public release and users. |

**Earlier profile-order cold/warm audio check (2026-11-13; owner-run diagnostic):** one signed-out public video (`dQw4w9WgXcQ`), Windows home network, three cold and three warm samples per adapter, 30-second sample budget, and FFmpeg 9.0.2. This run follows the signed-out profile-order update described below; its latency figures are superseded by the latest adapter recheck above.

| Adapter | Version | Full transfer + decode | Cold first media p50 / p95 | Cold decode p50 / p95 | Warm first media p50 / p95 | Warm decode p50 / p95 | Forward/backward HTTP byte ranges |
|---|---|---|---:|---:|---:|---:|---|
| eXcor | `0.1.4-excor0.2.0` | 6/6; 3,507,483 bytes each | 1,146 / 1,265 ms | 307 / 312 ms | 869 / 979 ms | 315 / 360 ms | 3/3 in each phase |
| InnerTubeX | `0.1.3-innertubex0.11.6` | 6/6; same 3,507,483 bytes | 1,169 / 1,211 ms | 365 / 370 ms | 135 / 143 ms | 365 / 374 ms | 3/3 in each phase |

Both adapters selected Opus and passed full FFmpeg decode plus forward/backward HTTP byte-offset checks in every cold and warm sample. Cold first-media p50 was close (1,146 ms for eXcor; 1,169 ms for InnerTubeX), and eXcor decoded about 58 ms faster at the median. In the separate warm run, InnerTubeX reached the first media byte sooner (135 ms vs 869 ms), while eXcor's median decode was about 50 ms faster (315 ms vs 365 ms). Each warm attempt includes a same-instance adapter warm-up before its measured attempt. The earlier cold result of 5,130 ms for eXcor first media predates the VISIONOS-first update and is retained as historical evidence. Three samples per phase on one video/network do not establish a broad performance ranking or a causal speedup. JVM startup is not measured; total `extractMs` and request counts are omitted because adapter instrumentation boundaries differ. HTTP byte ranges do not test player seeking.

The paired [cold report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-visionos-first.md) and [warm report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-warm-full.md), with their respective [cold](modules/samples/black-box-comparison/results/owner-home-2026-11-13-visionos-first.jsonl) and [warm](modules/samples/black-box-comparison/results/owner-home-2026-11-13-warm-full.jsonl) sanitized samples, contain the paired results. Both runs used the same video, network, adapter versions and base `main` commit, but came from a dirty local worktree; this evidence is excluded from the public readiness score and release claims. The [earlier three-sample baseline](modules/samples/black-box-comparison/results/owner-home-2026-11-13-adapter-fix-full.md) predates the profile-order change.

**Fresh matched cold recheck (2026-11-13 09:18 UTC):** both signed-out adapters again fully decoded the same 3,507,483-byte Opus stream and passed forward/backward HTTP byte-range probes in all three samples. First-media p50/p95 was 1,438/1,530 ms for eXcor and 1,358/1,535 ms for InnerTubeX; decode p50/p95 was 332/381 ms and 378/388 ms. In this run, InnerTubeX reached media about 79 ms sooner at the median, while eXcor decoded about 46 ms faster. This is one video and one Windows home network; byte-range probes are not player seeks. It came from a dirty local worktree and is excluded from the public readiness score and release claims. See the [normalized report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-autonomy-followup.md), [JSON summary](modules/samples/black-box-comparison/results/owner-home-2026-11-13-autonomy-followup-report.json), and [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-13-autonomy-followup.jsonl).

**Signed-out Android preview check (2026-11-13; separate from the paired benchmark):** on `dQw4w9WgXcQ`, the Android 8.0/API 26 and Android 16/API 36 previews selected 720p H.264 video with `audio/mp4` at 130 kbps from VISIONOS and rendered changing video frames. The API 36 run also logged two HTTP 206 playback-probe responses. This is one eXcor-only ID on one workstation network across two emulator API levels; no InnerTubeX Android app comparison, full-duration decode, or player-seek measurement was made. See the [sanitized run report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-android-preview-playback.md).

**Experimental HLS attempt (2026-11-13; Android 16/API 36):** on the same signed-out ID, **Try HLS** returned `UNPLAYABLE` with “The page needs to be reloaded” before a manifest was obtained. Media3 did not start. This adds no HLS playback evidence; see the [sanitized attempt report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-android-hls-attempt.md).

**Second-ID differential check (2026-11-13):** on public video `9bZkp7q19f0`, three cold InnerTubeX 0.1.3 attempts each passed full decode of 4,064,225 bytes and both HTTP byte-offset probes. eXcor 0.1.4 was classified `BLOCKED/ACCESS_DENIED` before media access in all three attempts. This is not a matched playback pass or evidence that eXcor's decoder failed; no access challenge was bypassed or retried. See the [normalized report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-second-video.md) and [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-13-second-video.jsonl).

**Third-ID differential check (2026-11-13):** on `CevxZvSJLk8`, InnerTubeX passed full 4,247,110-byte decode and both HTTP byte-offset probes in all three cold samples; eXcor was access-blocked before media in all three. This adds a second InnerTubeX-only result, not a second shared media pass. See the [normalized report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-third-video.md) and [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-13-third-video.jsonl). The four IDs were tested on one network from an uncommitted worktree, so the results remain diagnostic and excluded from the public score.

**Earlier fourth-ID differential check (2026-11-13; before the lazy-watch follow-up):** on stable public video `aqz-KE-bpKQ`, InnerTubeX passed full 10,332,653-byte Opus decode and forward/backward HTTP byte-offset probes in all three cold samples. First-media p50/p95 was 1,243/1,319 ms; decode p50/p95 was 1,206/1,279 ms. eXcor was classified `BLOCKED/ACCESS_DENIED` before media in all three samples and made zero media requests. See the [normalized report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-bbb-audio.md) and [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-13-bbb-audio.jsonl). This was an earlier access differential; the later 1/3 eXcor result is reported above. These one-network diagnostics came from a dirty worktree and are excluded from the public readiness score.

**Fifth-ID differential check (2026-11-13):** on public music video `kJQP7kiw5Fk`, InnerTubeX adapter 0.1.3 passed full 4,678,808-byte Opus decode and both HTTP byte-range probes in all three cold samples. First-media p50/p95 was 1,377/1,437 ms; decode p50/p95 was 466/495 ms. eXcor 0.1.4 returned `BLOCKED/ACCESS_DENIED` before media in all three samples and made zero media requests. This is another access differential, not a decoder or performance comparison. See the [normalized report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-fifth-video.md), [JSON summary](modules/samples/black-box-comparison/results/owner-home-2026-11-13-fifth-video-report.json), and [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-13-fifth-video.jsonl). All six IDs remain one-network diagnostics from a dirty worktree and are excluded from the public readiness score.

**Sixth-ID attempt (2026-11-13):** on stable public ID `M7lc1UVf-VE`, eXcor was `BLOCKED/ACCESS_DENIED` before media in all three cold samples. InnerTubeX adapter 0.1.3 passed both byte-range probes but timed out during full decode after 73,728 bytes in each sample. This adds no shared full-decode pass. See the [normalized report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-sixth-video.md), [JSON summary](modules/samples/black-box-comparison/results/owner-home-2026-11-13-sixth-video-report.json), and [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-13-sixth-video.jsonl).

**Java 25 runtime recheck (2026-11-13):** eXcor 0.1.4 and InnerTubeX adapter 0.1.3 each passed full decode of the same 3,507,483-byte Opus stream and forward/backward byte-range probes in 3/3 cold attempts on `dQw4w9WgXcQ`. Both used Java 17 bytecode; the launchers ran with the local Java 25 runtime. First-media p50/p95 was 1,131/1,348 ms for eXcor and 1,392/1,757 ms for InnerTubeX; decode p50/p95 was 319/363 ms and 369/369 ms. This confirms one ID/network on Java 25 and does not establish broad performance parity. The separate Temurin JDK 17.0.20.1 run below verifies that runtime too. The run came from a dirty local worktree and is excluded from the public readiness score. See the [normalized report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-java17-runtime-recheck.md), [JSON summary](modules/samples/black-box-comparison/results/owner-home-2026-11-13-java17-runtime-recheck-report.json), and [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-13-java17-runtime-recheck.jsonl).

**Temurin JDK 17.0.20.1 matched runtime check (2026-11-13):** eXcor and InnerTubeX each passed full 3,507,483-byte Opus decode and both forward/backward HTTP byte-range probes in 3/3 cold and 3/3 warm attempts on `dQw4w9WgXcQ`. Cold first-media p50/p95 was 460/528 ms for eXcor and 1,359/1,447 ms for InnerTubeX; warm was 125/139 ms and 185/193 ms. Cold decode p50/p95 was 383/426 ms and 428/459 ms; warm was 386/451 ms and 452/502 ms. This verifies both adapters on JDK 17 for one signed-out video/network; HTTP ranges are not player seeks, and the dirty-worktree run is excluded from public readiness scoring. See the [normalized report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-jdk17-direct-audio.md), [JSON summary](modules/samples/black-box-comparison/results/owner-home-2026-11-13-jdk17-direct-audio-report.json), and [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-13-jdk17-direct-audio.jsonl).

**JDK 17 second-ID repeat (2026-11-13):** On `aqz-KE-bpKQ`, eXcor was classified `ACCESS_DENIED` before media in 3/3 cold attempts; InnerTubeX decoded all 10,332,653 bytes and passed both HTTP byte-range probes in 3/3. A prior JDK 25 run on this ID had one eXcor full-decode/range pass in 3 attempts, so this adds no shared media pass and shows inconsistent access across runs. See the [normalized report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-jdk17-aqz-repeat.md), [JSON summary](modules/samples/black-box-comparison/results/owner-home-2026-11-13-jdk17-aqz-repeat-report.json), and [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-13-jdk17-aqz-repeat.jsonl).

**Earlier Cobalt-profile matched recheck (2026-11-13; before the profile-order update):** one cold sample per adapter on the same signed-out video and Windows home network. Both fully decoded the same 3,507,483-byte Opus stream and passed both HTTP byte-range probes.

| Adapter | First media byte | Full decode | Forward/backward HTTP byte ranges |
|---|---:|---:|---|
| eXcor `0.1.4-excor0.2.0` | 4,724 ms | 328 ms | PASS / PASS |
| InnerTubeX `0.1.3-innertubex0.11.6` | 1,100 ms | 376 ms | PASS / PASS |

These are single-run timings, not a performance ranking. In a companion eXcor live test, the updated Cobalt `TVHTML5` profile returned `UNPLAYABLE`, then VISIONOS supplied 27 usable formats and a 3,433,755-byte WebM/Opus stream; full FFmpeg decode and a midpoint HTTP 206 range read passed. That result motivated trying VISIONOS first for signed-out playback and loading the player script only when a scripted fallback or URL transform requires it. A one-off check of `9bZkp7q19f0` was skipped after an access challenge. See the [earlier matched report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-cobalt-profile-paired.md), [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-13-cobalt-profile-paired.jsonl), and [eXcor playback record](modules/samples/black-box-comparison/results/owner-home-2026-11-13-cobalt-profile-recheck.md). These owner-run diagnostics came from a dirty worktree and are excluded from the public readiness score; HTTP ranges do not establish player seeking. Two separate ffplay runs show actual eXcor forward/backward audio seeking on this ID ([first](modules/samples/black-box-comparison/results/owner-home-2026-11-13-player-seek.md), [repeat](modules/samples/black-box-comparison/results/owner-home-2026-11-13-player-seek-repeat.md)); player seeking was not part of the InnerTubeX comparison.

**Profile-order follow-up (2026-11-13):** signed-out extraction now tries VISIONOS before scripted clients and defers the player-script download until a scripted fallback needs it. The same public video passed full audio decode and midpoint HTTP range validation twice on the owner network: both runs decoded 3,433,755 bytes and read byte 1,716,877 with HTTP 206. A fresh 480p H.264/AAC run also fully decoded the 213.089-second mux and passed the midpoint video range read ([run record](modules/samples/black-box-comparison/results/owner-home-2026-11-13-visionos-first-live.md)). The matched three-sample audio run reached first media in p50 1,146 ms for eXcor and 1,169 ms for InnerTubeX; the new video check is eXcor-only and does not add a player-seek or multi-network comparison. The JVM suite and iOS arm64 compilation pass locally. This local change has not been released.

**Second-ID follow-up (2026-11-12; before the adapter fix):**
Three cold attempts per adapter covered dQw4w9WgXcQ and jNQXAC9IVRw. eXcor passed full decode and both HTTP offsets 3/3 on the first ID and was explicitly access-blocked on the second. InnerTubeX adapter 0.1.2 failed before requests on both IDs; those failures predate its JSON serialization fix in 0.1.3. See the [pre-fix report](modules/samples/black-box-comparison/results/owner-home-2026-11-12-classifier-recheck.md) and [sanitized samples](modules/samples/black-box-comparison/results/owner-home-2026-11-12-classifier-recheck.jsonl).

Separate owner-run playback checks that day also decoded 3,433,755 bytes of audio over 213.061 seconds and read a midpoint byte range (HTTP 206), then muxed and fully decoded a 480p H.264/AAC video for 213.089 seconds with a passing midpoint video range request ([test](modules/cleanroom/jvmTest/kotlin/com/excor/LivePlaybackTest.kt)). These are eXcor-only checks on one public video and did not include a player; two separate ffplay audio runs now show backward/forward seeking on the same ID and network, while broader repeatability remains unverified.

**Player-seek recheck (2026-11-13):** two signed-out quickstart attempts asked ffplay to start at 90 seconds for `dQw4w9WgXcQ`. Both returned `UNPLAYABLE` before stream extraction, so ffplay did not start and no seek or range result was collected. This does not change the matched comparison above.

**Follow-up (2026-11-13):** a fresh repeat on `dQw4w9WgXcQ` and checks of `M7lc1UVf-VE` and `kJQP7kiw5Fk` also returned `UNPLAYABLE` before extraction. Two additional five-second player attempts starting at 90 seconds (`aqz-KE-bpKQ` and `FGBhQbmPwH8`) returned the same status before a player or media request started. These signed-out access results add no decode/seek evidence and do not change the earlier one-video matched baseline.

**Owner-run player seek (2026-11-13):** after the quickstart was changed to continue extraction when `videoInfo` reported `UNPLAYABLE`, ffplay played signed-out VISIONOS WebM audio from 30 seconds. Two runs on the same video and home network sent Right then Left and each recorded HTTP 206 range starts `0`, `329300`, `980764`, and `824084`; the waveform refreshed after the keys in the repeat run. The first and repeat runs relayed 966,656 and 983,040 bytes, respectively, and each ended at its 45-second limit. Those two earlier runs were eXcor-only; the separate paired check now records InnerTubeX v0.11.6 backward/forward transport seeks on the same ID/network. The harnesses differ and video seeks plus broader ID/network repeatability remain open; see the [paired report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-paired-player-seek.md). See the [first run](modules/samples/black-box-comparison/results/owner-home-2026-11-13-player-seek.md) and [repeat run](modules/samples/black-box-comparison/results/owner-home-2026-11-13-player-seek-repeat.md).

**Adapter diagnosis (2026-11-13):** the earlier `FAIL/OTHER` was caused by missing Ktor JSON `ContentNegotiation` on the standalone InnerTubeX adapter's API client, before an HTTP request was sent. Adapter 0.1.3 adds JSON serialization and a loopback regression test; the new paired samples above pass. No InnerTubeX implementation code was inspected.

**Current assessment:** eXcor has metadata breadth, direct-playback evidence, and one same-ID/network audio-seek comparison: eXcor used FFplay, while InnerTubeX used the v0.11.6 PCM harness at exact tag commit `64c7df4d88d27e6964157b3b341f7ec70f88ea6e`. Both passed backward and forward audio seeking, but their harnesses differ, so the result does not compare performance. Multi-ID/network playback, video seeks, same-harness performance, SABR/UMP, successful live HLS, ciphered-stream playback and a public release remain open. The live readiness report tracks eXcor's evidence against the [100-point rubric](docs/competitiveness-evidence.json); its score is not a percentage of InnerTubeX features or an apples-to-apples performance result.

The current library exposes package `com.excor`, Gradle coordinates `com.excor:eXcor:0.2.0`, and an iOS XCFramework named `eXcor`. The active implementation is built from `modules/cleanroom/`; the older, uncompiled source is preserved under `modules/legacy/`.

## Current scope

The KMP library provides audio and video format selection, player-response and player-script handling, URL validation, host-supplied session and PO-token interfaces, remembered stream headers, refusal tracking, and player-data caching. Direct media URLs must pass the HTTPS YouTube/GoogleVideo `/videoplayback` allowlist. Experimental HLS extraction validates a GoogleVideo master-playlist URL, its bounded response and redirects, then returns it to a host media player; that player fetches child playlists and segments. Signed-out Android and JVM attempts received no manifest, so HLS playback has not been verified; see the [sanitized HLS report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-jvm-hls-attempt.md). eXcor does not mint PO tokens.

`Excor.music` puts Spotify, Apple Music and Deezer metadata behind one API, along with cross-provider matching, incremental playlist and library sync, audio-variant and DRM reporting, and authorized-playback decisions. Deezer and Apple's keyless endpoints are verified live; Spotify and the Apple Music API are tested offline only. See [music providers](docs/MUSIC_PROVIDERS.md) and [limitations](docs/MUSIC_LIMITATIONS.md). YouTube Music search, artists, playlists and radio are available through `Excor` (see below); `Excor.music` itself does not cover YouTube Music. The separate `eXcorKit` Swift package does not yet expose catalog APIs. The historical implementation in `modules/legacy/src/` remains in the repository pending a separate provenance audit; nothing compiles it any more. The earlier music-player apps were removed on 2026-11-03 (owner's decision) in favor of the preview apps below, which use this library. See [the audit](docs/REPOSITORY_AUDIT.md) and [implementation status](docs/IMPLEMENTATION_STATUS.md).

The experimental API and its authorization, paging, and provider limitations are documented in [Catalog metadata](docs/CATALOG.md).

## Installation

No release is published to a remote repository yet. Build and install the library into your local Maven repository from this checkout (JDK 21 toolchain):

```sh
./gradlew publishToMavenLocal
```

Then, in your app's Gradle build:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
    google()
}

dependencies {
    implementation("com.excor:eXcor:0.2.0")
}
```

Gradle picks the JVM, Android or iOS variant from the published Kotlin Multiplatform metadata. The JVM artifact targets Java 17 bytecode, matching InnerTubeX v0.11.6; Android also uses Java 17 bytecode with minSdk 26. Both comparison adapters have now run live under Temurin JDK 17.0.20.1 and Java 25 on this machine; [`modules/samples/jvm-quickstart`](modules/samples/jvm-quickstart) is a standalone app built with the same JVM target, and CI publishes the library locally and builds it on every push.

## Quick start (JVM and Android)

`excorDefaultHost()` gives a ready host with a signed-out session, an in-memory cache and the system clock:

```kotlin
val host = excorDefaultHost(region = "US", language = "en")
val excor = Excor(host)
try {
    val video = excor.videoInfo("dQw4w9WgXcQ")                 // title, channel, duration, views, playability
    val results = excor.search("daft punk")                   // videos, Shorts, channels, playlists, filters
    val next = excor.search("daft punk", results?.continuation) // the next page
    val channel = excor.channel("UC_kRDKYrUlrbtrSiyu5Tflg", tab = ExcorYouTubeChannelTab.PLAYLISTS)
    val comments = excor.comments("dQw4w9WgXcQ", newestFirst = true)

    val music = excor.musicSearch("daft punk")                // typed: songs, videos, artists, albums, playlists…
    val artist = excor.musicArtist("UC_kRDKYrUlrbtrSiyu5Tflg")
    val radio = excor.musicRadio(videoId = "FGBhQbmPwH8")     // pass radio.continuation back for more
} finally {
    excor.close()
}
```

Call `close()` when the eXcor instance is no longer needed. It releases eXcor's private transport resources; a host-supplied `OkHttpClient` remains owned by the host and can be shared with other app code.

Every call is `suspend`:
- It returns null when YouTube answered, but not with the expected page (an unavailable or removed item).
- It throws `ExcorRequestException` when the request itself kept failing after up to three attempts. `failure` says whether YouTube was rate limiting (`RATE_LIMITED`), had server errors (`SERVER_ERROR`) or couldn't be reached (`NETWORK`).
- Invalid arguments throw `IllegalArgumentException`.

[Feature matrix](docs/FEATURE_MATRIX.md) lists what is verified live and what is blocked; [error handling](docs/ERROR_HANDLING.md) explains failure results.

## Playback diagnostics (JVM sample)

The standalone quickstart includes an opt-in, signed-out direct-audio playback runner. Publish the library locally, then run it with `ffplay` installed:

```sh
./gradlew publishToMavenLocal
./gradlew -p modules/samples/jvm-quickstart run --args="--playback VIDEO_ID --seconds 45 --seek-seconds 90"
```

`--seek-seconds` asks ffplay to start at that timestamp; ffplay seeks to the nearest available point. Use Left/Right Arrow during playback for interactive backward/forward seeking, then press `q`. The runner prints request/status counts, bytes relayed, and observed Range offsets. Offset observations alone do not prove that a seek key was pressed. Add `--full-decode` to probe duration and decode the whole audio stream with `ffprobe` and `ffmpeg` (bounded to 30 minutes). Each invocation shares a 256 MiB media-body budget across playback, probe, and decode; reaching the cap terminates the child process and reports `byte_limit`.

The player receives only a random-path loopback URL. The signed media URL and allowed request headers stay in the quickstart process and are not printed. The runner covers direct whole-file audio only; it does not add HLS, DASH-manifest, or SABR/UMP playback. YouTube access refusals are reported as such.

## Streams

The host implements `ExcorHost` (or uses `excorDefaultHost`) to provide the HTTP client, session, clock, logging, cache settings, and optional PO-token provider.

```kotlin
val excor = Excor(host)
excor.init() // optional asynchronous warm-up

val stream = excor.extract(
    videoId = "dQw4w9WgXcQ",
    maxKbps = 128,
)

if (stream != null) {
    // Fetch stream.url with stream.headers. Keep the returned headers private.
    val headers = excor.headersFor(stream.url)

    // After a host player reports HTTP 403:
    val refusedVideoId = excor.onRefused(stream.url)
}
```

`maxKbps` selects low-bitrate audio at 64 kbps or below; above that threshold it does not cap the selected bitrate. `requireM4a` restricts selection to AAC/M4A.

### Video

```kotlin
val streams = excor.extractVideo(
    videoId = "dQw4w9WgXcQ",
    maxHeight = 1080,                                   // the tallest picture allowed
    codecs = setOf(ExcorVideoCodec.H264, ExcorVideoCodec.VP9),
)
if (streams != null) {
    val video = streams.video   // url, headers, width x height, fps, codec, initRange/indexRange
    val audio = streams.audio   // null only when the video is progressive (carries its own sound)
}
```

The best picture under `maxHeight` wins, then the higher frame rate, then the most widely decodable codec (H.264, VP9, AV1). The audio comes from the same response, in the matching container where one exists (M4A with MP4 video, Opus/WebM with WebM video), so the two can be played together or muxed without re-encoding. Both tracks pass the playback probe before they are returned. If the response has no playable separate audio, a progressive format is used instead. Segmented ("OTF") formats are not selected.

`excor.formats(videoId)` lists every whole-file audio and video format one client offers (itag, codec, size, frame rate, bitrate, length, byte ranges), for download choices. All of them are fetched with `list.headers`. One representative format is probed, since a response's URLs are signed together. If a listed URL is refused, call `exclude(videoId, list.profileId)` and ask again. A `TokenProvider` is an integration boundary: applications may supply tokens they are authorized to obtain, while eXcor never creates them.

### HLS manifest handoff (experimental)

`extractHls` returns an HLS manifest URL only when YouTube offers one and the bounded manifest probe succeeds:

```kotlin
val hls = excor.extractHls(videoId)
if (hls != null) {
    // Give hls.url and hls.headers to a host media player with HLS support.
    // Keep the signed URL and any host-provided PO token private.
}
```

The result includes `isLive`, client/profile identifiers and the media headers. The host player handles child playlists and media segments. eXcor validates the initial URL and manifest redirect chain; it does not proxy or validate each child request. Synthetic coverage passes. The first signed-out Android 16 live attempt returned `UNPLAYABLE` before a manifest was obtained, so no live HLS playback result is claimed; see the [attempt report](modules/samples/black-box-comparison/results/owner-home-2026-11-13-android-hls-attempt.md). DASH and SABR/UMP playback are not implemented.

## Preview apps

Two small apps for trying the library by hand, for the platforms the owner uses (Android and Windows); the library itself also targets the JVM and iOS. They are built on the clean-room library and are not published. The Android preview can send an experimental `extractHls` result to Media3's HLS player when YouTube offers a manifest; the host player handles child playlists and segments. No live HLS playback has been verified. Where YouTube refuses every signed-out client on a network, the apps report that result; they don't get around it.

- **Android** (`preview-android`): a debug APK, Android 8 and later. Search YouTube Music (the default) or YouTube, or paste or share a link. YouTube Music searches show its own filter chips for the query (Songs, Videos, Albums, Artists, Community playlists… as it offers them) and typed results; albums and playlists open as track lists, artists as their shelves, and Back returns. A song or video shows its details, plays audio or video (picture and sound from separate tracks, merged by ExoPlayer), and lists its formats. Sign-in cookies are encrypted with Android Keystore and excluded from backups; if secure storage is unavailable, sign-in lasts only until app exit. **Try HLS (experimental)** sends a manifest from `extractHls` to Media3 HLS when a client offers one; child playlists and segments are fetched by the player. Live HLS playback has not been verified. Download audio, Download video and tapping a listed format save to Downloads/eXcor; video is joined with its sound on the phone without re-encoding (H.264 with AAC as MP4; VP9 with Opus as WebM, from Android 10). The screen also shows the library's log.
- **Windows** (`preview-desktop`): an app folder with `eXcor Preview.exe` and its own Java runtime, so nothing needs to be installed. It has the same YouTube Music and YouTube search, filters, album, playlist and artist pages, and details. Play audio and Play video download the media and open it in the system's player; Save format saves any listed format. Imported or pasted YouTube cookies stay in memory until sign-out or app exit; older plaintext preference values are cleared on startup. Video above the qualities YouTube serves as one file needs [ffmpeg](https://ffmpeg.org) on the PATH to join the separate tracks. It also runs on macOS and Linux.

CI builds both on every push: download `excor-preview-apk` and `excor-preview-windows` from a run's artifacts. To build them locally:

```bash
./gradlew :preview-android:assembleDebug   # modules/platforms/preview/android/build/outputs/apk/debug/preview-android-debug.apk
./gradlew :preview-desktop:packageApp      # modules/platforms/preview/desktop/build/app/eXcor Preview/ (the .exe on Windows)
```

## Build and test

Use the Gradle wrapper and JDK 21 toolchain. The Kotlin, Ktor, coroutine, serialization and QuickJS-KT versions are pinned in the build.

```sh
./gradlew :jvmTest :jvmJar :assembleAndroidMain
```

The macOS workflow additionally runs iOS simulator tests, links the iOS frameworks, builds the XCFramework, type-checks the published Kotlin API from Swift, and tests/builds the native `eXcorKit` Swift package. Live tests are opt-in and should run only where YouTube access is permitted:

```sh
EXCOR_LIVE=1 ./gradlew :jvmTest --tests com.excor.LivePlaybackTest --rerun
```

Offline clean-room tests use synthetic fixtures under `modules/cleanroom/fixtures/`. The historical captures under `modules/legacy/fixtures/` are not inputs to the clean-room implementation. GitHub Actions and current verification limits are recorded in [implementation status](docs/IMPLEMENTATION_STATUS.md).

## Repository layout

- `modules/` groups the active library (`cleanroom/`), preserved implementation (`legacy/`), platform packages and previews (`platforms/`), and quickstarts and isolated comparisons (`samples/`).
- `api/` holds the Kotlin ABI validation baseline.
- `docs/` contains architecture, release evidence, plans, and maintainer notes; `scripts/` contains repository checks.
- `gradle/` and `.github/` hold build and CI configuration.

## Repository guidance

- [Clean-room policy](docs/CLEAN_ROOM.md) and [source provenance](PROVENANCE.md)
- [Architecture](docs/ARCHITECTURE.md) and [feature matrix](docs/FEATURE_MATRIX.md)
- [Repository audit](docs/REPOSITORY_AUDIT.md) and [implementation plan](docs/IMPLEMENTATION_PLAN.md)
- [Dependency licenses](docs/DEPENDENCY_LICENSES.md), [third-party notices](docs/THIRD_PARTY_NOTICES.md), [security policy](SECURITY.md), and [open questions](docs/maintainers/QUESTIONS.md)

The approved names are eXcor for Kotlin and eXcorKit for the separate Swift package; [BRIEF.md](docs/maintainers/BRIEF.md) uses those names throughout its active API contract. Historical app and implementation files now carry the eXcor naming, but their older API shapes remain unintegrated with the clean-room library.

## Native Swift package: eXcorKit

The separate Swift Package Manager product in [`modules/platforms/swift/`](modules/platforms/swift/) provides the section 8 API for iOS 15 and macOS 12. It uses Foundation and JavaScriptCore, with no third-party package dependencies. On macOS, run `swift test --package-path modules/platforms/swift`; GitHub Actions also builds the package for the iOS Simulator.
