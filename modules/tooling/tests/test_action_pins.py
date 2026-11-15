from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path


SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

import check_action_pins  # noqa: E402


class ActionPinCheckTest(unittest.TestCase):
    def test_accepts_full_sha_with_dependabot_version_comment(self):
        with tempfile.TemporaryDirectory() as directory:
            workflow = Path(directory) / "ci.yml"
            workflow.write_text(
                "jobs:\n  build:\n    steps:\n      - uses: actions/checkout@" + "a" * 40 + " # v7\n",
                encoding="utf-8",
            )

            count, errors = check_action_pins.check_root(Path(directory))

        self.assertEqual(count, 1)
        self.assertEqual(errors, [])

    def test_rejects_movable_tag_reference(self):
        with tempfile.TemporaryDirectory() as directory:
            workflow = Path(directory) / "ci.yml"
            workflow.write_text("jobs:\n  build:\n    steps:\n      - uses: actions/checkout@v7\n", encoding="utf-8")

            count, errors = check_action_pins.check_root(Path(directory))

        self.assertEqual(count, 1)
        self.assertIn("full 40-character commit SHA", errors[0])

    def test_allows_local_actions_without_external_pinning(self):
        with tempfile.TemporaryDirectory() as directory:
            workflow = Path(directory) / "ci.yml"
            workflow.write_text("jobs:\n  build:\n    steps:\n      - uses: ./.github/actions/local\n", encoding="utf-8")

            count, errors = check_action_pins.check_root(Path(directory))

        self.assertEqual(count, 0)
        self.assertEqual(errors, [])


if __name__ == "__main__":
    unittest.main()
