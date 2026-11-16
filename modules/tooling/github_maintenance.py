#!/usr/bin/env python3
"""Bounded deterministic issue triage; no issue text is executed or reported."""
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

    def request(self, method, path, payload=None):
        if self.calls >= 80 or method not in {"GET", "POST"} or (path != "" and not path.startswith("/")):
            raise ValueError("API request limit or method rejected")
        target = "/repos/bxanedot/eXcor" + path
        # Validate the final origin-form target at the network boundary, not only its callers.
        if not re.fullmatch(
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
        if self.token:
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
                raise ValueError(f"GitHub API HTTP {response.status}")
            body = response.read(MAX_BODY + 1)
            if len(body) > MAX_BODY:
                raise ValueError("GitHub response exceeded limit")
            return json.loads(body)
        finally:
            connection.close()


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


def snapshot(api):
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
            "obsolete_after_days": 30, "warning_period_days": 7}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--apply-labels", action="store_true")
    args = parser.parse_args()
    try:
        api = GitHub(args.repo, os.environ.get("GH_TOKEN", ""))
        report = snapshot(api)
        report["label_updates"] = apply_labels(api, report["issues"]) if args.apply_labels else 0
        report["mode"] = "apply-labels" if args.apply_labels else "dry-run"
        args.output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Maintenance {report['mode']}: {len(report['issues'])} issues, {len(report['pull_requests'])} PRs; label updates={report['label_updates']}")
    except (ValueError, KeyError, TypeError, OSError, http.client.HTTPException):
        print("Maintenance failed; no response bodies or credentials logged", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
