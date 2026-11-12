# eXcor / InnerTubeX live 0–100 checker

**What this measures:** evidence-attested readiness against a fixed 100-point rubric, refreshed by GitHub Actions. It is **not** the share of YouTube videos that play, nor proof of fully working SABR streaming.

## Open the live result

- [Current score and missing criteria](https://github.com/bxanedot/eXcor/blob/automation/excor-competitiveness/score/README.md)
- [Machine-readable score, gate status and CI state](https://raw.githubusercontent.com/bxanedot/eXcor/automation/excor-competitiveness/score/score.json)
- [GitHub Actions checker runs](https://github.com/bxanedot/eXcor/actions/workflows/competitiveness-score.yml)

A GitHub README badge uses the [Shields JSON endpoint](https://shields.io/badges/endpoint-badge) backed by the report's badge.json file.

## Scoring rules

The reviewable [criteria file](competitiveness-evidence.json) lists individually weighted capabilities: verified, blocked or unverified. The checker sums only verified points and requires an existing, cited file for every credit. Its integrity tests enforce category totals, reject duplicate entries and prevent earning SABR points while release gate G6 is blocked.

| Category | Maximum |
|---|---:|
| YouTube / YouTube Music metadata | 20 |
| Direct audio/video playback | 20 |
| Player and cipher compatibility | 15 |
| SABR/UMP playback | 20 |
| Reliability and security | 15 |
| Platforms and developer usability | 10 |
| **Total** | **100** |

**Important distinction:** These are source-linked engineering assertions, not automated proof of feature behavior. Only update a criterion to verified after its CI and, where needed, owner-authorized real playback evidence passes. A green CI build cannot automatically prove real streaming, and a bot-check refusal is not evidence for or against cipher correctness. Do not assign SABR points for the offline framing decoder or continuation assembler: real authorized transport, decode and seek tests are required.

A score of **100/100 additionally requires all twelve release gates** in [release-gates.json](release-gates.json) to be passing. Remote publication still requires separate owner approval.

## How the live checker runs

The GitHub workflow runs on every change to main, on manual dispatch, and hourly. It reads the current trusted main branch and:

1. Runs offline rubric integrity tests.
2. Calculates the weighted score from criterion states and G1–G12 release gates.
3. Looks up the exact main commit's CI workflow state using GitHub's API; displays pending, passed, failed or unknown separately **without automatically adding points**.
4. Writes readable Markdown, complete JSON and dynamic-badge JSON to the dedicated `automation/excor-competitiveness` branch, bootstrapping it as an isolated score-only branch if it has been removed, and uploads the results as a GitHub Actions artifact.

Score-only commits do not launch expensive multiplatform CI. If the report does not materially change, the checker skips its commit. The output does not include cookies, credentials, session headers, signed stream URLs, network captures or account details.

## Reproduce locally

Python 3.10+ (standard library only):

    python3 -m unittest discover -s scripts/tests -p test_competitiveness_score.py -v
    python3 scripts/excor_readiness.py --root . --out-dir /tmp/excor-readiness
    cat /tmp/excor-readiness/README.md

To check GitHub CI too, provide the exact current main commit:

    python3 scripts/excor_readiness.py --root . --out-dir /tmp/excor-readiness --repo bxanedot/eXcor --sha YOUR_MAIN_COMMIT_SHA --fetch-ci

The optional GH_TOKEN is read from the environment, never a command-line argument.

## Advancing toward 100

After a verified improvement is merged, update the corresponding criterion's state and evidence references in a PR. For live playback, record a sanitized owner-run outcome using [PLAYBACK_EVIDENCE_MATRIX.md](PLAYBACK_EVIDENCE_MATRIX.md) without sensitive captures or bypassing service restrictions. A later failure can also lower points; the checker must report regression accurately.

Initial score: **53/100** (17 metadata + 11 direct streaming + 8 player/cipher + 0 SABR + 11 reliability/security + 6 platforms). This is the prior engineering baseline expressed as a reproducible checklist.
