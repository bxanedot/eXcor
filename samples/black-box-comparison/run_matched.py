#!/usr/bin/env python3
"""Run the isolated direct-audio adapters under one comparison context."""

from __future__ import annotations

import argparse
import json
import os
import platform
import re
import shutil
import subprocess
import sys
from collections import Counter
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Sequence


TOOL_DIR = Path(__file__).resolve().parent
REPO_ROOT = TOOL_DIR.parents[1]
sys.path.insert(0, str(TOOL_DIR))
import report as report_contract  # noqa: E402


ADAPTERS = ("excor", "innertubex", "youtubejs", "newpipe")
VIDEO_ID = re.compile(r"^[A-Za-z0-9_-]{11}$")
SAFE_ID = re.compile(r"^[A-Za-z0-9_-]{1,64}$")
COMMIT = re.compile(r"^(?:[0-9a-f]{40}|[0-9a-f]{64})$")
LOCALE = re.compile(r"^[a-z]{2,3}(?:-[A-Z]{2})?$")


class RunnerError(ValueError):
    """An error safe to display without exposing adapter output."""


@dataclass(frozen=True)
class AdapterSpec:
    name: str
    command: tuple[str, ...]
    working_directory: Path


def _install_script(project: Path, application_name: str) -> Path:
    extension = ".bat" if os.name == "nt" else ""
    return (
        project
        / "build"
        / "install"
        / application_name
        / "bin"
        / f"{application_name}{extension}"
    )


def discover_adapters(names: Sequence[str]) -> list[AdapterSpec]:
    specs: list[AdapterSpec] = []
    projects = {
        "excor": "excor-jvm",
        "innertubex": "innertubex-jvm",
        "newpipe": "newpipe-jvm",
    }
    for name in names:
        if name == "youtubejs":
            node = shutil.which("node")
            project = TOOL_DIR / "youtubejs-node"
            script = project / "comparison-adapter.mjs"
            if node is None or not script.is_file():
                raise RunnerError("YouTube.js adapter prerequisites are missing")
            if not (project / "node_modules" / "youtubei.js").exists():
                raise RunnerError("YouTube.js dependencies are not installed; follow the README build steps")
            specs.append(AdapterSpec(name, (node, str(script)), project))
            continue

        project = TOOL_DIR / projects[name]
        application_name = f"{projects[name]}".replace("-jvm", "-black-box-adapter")
        script = _install_script(project, application_name)
        if not script.is_file():
            raise RunnerError(f"{name} adapter is not installed; follow the README build steps")
        specs.append(AdapterSpec(name, (str(script),), project))
    return specs


def _invoke(spec: AdapterSpec, arguments: Sequence[str], timeout_seconds: int) -> subprocess.CompletedProcess[str]:
    command = [*spec.command, *arguments]
    windows_batch = os.name == "nt" and spec.command[0].lower().endswith(".bat")
    try:
        if windows_batch:
            # Adapter arguments are validated enums, IDs, timestamps, or numeric values; no shell metacharacters
            # can enter the command line. Quote the batch path because Windows user folders often contain spaces.
            batch = f'"{spec.command[0]}"'
            safe_arguments = subprocess.list2cmdline(list(arguments))
            return subprocess.run(
                f"{batch} {safe_arguments}",
                cwd=spec.working_directory,
                shell=True,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=timeout_seconds,
                check=False,
            )
        return subprocess.run(
            command,
            cwd=spec.working_directory,
            shell=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout_seconds,
            check=False,
        )
    except subprocess.TimeoutExpired as exc:
        raise RunnerError("adapter exceeded the comparison timeout") from exc
    except OSError as exc:
        raise RunnerError("adapter process could not be started") from exc


def preflight(specs: Sequence[AdapterSpec]) -> None:
    for spec in specs:
        result = _invoke(spec, ["--help"], 10)
        if result.returncode != 0:
            raise RunnerError(f"{spec.name} adapter preflight failed; no samples were run")


