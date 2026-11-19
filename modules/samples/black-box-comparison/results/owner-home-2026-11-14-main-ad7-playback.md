# Current-main direct playback check

- **Source:** eXcor main commit ad7a6f1514c1f320b493c1e62d9bbb6c04c82a5a
- **Date:** 2026-11-14 04:40 UTC
- **Environment:** Windows x64, signed-out, owner home network
- **Content:** public music video dQw4w9WgXcQ
- **Command:** Set EXCOR_LIVE=1 and EXCOR_DECODE_ID=dQw4w9WgXcQ; run .\gradlew.bat :jvmTest --tests com.excor.LivePlaybackTest.extractedAudioDecodesFullyAndSeeks --rerun-tasks
- **Outcome:** 1 test passed, 0 skipped or failed. The VISIONOS path downloaded and fully decoded 3,433,755 bytes of Opus audio (213.061 seconds); the midpoint HTTP range returned 206 at byte 1,716,877.
- **Limit:** the midpoint check is an HTTP byte-range request, not a player-controlled seek. This is one video and one network; ciphered playback and broader repeatability remain unverified.

The test's temporary media file was deleted on completion. No media URL, cookie, token or raw response was retained.