#!/usr/bin/env python3
"""Evidence-attested 0–100 eXcor readiness; not a live playback benchmark."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import http.client
import json
import os
from pathlib import Path
import re
import sys
from urllib.parse import urlencode

GROUPS = {
    "metadata": ("YouTube and YouTube Music metadata", 20),
    "direct_streaming": ("Direct audio/video streaming", 20),
    "player_cipher": ("Player and cipher compatibility", 15),
    "sabr": ("SABR/UMP playback", 20),
    "reliability_security": ("Reliability and security", 15),
    "platforms": ("Platforms and developer usability", 10),
}


class EvidenceError(ValueError):
    pass


def calculate(evidence: dict, gate_file: dict, root: Path) -> dict:
    if evidence.get("schema_version") != 1:
        raise EvidenceError("Unsupported evidence schema")
    gates = gate_file.get("gates", [])
    if not isinstance(gates, list) or not gates:
        raise EvidenceError("Missing release gates")
    gate_states = {g["id"]: g["status"] for g in gates}
    if len(gates) != len(gate_states):
        raise EvidenceError("Duplicate release gates")
    criteria = evidence.get("criteria")
    if not isinstance(criteria, list) or not criteria:
        raise EvidenceError("Missing criteria")
    groups = {key: {"label": label, "score": 0, "max": maximum, "allocated": 0}
              for key, (label, maximum) in GROUPS.items()}
    seen = set()
    recorded = []
    for c in criteria:
        ident = c.get("id")
        group = c.get("group")
        points = c.get("points")
        state = c.get("state")
        gate = c.get("gate")
        refs = c.get("evidence_paths")
        if not isinstance(ident, str) or not re.fullmatch(r"[a-z0-9_]+", ident) or ident in seen:
            raise EvidenceError("Invalid or repeated criterion ID")
        seen.add(ident)
        if group not in GROUPS or state not in ("verified", "blocked", "unverified"):
            raise EvidenceError(f"Invalid group or state: {ident}")
        if type(points) is not int or points <= 0 or points > 20:
            raise EvidenceError(f"Invalid points: {ident}")
        if gate not in gate_states:
            raise EvidenceError(f"Unknown release gate: {ident}")
        if not isinstance(refs, list) or any(not isinstance(ref, str) for ref in refs):
            raise EvidenceError(f"Invalid evidence references: {ident}")
        if state == "verified":
            if gate_states[gate] == "blocked" or (group == "sabr" and gate_states.get("G6") != "passing"):
                raise EvidenceError(f"Blocked gate cannot award points: {ident}")
            if not refs:
                raise EvidenceError(f"Verified criterion needs proof: {ident}")
            for ref in refs:
                if (not ref or Path(ref).is_absolute() or ".." in Path(ref).parts
                        or not (root / ref).resolve().is_relative_to(root.resolve())
                        or not (root / ref).is_file()):
                    raise EvidenceError(f"Missing or unsafe proof for {ident}: {ref}")
        groups[group]["allocated"] += points
        earned = points if state == "verified" else 0
        groups[group]["score"] += earned
        recorded.append(dict(c, earned=earned))
    for group, totals in groups.items():
        if totals["allocated"] != totals["max"]:
            raise EvidenceError(f"Group weights for {group} must total {totals['max']}")
    if sum(x["max"] for x in groups.values()) != 100:
        raise EvidenceError("Rubric must total 100")
    score = sum(x["score"] for x in groups.values())
    fully_gated = len(gate_states) == 12 and all(s == "passing" for s in gate_states.values())
    if score == 100 and not fully_gated:
        raise EvidenceError("100/100 requires all twelve release gates to pass")
    return {
        "score": score, "max": 100, "target": evidence.get("target", "InnerTubeX"),
        "method": evidence.get("method"), "last_reviewed_utc": evidence.get("last_reviewed_utc"),
        "groups": groups, "criteria": recorded, "release_gates": gate_states,
        "release_decision": "GO" if score == 100 and fully_gated else "NO-GO",
    }


def github_ci(repository: str, sha: str, token: str = "") -> dict:
    info = {"status": "unknown", "conclusion": None, "url": None, "sha": sha}
    if not isinstance(repository, str):
        return info
    owner, separator, name = repository.partition("/")
    allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_.-"
    if (not separator or not owner or not name or owner in {".", ".."} or name in {".", ".."} or "/" in name
            or any(character not in allowed for character in owner + name)):
        return info
    if not re.fullmatch(r"[a-fA-F0-9]{40}", sha or ""):
        return info
    query = urlencode({"branch": "main", "head_sha": sha, "event": "push", "per_page": 20})
    target = f"/repos/{owner}/{name}/actions/workflows/ci.yml/runs?{query}"
    headers = {"Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28",
               "User-Agent": "excor-evidence-checker"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    connection = None
    try:
        connection = http.client.HTTPSConnection("api.github.com", timeout=15)
        connection.request("GET", target, headers=headers)
        reply = connection.getresponse()
        if reply.status != 200:
            return info
        runs = json.load(reply).get("workflow_runs", [])
    except (http.client.HTTPException, OSError, ValueError):
        return info
    finally:
        if connection is not None:
            connection.close()
    runs = [r for r in runs if r.get("head_sha") == sha and r.get("event") == "push" and r.get("head_branch") == "main"]
    if runs:
        run = max(runs, key=lambda r: r.get("run_number", 0))
        info.update(status=run.get("status", "unknown"), conclusion=run.get("conclusion"),
                    url=run.get("html_url"))
    return info


def markdown(report: dict) -> str:
    ci = report["ci"]
    result = [f"# eXcor vs. InnerTubeX: {report['score']}/100", "",
              "**Evidence-weighted readiness, not a live media playback success percentage.**", "",
              f"Release: **{report['release_decision']}** · main CI: **{ci['conclusion'] or ci['status']}** · rubric reviewed {report['last_reviewed_utc']}", "",
              "| Category | Points |", "|---|---:|"]
    for group in report["groups"].values():
        result.append(f"| {group['label']} | {group['score']}/{group['max']} |")
    result.extend(["", "## Missing verification", ""])
    for item in report["criteria"]:
        if item["state"] != "verified":
            result.append(f"- {item['points']} points — {item['label']} ({item['state']}; {item['gate']})")
    result.extend(["", "CI is checked live but never awards streaming/playback points.",
                   "Offline UMP framing, bot checks, and passing compilation alone do not establish SABR parity.",
                   "A 100/100 score requires every release gate to pass.", ""])
    if ci.get("url"):
        result.append(f"[Inspect main CI]({ci['url']})")
    return "\n".join(result) + "\n"


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--root", type=Path, default=Path("."))
    p.add_argument("--out-dir", type=Path, required=True)
    p.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY", ""))
    p.add_argument("--sha", default=os.environ.get("GITHUB_SHA", ""))
    p.add_argument("--fetch-ci", action="store_true")
    a = p.parse_args()
    root = a.root.resolve()
    try:
        evidence = json.loads((root / "docs/competitiveness-evidence.json").read_text())
        gates = json.loads((root / "docs/release-gates.json").read_text())
        report = calculate(evidence, gates, root)
    except (EvidenceError, OSError, ValueError, KeyError, TypeError) as exc:
        print(f"Readiness evidence failed validation: {exc}", file=sys.stderr)
        return 1
    report["checked_date_utc"] = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    report["main_sha"] = a.sha or None
    report["ci"] = github_ci(a.repo, a.sha, os.environ.get("GH_TOKEN", "")) if a.fetch_ci else {
        "status": "not_checked", "conclusion": None, "url": None, "sha": a.sha or None,
    }
    a.out_dir.mkdir(parents=True, exist_ok=True)
    (a.out_dir / "score.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    color = "red" if report["score"] < 40 else "orange" if report["score"] < 60 else "yellow" if report["score"] < 80 else "green"
    badge = {"schemaVersion": 1, "label": "eXcor evidence", "message": f"{report['score']}/100", "color": color}
    (a.out_dir / "badge.json").write_text(json.dumps(badge, indent=2) + "\n")
    (a.out_dir / "README.md").write_text(markdown(report))
    print(f"eXcor evidence score: {report['score']}/100; CI: {report['ci']['status']}; release: {report['release_decision']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
