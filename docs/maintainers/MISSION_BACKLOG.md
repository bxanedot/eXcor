# Maintainer backlog — 2026-11-15

The owner master mission targets evidence-backed superiority over InnerTubeX and
YouTube.js; it is not complete. GPL-3.0-only, historical Apache grants, third-party
notices and the clean-room boundary remain unchanged. Release remains NO-GO.

| Priority | Work | Dependency and current state |
|---|---|---|
| P0 | Protect default branch; review privileged automation | Branch API reports main unprotected; owner administration/review required |
| P1 | Integrate existing reliability fixes | #213 cancellation and owner-approved #214 streaming bounds merged; exact-main CI must remain green |
| P1 | Complete diagnostic stack | #216/#217 conflicts resolved and local JVM tests pass; fresh exact-head CI required; #218 remains draft and depends on #217 |
| P1 | Expand authorized playback/cipher matrix | #78 remains open; multiple IDs/networks and live cipher decode/seek absent; access refusals stay separate |
| P1 | DASH/HLS interoperability | Synthetic local reconstruction is not production DASH; service manifest decode and seeks still need authorized evidence |
| P1 | SABR/UMP production path | G6 blocked until independent service contract and authorized playable fixtures exist; framing alone is insufficient |
| P2 | Sustainable issue triage | Implemented pilot workflow, fixed labels and read-only branch/PR audit; owner approval/activation still required |
| P2 | Complete maintenance lifecycle | Add verified-fix/PR links, conservative issue closure, regression flags, persistent 30-day/7-day branch warnings and safe cleanup; preserve unique work |
| P2 | Competitor change tracking | Latest published versions rechecked: InnerTubeX v0.11.7 and YouTube.js 18.3.0; no implementation source read; scheduled release/interface tracking remains open |
| P2 | Benchmark breadth and quality | #215 streaming first-media timing correction merged; one shared ID/network is insufficient for superiority; expand eligible samples and environments |
| P3 | Installation/API onboarding | #219 merged; exact snippets and standalone JVM consumer compiled; native/platform guides remain distinct follow-ups |

Merge audit for this cycle:

| PR | Reviewed head | Merge commit |
|---|---|---|
| #219 | a654cf91b02345a7afdc42024da3a1509fdcd383 | 65e7bde97f2bfcadd6c36bf8e8f8da8ac1001c1f |
| #213 | ed4484dd2fd17ae7cc0b930b0c2045d16e806d4e | e874072b5a000825c0afa2e588fcec6e89b1ac71 |
| #215 | 3c1a6b0e31a9e2b001933dd1170b201e52a64516 | c70db5a62072925357d41907d6c3c5e02e2de994 |
| #214 | 197171acf4ec391a8284d6e676249e863e661668 | 457bd96a03e0f7c683dd1fe7840f67358ee1b42f |

All reported checks passed at those heads; unresolved review threads were absent.
#214's shared transport change received explicit owner merge approval in chat.
No admin override was used. GitHub's enabled delete-on-merge removed their remote
topic branches; stale refs were pruned. Local `docs/jvm-installation-guide` and
`fix/stream-bounded-http-responses` were removed only after merge confirmation.
The other two topic branches had no local branch in this checkout. Active,
unmerged, stacked-base and score branches remain. No release was published.
