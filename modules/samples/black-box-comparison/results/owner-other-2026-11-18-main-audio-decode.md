# Current-main live audio decode — 2026-11-18

- Tested source tree: PR head `1dfb40991b0c6615ca021b454eed0aaaa77f755b`; `git diff --quiet HEAD origin/main` confirmed it matches merged main `2cd94171959c9c0fcb58622396e9c0a7c61be7f7`.
- Run time: `2026-11-18T17:14:40Z`.
- Platform: Windows 11 x64; signed out; network category `other`, matching the earlier owner run on this workstation.
- Public video: `dQw4w9WgXcQ`; operation: direct audio extraction, complete file decode, and midpoint HTTP byte-range read.
- Runtime: Temurin JDK 21.0.13+9 and FFmpeg/FFprobe 9.0.2 essentials. The portable FFmpeg archive was kept under `work/` and its SHA-256 matched the publisher's digest `60f467265b1e312373dbcd92200c2618a74850f98d3d078e94296bb3fa2047ba`.

Command, with `EXCOR_LIVE=1`, `EXCOR_DECODE_ID=dQw4w9WgXcQ`, `JAVA_HOME_21_X64` set to JDK 21, and FFmpeg/FFprobe on `PATH`:

```powershell
.\gradlew.bat --no-daemon --console=plain '-Pexcor.autoProvisionJdk=false' '-Porg.gradle.java.installations.auto-download=false' '-Porg.gradle.java.installations.fromEnv=JAVA_HOME_21_X64' ':jvmTest' '--tests' 'com.excor.LivePlaybackTest.extractedAudioDecodesFullyAndSeeks'
```

**Result: PASS.** One test ran with zero skips or failures. eXcor selected the VISIONOS client and decoded the complete 3,433,755-byte Opus stream; FFprobe reported 213.061 seconds. The midpoint request returned HTTP 206 with exactly 65,536 bytes starting at offset 1,716,877.

This is one signed-out video on one network. The byte-range read is not a player seek. VISIONOS does not establish deciphered/ciphered playback, and this run does not compare another adapter. G4/G5 remain partial; no release gate is promoted.
