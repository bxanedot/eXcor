import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("comparison_report", ROOT / "report.py")
report_module = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = report_module
SPEC.loader.exec_module(report_module)


def sample(index, outcome="PASS", extract_ms=100, phase="cold", lane="direct_audio"):
    failure_class = {
        "PASS": None,
        "BLOCKED": "ACCESS_DENIED",
        "UNSUPPORTED": "UNSUPPORTED_TRANSPORT",
        "FAIL": "PARSE",
        "NOT_RUN": None,
    }[outcome]
    return {
        "schemaVersion": 1,
        "comparison": {
            "id": "run-20261111-home-01",
            "mainCommit": "a" * 40,
            "runDateUtc": "2026-11-11T18:30:00Z",
            "os": "linux",
            "networkCategory": "home",
            "contentCategory": "music_video",
            "sessionCategory": "signed_out",
            "timeoutSeconds": 120,
            "locale": "en-US",
            "decoderVersion": "ffmpeg-7.1",
        },
        "adapter": "excor",
        "adapterVersion": "0.2.0",
        "runtime": "jvm",
        "lane": lane,
        "phase": phase,
        "sampleIndex": index,
        "videoId": "dQw4w9WgXcQ",
        "outcome": outcome,
        "failureClass": failure_class,
        "measurements": {
            "metadataMs": 50,
            "extractMs": extract_ms,
            "startupMs": 200,
            "firstMediaByteMs": 300,
            "decodeMs": 1000,
            "requestCount": 2,
            "mediaRequestCount": 1,
            "bytesRead": 4096,
            "rangeRequests": 1,
            "heapDeltaBytes": -2048,
            "backwardSeek": "NOT_TESTED",
            "forwardSeek": "NOT_TESTED",
            "decode": "PASS",
            "codec": "opus",
            "mimeType": "audio/webm",
            "rangeProbeMethod": "NOT_TESTED",
            "rangeForwardProbe": "NOT_TESTED",
            "rangeBackwardProbe": "NOT_TESTED",
        },
    }


