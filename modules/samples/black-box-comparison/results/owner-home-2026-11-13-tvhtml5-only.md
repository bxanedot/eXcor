# Signed-out TVHTML5-only playback attempt

- **Date:** 2026-11-13
- **Environment:** owner's Windows home network; signed out; dirty local worktree
- **Public video:** `dQw4w9WgXcQ`
- **Client scope:** TVHTML5 only; VISIONOS and WEB skipped
- **Result:** one `/s/player/` script fetch was observed; the TVHTML5 player response returned `The page needs to be reloaded.` before a playable stream was returned. Only the request count was retained; no URL was logged.
- **Media checks:** no media stream, full decode, byte-range probe, or seek result.

This is an access/playability outcome, not evidence that eXcor's cipher transform failed. The attempt does not verify ciphered playback and does not change G4's partial status.

Reproduction from the repository root:

```powershell
$env:EXCOR_LIVE='1'
$env:EXCOR_FORCE_TV='1'
$env:EXCOR_DECODE_ID='dQw4w9WgXcQ'
.\gradlew.bat :jvmTest --tests com.excor.LivePlaybackTest.extractedAudioDecodesFullyAndSeeks
```
