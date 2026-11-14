# Signed-out HLS attempt — 2026-11-14

- Source commit: `0ed9cf7` (the clean-room library sources match main; the commit changes only live-test classification).
- Platform and network: Windows x64 workstation, owner's home network, signed-out `LiveHost`.
- Public video ID: `dQw4w9WgXcQ`.
- Command: `$env:EXCOR_LIVE='1'; $env:EXCOR_HLS_ID='dQw4w9WgXcQ'; .\gradlew.bat :jvmTest --tests com.excor.LivePlaybackTest.hlsManifestDecodesForHostPlayer --rerun-tasks --no-daemon`
- Outcome: **ACCESS BLOCKED**. YouTube returned the playability reason `The page needs to be reloaded` before eXcor returned an HLS manifest. The test recorded one skip and zero failures; FFmpeg received no manifest and decoded no media.
- Interpretation: this records a service refusal, not HLS compatibility. HLS playback remains unverified and G4/G5 remain partial. No credentials, signed URLs, or raw responses were stored.
