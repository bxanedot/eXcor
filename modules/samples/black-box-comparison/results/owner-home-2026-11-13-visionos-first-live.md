# Signed-out current-worktree direct-audio recheck

- Run time: 2026-11-13 04:39 UTC
- Base commit: `a9995256e3629b583f0e15faa4f73479aa54208a`
- Source: dirty local worktree with VISIONOS-first signed-out profile order
- Environment: owner's Windows home network, signed out
- Public video: `dQw4w9WgXcQ`
- Test: `LivePlaybackTest.extractedAudioDecodesFullyAndSeeks`, one sample
- Result: selected VISIONOS, full audio decode and midpoint HTTP range read passed

VISIONOS returned 27 usable formats and a WebM/Opus stream. FFmpeg decoded all 3,433,755 bytes (213.061 seconds); the midpoint request at byte 1,716,877 returned HTTP 206 at the requested offset.

The test name's “seeks” refers to an HTTP byte-range read, not a player-driven seek. This is one eXcor audio result on one network; it does not verify ciphered playback, signed-in playback, HLS, or InnerTubeX player parity.

## Fresh repeat — 2026-11-13 05:03:22 UTC

- Same public video and owner Windows home network; signed out.
- `LivePlaybackTest.extractedAudioDecodesFullyAndSeeks` passed again with zero skips or failures.
- Selected VISIONOS and decoded the complete 3,433,755-byte WebM/Opus stream (213.061 seconds); the midpoint HTTP range returned 206 at byte 1,716,877.
- This repeats eXcor-only decode and byte-range evidence. It is not a player-driven seek or a new InnerTubeX comparison.

## Fresh 480p video/audio check — 2026-11-13 05:05:25 UTC

- Same public video and owner Windows home network; signed out; video height capped at 480p.
- `LivePlaybackTest.extractedVideoAndAudioDecodeAndMux` passed with zero skips or failures.
- Selected VISIONOS and returned 854x480 H.264 at 25 fps plus AAC. The 14,103,519-byte video track and muxed audio/video stream decoded fully for 213.089 seconds; a midpoint video byte-range read passed.
- This is eXcor-only direct-stream evidence on the same video/network. The range read is not a player seek; InnerTubeX video parity remains unmeasured.
