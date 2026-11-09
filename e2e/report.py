#!/usr/bin/env python3
"""report.py — turns a Maestro JUnit XML report into a readable summary.md.

Usage:
    report.py RUN_DIR

RUN_DIR must contain:
  - EITHER a top-level junit.xml (legacy single-run layout: one `maestro test` over every
    flow, written by `maestro test ... --format junit --output RUN_DIR/junit.xml`)
  - OR one junit.xml per immediate subdirectory (Task 7a "Take 2" layout: run.sh now runs
    each selected flow file ONE AT A TIME — per-journey fixtures — writing each flow's own
    `RUN_DIR/<flow-stem>/junit.xml`). See find_junit_paths()/collect_cases(): a top-level
    junit.xml always wins outright; otherwise every subdirectory's junit.xml is parsed and
    merged into one table, in sorted (deterministic) subdirectory order.
  - meta.json   — run metadata written by run.sh before/after the maestro invocation:
                  {
                    "device": "<human label, e.g. 'Tab_35_PixelTablet (emulator-5554)'>",
                    "avd": "<avd name or null for --serial>",
                    "api": <int sdk level>,
                    "build_type": "debug" | "release",
                    "version_name": "<app versionName>",
                    "version_code": <app versionCode>,
                    "date": "<YYYY-MM-DD>",
                    "tags": ["smoke", ...],
                    "copy_to": "<optional absolute path>"   # set by run.sh for --build release
                                                              # or --tags smoke
                  }

Writes RUN_DIR/summary.md: a metadata header, a
`| Journey | Result | Duration | Failing step | Screenshot |` table (one row per Maestro
testcase), and a totals line. When meta.json has "copy_to", the same file is also copied there
(run.sh resolves that path the same way app/build.gradle.kts resolves `releaseDir`: the
`mirrolit.releaseDir` key in local.properties, else `<repo root>/releases`).

JUnit shape (verified against the installed Maestro 2.12.1 CLI jar,
maestro/cli/report/JUnitTestSuiteReporter$TestSuite / $TestCase):
    <testsuites>
      <testsuite name=".." device=".." tests=".." failures=".." time=".." timestamp="..">
        <testcase id=".." name=".." classname=".." file=".." time=".." timestamp=".."
                  status="SUCCESS|ERROR|WARNING|CANCELED|STOPPED|...">
          <failure message="..">body</failure>   <!-- only present when not SUCCESS -->
        </testcase>
        ...
      </testsuite>
      ...
    </testsuites>
"""
import json
import shutil
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

PASS_STATUS = "SUCCESS"


def parse_junit(junit_path):
    """Returns a list of dicts: {id, name, classname, file, time, status, failure_message}."""
    junit_path = Path(junit_path)
    root = ET.parse(junit_path).getroot()
    cases = []
    for testcase in root.iter("testcase"):
        failure = testcase.find("failure")
        failure_message = None
        if failure is not None:
            msg = (failure.get("message") or "").strip()
            body = (failure.text or "").strip()
            failure_message = msg or body
            if msg and body and body not in msg:
                failure_message = f"{msg} ({body})"
        cases.append(
            {
                "id": testcase.get("id") or "",
                "name": testcase.get("name") or testcase.get("id") or "",
                "classname": testcase.get("classname") or "",
                "file": testcase.get("file") or "",
                "time": testcase.get("time") or "",
                "status": testcase.get("status") or "",
                "failure_message": failure_message,
            }
        )
    return cases


def result_label(case):
    return "PASS" if case["status"] == PASS_STATUS else "FAIL"


def _flow_stem_candidates(case):
    """Candidate directory-name stems for this testcase's per-flow debug output, tried in
    order: the `file` attribute's basename without extension, then the `id`, then a
    filesystem-safe version of `name`."""
    candidates = []
    if case.get("file"):
        stem = Path(case["file"]).stem
        if stem:
            candidates.append(stem)
    if case.get("id"):
        candidates.append(case["id"])
    if case.get("name"):
        safe = "".join(c if c.isalnum() or c in "-_." else "_" for c in case["name"])
        candidates.append(safe)
    # de-dup, keep order
    seen = set()
    out = []
    for c in candidates:
        if c and c not in seen:
            seen.add(c)
            out.append(c)
    return out


