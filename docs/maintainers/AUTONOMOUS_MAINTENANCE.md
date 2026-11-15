# Autonomous maintenance pilot

`maintenance.yml` runs daily at 06:43 UTC, when an issue is opened/edited/reopened,
and on manual dispatch. It checks out trusted `main` only. It never executes
issue text or PR code, and is not triggered by `pull_request_target`.

The default audit job has read permissions. It produces a sanitized JSON artifact
and a count-only run summary. Titles, bodies, comments, author/account details,
headers and tokens are absent from reports. Security-like issues get a category
but no writes or duplicate suggestions. No public bot comments are posted.

## Enable label writes

Owner review is required before merging this workflow and enabling its write job.
After reviewing the policy and a dry-run artifact, set the repository Actions
variable `EXCOR_MAINTENANCE_APPLY_LABELS=true`. Manual dispatch defaults to
`dry_run=true`; choose false only to exercise the enabled label job. Unset or
false variables leave the writer disabled. No personal token is required.

Only the separate triage job receives `issues: write`. It re-fetches each issue
before adding fixed category/priority/reproduction labels, keeps existing human
category and priority choices, and never removes labels. It updates at most 15
issues per run; later runs can process the remaining missing labels. Label
creation uses only the hard-coded allowlist. Closed issues and PRs are skipped.

Classification is heuristic and conservative, not a diagnosis. Exact normalized
title matches are duplicate suggestions in the audit only; they do not link,
close or suppress reports. `blocked by #N` references become dependency hints.

The API client uses one fixed HTTPS origin, refuses redirects, limits each body
to 2 MiB, each request to 15 seconds, and a run to 80 requests. It stops on API
errors, including rate-limit responses; it does not retry or spam comments.
The configured HTTP CONNECT proxy is supported without sending the GitHub bearer
token to another HTTP origin. Scheduled jobs have a 10-minute hard timeout and
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
regressions, link PR fixes, monitor competitor releases, or automate merge policy.
Those remain explicit backlog items, rather than implied capabilities.

## Verification

```sh
python3 -m unittest discover -s modules/tooling/tests -p 'test_github_maintenance.py' -v
python3 modules/tooling/github_maintenance.py --repo bxanedot/eXcor --output /tmp/maintenance.json
```

A 2026-11-15 read-only live run found one issue, three open PRs, working
`delete_on_merge`, no obsolete branch candidates, and `default_branch_protected=false`.
No labels or branches changed. The focused synthetic suite passed ten tests,
including malicious text, stale security/closed states, idempotence, API limits,
redirect refusal, human labels, and protected/active/unique branch retention.
The immutable-action checker also now recognizes named steps (`- name:` followed
by `uses:`); the new regression failed before the correction and passed after it.
All 28 offline tooling tests and 24 pinned-action references passed validation.
The scheduled Actions run and privileged label writes still need owner-approved
activation and verification after merge; local tests do not establish that.

The branch API reports `main` unprotected and the effective rules endpoint was
empty. Classic protection administration was inaccessible (HTTP 403), so the
owner should configure/verify required checks and review protection. This pilot
does not change repository administration or weaken any protection.

Sources: [GitHub workflow events](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows),
[REST issue labels](https://docs.github.com/en/rest/issues/labels), and
[existing pinned CI actions](../../.github/workflows/ci.yml).
