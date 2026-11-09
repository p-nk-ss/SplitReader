#!/usr/bin/env python3
"""Unit tests for report.py (JUnit XML -> summary.md). TDD per the task-6 brief: written and
observed failing (report.py did not exist / did not implement the behaviour yet) before
report.py was implemented.

Run: python3 e2e/test_report.py
"""
import json
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import report  # noqa: E402

# A JUnit XML sample matching maestro's own JUnitTestSuiteReporter shape (verified against the
# installed Maestro 2.12.1 CLI jar, maestro/cli/report/JUnitTestSuiteReporter$TestSuite and
# $TestCase: testsuite attrs name/device/tests/failures/time/timestamp; testcase attrs
# id/name/classname/file/time/timestamp/status (FlowStatus: SUCCESS/ERROR/...), optional nested
# <failure message="...">body</failure>).
SAMPLE_JUNIT = """<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="e2e/flows" device="emulator-5554" tests="2" failures="1" time="12.345" timestamp="2026-11-08T15:30:00">
    <testcase id="j_lib_01" name="J-LIB-01 open library" classname="e2e.flows" file="flows/j_lib_01.yaml" time="5.012" timestamp="2026-11-08T15:30:05" status="SUCCESS"/>
    <testcase id="j_imp_01" name="J-IMP-01 import book" classname="e2e.flows" file="flows/j_imp_01.yaml" time="7.345" timestamp="2026-11-08T15:30:12" status="ERROR">
      <failure message="Assertion is false: &quot;.*Wrong Title.*&quot; is visible">at step 24: assertVisible</failure>
    </testcase>
  </testsuite>
</testsuites>
"""

SAMPLE_META = {
    "device": "Tab_35_PixelTablet (emulator-5554)",
    "avd": "Tab_35_PixelTablet",
    "api": 35,
    "build_type": "debug",
    "version_name": "1.0",
    "version_code": 24,
    "date": "2026-11-08",
    "tags": ["smoke"],
}


