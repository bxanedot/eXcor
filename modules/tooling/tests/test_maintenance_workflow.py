"""Exercise actual maintenance conditions; no workflow or issue text is executed."""
import ast
from pathlib import Path
import re
import unittest

WORKFLOW = Path(__file__).resolve().parents[3] / ".github/workflows/maintenance.yml"


def evaluate_condition(expression, contexts):
    expression = expression.replace("&&", " and ").replace("||", " or ")
    expression = re.sub(r"(?<!['\"\w])false(?!['\"\w])", "False", expression)
    expression = re.sub(r"(?<!['\"\w])true(?!['\"\w])", "True", expression)

    def evaluate(node):
        if isinstance(node, ast.Constant) and type(node.value) in (str, bool):
            return node.value
        if isinstance(node, ast.Name):
            return contexts[node.id]
        if isinstance(node, ast.Attribute):
            return evaluate(node.value)[node.attr]
        if isinstance(node, ast.BoolOp):
            values = [evaluate(value) for value in node.values]
            if isinstance(node.op, ast.And): return all(values)
            if isinstance(node.op, ast.Or): return any(values)
        if isinstance(node, ast.Compare) and len(node.ops) == 1:
            left, right = evaluate(node.left), evaluate(node.comparators[0])
            if isinstance(left, str) and isinstance(right, str):
                left, right = left.casefold(), right.casefold()
            if isinstance(node.ops[0], ast.Eq): return left == right
            if isinstance(node.ops[0], ast.NotEq): return left != right
        raise AssertionError("Unsupported maintenance-condition syntax; extend the gate review")

    return evaluate(ast.parse(expression.strip(), mode="eval").body)


def writer_enabled(setting, event, dry_run):
    section = WORKFLOW.read_text(encoding="utf-8").split("\n  triage:\n", 1)[1]
    match = re.search(r"^    if: >-\n((?:      .*\n)+)", section, re.M)
    expression = " ".join(line.strip() for line in match[1].splitlines())
    contexts = {"vars": {"EXCOR_MAINTENANCE_APPLY_LABELS": setting},
                "github": {"event_name": event}, "inputs": {"dry_run": dry_run}}
    return evaluate_condition(expression, contexts)


class MaintenanceWorkflowTest(unittest.TestCase):
    def test_manual_dry_runs_never_enable_writes(self):
        for setting in ("", "true", "false", "unexpected"):
            self.assertFalse(writer_enabled(setting, "workflow_dispatch", True))

    def test_scheduled_and_issue_writes_default_on_and_honor_disable(self):
        for event in ("schedule", "issues"):
            for setting in ("", "true"):
                self.assertTrue(writer_enabled(setting, event, False))
            for setting in ("false", "unexpected", " true "):
                self.assertFalse(writer_enabled(setting, event, False))

    def test_manual_apply_requires_enabled_policy(self):
        for setting in ("", "true"):
            self.assertTrue(writer_enabled(setting, "workflow_dispatch", False))
        for setting in ("false", "unexpected"):
            self.assertFalse(writer_enabled(setting, "workflow_dispatch", False))

    def test_competitor_reads_only_run_for_schedule_and_manual_events(self):
        match = re.search(r"EXCOR_CHECK_COMPETITORS: \$\{\{(.*?)\}\}", WORKFLOW.read_text(encoding="utf-8"))
        for event, expected in [("schedule", True), ("workflow_dispatch", True),
                                ("issues", False), ("pull_request", False)]:
            self.assertEqual(expected, evaluate_condition(match[1], {"github": {"event_name": event}}))


if __name__ == "__main__":
    unittest.main()
