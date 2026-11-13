#!/usr/bin/env python3
"""Validate and summarize sanitized black-box comparison samples."""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
from typing import Any, Iterable


TOP_KEYS = {
    "schemaVersion", "comparison", "adapter", "adapterVersion", "runtime",
    "lane", "phase", "sampleIndex", "videoId", "outcome", "failureClass",
    "measurements",
}
COMPARISON_KEYS = {
    "id", "mainCommit", "runDateUtc", "os", "networkCategory",
    "contentCategory", "sessionCategory", "timeoutSeconds", "locale",
    "decoderVersion",
}
MEASUREMENT_KEYS = {
    "metadataMs", "extractMs", "startupMs", "firstMediaByteMs", "decodeMs",
    "requestCount", "mediaRequestCount", "bytesRead", "rangeRequests",
    "heapDeltaBytes", "backwardSeek", "forwardSeek", "decode", "codec",
    "mimeType",
}
OPTIONAL_MEASUREMENT_KEYS = {"rangeProbeMethod", "rangeForwardProbe", "rangeBackwardProbe"}
ADAPTERS = {"excor", "innertubex", "youtubejs", "newpipe"}
RUNTIMES = {"jvm", "node"}
LANES = {"direct_audio", "direct_video", "hls", "sabr_ump"}
PHASES = {"cold", "warm"}
OUTCOMES = {"PASS", "BLOCKED", "UNSUPPORTED", "FAIL", "NOT_RUN"}
FAILURE_CLASSES = {
    "ACCESS_DENIED", "UNSUPPORTED_TRANSPORT", "HTTP", "PARSE", "DECODE",
    "TIMEOUT", "OTHER",
}
SEEK_RESULTS = {"PASS", "FAIL", "UNSUPPORTED", "NOT_TESTED"}
RANGE_PROBE_METHODS = {"HTTP_BYTE_RANGE", "NOT_TESTED"}
RANGE_PROBE_RESULTS = {"PASS", "IGNORED", "ACCESS_DENIED", "ERROR", "UNAVAILABLE", "NOT_TESTED"}
NETWORK_CATEGORIES = {"home", "mobile", "ci", "other"}
CONTENT_CATEGORIES = {
    "stable_public_video", "music_video", "live", "unavailable_case", "other_public",
}
OPERATING_SYSTEMS = {"windows", "macos", "linux", "android", "ios"}
LATENCY_METRICS = (
    "metadataMs", "extractMs", "startupMs", "firstMediaByteMs", "decodeMs",
)
NUMERIC_METRICS = LATENCY_METRICS + (
    "requestCount", "mediaRequestCount", "bytesRead", "rangeRequests", "heapDeltaBytes",
)
INT_METRICS = {"requestCount", "mediaRequestCount", "bytesRead", "rangeRequests", "heapDeltaBytes"}
GROUP_KEYS = (
    "id", "mainCommit", "runDateUtc", "os", "networkCategory", "contentCategory",
    "sessionCategory", "timeoutSeconds", "locale", "decoderVersion",
)
SAFE_VERSION = re.compile(r"^[A-Za-z0-9._+\-]{1,64}$")
SAFE_ID = re.compile(r"^[A-Za-z0-9_-]{1,64}$")
VIDEO_ID = re.compile(r"^[A-Za-z0-9_-]{11}$")
COMMIT = re.compile(r"^(?:[0-9a-f]{40}|[0-9a-f]{64})$")
LOCALE = re.compile(r"^[a-z]{2,3}(?:-[A-Z]{2})?$")
CODEC = re.compile(r"^[A-Za-z0-9._+\-]{1,80}$")
MIME = re.compile(r"^[A-Za-z0-9.+\-]{1,80}/[A-Za-z0-9.+\-]{1,80}$")


class ValidationError(ValueError):
    """A safe error that never includes the rejected input value."""


