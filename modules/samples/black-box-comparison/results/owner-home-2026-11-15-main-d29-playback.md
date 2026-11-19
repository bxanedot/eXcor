# Owner-home exact-main playback check — 2026-11-15

## Conditions

- eXcor source: exact main commit d29f6ae3e433c103748b12634c56f193788ac8cc
- Platform/network: Windows x64, owner's home network
- Session: signed out; public music video dQw4w9WgXcQ
- Decoder: FFmpeg/ffprobe 9.0.2
- Command: EXCOR_LIVE=1, EXCOR_DECODE_ID=dQw4w9WgXcQ, EXCOR_VIDEO_HEIGHT=480, then gradlew.bat :jvmTest --tests com.excor.LivePlaybackTest.extractedAudioDecodesFullyAndSeeks --tests com.excor.LivePlaybackTest.extractedVideoAndAudioDecodeAndMux --rerun-tasks --no-daemon --console=plain

## Result

Both tests passed, with zero skips or failures.

- Audio: VISIONOS served Opus; FFmpeg decoded 3,433,755 bytes for 213.061 seconds. The midpoint HTTP byte-range read passed.
- Video plus audio: VISIONOS served 480p H.264 at 854×480 and 25 fps; the video track was 14,103,519 bytes. FFmpeg muxed it with AAC audio and decoded both streams for 213.089 seconds. The video midpoint HTTP byte-range read passed.

Temporary media files were removed by the tests. No account credentials, signed URLs, or raw service responses were recorded.

## Limits

This is one public video and one network. The midpoint byte-range checks validate transport offsets; they do not simulate player-controlled seeking. This eXcor-only test does not establish InnerTubeX video parity, ciphered playback, signed-in behavior, or cross-network reliability. Keep G4, G5 and G11 partial.
