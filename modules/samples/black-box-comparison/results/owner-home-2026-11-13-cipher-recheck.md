# Signed-out player transform recheck

Date: 2026-11-13

Source commit: `4154f8e90095a62c5d45469300ed7afea8b1cc1f` (`test: verify direct n challenge playback`)

Platform/network/session: Windows x64, owner's home network, signed out

Test: `com.excor.LivePlayerCipherTest.firstPartyPlayerTransformsProduceDecodableAudio`

PowerShell command: `$env:EXCOR_LIVE='1'; $env:EXCOR_CIPHER_ID='<video-id>'; .\gradlew.bat :jvmTest --tests com.excor.LivePlayerCipherTest.firstPartyPlayerTransformsProduceDecodableAudio --rerun-tasks`

The updated verifier looks for an eligible audio transform before fetching player JavaScript. It accepts a valid encrypted signature, a valid `n` challenge, or both; a direct media URL qualifies when it has a valid `n`. It applies only the transforms that are present. The four signed-out pages from the earlier check were re-run against exact source commit `4154f8e90095a62c5d45469300ed7afea8b1cc1f`:

| Video ID | Content category | Audio formats | Formats with `signatureCipher` | Direct URL `n` challenges | Outcome |
|---|---|---:|---:|---:|---|
| `dQw4w9WgXcQ` | music video | 4 | 0 | 0 | skip before player-script request |
| `kJQP7kiw5Fk` | music video | 4 | 0 | 0 | skip before player-script request |
| `aqz-KE-bpKQ` | stable public video | 16 | 0 | 0 | skip before player-script request |
| `M7lc1UVf-VE` | stable public video | 12 | 0 | 0 | skip before player-script request |

Correction to the earlier 6341b95 probe: that verifier fetched the player script before filtering formats. It did not execute the solver, construct a transformed media URL or request media, but it **did fetch the player script**. The updated verifier checks eligibility first; on the four current responses it made no player-script or media request. No signature, `n` value, signed media URL, cookie or account credential was printed or saved. These runs provide no evidence for or against solver correctness.

A separate forced-TV integration rerun at core source commit `6409c62` on the same network stopped during metadata lookup with YouTube's `The page needs to be reloaded.` refusal, before extraction, decode, range reads or seeking. This is access/client evidence, not a cipher result.

This is a single-network signed-out diagnostic, not a comparison against InnerTubeX. G4/G5 remain partial and issue #78 stays open until repeatable eligible ciphered and direct-stream playback, decode and seek checks pass.

## Expanded signed-out sample — 2026-11-13

The same opt-in verifier was run at repository head `fd423509d8624dc2ea7442597f66fba881478aa3` on eight additional public videos. The implementation is unchanged from source commit `4154f8e90095a62c5d45469300ed7afea8b1cc1f`. Each command set `EXCOR_LIVE=1` and `EXCOR_CIPHER_ID` to that row's ID, then ran `:jvmTest --tests com.excor.LivePlayerCipherTest.firstPartyPlayerTransformsProduceDecodableAudio --rerun-tasks --console=plain` on Windows x64, signed out, on the owner's home network.

| Video ID | Content category | Audio formats | Formats with `signatureCipher` | Direct URL `n` challenges | Outcome |
|---|---|---:|---:|---:|---|
| `jNQXAC9IVRw` | First YouTube upload | 8 | 0 | 0 | skip before player-script request |
| `9bZkp7q19f0` | Music video | 4 | 0 | 0 | skip before player-script request |
| `CevxZvSJLk8` | Music video | 4 | 0 | 0 | skip before player-script request |
| `JGwWNGJdvx8` | Music video | 4 | 0 | 0 | skip before player-script request |
| `FGBhQbmPwH8` | Music video | 4 | 0 | 0 | skip before player-script request |
| `OPf0YbXqDm0` | Music video | 4 | 0 | 0 | skip before player-script request |
| `L_jWHffIx5E` | Music video | 4 | 0 | 0 | skip before player-script request |
| `Zi_XLOBDo_Y` | Music video | 4 | 0 | 0 | skip before player-script request |

All eight test runs returned the expected no-eligible-challenge skip. Because the eligibility gate stopped each case before player JavaScript, no solver, transformed URL, or media request was exercised. Together with the four earlier IDs, the sample now covers twelve signed-out public pages on one network; it still provides no evidence for or against solver correctness or transformed playback.

After those live checks, offline coverage was extended for a `signatureCipher` entry with no `n` parameter and for rejecting an empty malformed `n` instead of treating it as signature-only. The direct-URL `n`, signature-only and malformed-`n` regression tests pass. The eight added live pages had zero `signatureCipher` entries and zero direct-URL `n` challenges, so they do not exercise the new signature-only case; no live solver or transformed playback was verified.

The test-only verifier correction is committed at `09a5d45784683710e00a65825249ff5198b9320b`. Its three focused JVM regressions pass, and all nine exact-head CI jobs passed in [run 43482332919](https://github.com/bxanedot/eXcor/actions/runs/43482332919). The live score refresh [run 43484736090](https://github.com/bxanedot/eXcor/actions/runs/43484736090) remains 53/100, Release NO-GO, with main CI success. Issue #78 remains open because no tested ID exposed an eligible live cipher challenge.