class ReportTest(unittest.TestCase):
    def setUp(self):
        self.run_dir = Path(tempfile.mkdtemp(prefix="e2e_report_test_"))
        (self.run_dir / "junit.xml").write_text(SAMPLE_JUNIT, encoding="utf-8")
        (self.run_dir / "meta.json").write_text(json.dumps(SAMPLE_META), encoding="utf-8")
        # Mirror the real per-flow screenshot layout maestro writes under --test-output-dir
        # (observed with Maestro 2.12.1): one extra timestamped directory, then
        # <flow-file-stem>/screenshots/step-NNN-....png — not directly under run_dir.
        shots = self.run_dir / "2026-11-08_154709" / "j_imp_01" / "screenshots"
        shots.mkdir(parents=True)
        (shots / "step-007-runFlow.png").write_bytes(b"x")
        (shots / "step-024-assertCondition.png").write_bytes(b"x")

    def tearDown(self):
        shutil.rmtree(self.run_dir, ignore_errors=True)

    def test_parse_junit_returns_both_cases(self):
        cases = report.parse_junit(self.run_dir / "junit.xml")
        self.assertEqual(len(cases), 2)
        names = [c["name"] for c in cases]
        self.assertIn("J-LIB-01 open library", names)
        self.assertIn("J-IMP-01 import book", names)

    def test_pass_case_has_pass_result_and_no_screenshot(self):
        cases = report.parse_junit(self.run_dir / "junit.xml")
        passed = next(c for c in cases if c["name"] == "J-LIB-01 open library")
        self.assertEqual(passed["status"], "SUCCESS")
        self.assertEqual(report.result_label(passed), "PASS")

    def test_fail_case_has_fail_result_and_message(self):
        cases = report.parse_junit(self.run_dir / "junit.xml")
        failed = next(c for c in cases if c["name"] == "J-IMP-01 import book")
        self.assertEqual(report.result_label(failed), "FAIL")
        self.assertIn("Wrong Title", failed["failure_message"])

    def test_find_screenshot_for_failed_case_picks_latest_step(self):
        cases = report.parse_junit(self.run_dir / "junit.xml")
        failed = next(c for c in cases if c["name"] == "J-IMP-01 import book")
        shot = report.find_screenshot(self.run_dir, failed)
        self.assertIsNotNone(shot)
        self.assertTrue(str(shot).endswith("step-024-assertCondition.png"))

    def test_find_screenshot_for_passed_case_is_none(self):
        cases = report.parse_junit(self.run_dir / "junit.xml")
        passed = next(c for c in cases if c["name"] == "J-LIB-01 open library")
        self.assertIsNone(report.find_screenshot(self.run_dir, passed))

    def test_write_summary_creates_markdown_with_table_and_totals(self):
        meta = report.load_meta(self.run_dir)
        out_path = report.write_summary(self.run_dir, meta)
        self.assertTrue(out_path.exists())
        text = out_path.read_text(encoding="utf-8")

        # Metadata header
        self.assertIn("Tab_35_PixelTablet", text)
        self.assertIn("35", text)
        self.assertIn("debug", text)
        self.assertIn("1.0", text)
        self.assertIn("24", text)
        self.assertIn("2026-11-08", text)
        self.assertIn("smoke", text)

        # Table
        self.assertIn("| Journey | Result | Duration | Failing step | Screenshot |", text)
        self.assertIn("J-LIB-01 open library", text)
        self.assertIn("PASS", text)
        self.assertIn("J-IMP-01 import book", text)
        self.assertIn("FAIL", text)
        self.assertIn("Wrong Title", text)
        self.assertIn("step-024-assertCondition.png", text)

        # Totals
        self.assertIn("1 passed", text)
        self.assertIn("1 failed", text)
        self.assertIn("2 total", text)

    def test_write_summary_pass_only_run_shows_zero_failures(self):
        only_pass = SAMPLE_JUNIT.replace(
            '<testcase id="j_imp_01" name="J-IMP-01 import book" classname="e2e.flows" '
            'file="flows/j_imp_01.yaml" time="7.345" timestamp="2026-11-08T15:30:12" '
            'status="ERROR">\n      <failure message="Assertion is false: &quot;.*Wrong Title.*&quot; '
            'is visible">at step 24: assertVisible</failure>\n    </testcase>',
            "",
        )
        (self.run_dir / "junit.xml").write_text(only_pass, encoding="utf-8")
        meta = report.load_meta(self.run_dir)
        out_path = report.write_summary(self.run_dir, meta)
        text = out_path.read_text(encoding="utf-8")
        self.assertIn("1 passed", text)
        self.assertIn("0 failed", text)
        self.assertIn("1 total", text)

    def test_main_copies_to_releases_path_when_copy_to_set(self):
        meta = dict(SAMPLE_META)
        dest_dir = self.run_dir / "releases_out"
        dest_dir.mkdir()
        meta["copy_to"] = str(dest_dir / "e2e-1.0-24-Tab_35_PixelTablet.md")
        (self.run_dir / "meta.json").write_text(json.dumps(meta), encoding="utf-8")

        report.main([str(self.run_dir)])

        self.assertTrue((self.run_dir / "summary.md").exists())
        self.assertTrue(Path(meta["copy_to"]).exists())


