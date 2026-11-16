#!/usr/bin/env python3
"""Bounded issue triage and public release observations; no issue text is executed or reported."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import http.client
import json
import os
from pathlib import Path
import re
import sys
from urllib.parse import quote

CATEGORIES = {
    "bug": "bug", "feature": "enhancement", "documentation": "documentation",
    "performance": "performance", "security": "security", "testing": "testing",
    "maintenance": "maintenance",
}
LABELS = set(CATEGORIES.values()) | {"P0", "P1", "P2", "P3", "needs-reproduction"}
MAX_BODY = 2 * 1024 * 1024
MAX_ITEMS = 100

# Manually reviewed public release baselines; matching tags are not capability evidence.
RELEASE_PROJECTS = {
    "innertubex": {"path": "/repos/MetrolistGroup/innertubex/releases/latest",
                   "repo": "MetrolistGroup/innertubex", "baseline": "0.11.7"},
    "youtubejs": {"path": "/repos/LuanRT/YouTube.js/releases/latest",
                  "repo": "LuanRT/YouTube.js", "baseline": "18.3.0"},
}
VERSION_PATTERN = r"(?:0|[1-9][0-9]{0,5})\.(?:0|[1-9][0-9]{0,5})\.(?:0|[1-9][0-9]{0,5})"
RELEASE_STATUSES = {"MATCHES_BASELINE", "REVIEW_NEEDED", "ACCESS_BLOCKED", "RATE_LIMITED",
                    "HTTP_ERROR", "NETWORK_ERROR", "RESPONSE_TOO_LARGE", "UNVERIFIED_METADATA"}


class GitHubHttpError(ValueError):
    def __init__(self, status_code, rate_limited=False):
        self.status_code = status_code
        self.rate_limited = rate_limited or status_code == 429
        super().__init__(f"GitHub API HTTP {status_code}")


class GitHubResponseLimitError(ValueError):
    pass


class ReleaseMetadataError(ValueError):
    pass


def number(value):
    if type(value) is not int or not 0 < value < 2**31:
        raise ValueError("Invalid issue number")
    return value


def classify(issue):
    ident = number(issue.get("number"))
    title = str(issue.get("title", ""))[:256]
    body = str(issue.get("body") or "")[:8192]
    existing = {x.get("name") for x in issue.get("labels", []) if isinstance(x, dict)}
    text = title.lower()
    if "security" in existing or re.search(r"\b(security|vulnerability|credential|secret leak|token leak)\b", text):
        category = "security"
    elif "documentation" in existing or re.search(r"\b(docs?|documentation|readme|typo)\b", text):
        category = "documentation"
    elif re.search(r"\b(performance|latency|slow|memory|benchmark)\b", text):
        category = "performance"
    elif "bug" in existing or re.search(r"\b(bug|crash|broken|regression|fails?|error)\b", text):
        category = "bug"
    elif re.search(r"\b(tests?|fixtures?|coverage)\b", text):
        category = "testing"
    elif "enhancement" in existing or re.search(r"\b(feature|support|add|request)\b", text):
        category = "feature"
    else:
        category = "maintenance"
    explicit = re.match(r"P([0-3]):", title, re.I)
    inferred = "P0" if category == "security" else (
        "P1" if category == "bug" and re.search(r"playback|extract|stream|cipher", text)
        else "P2" if category in {"bug", "feature", "performance"} else "P3")
    priority = next((p for p in ("P0", "P1", "P2", "P3") if p in existing),
                    f"P{explicit[1]}" if explicit else inferred)
    missing = category == "bug" and not all(
        re.search(pattern, body, re.I) for pattern in (r"repro|steps", r"version|commit", r"platform|android|ios|jvm|windows|linux|macos"))
    labels = [CATEGORIES[category], priority] + (["needs-reproduction"] if missing else [])
    additions = set(labels) - existing
    if existing & set(CATEGORIES.values()):
        additions -= set(CATEGORIES.values())
    if existing & {"P0", "P1", "P2", "P3"}:
        additions -= {"P0", "P1", "P2", "P3"}
    return {"number": ident, "category": category, "priority": priority,
            "labels": labels, "add_labels": sorted(additions), "missing_reproduction": missing,
            "blocked_by": sorted({int(x) for x in re.findall(r"blocked by #([1-9][0-9]{0,8})\b", body, re.I)})[:10]}


def triage(issues):
    if len(issues) > MAX_ITEMS:
        raise ValueError("Issue inventory exceeds limit")
    result, seen = [], {}
    for issue in sorted(issues, key=lambda x: number(x.get("number"))):
        if "pull_request" in issue:
            continue
        item = classify(issue)
        # An exact normalized title is only a duplicate suggestion, never a closure.
        title = " ".join(str(issue.get("title", ""))[:256].casefold().split())
        if item["category"] != "security" and len(title) >= 20:
            if title in seen:
                item["possible_duplicate_of"] = seen[title]
            else:
                seen[title] = item["number"]
        result.append(item)
    return result


def link_fix_claims(plans, pulls):
    """Link bounded PR-body claims only; never infer that a fix is verified."""
    if len(pulls) > MAX_ITEMS:
        raise ValueError("PR inventory exceeds limit")
    eligible = {p["number"]: p for p in plans if p["category"] != "security"}
    links = {ident: set() for ident in eligible}
    for pull in pulls:
        ident = number(pull.get("number"))
        body = str(pull.get("body") or "")[:8192]
        for match in re.finditer(r"\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\s+#([1-9][0-9]{0,8})\b", body, re.I):
            target = int(match[1])
            if target in links:
                links[target].add(ident)
    for ident, claims in links.items():
        if claims:
            eligible[ident]["unverified_fix_prs"] = sorted(claims)


class GitHub:
    def __init__(self, repo, token=""):
        if repo != "bxanedot/eXcor":
            raise ValueError("Repository is not allowed by maintenance policy")
        self.token, self.calls = token, 0

    def request(self, method, path, payload=None, *, public_release=False):
        if self.calls >= 80 or method not in {"GET", "POST"} or (path != "" and not path.startswith("/")):
            raise ValueError("API request limit or method rejected")
        if type(public_release) is not bool:
            raise ValueError("Invalid public release mode")
        target = path if public_release else "/repos/bxanedot/eXcor" + path
        # Public reads have separate fixed paths and never receive the repository token.
        if public_release and (method != "GET" or payload is not None or
                               target not in {p["path"] for p in RELEASE_PROJECTS.values()}):
            raise ValueError("Public release target is not allowed")
        # Validate the final origin-form target at the network boundary, not only its callers.
        if not public_release and not re.fullmatch(
            r"/repos/bxanedot/eXcor(?:"
            r"/(?:issues|pulls)\?state=open&per_page=100|"
            r"/(?:branches|labels)\?per_page=100|/labels|"
            r"/issues/[1-9][0-9]{0,9}(?:/labels)?|"
            r"/compare/[0-9a-f]{40}\.\.\.main)?", target
        ):
            raise ValueError("API target is not allowed by maintenance policy")
        if method == "POST" and path != "/labels" and not re.fullmatch(r"/issues/[1-9][0-9]{0,9}/labels", path):
            raise ValueError("API write target is not allowed")
        self.calls += 1
        headers = {"Accept": "application/vnd.github+json", "User-Agent": "excor-maintenance",
                   "X-GitHub-Api-Version": "2022-11-28"}
        if self.token and not public_release:
            headers["Authorization"] = f"Bearer {self.token}"
        data = json.dumps(payload).encode() if payload is not None else None
        if data is not None:
            headers["Content-Type"] = "application/json"
        proxy = os.environ.get("HTTPS_PROXY", "")
        if proxy:
            # The approved cloud proxy is a fixed alternative, never an arbitrary destination.
            if proxy not in {"http://proxy:8080", "http://proxy:8080/"}:
                raise ValueError("Proxy is not allowed by maintenance policy")
            connection = http.client.HTTPSConnection("proxy", 8080, timeout=15)
            connection.set_tunnel("api.github.com", 443)
        else:
            connection = http.client.HTTPSConnection("api.github.com", timeout=15)
        try:
            connection.request(method, target, body=data, headers=headers)
            response = connection.getresponse()
            if not 200 <= response.status < 300:
                limited = response.status == 403 and (
                    response.getheader("X-RateLimit-Remaining") == "0" or
                    response.getheader("Retry-After") is not None)
                raise GitHubHttpError(response.status, limited)
            body = response.read(MAX_BODY + 1)
            if len(body) > MAX_BODY:
                raise GitHubResponseLimitError("GitHub response exceeded limit")
            return json.loads(body)
        finally:
            connection.close()

    def latest_release(self, project):
        if type(project) is not str or project not in RELEASE_PROJECTS:
            raise ValueError("Competitor is not allowed")
        return self.request("GET", RELEASE_PROJECTS[project]["path"], public_release=True)


def release_metadata(project, value):
    if type(value) is not dict or value.get("draft") is not False or value.get("prerelease") is not False:
        raise ReleaseMetadataError("Unsupported release metadata")
    tag, published = value.get("tag_name"), value.get("published_at")
    match = re.fullmatch(r"[vV]?(" + VERSION_PATTERN + r")", tag) if type(tag) is str and len(tag) <= 22 else None
    if match is None or type(published) is not str or not re.fullmatch(
        r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]{1,6})?Z", published
    ):
        raise ReleaseMetadataError("Unsupported release metadata")
    try:
        published = datetime.fromisoformat(published.replace("Z", "+00:00")).isoformat().replace("+00:00", "Z")
    except ValueError:
        raise ReleaseMetadataError("Invalid release timestamp") from None
    return {"latest_version": match[1], "published_at": published,
            "release_url": "https://github.com/" + RELEASE_PROJECTS[project]["repo"] + "/releases/tag/" + quote(tag, safe="")}


def observe_competitor_releases(api):
    observations = []
    for project, policy in RELEASE_PROJECTS.items():
        item = {"project": project, "baseline_version": policy["baseline"], "baseline_date": "2026-11-15",
                "api_url": "https://api.github.com" + policy["path"]}
        try:
            item.update(release_metadata(project, api.latest_release(project)))
            item["status"] = "MATCHES_BASELINE" if item["latest_version"] == policy["baseline"] else "REVIEW_NEEDED"
        except GitHubHttpError as failure:
            item["status"] = "RATE_LIMITED" if failure.rate_limited else (
                "ACCESS_BLOCKED" if failure.status_code in {401, 403} else "HTTP_ERROR")
            item["status_code"] = failure.status_code
        except GitHubResponseLimitError:
            item["status"] = "RESPONSE_TOO_LARGE"
        except (OSError, http.client.HTTPException):
            item["status"] = "NETWORK_ERROR"
        except (ReleaseMetadataError, json.JSONDecodeError, UnicodeDecodeError):
            item["status"] = "UNVERIFIED_METADATA"
        observations.append(item)
    return observations


def render_summary(report):
    mode, updates = report["mode"], report["label_updates"]
    if mode not in {"dry-run", "apply-labels"} or type(updates) is not int or not 0 <= updates <= 15:
        raise ValueError("Invalid summary metadata")
    lines = [f"Maintenance {mode}: issues={len(report['issues'])}; PRs={len(report['pull_requests'])}; label updates={updates}"]
    observations = report["competitor_releases"]
    if report["competitor_check"] == "NOT_REQUESTED" and not observations:
        return "\n".join(lines + ["Competitor release check: NOT_REQUESTED", ""])
    if report["competitor_check"] != "REQUESTED" or len(observations) != len(RELEASE_PROJECTS) or {
        x["project"] for x in observations
    } != set(RELEASE_PROJECTS):
        raise ValueError("Invalid summary metadata")
    lines += ["", "| Public GitHub release | Latest | Baseline | Observation |", "|---|---|---|---|"]
    for item in observations:
        project, status, latest = item["project"], item["status"], item.get("latest_version", "unverified")
        if status not in RELEASE_STATUSES or (latest != "unverified" and (
            type(latest) is not str or not re.fullmatch(VERSION_PATTERN, latest)
        )):
            raise ValueError("Invalid summary metadata")
        lines.append(f"| {project} | {latest} | {RELEASE_PROJECTS[project]['baseline']} | {status} |")
    return "\n".join(lines) + "\n"


def apply_labels(api, plans):
    wanted = set()
    updates = []
    pending = [p for p in plans if p.get("add_labels") and p["category"] != "security"]
    for plan in pending[:15]:
        ident = number(plan["number"])
        if set(plan["labels"]) - LABELS:
            raise ValueError("Unrecognized label")
        if plan["category"] == "security":
            continue  # No write or public comment for a possible security disclosure.
        current = api.request("GET", f"/issues/{ident}")
        if current.get("state") != "open" or "pull_request" in current:
            continue
        # Recalculate from the current issue to avoid applying a stale snapshot's labels.
        fresh = classify(current)
        if fresh["category"] == "security":
            continue
        existing = {x["name"] for x in current.get("labels", [])}
        missing = set(fresh["labels"]) - existing
        # Keep existing human category and priority choices; never remove labels.
        if existing & set(CATEGORIES.values()):
            missing -= set(CATEGORIES.values())
        if existing & {"P0", "P1", "P2", "P3"}:
            missing -= {"P0", "P1", "P2", "P3"}
        if missing:
            updates.append((ident, sorted(missing)))
            wanted |= missing
    known = {x["name"] for x in api.request("GET", "/labels?per_page=100")} if wanted else set()
    for label in sorted(wanted - known):
        api.request("POST", "/labels", {"name": label, "color": "ededed",
                    "description": "eXcor deterministic maintenance triage"})
    for ident, labels in updates:
        api.request("POST", f"/issues/{ident}/labels", {"labels": labels})
    return len(updates)


def snapshot(api, check_competitors=False):
    issues = api.request("GET", "/issues?state=open&per_page=100")
    pulls = api.request("GET", "/pulls?state=open&per_page=100")
    branches = api.request("GET", "/branches?per_page=100")
    repo = api.request("GET", "")
    main = repo["default_branch"]
    if main != "main":
        raise ValueError("Default branch requires maintenance policy review")
    excluded = {main, "automation/excor-competitiveness"}
    excluded |= {p["head"]["ref"] for p in pulls} | {p["base"]["ref"] for p in pulls}
    branch_plan = []
    for branch in branches[:25]:
        name = branch["name"]
        if branch.get("protected") or name in excluded or re.match(r"(?:release[/-]|releases[/-])", name):
            continue
        sha = branch["commit"]["sha"]
        comparison = api.request("GET", f"/compare/{sha}...{quote(main, safe='')}")
        # head=main relative to base=topic: only identical/ahead has no unique topic commits.
        reachable = comparison.get("status") in {"identical", "ahead"} and comparison.get("behind_by") == 0
        branch_plan.append({"branch": name, "sha": sha, "reachable_from_main": reachable,
                            "action": "retain_until_inactivity_and_warning_verified" if reachable else "retain_unique_or_unknown"})
    plans = triage(issues)
    link_fix_claims(plans, pulls)
    return {"generated_at": datetime.now(timezone.utc).isoformat(), "issues": plans,
            "pull_requests": [{"number": number(p["number"]), "head": p["head"]["sha"], "draft": p["draft"]} for p in pulls],
            "branch_audit": branch_plan, "delete_on_merge": repo.get("delete_branch_on_merge"),
            "default_branch_protected": next((b.get("protected") for b in branches if b["name"] == main), None),
            "inventory_complete": len(issues) < 100 and len(pulls) < 100 and len(branches) <= 25,
            "branches_evaluated_limit": 25, "branch_deletion_enabled": False,
            "obsolete_after_days": 30, "warning_period_days": 7,
            "competitor_check": "REQUESTED" if check_competitors else "NOT_REQUESTED",
            "competitor_releases": observe_competitor_releases(api) if check_competitors else []}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--apply-labels", action="store_true")
    parser.add_argument("--check-competitors", action="store_true")
    parser.add_argument("--summary-output", type=Path)
    args = parser.parse_args()
    try:
        api = GitHub(args.repo, os.environ.get("GH_TOKEN", ""))
        report = snapshot(api, check_competitors=args.check_competitors)
        report["label_updates"] = apply_labels(api, report["issues"]) if args.apply_labels else 0
        report["mode"] = "apply-labels" if args.apply_labels else "dry-run"
        args.output.write_text(json.dumps(report, indent=2) + "\n")
        if args.summary_output is not None:
            args.summary_output.write_text(render_summary(report), encoding="utf-8")
        for item in report["competitor_releases"]:
            print(f"Competitor release {item['project']}: {item['status']} latest={item.get('latest_version', 'unverified')} baseline={item['baseline_version']}")
        print(f"Maintenance {report['mode']}: {len(report['issues'])} issues, {len(report['pull_requests'])} PRs; label updates={report['label_updates']}")
    except (ValueError, KeyError, TypeError, OSError, http.client.HTTPException):
        print("Maintenance failed; no response bodies or credentials logged", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
