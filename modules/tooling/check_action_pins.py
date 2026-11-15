#!/usr/bin/env python3
"""Require immutable, version-labeled references for actions in GitHub workflows."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


STEP_USE = re.compile(r"^\s*-\s*uses:\s*(?P<reference>[^\s#]+)(?P<suffix>.*)$")
ACTION_REFERENCE = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*@[0-9a-f]{40}$")
DOCKER_REFERENCE = re.compile(r"^docker://[^\s@]+@sha256:[0-9a-f]{64}$")
VERSION_COMMENT = re.compile(r"^\s+#\s+v[0-9][A-Za-z0-9._+-]*\s*$")


def check_workflow(path: Path) -> tuple[int, list[str]]:
    count = 0
    errors: list[str] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        match = STEP_USE.match(line)
        if match is None:
            continue
        reference = match.group("reference")
        if reference.startswith("./"):
            continue
        count += 1
        if reference.startswith("docker://"):
            if not DOCKER_REFERENCE.fullmatch(reference):
                errors.append(f"{path}:{line_number}: container action must use an immutable sha256 digest")
            continue
        if ACTION_REFERENCE.fullmatch(reference) is None:
            errors.append(f"{path}:{line_number}: external action must use a full 40-character commit SHA")
            continue
        if VERSION_COMMENT.fullmatch(match.group("suffix")) is None:
            errors.append(f"{path}:{line_number}: SHA-pinned action must keep a version comment for Dependabot")
    return count, errors


def check_root(root: Path) -> tuple[int, list[str]]:
    workflows = sorted((*root.rglob("*.yml"), *root.rglob("*.yaml")))
    if not workflows:
        return 0, [f"{root}: no workflow files found"]
    total = 0
    errors: list[str] = []
    for path in workflows:
        count, path_errors = check_workflow(path)
        total += count
        errors.extend(path_errors)
    return total, errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(".github/workflows"))
    args = parser.parse_args()
    root = args.root.resolve()
    if not root.is_dir():
        print("workflow directory does not exist", file=sys.stderr)
        return 2
    count, errors = check_root(root)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Immutable action pin check passed ({count} external references).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
