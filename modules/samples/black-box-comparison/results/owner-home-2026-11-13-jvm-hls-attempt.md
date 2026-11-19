# Signed-out JVM HLS attempt

- Date: 2026-11-13 UTC
- Base commit: `1bb21422fa2b660674d9b63d98791387772845e9`; the opt-in HLS test and its Gradle input were local changes during this check.
- Environment: Windows x64 workstation, signed out, home network, FFmpeg 9.0.2.
- Operation: `Excor.extractHls`, followed by an FFmpeg decode of up to eight seconds only if a validated manifest is returned.

## Results

| Public video | Outcome |
|---|---|
| `dQw4w9WgXcQ` | YouTube returned `UNPLAYABLE` (“The page needs to be reloaded”) before a manifest. The opt-in test failed visibly; FFmpeg did not start. |
| `aqz-KE-bpKQ` | YouTube required sign-in to confirm the request was not a bot. The existing live-test helper classified this as an access challenge and skipped; no manifest was returned. |
| [NASA's ISS live stream](https://www.youtube.com/watch?v=M3HKLzjvKPc) | The same sign-in/bot challenge blocked the signed-out request before a manifest. No FFmpeg decode ran. |

No attempt bypassed an access check, used cookies or tokens, or saved/logged a signed media URL or raw response. None of the three attempts reached HLS decoding, and no InnerTubeX HLS run was made. This is network-specific blocked evidence, not evidence that the HLS decoder succeeds or that the product lacks the path; successful manifest handoff, playback and seeking remain unverified. G5 stays partial.

Reproduce with `EXCOR_LIVE=1 EXCOR_HLS_ID=VIDEO_ID ./gradlew :jvmTest --tests com.excor.LivePlaybackTest.hlsManifestDecodesForHostPlayer --rerun-tasks` (PowerShell uses `$env:EXCOR_LIVE`, `$env:EXCOR_HLS_ID` and `gradlew.bat`).
