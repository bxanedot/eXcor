# Autonomous maintenance pilot

`maintenance.yml` runs daily at 06:43 UTC, when an issue is opened/edited/reopened,
and on manual dispatch. It checks out trusted `main` only. It never executes
issue text or PR code, and is not triggered by `pull_request_target`.

The default audit job has read permissions. It produces a sanitized JSON artifact
and a bounded run summary with repository counts and optional public-release observations. Titles, bodies, comments, author/account details,
headers and tokens are absent from reports. Security-like issues get a category
but no writes or duplicate suggestions. No public bot comments are posted.

## Fixed-label write policy

After a successful read-only audit, scheduled and issue-event runs apply fixed
labels by default. Set the repository Actions variable
`EXCOR_MAINTENANCE_APPLY_LABELS=false` to disable the writer. An unset variable
uses the enabled repository default; `true` explicitly enables it. Other nonempty
values disable writes, so a mistyped setting fails closed.

Manual dispatch defaults to `dry_run=true` and remains read-only for every
configuration value. Choose false to run the enabled writer. The explicit
repository disable setting also blocks manual apply runs. No personal token or
Actions-variable administration is needed to use the default policy. This
activation follows the owner's delegated fixed-label decision; it changes the
initial pilot's opt-in default, while retaining its fixed write scope and audit.

Only the separate triage job receives `issues: write`. It re-fetches each issue
before adding fixed category/priority/reproduction labels, keeps existing human
category and priority choices, and never removes labels. It updates at most 15
issues per run; later runs can process the remaining missing labels. Label
creation uses only the hard-coded allowlist. Closed issues and PRs are skipped.

Classification is heuristic and conservative, not a diagnosis. Exact normalized
title matches are duplicate suggestions in the audit only; they do not link,
close or suppress reports. `blocked by #N` references become dependency hints. PR bodies are scanned for
local `fixes #N`, `closes #N`, and `resolves #N` claims. The audit links only
currently open, non-security issues through `unverified_fix_prs`; quoted text
may produce a claim, so this is never treated as verified resolution. Each PR
body is capped at 8,192 characters, at most 100 PRs are considered, and only numeric IDs
enter the report. Cross-repository references and ordinary mentions are ignored.

The repository API client accepts only `lylicht/eXcor` and a closed set of
read/label paths. On 2026-11-16 GitHub redirected the historical clone URL to
`lylicht/eXcor`; public metadata confirmed the same repository ID, `1434956251`.
The fixed allowlist was updated to this canonical name. The historical name is
rejected before connecting; HTTP redirects remain forbidden. Eight additional
fixed public latest-release GET paths are allowed for the release observer, with no Authorization header. The final
target is validated at the network boundary. Unexpected paths,
other repositories and a renamed default branch require policy review. It uses
one fixed HTTPS origin, refuses redirects, limits each body
to 2 MiB and a run to 80 requests. Socket operations have a 15-second inactivity
timeout; the Actions job supplies the ten-minute hard wall-time bound. It stops on API
errors, including rate-limit responses; it does not retry or spam comments.
The managed cloud's fixed `http://proxy:8080` CONNECT proxy is supported;
arbitrary proxy hosts/ports are rejected before connection. The GitHub bearer
token is sent only after TLS to api.github.com, not to another HTTP origin. Scheduled jobs have a 10-minute hard timeout and
one shared concurrency group. Writes are additive and repeatable after failure.

## Branch and PR limits

The report records current PR head SHAs and draft state. It does **not** approve
or merge PRs, declare checks green, or bypass protection. Existing exact-head CI
and human/owner review requirements remain authoritative.

GitHub delete-on-merge is checked in each report. The branch audit excludes
`main`, protected/release branches, the score branch, and every observed open
PR head/base. Candidate comparison uses GitHub ancestry, not branch-name guesses.
Unknown and unique work is retained. **No branch deletion is implemented.**
The 30-day inactivity and seven-day warning thresholds are reported as required
future policy; age and warning history are not yet verified, so even reachable
branches are retained. This is a safe audit, not complete obsolete-branch cleanup.

Inventories are bounded at 100 issues/PRs/branches and at most 25 branch
comparisons. The report flags an incomplete inventory; no deletion or closure
can occur regardless. This pilot does not yet close resolved issues, reopen
regressions, verify PR fixes, review competitor API/behavior changes, or automate merge policy.
Those remain explicit backlog items, rather than implied capabilities.


