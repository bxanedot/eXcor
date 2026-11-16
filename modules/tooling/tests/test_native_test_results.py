from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_native_test_results import check_results


class NativeTestResultsTest(unittest.TestCase):
    def check_xml(self, text):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "TEST-synthetic.xml").write_text(text)
            return check_results(root)

    def test_counts_executed_and_skipped_cases(self):
        self.assertEqual((2, 1), self.check_xml('<testsuite tests="2" failures="0" errors="0"><testcase/><testcase><skipped/></testcase></testsuite>'))
        self.assertEqual((1, 0), self.check_xml('<testsuites><testsuite failures="0"><testcase/></testsuite></testsuites>'))

    def test_rejects_disabled_missing_or_entirely_skipped_tests(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError): check_results(Path(directory))
        for text in ('<testsuite tests="0"/>', '<testsuite><testcase><skipped/></testcase></testsuite>'):
            with self.subTest(text=text), self.assertRaises(ValueError): self.check_xml(text)

    def test_rejects_failed_cases_and_aggregate_errors(self):
        for text in ('<testsuite failures="1"><testcase/></testsuite>',
                     '<testsuite errors="1"><testcase/></testsuite>',
                     '<testsuite failures="invalid"><testcase/></testsuite>',
                     '<testsuite><testcase><failure/></testcase></testsuite>',
                     '<testsuite><testcase><error/></testcase></testsuite>'):
            with self.subTest(text=text), self.assertRaises(ValueError): self.check_xml(text)

    def test_rejects_counter_mismatches_instead_of_counting_skips_as_execution(self):
        for text in ('<testsuite tests="2"><testcase/></testsuite>',
                     '<testsuite skipped="1"><testcase/></testsuite>',
                     '<testsuite tests="999999999999999999999"><testcase/></testsuite>',
                     '<testsuite><testsuite><testcase/></testsuite></testsuite>',
                     '<testsuites errors="1"><testsuite><testcase/></testsuite></testsuites>',
                     '<testsuites skipped="1"><testsuite><testcase/></testsuite></testsuites>'):
            with self.subTest(text=text), self.assertRaises(ValueError): self.check_xml(text)

    def test_rejects_declarations_and_non_junit_roots(self):
        for text in ('<!DOCTYPE testsuite><testsuite><testcase/></testsuite>', '<response><testcase/></response>'):
            with self.subTest(text=text), self.assertRaises(ValueError): self.check_xml(text)

    def test_bounds_report_bytes_and_inventory(self):
        with patch('check_native_test_results.MAX_REPORT_BYTES', 8):
            with self.assertRaises(ValueError): self.check_xml('<testsuite><testcase/></testsuite>')
        with patch('check_native_test_results.MAX_TOTAL_BYTES', 8):
            with self.assertRaises(ValueError): self.check_xml('<testsuite><testcase/></testsuite>')
        with patch('check_native_test_results.MAX_REPORTS', 0):
            with self.assertRaises(ValueError): self.check_xml('<testsuite><testcase/></testsuite>')


if __name__ == "__main__": unittest.main()