class PerFlowBooksReportTest(unittest.TestCase):
    """TDD for Task 7a "Take 2": run.sh now runs each selected flow file ONE AT A TIME (per-journey
    books: it pushes only that flow's fixtures, runs `maestro test` on that single flow file, and
    collects that flow's own junit.xml under RUN_DIR/<flow-stem>/junit.xml) instead of one
    `maestro test` over the whole e2e/flows directory with one top-level RUN_DIR/junit.xml.
    report.py must therefore build ONE summary.md from potentially MANY per-flow junit.xml files.
    These tests were written and observed failing (collect_cases/find_junit_paths did not exist
    yet) before report.py was changed — see task-7a-report.md "Take 2".
    """

    JLIB01_JUNIT = """<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="e2e/flows/library/J-LIB-01.yaml" device="emulator-5554" tests="1" failures="0" time="40.0" timestamp="2026-11-09T10:00:00">
    <testcase id="j_lib_01" name="J-LIB-01 First launch on the free tier" classname="e2e.flows" file="flows/library/J-LIB-01.yaml" time="40.0" timestamp="2026-11-09T10:00:00" status="SUCCESS"/>
  </testsuite>
</testsuites>
"""

    JLIB02_JUNIT = """<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="e2e/flows/library/J-LIB-02.yaml" device="emulator-5554" tests="1" failures="1" time="12.0" timestamp="2026-11-09T10:01:00">
    <testcase id="j_lib_02" name="J-LIB-02 Catalog evening" classname="e2e.flows" file="flows/library/J-LIB-02.yaml" time="12.0" timestamp="2026-11-09T10:01:00" status="ERROR">
      <failure message="Assertion is false: &quot;POPULAR&quot; is visible">at step 2: assertVisible</failure>
    </testcase>
  </testsuite>
</testsuites>
"""

    def setUp(self):
        self.run_dir = Path(tempfile.mkdtemp(prefix="e2e_report_perflow_test_"))
        (self.run_dir / "meta.json").write_text(json.dumps(SAMPLE_META), encoding="utf-8")
        d1 = self.run_dir / "J-LIB-01"
        d2 = self.run_dir / "J-LIB-02"
        d1.mkdir()
        d2.mkdir()
        (d1 / "junit.xml").write_text(self.JLIB01_JUNIT, encoding="utf-8")
        (d2 / "junit.xml").write_text(self.JLIB02_JUNIT, encoding="utf-8")
        shots = d2 / "J-LIB-02" / "screenshots"
        shots.mkdir(parents=True)
        (shots / "step-002-assertVisible.png").write_bytes(b"x")

    def tearDown(self):
        shutil.rmtree(self.run_dir, ignore_errors=True)

    def test_find_junit_paths_finds_both_per_flow_files_when_no_top_level_junit(self):
        paths = report.find_junit_paths(self.run_dir)
        self.assertEqual(len(paths), 2)

    def test_find_junit_paths_prefers_top_level_junit_when_present(self):
        # Legacy single-run layout (pre-Take-2 run.sh, and the existing ReportTest fixtures
        # above) must keep working unchanged: a top-level junit.xml wins over any per-flow ones.
        (self.run_dir / "junit.xml").write_text(SAMPLE_JUNIT, encoding="utf-8")
        paths = report.find_junit_paths(self.run_dir)
        self.assertEqual(paths, [self.run_dir / "junit.xml"])

    def test_collect_cases_merges_all_per_flow_junit_files(self):
        cases = report.collect_cases(self.run_dir)
        names = [c["name"] for c in cases]
        self.assertEqual(len(cases), 2)
        self.assertIn("J-LIB-01 First launch on the free tier", names)
        self.assertIn("J-LIB-02 Catalog evening", names)

    def test_write_summary_merges_per_flow_runs_into_one_table_and_totals(self):
        meta = report.load_meta(self.run_dir)
        out_path = report.write_summary(self.run_dir, meta)
        text = out_path.read_text(encoding="utf-8")
        self.assertIn("J-LIB-01 First launch on the free tier", text)
        self.assertIn("J-LIB-02 Catalog evening", text)
        self.assertIn("1 passed", text)
        self.assertIn("1 failed", text)
        self.assertIn("2 total", text)
        self.assertIn("POPULAR", text)

    def test_write_summary_finds_screenshot_for_failed_per_flow_case(self):
        meta = report.load_meta(self.run_dir)
        out_path = report.write_summary(self.run_dir, meta)
        text = out_path.read_text(encoding="utf-8")
        self.assertIn("step-002-assertVisible.png", text)


if __name__ == "__main__":
    unittest.main()
