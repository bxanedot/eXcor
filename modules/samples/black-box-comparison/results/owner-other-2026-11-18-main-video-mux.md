# Current-main live video/audio mux — 2026-11-18

- Tested implementation tree: branch commit `951e46e`; `git diff --quiet origin/main -- modules/cleanroom modules/platforms` confirmed the implementation matches merged main `2cd94171959c9c0fcb58622396e9c0a7c61be7f7`.
- Run time: `2026-11-18T17:35:04Z`.
- Platform: Windows 11 x64; signed out; network category `other`, matching the earlier owner run on this workstation.
- Public video: `dQw4w9WgXcQ`; operation: extract 480p video and audio, mux, decode all media, and read a midpoint video byte range.
- Runtime: Temurin JDK 21.0.13+9 and FFmpeg/FFprobe 9.0.2 essentials. The portable FFmpeg archive was kept under `work/` and its SHA-256 matched the publisher's digest `60f467265b1e312373dbcd92200c2618a74850f98d3d078e94296bb3fa2047ba`.

Command, with `EXCOR_LIVE=1`, `EXCOR_DECODE_ID=dQw4w9WgXcQ`, `EXCOR_VIDEO_HEIGHT=480`, `JAVA_HOME_21_X64` set to JDK 21, and FFmpeg/FFprobe on `PATH`:

```powershell
.\gradlew.bat --no-daemon --console=plain '-Pexcor.autoProvisionJdk=false' '-Porg.gradle.java.installations.auto-download=false' '-Porg.gradle.java.installations.fromEnv=JAVA_HOME_21_X64' ':jvmTest' '--tests' 'com.excor.LivePlaybackTest.extractedVideoAndAudioDecodeAndMux'
```

**Result: PASS.** One test ran with zero skips or failures. eXcor selected VISIONOS, extracted 854×480 H.264 at 25 fps and AAC audio, muxed and decoded the 14,103,519-byte video track, and FFprobe reported 213.089 seconds. The midpoint request returned HTTP 206 with exactly 65,536 bytes starting at offset 7,051,759.

This is one signed-out video on one network. The byte-range read is not a player seek. The run does not verify deciphered/ciphered playback or compare another adapter. G4/G5 remain partial; no release gate is promoted.
