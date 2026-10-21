"""Regression tests for the evidence checker; no network or JVM required."""
import contextlib
import io
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import summarize_test_results as report


class ReportTests(unittest.TestCase):
    def run_case(self, xml, expected, outcome='success'):
        with tempfile.TemporaryDirectory() as directory:
            if xml is not None:
                Path(directory, 'TEST-fixture.xml').write_text(xml, encoding='utf-8')
            summary = Path(directory, 'summary.md')
            with patch('sys.argv', ['report', '--directory', directory]), patch.dict(os.environ, {'GITHUB_STEP_SUMMARY': str(summary), 'KOTLIN_OUTCOME': outcome}), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(report.main(), expected)
            self.assertIn('Testes Kotlin/JVM', summary.read_text())

    def test_success(self):
        self.run_case('<testsuite tests="2" failures="0" errors="0" skipped="1"><testcase/><testcase><skipped/></testcase></testsuite>', 0)

    def test_failure(self):
        self.run_case('<testsuite tests="1" failures="1"><testcase><failure>PRIVATE_PAYLOAD</failure></testcase></testsuite>', 1)

    def test_error(self):
        self.run_case('<testsuite tests="1" errors="1"><testcase><error/></testcase></testsuite>', 1)

    def test_missing(self):
        self.run_case(None, 1)

    def test_malformed(self):
        self.run_case('<broken', 1)

    def test_empty(self):
        self.run_case('<testsuite tests="0"/>', 1)

    def test_all_skipped(self):
        self.run_case('<testsuite tests="1" skipped="1"><testcase><skipped/></testcase></testsuite>', 1)

    def test_inconsistent(self):
        self.run_case('<testsuite tests="3"><testcase/></testsuite>', 1)

    def test_wrong_root(self):
        self.run_case('<testsuites/>', 1)

    def test_bad_number(self):
        self.run_case('<testsuite tests="oops"/>', 1)

    def test_gradle_failure_not_hidden(self):
        self.run_case('<testsuite tests="1"><testcase/></testsuite>', 1, 'failure')

    def test_multiple_files(self):
        with tempfile.TemporaryDirectory() as directory:
            for name in ['A', 'B']:
                Path(directory, f'TEST-{name}.xml').write_text('<testsuite tests="1"><testcase/></testsuite>')
            self.assertEqual(report.collect(directory)['tests'], 2)

    def test_payload_not_in_summary(self):
        with tempfile.TemporaryDirectory() as directory:
            Path(directory, 'TEST-private.xml').write_text('<testsuite tests="1" failures="1"><testcase><failure>PRIVATE_PAYLOAD</failure></testcase></testsuite>')
            with patch('sys.argv', ['report', '--directory', directory]), patch.dict(os.environ, {'GITHUB_STEP_SUMMARY': '', 'KOTLIN_OUTCOME': 'failure'}), contextlib.redirect_stdout(io.StringIO()) as output:
                self.assertEqual(report.main(), 1)
            self.assertNotIn('PRIVATE_PAYLOAD', output.getvalue())


if __name__ == '__main__':
    unittest.main()
