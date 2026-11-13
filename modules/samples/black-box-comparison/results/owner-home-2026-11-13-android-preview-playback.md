# Android signed-out preview playback — 2026-11-13

Owner-run Android preview playback observations on two emulator API levels. These are platform compatibility datapoints, not an InnerTubeX comparison.

## Run

- Time: approximately 2026-11-13 06:38–06:39 UTC on API 36 and 06:55–06:58 UTC on API 26.
- Source base: `main` at `a9995256e3629b583f0e15faa4f73479aa54208a`; worktree was dirty. The installed app was the current debug preview APK.
- Devices: Android 16 / API 36 x86_64 emulator, existing unlocked user 0; Android 8.0 / API 26 x86_64 emulator created for minSdk verification.
- Network: emulator Wi-Fi through the owner's workstation; no IP retained.
- Session: signed out on both; the preview showed its Google sign-in and cookie-import controls.
- Public video: `dQw4w9WgXcQ`.
- Result: PASS for visible playback on both API levels. Each preview resolved the title and reported `Playing 720p H.264 · audio/mp4 130 kbps · VISIONOS`; video frames changed during the runs. The API 36 app log showed two HTTP 206 playback-probe responses, and its captures more than six seconds apart showed different frames. On API 26, ExoPlayer controls showed the active pause control while later captures showed a different scene.

## Limits

One video and one workstation Wi-Fi connection were checked across two emulator API levels. No InnerTubeX Android app was run, so this does not add an Android head-to-head result. Full-duration decode, player seeking, mobile data, additional IDs, live HLS and signed-in playback remain unverified. It does not change the matched JVM-adapter measurements or release-gate statuses.
