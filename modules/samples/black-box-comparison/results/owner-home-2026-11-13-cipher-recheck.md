# Signed-out player transform recheck

Date: 2026-11-13

Source commit: `4154f8e90095a62c5d45469300ed7afea8b1cc1f` (`test: verify direct n challenge playback`)

Platform/network/session: Windows x64, owner's home network, signed out

Test: `com.excor.LivePlayerCipherTest.firstPartyPlayerTransformsProduceDecodableAudio`

PowerShell command: `$env:EXCOR_LIVE='1'; $env:EXCOR_CIPHER_ID='<video-id>'; .\gradlew.bat :jvmTest --tests com.excor.LivePlayerCipherTest.firstPartyPlayerTransformsProduceDecodableAudio --rerun-tasks`

The updated verifier now looks for a usable audio challenge before fetching player JavaScript. It accepts either a signature cipher containing an `n` challenge or a direct media URL with an `n` challenge. The four signed-out pages from the earlier check were re-run against exact source commit `4154f8e90095a62c5d45469300ed7afea8b1cc1f`:

| Video ID | Content category | Audio formats | Formats with `signatureCipher` | Direct URL `n` challenges | Outcome |
|---|---|---:|---:|---:|---|
| `dQw4w9WgXcQ` | music video | 4 | 0 | 0 | skip before player-script request |
| `kJQP7kiw5Fk` | music video | 4 | 0 | 0 | skip before player-script request |
| `aqz-KE-bpKQ` | stable public video | 16 | 0 | 0 | skip before player-script request |
| `M7lc1UVf-VE` | stable public video | 12 | 0 | 0 | skip before player-script request |

Correction to the earlier 6341b95 probe: that verifier fetched the player script before filtering formats. It did not execute the solver, construct a transformed media URL or request media, but it **did fetch the player script**. The updated verifier checks eligibility first; on the four current responses it made no player-script or media request. No signature, `n` value, signed media URL, cookie or account credential was printed or saved. These runs provide no evidence for or against solver correctness.

A separate forced-TV integration rerun at core source commit `6409c62` on the same network stopped during metadata lookup with YouTube's `The page needs to be reloaded.` refusal, before extraction, decode, range reads or seeking. This is access/client evidence, not a cipher result.

This is a single-network signed-out diagnostic, not a comparison against InnerTubeX. G4/G5 remain partial and issue #78 stays open until repeatable eligible ciphered and direct-stream playback, decode and seek checks pass.
