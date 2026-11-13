# Owner-run live extraction recovery after one synthetic 503

- **Date:** 2026-11-13 12:44 UTC
- **Environment:** owner's Windows home network; signed out; dirty local worktree
- **Public video:** `dQw4w9WgXcQ`
- **Fault:** after the metadata lookup, the local OkHttp interceptor returned one synthetic HTTP 503 with `Retry-After: 0` for the first `/youtubei/v1/player` POST. That failed request was not sent to YouTube. The next player POST went to YouTube and succeeded.
- **Result:** eXcor recovered through its bounded retry path, selected VISIONOS, and returned audio. The test logged 27 formats, 27 usable formats and 3 audio candidates.
- **Media check:** FFmpeg fully decoded 3,433,755 bytes (213.061 s); a midpoint HTTP 206 range read at byte 1,716,877 passed.

This verifies one retry after a locally injected client-side response in a real signed-out extraction flow. It does not simulate an upstream YouTube outage, test cancellation against a live outage, or exercise player seeking. G8 remains partial.

Reproduction from the repository root:

```powershell
$env:EXCOR_LIVE='1'
$env:EXCOR_LIVE_503_ONCE='1'
$env:EXCOR_DECODE_ID='dQw4w9WgXcQ'
.\gradlew.bat :jvmTest --tests com.excor.LivePlaybackTest.extractedAudioDecodesFullyAndSeeks
```
