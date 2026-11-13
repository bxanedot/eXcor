# Android experimental HLS attempt — 2026-11-13

## Setup

- App: local eXcor Preview 0.2.0 debug build
- Device: Android 16 / API 36 emulator, signed out
- Network: owner's Windows workstation Wi-Fi
- Public video ID: `dQw4w9WgXcQ`
- Action: tapped **Try HLS (experimental)**

## Result

The app failed while finding the HLS manifest. The observed status was `UNPLAYABLE` with the reason **“The page needs to be reloaded.”** The local diagnostic log also recorded that WEB was skipped because no host streaming-token provider was configured. eXcor did not return a manifest, so Media3 never started and no HLS media playback or seek was observed.

No account credentials, cookies, PO tokens, signed URLs or raw response bodies were supplied or saved. No access challenge was bypassed. This is one signed-out video on one network; it records this failed attempt and does not establish HLS behavior for other videos or networks.