def _parse_adapter_sample(
    stdout: str,
    *,
    adapter: str,
    video_id: str,
    phase: str,
    sample_index: int,
    expected_comparison: dict[str, object],
) -> dict[str, object]:
    lines = [line for line in stdout.splitlines() if line.strip()]
    if len(lines) != 1 or len(lines[0]) > 32768:
        raise RunnerError(f"{adapter} returned an invalid sample; no JSONL output was written")
    try:
        raw = json.loads(
            lines[0],
            object_pairs_hook=report_contract._unique_object,
            parse_constant=report_contract._reject_json_constant,
        )
        sample = report_contract.validate_sample(raw)
    except (ValueError, TypeError, KeyError, RecursionError):
        raise RunnerError(f"{adapter} returned an invalid sample; no JSONL output was written") from None

    if (
        sample["adapter"] != adapter
        or sample["videoId"] != video_id
        or sample["lane"] != "direct_audio"
        or sample["phase"] != phase
        or sample["sampleIndex"] != sample_index
        or sample["comparison"] != expected_comparison
    ):
        raise RunnerError(f"{adapter} returned a mismatched comparison context; no JSONL output was written")
    return sample


def _git_main_commit() -> str:
    try:
        result = subprocess.run(
            ["git", "rev-parse", "main"],
            cwd=REPO_ROOT,
            shell=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=10,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise RunnerError("cannot determine the local main commit; pass --main-commit") from exc
    commit = result.stdout.strip()
    if result.returncode != 0 or COMMIT.fullmatch(commit) is None:
        raise RunnerError("cannot determine the local main commit; pass --main-commit")
    return commit


def _parse_options(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--video-id", action="append", required=True, help="public 11-character video ID; repeat for a matched set")
    parser.add_argument("--comparison-id", required=True)
    parser.add_argument("--network-category", choices=sorted(report_contract.NETWORK_CATEGORIES), required=True)
    parser.add_argument("--content-category", choices=sorted(report_contract.CONTENT_CATEGORIES), required=True)
    parser.add_argument("--phases", nargs="+", choices=sorted(report_contract.PHASES), default=["cold", "warm"])
    parser.add_argument("--sample-indices", nargs="+", type=int, default=[1])
    parser.add_argument("--timeout-seconds", type=int, default=30)
    parser.add_argument("--locale", default="en-US")
    parser.add_argument("--main-commit", help="full eXcor main SHA; defaults to local git rev-parse main")
    parser.add_argument("--run-date-utc", help="shared UTC timestamp; defaults to this run's start time")
    parser.add_argument("--adapters", nargs="+", choices=ADAPTERS, help="defaults to all four adapters")
    parser.add_argument("--output", required=True, type=Path, help="new JSONL file; existing files are never overwritten")
    args = parser.parse_args(argv)

    if not SAFE_ID.fullmatch(args.comparison_id):
        raise RunnerError("comparison ID is invalid")
    if any(VIDEO_ID.fullmatch(video_id) is None for video_id in args.video_id):
        raise RunnerError("video IDs must be public 11-character IDs")
    if len(set(args.video_id)) != len(args.video_id):
        raise RunnerError("video IDs must be unique")
    if not LOCALE.fullmatch(args.locale):
        raise RunnerError("locale is invalid")
    if not 1 <= args.timeout_seconds <= 30:
        raise RunnerError("timeout must be between 1 and 30 seconds")
    if not args.sample_indices or any(index < 1 for index in args.sample_indices):
        raise RunnerError("sample indices must be positive integers")
    if len(set(args.sample_indices)) != len(args.sample_indices):
        raise RunnerError("sample indices must be unique")
    if len(set(args.phases)) != len(args.phases):
        raise RunnerError("phases must be unique")
    args.adapters = args.adapters or list(ADAPTERS)
    if len(set(args.adapters)) != len(args.adapters):
        raise RunnerError("adapters must be unique")
    if len(args.video_id) * len(args.phases) * len(args.sample_indices) * len(args.adapters) > 10000:
        raise RunnerError("comparison request exceeds the report sample limit")

    if args.main_commit is None:
        args.main_commit = _git_main_commit()
    if COMMIT.fullmatch(args.main_commit) is None:
        raise RunnerError("main commit must be a full hexadecimal SHA")

    if args.run_date_utc is None:
        args.run_date_utc = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    if not isinstance(args.run_date_utc, str) or not args.run_date_utc.endswith("Z"):
        raise RunnerError("run date must be a UTC ISO-8601 timestamp")
    try:
        datetime.fromisoformat(args.run_date_utc[:-1] + "+00:00")
    except ValueError as exc:
        raise RunnerError("run date must be a UTC ISO-8601 timestamp") from exc

    args.output = args.output.expanduser().resolve()
    if args.output.exists():
        raise RunnerError("output path already exists; choose a new JSONL file")
    return args


def run(argv: Sequence[str] | None = None) -> int:
    try:
        args = _parse_options(argv)
        specs = discover_adapters(args.adapters)
        preflight(specs)
        comparison: dict[str, object] = {
            "id": args.comparison_id,
            "mainCommit": args.main_commit,
            "runDateUtc": args.run_date_utc,
            "os": {
                "Windows": "windows",
                "Darwin": "macos",
                "Linux": "linux",
            }.get(platform.system(), "other"),
            "networkCategory": args.network_category,
            "contentCategory": args.content_category,
            "sessionCategory": "signed_out",
            "timeoutSeconds": args.timeout_seconds,
            "locale": args.locale,
            "decoderVersion": None,
        }
        if comparison["os"] == "other":
            raise RunnerError("unsupported operating system")

        samples: list[dict[str, object]] = []
        seen: set[tuple[object, ...]] = set()
        for video_id in args.video_id:
            for phase in args.phases:
                for sample_index in args.sample_indices:
                    for spec in specs:
                        print(f"Running {spec.name}: phase={phase}, sample={sample_index}")
                        adapter_args = [
                            "--video-id", video_id,
                            "--comparison-id", args.comparison_id,
                            "--main-commit", args.main_commit,
                            "--run-date-utc", args.run_date_utc,
                            "--network-category", args.network_category,
                            "--content-category", args.content_category,
                            "--locale", args.locale,
                            "--timeout-seconds", str(args.timeout_seconds),
                            "--phase", phase,
                            "--sample-index", str(sample_index),
                        ]
                        result = _invoke(spec, adapter_args, args.timeout_seconds + 10)
                        if result.returncode != 0:
                            raise RunnerError(f"{spec.name} failed with exit code {result.returncode}; no JSONL output was written")
                        sample = _parse_adapter_sample(
                            result.stdout,
                            adapter=spec.name,
                            video_id=video_id,
                            phase=phase,
                            sample_index=sample_index,
                            expected_comparison=comparison,
                        )
                        key = (
                            sample["comparison"]["id"], sample["adapter"], sample["lane"],
                            sample["videoId"], sample["phase"], sample["sampleIndex"],
                        )
                        if key in seen:
                            raise RunnerError("comparison produced a duplicate sample; no JSONL output was written")
                        seen.add(key)
                        samples.append(sample)

        args.output.parent.mkdir(parents=True, exist_ok=True)
        created = False
        try:
            with args.output.open("x", encoding="utf-8", newline="\n") as stream:
                created = True
                for sample in samples:
                    stream.write(json.dumps(sample, separators=(",", ":"), ensure_ascii=False) + "\n")
        except OSError as exc:
            if created:
                try:
                    args.output.unlink()
                except OSError:
                    pass
            raise RunnerError("could not write the JSONL output") from exc

        outcomes = Counter(sample["outcome"] for sample in samples)
        summary = ", ".join(f"{name}={outcomes[name]}" for name in ("PASS", "BLOCKED", "UNSUPPORTED", "FAIL", "NOT_RUN") if outcomes[name])
        print(f"Saved {len(samples)} sanitized samples to {args.output}")
        print(f"Outcomes: {summary or 'none'}")
        return 0
    except RunnerError as exc:
        print(f"comparison aborted: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(run())