def find_screenshot(run_dir, case):
    """Finds the last (highest step-numbered) screenshot Maestro wrote for this testcase's flow,
    under a `<flow-stem>/screenshots/` directory somewhere below `run_dir`. Returns None for a
    passing case (nothing interesting to show) or when no matching directory/screenshot exists.

    Searched recursively, not just directly under run_dir: with `--test-output-dir RUN`, Maestro
    2.12.1 was observed nesting one extra timestamped directory (`RUN/<run-ts>/<flow-stem>/...`)
    rather than writing `RUN/<flow-stem>/...` directly — recursing is robust to that layout and
    to a flat one, without depending on the exact nesting depth.
    """
    if result_label(case) != "FAIL":
        return None
    run_dir = Path(run_dir)
    for stem in _flow_stem_candidates(case):
        for shots_dir in sorted(run_dir.glob(f"**/{stem}/screenshots")):
            if shots_dir.is_dir():
                pngs = sorted(shots_dir.glob("*.png"))
                if pngs:
                    return pngs[-1]
    return None


def find_junit_paths(run_dir):
    """Finds the junit.xml file(s) to read for this run.

    run.sh (Task 7a "Take 2") runs each selected flow file ONE AT A TIME — per-journey
    fixtures — and writes each flow's own junit.xml under RUN_DIR/<flow-stem>/junit.xml,
    rather than one `maestro test` over the whole flows directory with a single top-level
    RUN_DIR/junit.xml. A top-level RUN_DIR/junit.xml, when present, wins outright (the legacy
    single-run layout, e.g. a manual single-flow `maestro test ... --output RUN_DIR/junit.xml`
    invocation) — it is never merged with per-flow files, since that single file already is
    the whole run. Otherwise every immediate subdirectory's own junit.xml is collected, sorted
    for a deterministic row order.
    """
    run_dir = Path(run_dir)
    top = run_dir / "junit.xml"
    if top.exists():
        return [top]
    return sorted(run_dir.glob("*/junit.xml"))


def collect_cases(run_dir):
    """Parses every junit.xml file found by find_junit_paths and returns one merged list of
    testcase dicts, in file order (each file's own testcase order preserved)."""
    cases = []
    for junit_path in find_junit_paths(run_dir):
        cases.extend(parse_junit(junit_path))
    return cases


def load_meta(run_dir):
    meta_path = Path(run_dir) / "meta.json"
    if not meta_path.exists():
        return {}
    return json.loads(meta_path.read_text(encoding="utf-8"))


def _fmt_time(raw):
    if not raw:
        return "-"
    try:
        return f"{float(raw):.1f}s"
    except ValueError:
        return str(raw)


def _escape_cell(text):
    if text is None:
        return ""
    return str(text).replace("|", "\\|").replace("\n", " ").strip()


def render_summary(run_dir, meta, cases):
    run_dir = Path(run_dir)
    lines = []
    lines.append("# Mirrolit E2E run summary")
    lines.append("")
    lines.append(f"- Device: {meta.get('device', '-')}")
    if meta.get("avd"):
        lines.append(f"- AVD: {meta.get('avd')}")
    lines.append(f"- API level: {meta.get('api', '-')}")
    lines.append(f"- Build type: {meta.get('build_type', '-')}")
    lines.append(f"- Version: {meta.get('version_name', '-')} ({meta.get('version_code', '-')})")
    lines.append(f"- Date: {meta.get('date', '-')}")
    tags = meta.get("tags") or []
    lines.append(f"- Tags: {', '.join(tags) if tags else '-'}")
    lines.append("")
    lines.append("| Journey | Result | Duration | Failing step | Screenshot |")
    lines.append("|---|---|---|---|---|")

    passed = 0
    failed = 0
    for case in cases:
        result = result_label(case)
        if result == "PASS":
            passed += 1
            failing_step = ""
            screenshot = ""
        else:
            failed += 1
            failing_step = _escape_cell(case.get("failure_message") or "(no failure message)")
            shot = find_screenshot(run_dir, case)
            screenshot = str(shot) if shot else "(none)"
        lines.append(
            f"| {_escape_cell(case.get('name'))} | {result} | {_fmt_time(case.get('time'))} "
            f"| {failing_step} | {screenshot} |"
        )

    total = passed + failed
    lines.append("")
    lines.append(f"**Totals: {passed} passed, {failed} failed, {total} total**")
    lines.append("")
    return "\n".join(lines)


def write_summary(run_dir, meta):
    run_dir = Path(run_dir)
    cases = collect_cases(run_dir)
    text = render_summary(run_dir, meta, cases)
    out_path = run_dir / "summary.md"
    out_path.write_text(text, encoding="utf-8")
    return out_path


def main(argv):
    if len(argv) != 1:
        print("usage: report.py RUN_DIR", file=sys.stderr)
        return 2
    run_dir = Path(argv[0])
    meta = load_meta(run_dir)
    out_path = write_summary(run_dir, meta)
    print(f"Wrote {out_path}")

    copy_to = meta.get("copy_to")
    if copy_to:
        dest = Path(copy_to)
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(out_path, dest)
        print(f"Copied to {dest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
