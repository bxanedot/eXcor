import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("comparison_runner", ROOT / "run_matched.py")
runner_module = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = runner_module
SPEC.loader.exec_module(runner_module)


def sample():
    return {
        "schemaVersion": 1,
        "comparison": {
            "id": "run-home-01",
            "mainCommit": "a" * 40,
            "runDateUtc": "2026-11-12T12:00:00Z",
            "os": "linux",
            "networkCategory": "home",
            "contentCategory": "music_video",
            "sessionCategory": "signed_out",
            "timeoutSeconds": 30,
            "locale": "en-US",
            "decoderVersion": None,
        },
        "adapter": "excor",
        "adapterVersion": "0.1.0-excor0.2.0",
        "runtime": "jvm",
        "lane": "direct_audio",
        "phase": "cold",
        "sampleIndex": 1,
        "videoId": "dQw4w9WgXcQ",
        "outcome": "PASS",
        "failureClass": None,
        "measurements": {
            "metadataMs": 25,
            "extractMs": 125,
            "startupMs": None,
            "firstMediaByteMs": 250,
            "decodeMs": None,
            "requestCount": 4,
            "mediaRequestCount": 1,
            "bytesRead": 65536,
            "rangeRequests": 1,
            "heapDeltaBytes": 1024,
            "backwardSeek": "NOT_TESTED",
            "forwardSeek": "NOT_TESTED",
            "decode": "NOT_TESTED",
            "codec": "opus",
            "mimeType": "audio/webm",
            "rangeProbeMethod": "NOT_TESTED",
            "rangeForwardProbe": "NOT_TESTED",
            "rangeBackwardProbe": "NOT_TESTED",
        },
    }


class MatchedRunnerSampleTest(unittest.TestCase):
    def setUp(self):
        self.expected = sample()["comparison"]

    def test_accepts_one_sanitized_sample_with_the_shared_context(self):
        result = runner_module._parse_adapter_sample(
            json.dumps(sample()),
            adapter="excor",
            video_id="dQw4w9WgXcQ",
            phase="cold",
            sample_index=1,
            expected_comparison=self.expected,
        )
        self.assertEqual(result["outcome"], "PASS")

    def test_rejects_samples_with_a_different_comparison_context(self):
        value = sample()
        value["comparison"]["runDateUtc"] = "2026-11-12T12:00:01Z"
        with self.assertRaises(runner_module.RunnerError):
            runner_module._parse_adapter_sample(
                json.dumps(value),
                adapter="excor",
                video_id="dQw4w9WgXcQ",
                phase="cold",
                sample_index=1,
                expected_comparison=self.expected,
            )

    def test_rejects_multiple_lines_without_echoing_raw_adapter_output(self):
        secret = "https://video.example/private?token=secret"
        with self.assertRaises(runner_module.RunnerError) as raised:
            runner_module._parse_adapter_sample(
                secret + "\n{}",
                adapter="excor",
                video_id="dQw4w9WgXcQ",
                phase="cold",
                sample_index=1,
                expected_comparison=self.expected,
            )
        self.assertNotIn("video.example", str(raised.exception))
        self.assertNotIn("secret", str(raised.exception))

    def test_range_probes_require_the_matched_jvm_pair(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "results.jsonl"
            base = [
                "--video-id", "dQw4w9WgXcQ",
                "--comparison-id", "run-home-01",
                "--network-category", "home",
                "--content-category", "music_video",
                "--main-commit", "a" * 40,
                "--output", str(output),
                "--range-probes",
            ]
            with self.assertRaises(runner_module.RunnerError):
                runner_module._parse_options(base)
            options = runner_module._parse_options(base + ["--adapters", "excor", "innertubex"])
            self.assertTrue(options.range_probes)
            self.assertEqual(set(options.adapters), {"excor", "innertubex"})


if __name__ == "__main__":
    unittest.main()
