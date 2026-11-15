# eXcor public-video initial-range matrix — 2026-11-15

- eXcor main metadata: `766996e16dcb6a12ec5da5c7d9528094a7318d8f`
- Platform: signed-out Windows x64 JVM test on one owner home network
- Test: `LivePlaybackTest.publicMusicVideosRecordRangeOutcomeForEachId`
- IDs: `ScMzIvxBSi4`, `ysz5S6PUM-U`, `1ZAPwfrtAFY`

| Video ID | eXcor outcome |
|---|---|
| `ScMzIvxBSi4` | `ACCESS_BLOCKED` |
| `ysz5S6PUM-U` | `VIDEO_UNAVAILABLE` |
| `1ZAPwfrtAFY` | `ACCESS_BLOCKED` |

The test was skipped after recording every selected ID as blocked or unavailable; it had zero failures and produced no readable media range. The local test-harness change recognizes only the known unavailable-video message; product playback code was unchanged. This is an access/eligibility record, not successful playback evidence. See the separate [paired adapter follow-up](owner-home-2026-11-15-unavailable-followup.md) and the six-sample [ScMz repeat](owner-home-2026-11-15-scmz-repeat.md).

Reproduce the live matrix with:

```powershell
$env:EXCOR_LIVE = '1'
$env:EXCOR_LIVE_IDS = 'ScMzIvxBSi4,ysz5S6PUM-U,1ZAPwfrtAFY'
.\gradlew.bat :jvmTest --tests 'com.excor.LivePlaybackTest.publicMusicVideosRecordRangeOutcomeForEachId' --rerun-tasks --no-daemon --console=plain
Remove-Item Env:\EXCOR_LIVE
Remove-Item Env:\EXCOR_LIVE_IDS
```

No cookies, tokens, signed media URLs, request bodies or raw responses are included.
