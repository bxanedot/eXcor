# G6 clean-room SABR/UMP feasibility gate (2026-11-11)

**Decision: NO-GO for a production SABR transport claim.** This is a verification plan, not evidence of playback. The existing offline framing/response assembly does not establish a network contract, playable media, decoding or seeking. Keep G6 blocked and the SABR score at 0/20.

## Allowed inputs and provenance
- Independently documented protocol facts with recorded provenance and license review; synthetic vectors written from those facts.
- Owner-operated, authorized playback observations recorded only as sanitized outcomes (platform, commit, date, outcome class). No cookies, session headers, signed URLs, raw captures or private media.
- Do not inspect or translate GPL/LGPL/AGPL extractor implementation, including InnerTubeX, or the quarantined historical source trees.
- Never circumvent bot checks, consent, CAPTCHA, DRM or access restrictions. A denial is a denial, not a failed decoder test.

## Decision options
| Option | Present decision | Reconsider when |
| --- | --- | --- |
| Retain direct transport and explicitly report unsupported SABR | **GO** for current bounded scope | Reassess when independent SABR contract exists |
| Implement an internal KMP SABR adapter | **NO-GO** for production | Independently specified bootstrap, negotiation, representation selection, response sequencing and seek/recovery; authorized reproducible playback evidence |
| Accept host-supplied transport integration | **Research only** | Defined minimal interface, security and cancellation contract, independent test fixtures and iOS/JVM/Android compatibility |

## Required proof sequence (each stage independently reviewed)
1. **Provenance gate:** record source URL, license, date, precise claim and synthetic fixture derivation; reject undocumented or competitor-code-derived assumptions.
2. **Offline contract gate:** bounded request/response parser and synthetic tests for invalid lengths, truncation, duplicate/out-of-order parts, gaps, cancellation, backpressure, retry budgets, and memory ceilings. These tests award **no SABR playback points**.
3. **Authorized negotiation gate:** on the owner's own device, verify permitted bootstrap and response handling without bypassing a denial; record only sanitized outcomes.
4. **Audio gate:** assemble playable audio, fully decode it, verify timestamps and a midstream seek against an authorized source.
5. **Video gate:** select compatible representations, assemble audio/video, decode/mux, and verify seek plus cancellation/recovery.
6. **Platform gate:** repeat representative authorized tests on supported JVM/Android/iOS targets; verify no credentials or media URLs enter diagnostics.
7. **Evidence gate:** merge reviewed implementation, passing required CI, and reproducible owner-only results before updating docs/competitiveness-evidence.json or G6.

## Acceptance and stopping conditions
- Every stage has a commit SHA, platform, test command, expected result, observed sanitized outcome, and date.
- Unsupported transport and access denial are separate statuses; never classify them as playback success.
- Stop at the first unavailable independent contract or authorized test source. Keep direct-only behavior explicit and G6 blocked; do not invent fixtures from protected captures.
- No remote release or publication without owner authorization.

Tracking: #79 (transport decision), #78 (owner-run playback matrix), #77 (offline reliability/security).
