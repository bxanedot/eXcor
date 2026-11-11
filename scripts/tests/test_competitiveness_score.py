"""Offline integrity tests; no YouTube or account requests."""
import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from excor_readiness import calculate, EvidenceError, markdown

ROOT = Path(__file__).resolve().parents[2]


class ReadinessScoreTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rubric = json.loads((ROOT / "docs/competitiveness-evidence.json").read_text())
        cls.gates = json.loads((ROOT / "docs/release-gates.json").read_text())

    def test_baseline_and_allocation(self):
        r = calculate(self.rubric, self.gates, ROOT)
        self.assertEqual(53, r["score"])
        self.assertEqual([17, 11, 8, 0, 11, 6], [g["score"] for g in r["groups"].values()])
        self.assertEqual(100, sum(g["max"] for g in r["groups"].values()))
        self.assertEqual("NO-GO", r["release_decision"])
        text = markdown({**r, "ci": {"status": "unknown", "conclusion": None, "url": None}})
        self.assertIn("not a live media playback success percentage", text.lower())

    def test_blocked_sabr_cannot_earn_points(self):
        rubric = copy.deepcopy(self.rubric)
        row = next(c for c in rubric["criteria"] if c["id"] == "sabr_audio")
        row["state"] = "verified"
        row["evidence_paths"] = ["docs/release-gates.json"]
        with self.assertRaises(EvidenceError):
            calculate(rubric, self.gates, ROOT)

    def test_missing_proof_rejected(self):
        rubric = copy.deepcopy(self.rubric)
        rubric["criteria"][0]["evidence_paths"] = []
        with self.assertRaises(EvidenceError):
            calculate(rubric, self.gates, ROOT)

    def test_verified_proof_symlink_cannot_escape_repository(self):
        # A tracked-looking path must not point outside the checked-out tree.
        with tempfile.TemporaryDirectory() as directory:
            sandbox = Path(directory)
            root = sandbox / "repo"
            root.mkdir()
            external = sandbox / "outside.txt"
            external.write_text("not repository evidence")
            try:
                (root / "proof.txt").symlink_to(external)
            except (OSError, NotImplementedError):
                self.skipTest("Symlinks are unavailable on this platform")
            rubric = copy.deepcopy(self.rubric)
            for criterion in rubric["criteria"]:
                if criterion["state"] == "verified":
                    criterion["evidence_paths"] = ["proof.txt"]
            with self.assertRaises(EvidenceError):
                calculate(rubric, self.gates, root)

    def test_duplicate_criterion_rejected(self):
        rubric = copy.deepcopy(self.rubric)
        rubric["criteria"][1]["id"] = rubric["criteria"][0]["id"]
        with self.assertRaises(EvidenceError):
            calculate(rubric, self.gates, ROOT)

    def test_group_allocation_cannot_be_inflated(self):
        rubric = copy.deepcopy(self.rubric)
        rubric["criteria"][0]["points"] += 1
        with self.assertRaises(EvidenceError):
            calculate(rubric, self.gates, ROOT)

    def test_100_requires_all_gates_passing(self):
        rubric = copy.deepcopy(self.rubric)
        for c in rubric["criteria"]:
            c["state"] = "verified"
            c["evidence_paths"] = ["docs/release-gates.json"]
        with self.assertRaises(EvidenceError):
            calculate(rubric, self.gates, ROOT)
        gates = copy.deepcopy(self.gates)
        next(g for g in gates["gates"] if g["id"] == "G6")["status"] = "passing"
        with self.assertRaises(EvidenceError):
            calculate(rubric, gates, ROOT)


if __name__ == "__main__":
    unittest.main()
