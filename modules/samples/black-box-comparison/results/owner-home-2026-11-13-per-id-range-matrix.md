# Owner-home per-video range outcome matrix

- Date: 2026-11-13 23:53 UTC
- Test source: `9cc903163d58ef97f11f4352fef02a3c51baee90`
- Library source: unchanged from `b3501a28df0c2d4c9da0a3f37ef23d60884596fe`
- Platform: Windows x64
- Network: owner's home network
- Session: signed out, no cookies or streaming-token provider
- Content: three public music videos

## Command

```powershell
$env:EXCOR_LIVE='1'
$env:EXCOR_LIVE_IDS='dQw4w9WgXcQ,kJQP7kiw5Fk,JGwWNGJdvx8'
.\gradlew.bat :jvmTest --tests 'com.excor.LivePlaybackTest.publicMusicVideosRecordRangeOutcomeForEachId' --rerun-tasks --no-daemon --console=plain
```

## Outcomes

| Video ID | Result | Sanitized observation |
|---|---|---|
| `dQw4w9WgXcQ` | `RANGE_READABLE` | VISIONOS selected WebM/Opus. HTTP 206 honored `bytes=0-1023`; the test read all 1,024 requested bytes. |
| `kJQP7kiw5Fk` | `ACCESS_BLOCKED` | VISIONOS and TVHTML5 returned `LOGIN_REQUIRED` with YouTube's bot-check reason. WEB was skipped because no host token provider was supplied. No media range request was made. |
| `JGwWNGJdvx8` | `ACCESS_BLOCKED` | Same access classification as above. No media range request was made. |

The opt-in test passed with one range-readable stream and two explicitly blocked cases. A baseline run at main `b3501a2` skipped at the second ID and never attempted the third; the new test records each case and continues after recognized access challenges. Its output includes video IDs and outcome classes only, never URLs or headers.

## Repeat run — 2026-11-14 00:00 UTC

The same command, test source, platform, network and signed-out session were repeated. Gradle reported one test, zero skips and zero failures, with the same sanitized outcomes: `dQw4w9WgXcQ:RANGE_READABLE`, `kJQP7kiw5Fk:ACCESS_BLOCKED`, and `JGwWNGJdvx8:ACCESS_BLOCKED`.

This verifies a 1 KiB initial HTTP range only. It does not verify full decode, mux, interactive seeking, ciphered playback, HLS or InnerTubeX. The run adds no G4/G5/G11 parity credit; those gates remain partial.