class ComparisonReportTest(unittest.TestCase):
    def test_percentiles_include_only_passes_but_counts_keep_blocked_samples(self):
        records = [
            report_module.validate_sample(sample(1, extract_ms=100)),
            report_module.validate_sample(sample(2, extract_ms=300)),
            report_module.validate_sample(sample(3, outcome="BLOCKED", extract_ms=None)),
        ]

        result = report_module.build_report(records)
        summary = result["summaries"][0]
        self.assertEqual(summary["totalSamples"], 3)
        self.assertEqual(summary["outcomes"]["PASS"], 2)
        self.assertEqual(summary["outcomes"]["BLOCKED"], 1)
        self.assertEqual(summary["failureClasses"], {"ACCESS_DENIED": 1})
        self.assertEqual(summary["metrics"]["extractMs"], {
            "eligibleSamples": 2,
            "p50": 100,
            "p95": 300,
        })

    def test_cold_warm_and_transport_lanes_are_separate_groups(self):
        rows = [
            report_module.validate_sample(sample(1, phase="cold")),
            report_module.validate_sample(sample(2, phase="warm")),
            report_module.validate_sample(sample(3, lane="hls")),
        ]
        result = report_module.build_report(rows)
        self.assertEqual(len(result["summaries"]), 3)

    def test_rejects_unknown_fields_without_echoing_values(self):
        row = sample(1)
        row["signedUrl"] = "https://video.example/private?token=secret"
        with self.assertRaises(report_module.ValidationError) as raised:
            report_module.validate_sample(row)
        self.assertNotIn("video.example", str(raised.exception))
        self.assertNotIn("secret", str(raised.exception))

    def test_rejects_raw_url_in_mime_without_echoing_it(self):
        row = sample(1)
        row["measurements"]["mimeType"] = "https://video.example/private?token=secret"
        with self.assertRaises(report_module.ValidationError) as raised:
            report_module.validate_sample(row)
        self.assertNotIn("video.example", str(raised.exception))
        self.assertNotIn("secret", str(raised.exception))

    def test_outcome_and_failure_class_must_match(self):
        row = sample(1, outcome="BLOCKED")
        row["failureClass"] = "PARSE"
        with self.assertRaises(report_module.ValidationError):
            report_module.validate_sample(row)

    def test_failed_seek_requires_failed_sample(self):
        row = sample(1)
        row["measurements"]["backwardSeek"] = "FAIL"
        with self.assertRaises(report_module.ValidationError):
            report_module.validate_sample(row)

    def test_reports_http_byte_ranges_separately_from_playback_seeks(self):
        row = sample(1)
        row["measurements"].update({
            "rangeProbeMethod": "HTTP_BYTE_RANGE",
            "rangeForwardProbe": "PASS",
            "rangeBackwardProbe": "IGNORED",
            "backwardSeek": "NOT_TESTED",
            "forwardSeek": "NOT_TESTED",
        })
        result = report_module.build_report([report_module.validate_sample(row)])
        summary = result["summaries"][0]
        self.assertEqual(summary["rangeProbeResults"]["rangeProbeMethod"], {"HTTP_BYTE_RANGE": 1})
        self.assertEqual(summary["rangeProbeResults"]["rangeForwardProbe"], {"PASS": 1})
        self.assertEqual(summary["rangeProbeResults"]["rangeBackwardProbe"], {"IGNORED": 1})
        self.assertEqual(result["samples"][0]["measurements"]["backwardSeek"], "NOT_TESTED")

    def test_legacy_schema_v1_samples_default_new_range_fields(self):
        row = sample(1)
        for key in ("rangeProbeMethod", "rangeForwardProbe", "rangeBackwardProbe"):
            del row["measurements"][key]
        validated = report_module.validate_sample(row)
        self.assertEqual(validated["measurements"]["rangeProbeMethod"], "NOT_TESTED")
        self.assertEqual(validated["measurements"]["rangeForwardProbe"], "NOT_TESTED")
        self.assertEqual(validated["measurements"]["rangeBackwardProbe"], "NOT_TESTED")

    def test_rejects_range_probe_results_without_declaring_method(self):
        row = sample(1)
        row["measurements"]["rangeForwardProbe"] = "PASS"
        with self.assertRaises(report_module.ValidationError):
            report_module.validate_sample(row)

    def test_load_rejects_duplicate_json_keys_and_duplicate_sample_keys(self):
        encoded = json.dumps(sample(1))
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "results.jsonl"
            path.write_text('{"schemaVersion":1,"schemaVersion":1}\n', encoding="utf-8")
            with self.assertRaisesRegex(report_module.ValidationError, "line 1"):
                report_module.load_samples(path)
            path.write_text(encoded + "\n" + encoded + "\n", encoding="utf-8")
            with self.assertRaisesRegex(report_module.ValidationError, "duplicate sample key"):
                report_module.load_samples(path)

    def test_load_rejects_invalid_utf8_without_traceback_content(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "results.jsonl"
            path.write_bytes(b"\xff\xfe\n")
            with self.assertRaisesRegex(report_module.ValidationError, "valid UTF-8"):
                report_module.load_samples(path)

    def test_load_rejects_integer_too_large_for_json_parser(self):
        encoded = json.dumps(sample(1)).replace('"extractMs": 100', '"extractMs": ' + "9" * 5000)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "results.jsonl"
            path.write_text(encoded + "\n", encoding="utf-8")
            with self.assertRaisesRegex(report_module.ValidationError, "line 1: invalid sample"):
                report_module.load_samples(path)

    def test_nearest_rank_percentiles_are_deterministic(self):
        values = list(range(1, 101))
        self.assertEqual(report_module.nearest_rank(values, 0.50), 50)
        self.assertEqual(report_module.nearest_rank(values, 0.95), 95)


if __name__ == "__main__":
    unittest.main()
