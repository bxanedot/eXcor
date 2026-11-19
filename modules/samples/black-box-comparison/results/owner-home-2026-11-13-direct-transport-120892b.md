# Owner-home signed-out direct transport recheck

- Date: 2026-11-13 UTC
- Source: [PR #154 head `120892be137b6182c512a26a92d74bc64b14e199`](https://github.com/bxanedot/eXcor/commit/120892be137b6182c512a26a92d74bc64b14e199); the clean-room sources are unchanged from base `0409b1fd15130a2ce2723bd420cb02e99da2709f`.
- Platform: Windows x64; FFmpeg and ffprobe 9.0.2.
- Network: owner's home network, signed-out `LiveHost` with no token provider.
- Content category: one public music video, `dQw4w9WgXcQ`.

## Command

```powershell
$env:EXCOR_LIVE='1'
$env:EXCOR_DECODE_ID='dQw4w9WgXcQ'
$env:EXCOR_VIDEO_HEIGHT='480'
.\gradlew.bat :jvmTest --tests 'com.excor.LivePlaybackTest.extractedAudioDecodesFullyAndSeeks' --tests 'com.excor.LivePlaybackTest.extractedVideoAndAudioDecodeAndMux' --tests 'com.excor.LivePlaybackTest.formatListIsRangeReadable' --rerun-tasks --no-daemon
```

All three focused tests passed.

| Check | Sanitized result |
|---|---|
| Direct audio | VISIONOS returned WebM/Opus. FFmpeg decoded the full 3,433,755-byte stream (213.061 s); the midpoint HTTP range at byte 1,716,877 returned the requested content range. |
| Video plus matching audio | VISIONOS returned 480p H.264 (854×480, 25 fps; 14,103,519 video bytes) with AAC audio. FFmpeg muxed both streams without re-encoding and decoded 213.089 s; the video midpoint range matched the requested offset. |
| Format list | 27 VISIONOS formats, from 144p to 2160p; every advertised format returned a readable range. |

This run verifies direct transport on one video and one network. HTTP range reads are not interactive player seeks. These tests did not invoke InnerTubeX, verify HLS, or expose a live cipher challenge, so G4/G5 remain partial and this run adds no head-to-head score.
