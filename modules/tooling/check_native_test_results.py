#!/usr/bin/env python3
"""Require executed, passing native test cases; a disabled Gradle task is not verification."""
from __future__ import annotations

import argparse
from itertools import islice
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

MAX_REPORTS = 512
MAX_REPORT_BYTES = 8 * 1024 * 1024
MAX_TOTAL_BYTES = 64 * 1024 * 1024


def _check_counts(node: ET.Element, tests: int, skipped: int) -> None:
    for field, expected in (("failures", 0), ("errors", 0), ("tests", tests), ("skipped", skipped)):
        value = node.get(field)
        if value is not None and (len(value) > 9 or not value.isascii() or
                                  not value.isdigit() or int(value) != expected):
            raise ValueError("Native JUnit counts are failed or inconsistent")


def check_results(directory: Path) -> tuple[int, int]:
    reports = list(islice(directory.glob("TEST-*.xml"), MAX_REPORTS + 1))
    if not reports or len(reports) > MAX_REPORTS:
        raise ValueError("Expected a bounded, nonempty native JUnit report set")
    total = skipped = byte_count = 0
    for report in reports:
        with report.open("rb") as source:
            data = source.read(MAX_REPORT_BYTES + 1)
        byte_count += len(data)
        if len(data) > MAX_REPORT_BYTES or byte_count > MAX_TOTAL_BYTES:
            raise ValueError("Native JUnit reports exceed the verification byte limit")
        if b"\x00" in data or b"<!DOCTYPE" in data.upper() or b"<!ENTITY" in data.upper():
            raise ValueError("Native JUnit declarations are unsupported")
        root = ET.fromstring(data)
        if root.tag not in ("testsuite", "testsuites"):
            raise ValueError("Unexpected native JUnit root")
        suites = [root] if root.tag == "testsuite" else list(root.findall("testsuite"))
        if root.tag == "testsuites" and root.findall("testcase"):
            raise ValueError("Unexpected native JUnit nesting")
        report_total = report_skipped = 0
        for suite in suites:
            if suite.findall("testsuite"):
                raise ValueError("Nested native JUnit suites are unsupported")
            cases = list(suite.findall("testcase"))
            suite_skipped = 0
            for case in cases:
                if case.find("failure") is not None or case.find("error") is not None:
                    raise ValueError("Native JUnit test case failed")
                suite_skipped += case.find("skipped") is not None
            _check_counts(suite, len(cases), suite_skipped)
            report_total += len(cases)
            report_skipped += suite_skipped
        if root.tag == "testsuites":
            _check_counts(root, report_total, report_skipped)
        total += report_total
        skipped += report_skipped
    if total <= skipped:
        raise ValueError("No native test cases executed successfully")
    return total, skipped


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    try:
        total, skipped = check_results(args.directory)
    except (OSError, ValueError, ET.ParseError):
        # Native test output may contain host data; report fixed diagnostics only.
        print("Native test verification failed: missing, invalid, failed or entirely skipped reports", file=sys.stderr)
        return 1
    print(f"Native test verification: {total - skipped} executed successfully, {skipped} skipped")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
