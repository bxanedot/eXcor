# Exact-main signed-out playback and range matrix — 2026-11-14

- **Commit:** 2a9f8dd6a0de4cef37724fad7102fc50c0c8a0cf
- **Range matrix:** 2026-11-14 13:34:32 UTC; one selected test, zero skips or failures.
- **Full decode:** 2026-11-14 13:36:02 UTC; two selected tests, zero skips, failures or errors.
- **Environment:** Windows x64 owner workstation, signed out, Temurin JDK 25.0.4.1, FFmpeg/FFprobe 9.0.2.
- **IDs:** dQw4w9WgXcQ, kJQP7kiw5Fk, JGwWNGJdvx8.

| Check | Observed result |
|---|---|
| Initial range matrix | dQw4w9WgXcQ was RANGE_READABLE; kJQP7kiw5Fk and JGwWNGJdvx8 were ACCESS_BLOCKED after YouTube returned LOGIN_REQUIRED bot-check reasons. |
| Direct audio | VISIONOS returned Opus; all 3,433,755 bytes decoded; duration 213.061 s; the midpoint HTTP range at byte 1,716,877 returned 206 with the validated 65,536-byte range. |
| Video and audio | VISIONOS returned 480p H.264 (854×480, 25 fps, 14,103,519 video bytes) and AAC audio. FFmpeg stream-copied both tracks into an MKV and decoded the full 213.089 s mux. The midpoint video HTTP range returned 206 at the requested offset. |

Commands:

```powershell
$env:EXCOR_LIVE='1'
$env:EXCOR_LIVE_IDS='dQw4w9WgXcQ,kJQP7kiw5Fk,JGwWNGJdvx8'
.\gradlew.bat :jvmTest --tests 'com.excor.LivePlaybackTest.publicMusicVideosRecordRangeOutcomeForEachId' --rerun-tasks --no-daemon

$env:EXCOR_DECODE_ID='dQw4w9WgXcQ'
$env:EXCOR_VIDEO_HEIGHT='480'
.\gradlew.bat :jvmTest --tests 'com.excor.LivePlaybackTest.extractedAudioDecodesFullyAndSeeks' --tests 'com.excor.LivePlaybackTest.extractedVideoAndAudioDecodeAndMux' --rerun-tasks --no-daemon
```

The tests ran signed out with no cookies or token provider and deleted temporary media. The run is eXcor-only on one video and one network; no InnerTubeX adapter, player-controlled seek, ciphered playback, or HLS playback was tested. The test names' “seeks” are midpoint HTTP byte-range checks, not player seeks. The access blocks are not decoder failures. This result adds current-main evidence but does not promote G4 or G5.