## Public competitor release observations

Scheduled and manual audits query GitHub's latest full release for the eight
owner-selected projects: InnerTubeX, YouTube.js, tombulled/innertube,
YoutubeExplode, youtube-transcript-api, ytmusicapi, yewtube and alaouy/YouTube. Issue-event audits skip these queries, and the label writer
leaves the optional check disabled. There are at most eight extra requests, sharing
the client's request/body/socket bounds. They use the fixed api.github.com origin,
refuse redirects and send no repository token. No competitor source is fetched.

`--check-competitors` records one observation per project, including failures.
Only numeric X.Y.Z tags (optional v/V prefix), full-release flags and a valid UTC
publication timestamp are accepted. Unusual metadata is unverified and its raw
values are discarded. The report retains HTTP/access/rate-limit, network,
response-size and metadata states separately. Known observation failures leave
the repository audit usable; unexpected policy/programming errors fail it.

A version difference from the 2026-11-16 baselines is `REVIEW_NEEDED`; a match
records release metadata only. This covers GitHub releases, with npm-registry,
interface, feature-gap and behavioral review remaining separate work. The
observer never edits the competitor matrix, creates issues, copies implementation
code, or changes readiness points. JSON artifacts, bounded console observations
and `--summary-output` Markdown carry sanitized versions/statuses for review.
The summary formatter accepts fixed project/status names and numeric versions.

## Verification

```sh
python3 -m unittest discover -s modules/tooling/tests -p 'test_*maintenance*.py' -v
python3 modules/tooling/github_maintenance.py --repo bxanedot/eXcor --output /tmp/maintenance.json --check-competitors --summary-output /tmp/maintenance.md
```

A 2026-11-15 read-only live run found one issue, three open PRs, working
`delete_on_merge`, no obsolete branch candidates, and `default_branch_protected=false`.
No labels or branches changed. The focused synthetic suite passed twelve tests,
including malicious text, stale security/closed states, idempotence, API limits,
redirect refusal, human labels, and protected/active/unique branch retention.
The immutable-action checker also now recognizes named steps (`- name:` followed
by `uses:`); the new regression failed before the correction and passed after it.
All 30 offline tooling tests and 24 pinned-action references passed validation.
The activation follow-up adds tests against the actual workflow condition for
scheduled/issue defaults, explicit disable and unknown settings, and manual
dry-run/apply behavior. These run in both audit and writer policy checks.
A manual `dry_run=true` Actions run on main `9a4672a` passed the policy tests,
audit, and artifact-retention steps: [run 43776027815](https://github.com/bxanedot/eXcor/actions/runs/43776027815).
The privileged triage job was skipped. Artifact download was access-blocked in
the cloud environment, so its contents were not inspected here. At that checkpoint, scheduled-event execution and privileged label writes were
unverified. Actions-variable access returned HTTP 403; that response does not
establish whether the effective workflow policy is enabled.


To verify activation after merge, dispatch `maintenance.yml` on `main` with
`dry_run=true` and confirm that audit succeeds and triage is skipped. Then dispatch
with `dry_run=false` and inspect the triage result and sanitized label-update
count. A skipped job means writes were disabled, not verified. A successful job
with zero updates verifies execution but may mean all desired labels already
exist. Keep the first run URL/count in the maintenance PR's validation record.
The first actual scheduled run is now observed: [run 43907193097](https://github.com/bxanedot/eXcor/actions/runs/43907193097)
on main `edf2bdea5553ff01be916efb72df558ecd386081`, created 2026-11-16 16:24 UTC.
Audit and triage both succeeded; each reported one issue, three PRs and zero label
updates. This verifies one scheduled execution and an idempotent writer on that
snapshot. It used the then-current two-project release observer; validation of
the expanded eight-project observer on main and ongoing recurrence remain separate.

The branch API reports `main` unprotected and the effective rules endpoint was
empty. Classic protection administration was inaccessible (HTTP 403), so the
owner should configure/verify required checks and review protection. This pilot
does not change repository administration or weaken any protection.

Sources: [GitHub workflow events](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows),
[REST issue labels](https://docs.github.com/en/rest/issues/labels), and
[existing pinned CI actions](../../.github/workflows/ci.yml).
