# Current-main signed-out audio/video playback check — 2026-11-14

- **Commit:** `36d5bd0a08a39c1c708b9d71405cbacc2ca1fc8b`
- **Run:** 2026-11-14 09:54 UTC, Windows x64 owner workstation, home network, signed out
- **Video:** `dQw4w9WgXcQ`
- **Runtime:** Temurin JDK 25.0.4.1, FFmpeg/FFprobe 9.0.2

The two selected `LivePlaybackTest` cases passed with zero skips or failures:

| Check | Observed result |
|---|---|
| Direct audio | VISIONOS returned Opus audio; 3,433,755 bytes fully decoded; duration 213.061 s; midpoint HTTP range returned 206 at byte 1,716,877. |
| Video plus audio | VISIONOS returned 480p H.264 video (854×480, 25 fps, 14,103,519 video bytes) and AAC audio. FFmpeg stream-copied both into an MKV, decoded the complete mux, and reported 213.089 s with both audio and video streams. The midpoint video HTTP range returned 206 with the requested offset. |

Command:

```powershell
$env:EXCOR_LIVE='1'
$env:EXCOR_DECODE_ID='dQw4w9WgXcQ'
.\gradlew.bat --no-daemon :jvmTest `
  --tests 'com.excor.LivePlaybackTest.extractedAudioDecodesFullyAndSeeks' `
  --tests 'com.excor.LivePlaybackTest.extractedVideoAndAudioDecodeAndMux'
```

The tests ran signed out without cookies or a token provider and removed their temporary media files. They verify one public video on one network; the InnerTubeX adapter was not included in the video/mux run. The midpoint probes are HTTP byte-range reads, not player-controlled seeks. Ciphered playback, HLS/SABR, another network, repeatability across IDs, and video parity against InnerTubeX remain unverified. This evidence does not promote G4 or G5.
