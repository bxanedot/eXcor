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

    def test_accepts_the_direct_video_lane_when_requested(self):
        value = sample()
        value["lane"] = "direct_video"
        result = runner_module._parse_adapter_sample(
            json.dumps(value),
            adapter="excor",
            video_id="dQw4w9WgXcQ",
            phase="cold",
            sample_index=1,
            expected_comparison=self.expected,
            expected_lane="direct_video",
        )
        self.assertEqual(result["lane"], "direct_video")

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
            try:
                runner_module._parse_options(base)
            except runner_module.RunnerError:
                return
            except SystemExit as error:
                self.fail(f"runner rejected conflicting modes through argparse with exit code {error.code}")
            self.fail("runner accepted both full-decode modes")
            options = runner_module._parse_options(base + ["--adapters", "excor", "innertubex"])
            self.assertTrue(options.range_probes)
            self.assertEqual(set(options.adapters), {"excor", "innertubex"})

    def test_full_video_decode_requires_the_matched_jvm_pair(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "results.jsonl"
            base = [
                "--video-id", "dQw4w9WgXcQ",
                "--comparison-id", "run-home-01",
                "--network-category", "home",
                "--content-category", "music_video",
                "--main-commit", "a" * 40,
                "--output", str(output),
                "--full-video-decode",
            ]
            try:
                options = runner_module._parse_options(base + ["--adapters", "excor", "innertubex"])
            except SystemExit as error:
                self.fail(f"runner rejected full video decode with exit code {error.code}")
            self.assertTrue(options.full_video_decode)
            self.assertFalse(options.full_decode)
            with self.assertRaises(runner_module.RunnerError):
                runner_module._parse_options(base + ["--adapters", "excor"])

    def test_audio_and_video_full_decode_modes_are_mutually_exclusive(self):
        with tempfile.TemporaryDirectory() as directory:
            base = [
                "--video-id", "dQw4w9WgXcQ",
                "--comparison-id", "run-home-01",
                "--network-category", "home",
                "--content-category", "music_video",
                "--main-commit", "a" * 40,
                "--output", str(Path(directory) / "results.jsonl"),
                "--full-decode", "--full-video-decode",
                "--adapters", "excor", "innertubex",
            ]
            try:
                runner_module._parse_options(base)
            except runner_module.RunnerError:
                return
            except SystemExit as error:
                self.fail(f"runner rejected conflicting modes through argparse with exit code {error.code}")
            self.fail("runner accepted both full-decode modes")


if __name__ == "__main__":
    unittest.main()