def _exact_keys(value: Any, expected: set[str], where: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ValidationError(f"{where} must be an object")
    keys = set(value)
    if keys != expected:
        raise ValidationError(f"{where} has missing or unknown fields")
    return value


def _enum(value: Any, allowed: set[str], where: str) -> str:
    if not isinstance(value, str) or value not in allowed:
        raise ValidationError(f"{where} has an unsupported value")
    return value


def _version(value: Any, where: str, optional: bool = False) -> str | None:
    if optional and value is None:
        return None
    if not isinstance(value, str) or SAFE_VERSION.fullmatch(value) is None:
        raise ValidationError(f"{where} is invalid")
    return value


def _nonnegative_number(value: Any, where: str) -> int | float | None:
    if value is None:
        return None
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValidationError(f"{where} must be a nonnegative number or null")
    if value < 0 or (isinstance(value, float) and not math.isfinite(value)):
        raise ValidationError(f"{where} must be a nonnegative number or null")
    return value


def _nonnegative_integer(value: Any, where: str) -> int | None:
    if value is None:
        return None
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise ValidationError(f"{where} must be a nonnegative integer or null")
    return value


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValidationError("duplicate JSON field")
        result[key] = value
    return result


def _reject_json_constant(_: str) -> None:
    raise ValidationError("non-finite JSON number")


def validate_sample(raw: Any) -> dict[str, Any]:
    sample = _exact_keys(raw, TOP_KEYS, "sample")
    if sample["schemaVersion"] != 1 or isinstance(sample["schemaVersion"], bool):
        raise ValidationError("schemaVersion must be 1")

    comparison = _exact_keys(sample["comparison"], COMPARISON_KEYS, "comparison")
    if not isinstance(comparison["id"], str) or SAFE_ID.fullmatch(comparison["id"]) is None:
        raise ValidationError("comparison.id is invalid")
    if not isinstance(comparison["mainCommit"], str) or COMMIT.fullmatch(comparison["mainCommit"]) is None:
        raise ValidationError("comparison.mainCommit must be a full commit hash")
    timestamp = comparison["runDateUtc"]
    if not isinstance(timestamp, str) or not timestamp.endswith("Z"):
        raise ValidationError("comparison.runDateUtc must be UTC ISO-8601")
    try:
        datetime.fromisoformat(timestamp[:-1] + "+00:00")
    except ValueError as exc:
        raise ValidationError("comparison.runDateUtc must be UTC ISO-8601") from exc
    _enum(comparison["os"], OPERATING_SYSTEMS, "comparison.os")
    _enum(comparison["networkCategory"], NETWORK_CATEGORIES, "comparison.networkCategory")
    _enum(comparison["contentCategory"], CONTENT_CATEGORIES, "comparison.contentCategory")
    _enum(comparison["sessionCategory"], {"signed_out"}, "comparison.sessionCategory")
    timeout = comparison["timeoutSeconds"]
    if isinstance(timeout, bool) or not isinstance(timeout, int) or not 1 <= timeout <= 3600:
        raise ValidationError("comparison.timeoutSeconds must be between 1 and 3600")
    if not isinstance(comparison["locale"], str) or LOCALE.fullmatch(comparison["locale"]) is None:
        raise ValidationError("comparison.locale is invalid")
    _version(comparison["decoderVersion"], "comparison.decoderVersion", optional=True)

    adapter = _enum(sample["adapter"], ADAPTERS, "adapter")
    _version(sample["adapterVersion"], "adapterVersion")
    _enum(sample["runtime"], RUNTIMES, "runtime")
    _enum(sample["lane"], LANES, "lane")
    _enum(sample["phase"], PHASES, "phase")
    sample_index = sample["sampleIndex"]
    if isinstance(sample_index, bool) or not isinstance(sample_index, int) or sample_index < 1:
        raise ValidationError("sampleIndex must be a positive integer")
    if not isinstance(sample["videoId"], str) or VIDEO_ID.fullmatch(sample["videoId"]) is None:
        raise ValidationError("videoId must be a public 11-character video ID")

    outcome = _enum(sample["outcome"], OUTCOMES, "outcome")
    failure_class = sample["failureClass"]
    if failure_class is not None:
        failure_class = _enum(failure_class, FAILURE_CLASSES, "failureClass")
    required_failure = {
        "PASS": None,
        "BLOCKED": "ACCESS_DENIED",
        "UNSUPPORTED": "UNSUPPORTED_TRANSPORT",
        "NOT_RUN": None,
    }
    if outcome in required_failure and failure_class != required_failure[outcome]:
        raise ValidationError("outcome and failureClass disagree")
    if outcome == "FAIL" and failure_class not in {"HTTP", "PARSE", "DECODE", "TIMEOUT", "OTHER"}:
        raise ValidationError("FAIL requires a technical failureClass")

    measurement_value = sample["measurements"]
    if not isinstance(measurement_value, dict):
        raise ValidationError("measurements must be an object")
    measurement_keys = set(measurement_value)
    missing_measurements = MEASUREMENT_KEYS - measurement_keys
    unknown_measurements = measurement_keys - MEASUREMENT_KEYS - OPTIONAL_MEASUREMENT_KEYS
    if missing_measurements or unknown_measurements:
        raise ValidationError("measurements has missing or unknown fields")
    measurements = dict(measurement_value)
    measurements.setdefault("rangeProbeMethod", "NOT_TESTED")
    measurements.setdefault("rangeForwardProbe", "NOT_TESTED")
    measurements.setdefault("rangeBackwardProbe", "NOT_TESTED")
    for name in LATENCY_METRICS:
        _nonnegative_number(measurements[name], f"measurements.{name}")
    for name in INT_METRICS:
        if name == "heapDeltaBytes":
            value = measurements[name]
            if value is not None and (isinstance(value, bool) or not isinstance(value, int)):
                raise ValidationError("measurements.heapDeltaBytes must be an integer or null")
        else:
            _nonnegative_integer(measurements[name], f"measurements.{name}")
    for name in ("backwardSeek", "forwardSeek", "decode"):
        _enum(measurements[name], SEEK_RESULTS, f"measurements.{name}")
    _enum(measurements["rangeProbeMethod"], RANGE_PROBE_METHODS, "measurements.rangeProbeMethod")
    for name in ("rangeForwardProbe", "rangeBackwardProbe"):
        _enum(measurements[name], RANGE_PROBE_RESULTS, f"measurements.{name}")
    if measurements["rangeProbeMethod"] == "NOT_TESTED" and any(
        measurements[name] != "NOT_TESTED" for name in ("rangeForwardProbe", "rangeBackwardProbe")
    ):
        raise ValidationError("range probe results require a declared method")
    codec = measurements["codec"]
    if codec is not None and (not isinstance(codec, str) or CODEC.fullmatch(codec) is None):
        raise ValidationError("measurements.codec is invalid")
    mime = measurements["mimeType"]
    if mime is not None and (not isinstance(mime, str) or MIME.fullmatch(mime) is None):
        raise ValidationError("measurements.mimeType is invalid")
    if outcome == "PASS" and any(measurements[key] == "FAIL" for key in ("backwardSeek", "forwardSeek", "decode")):
        raise ValidationError("PASS cannot contain a failed seek or decode")
    if outcome != "FAIL" and any(measurements[key] == "FAIL" for key in ("backwardSeek", "forwardSeek", "decode")):
        raise ValidationError("failed seek/decode requires outcome FAIL")

    # Rebuild the object with only validated values; unknown/raw fields never flow to reports.
    return {
        "schemaVersion": 1,
        "comparison": dict(comparison),
        "adapter": adapter,
        "adapterVersion": sample["adapterVersion"],
        "runtime": sample["runtime"],
        "lane": sample["lane"],
        "phase": sample["phase"],
        "sampleIndex": sample_index,
        "videoId": sample["videoId"],
        "outcome": outcome,
        "failureClass": failure_class,
        "measurements": dict(measurements),
    }


def load_samples(path: Path, max_samples: int = 10000) -> list[dict[str, Any]]:
    samples: list[dict[str, Any]] = []
    seen: set[tuple[Any, ...]] = set()
    try:
        stream = path.open("r", encoding="utf-8")
    except OSError as exc:
        raise ValidationError("cannot read input file") from exc
    try:
        with stream:
            for line_number, line in enumerate(stream, start=1):
                if len(line) > 32768:
                    raise ValidationError(f"line {line_number}: input is too large")
                if not line.strip():
                    continue
                try:
                    raw = json.loads(
                        line,
                        object_pairs_hook=_unique_object,
                        parse_constant=_reject_json_constant,
                    )
                    sample = validate_sample(raw)
                except (ValueError, TypeError, KeyError, RecursionError):
                    raise ValidationError(f"line {line_number}: invalid sample") from None
                key = (
                    sample["comparison"]["id"], sample["adapter"], sample["lane"],
                    sample["videoId"], sample["phase"], sample["sampleIndex"],
                )
                if key in seen:
                    raise ValidationError(f"line {line_number}: duplicate sample key")
                seen.add(key)
                samples.append(sample)
                if len(samples) > max_samples:
                    raise ValidationError("input contains too many samples")
    except UnicodeDecodeError:
        raise ValidationError("input is not valid UTF-8") from None
    if not samples:
        raise ValidationError("input contains no samples")
    return samples


def nearest_rank(values: Iterable[int | float], percentile: float) -> int | float | None:
    ordered = sorted(values)
    if not ordered:
        return None
    index = max(0, math.ceil(percentile * len(ordered)) - 1)
    return ordered[index]


def build_report(samples: list[dict[str, Any]]) -> dict[str, Any]:
    groups: dict[tuple[Any, ...], list[dict[str, Any]]] = defaultdict(list)
    for sample in samples:
        comparison = sample["comparison"]
        key = (
            sample["adapter"], sample["adapterVersion"], sample["runtime"],
            sample["lane"], sample["phase"], sample["videoId"],
            *(comparison[name] for name in GROUP_KEYS),
        )
        groups[key].append(sample)

    summaries = []
    for key, rows in sorted(groups.items(), key=lambda pair: tuple(str(v) for v in pair[0])):
        adapter, version, runtime, lane, phase, video_id, *context_values = key
        outcomes = Counter(row["outcome"] for row in rows)
        failures = Counter(row["failureClass"] for row in rows if row["failureClass"] is not None)
        metrics: dict[str, dict[str, Any]] = {}
        for metric in NUMERIC_METRICS:
            values = [
                row["measurements"][metric]
                for row in rows
                if row["outcome"] == "PASS" and row["measurements"][metric] is not None
            ]
            metrics[metric] = {
                "eligibleSamples": len(values),
                "p50": nearest_rank(values, 0.50),
                "p95": nearest_rank(values, 0.95),
            }
        summaries.append({
            "adapter": adapter,
            "adapterVersion": version,
            "runtime": runtime,
            "lane": lane,
            "phase": phase,
            "videoId": video_id,
            "comparison": dict(zip(GROUP_KEYS, context_values)),
            "totalSamples": len(rows),
            "outcomes": {name: outcomes.get(name, 0) for name in sorted(OUTCOMES)},
            "failureClasses": dict(sorted(failures.items())),
            "rangeProbeResults": {
                name: dict(sorted(Counter(row["measurements"][name] for row in rows).items()))
                for name in ("rangeProbeMethod", "rangeForwardProbe", "rangeBackwardProbe")
            },
            "metrics": metrics,
        })
    return {"schemaVersion": 1, "summaries": summaries, "samples": samples}


def render_markdown(report: dict[str, Any]) -> str:
    lines = [
        "# Black-box comparison report",
        "",
        "Timings include only PASS samples. Outcome counts include every attempted sample.",
        "The report emits only validated normalized fields; adapters must exclude secrets from those fields.",
        "",
        "| Comparison | Video ID | OS | Network | Content | Adapter | Version | Lane | Phase | Samples | PASS | BLOCKED | UNSUPPORTED | FAIL | NOT_RUN | HTTP range F/B | First media p50/p95 (ms) | Decode p50/p95 (ms) |",
        "|---|---|---|---|---|---|---|---|---|---:|---:|---:|---:|---:|---:|---|---:|---:|",
    ]
    for row in report["summaries"]:
        outcomes = row["outcomes"]
        range_results = row["rangeProbeResults"]
        forward_counts = range_results["rangeForwardProbe"]
        backward_counts = range_results["rangeBackwardProbe"]
        range_summary = (
            "F " + ", ".join(f"{name}={count}" for name, count in forward_counts.items()) + "; "
            "B " + ", ".join(f"{name}={count}" for name, count in backward_counts.items())
        )
        first_media = row["metrics"]["firstMediaByteMs"]
        first_media_summary = (
            f"{first_media['p50']:.0f}/{first_media['p95']:.0f}"
            if first_media["eligibleSamples"] else "n/a"
        )
        decode = row["metrics"]["decodeMs"]
        decode_summary = f"{decode['p50']:.0f}/{decode['p95']:.0f}" if decode["eligibleSamples"] else "n/a"
        lines.append(
            f"| {row['comparison']['id']} | {row['videoId']} | {row['comparison']['os']} | {row['comparison']['networkCategory']} | "
            f"{row['comparison']['contentCategory']} | {row['adapter']} | {row['adapterVersion']} | {row['lane']} | {row['phase']} | "
            f"{row['totalSamples']} | {outcomes['PASS']} | {outcomes['BLOCKED']} | "
            f"{outcomes['UNSUPPORTED']} | {outcomes['FAIL']} | {outcomes['NOT_RUN']} | {range_summary} | "
            f"{first_media_summary} | {decode_summary} |"
        )
    lines.extend([
        "",
        "HTTP byte-range probes are reported separately from playback seeks. `rangeProbeResults` in JSON contains the method and per-direction result counts; these probes do not establish decoder or player seek behavior.",
        "",
        "First-media and decode table timings show nearest-rank p50/p95 in milliseconds, rounded to whole milliseconds. "
        "JSON retains metric-specific eligible sample counts. All latency summaries are grouped by comparison ID, commit, date, "
        "video ID, OS, network category, content category, signed-out session, timeout, locale, decoder, "
        "adapter version, lane, and cold/warm phase. `eligibleSamples` is shown per metric in JSON.",
        "",
    ])
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path, help="sanitized JSONL adapter results")
    parser.add_argument("--format", choices=("json", "markdown"), default="json")
    parser.add_argument("--output", type=Path, help="output path; defaults to stdout")
    args = parser.parse_args(argv)
    try:
        report = build_report(load_samples(args.input))
    except ValidationError as exc:
        print(f"report rejected: {exc}", file=sys.stderr)
        return 2
    output = json.dumps(report, indent=2, sort_keys=True) if args.format == "json" else render_markdown(report)
    if args.output:
        try:
            args.output.write_text(output + "\n", encoding="utf-8")
        except OSError:
            print("cannot write output file", file=sys.stderr)
            return 2
    else:
        print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
