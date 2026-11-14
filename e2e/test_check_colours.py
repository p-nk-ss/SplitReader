#!/usr/bin/env python3
"""Unit tests for check_colours.py (the exact-palette check run.sh makes after a flow).

Run: python3 e2e/test_check_colours.py
"""
import os
import struct
import sys
import tempfile
import unittest
import zlib
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import check_colours  # noqa: E402
import report  # noqa: E402

COLOR_KT = """
val PaperBg        = Color(0xFFF5EFE3)
val SepiaBg        = Color(0xFFEFE0C2)
val NightBg        = Color(0xFF15171F)
val AmoledBg        = Color(0xFF000000)
val HalfClear      = Color(0x80FFFFFF)
"""


def png(path, width, height, pixel_at, alpha=False):
    """Writes an 8-bit RGB(A) PNG; pixel_at(x, y) -> (r, g, b)."""
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw.extend(pixel_at(x, y))
            if alpha:
                raw.append(255)

    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body))

    data = b"\x89PNG\r\n\x1a\n"
    data += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6 if alpha else 2, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(bytes(raw)))
    data += chunk(b"IEND", b"")
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_bytes(data)


def mostly(rgb, ink=(0x2A, 0x22, 0x18)):
    """A background with a fifth of the pixels in another colour (the crop's text)."""
    return lambda x, y: ink if x % 5 == 0 else rgb


SEPIA = (0xEF, 0xE0, 0xC2)
PAPER = (0xF5, 0xEF, 0xE3)
NIGHT = (0x15, 0x17, 0x1F)

FLOW = """appId: ${APP_ID}
name: J-X
---
- takeScreenshot:
    path: colour-check/step7-display__SepiaBg
    cropOn: "Display"
- takeScreenshot:
    path: "colour-check/step9-library__NightBg"
    cropOn: "Your shelf"
"""


class CheckColoursTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.flow = self.tmp / "J-X.yaml"
        self.flow.write_text(FLOW, encoding="utf-8")
        self.color_kt = self.tmp / "Color.kt"
        self.color_kt.write_text(COLOR_KT, encoding="utf-8")
        self.run = self.tmp / "run" / "J-X"
        # Maestro nests its output one timestamped directory deeper.
        self.crops = self.run / "2026-11-14_210000" / "colour-check"

    def check(self):
        return check_colours.check(self.flow, self.run, self.color_kt)

    def test_palette_reads_opaque_constants_only(self):
        palette = check_colours.read_palette(self.color_kt)
        self.assertEqual(palette["SepiaBg"], "EFE0C2")
        self.assertEqual(palette["AmoledBg"], "000000")
        self.assertNotIn("HalfClear", palette)

    def test_expected_crops_are_read_from_the_flow(self):
        self.assertEqual(
            check_colours.expected_crops(self.flow),
            [("step7-display", "SepiaBg"), ("step9-library", "NightBg")],
        )

    def test_right_colours_pass(self):
        png(self.crops / "step7-display__SepiaBg.png", 20, 10, mostly(SEPIA))
        png(self.crops / "step9-library__NightBg.png", 20, 10, mostly(NIGHT), alpha=True)
        self.assertEqual(self.check(), [])

    def test_paper_where_sepia_is_expected_fails_and_names_both(self):
        png(self.crops / "step7-display__SepiaBg.png", 20, 10, mostly(PAPER))
        png(self.crops / "step9-library__NightBg.png", 20, 10, mostly(NIGHT))
        problems = self.check()
        self.assertEqual(len(problems), 1)
        self.assertIn("step7-display", problems[0])
        self.assertIn("SepiaBg #EFE0C2", problems[0])
        self.assertIn("#F5EFE3 (PaperBg)", problems[0])

    def test_amoled_where_night_is_expected_fails(self):
        png(self.crops / "step7-display__SepiaBg.png", 20, 10, mostly(SEPIA))
        png(self.crops / "step9-library__NightBg.png", 20, 10, mostly((0, 0, 0)))
        problems = self.check()
        self.assertEqual(len(problems), 1)
        self.assertIn("#000000 (AmoledBg)", problems[0])

    def test_a_crop_that_was_not_taken_fails(self):
        png(self.crops / "step7-display__SepiaBg.png", 20, 10, mostly(SEPIA))
        problems = self.check()
        self.assertEqual(len(problems), 1)
        self.assertIn("step9-library", problems[0])
        self.assertIn("not taken", problems[0])

    def test_right_colour_that_is_not_the_background_fails(self):
        # The expected colour is there, but only as a minority of the crop.
        png(self.crops / "step7-display__SepiaBg.png", 20, 10,
            lambda x, y: SEPIA if x < 8 else (x * 7 % 256, y * 9 % 256, 3))
        png(self.crops / "step9-library__NightBg.png", 20, 10, mostly(NIGHT))
        problems = self.check()
        self.assertEqual(len(problems), 1)
        self.assertIn("step7-display", problems[0])

    def test_unknown_constant_fails(self):
        self.flow.write_text(FLOW.replace("__NightBg", "__MidnightBg"), encoding="utf-8")
        png(self.crops / "step7-display__SepiaBg.png", 20, 10, mostly(SEPIA))
        png(self.crops / "step9-library__MidnightBg.png", 20, 10, mostly(NIGHT))
        problems = self.check()
        self.assertEqual(len(problems), 1)
        self.assertIn("MidnightBg", problems[0])

    def test_main_writes_a_junit_row_report_py_reads_as_fail(self):
        png(self.crops / "step7-display__SepiaBg.png", 20, 10, mostly(PAPER))
        png(self.crops / "step9-library__NightBg.png", 20, 10, mostly(NIGHT))
        junit = self.tmp / "run" / "J-X-colours" / "junit.xml"
        code = check_colours.main([str(self.flow), str(self.run), str(junit), "--color-kt", str(self.color_kt)])
        self.assertEqual(code, 1)
        cases = report.collect_cases(self.tmp / "run")
        self.assertEqual(len(cases), 1)
        self.assertEqual(report.result_label(cases[0]), "FAIL")
        self.assertEqual(cases[0]["name"], "J-X colours")
        self.assertIn("SepiaBg", cases[0]["failure_message"])

    def test_main_writes_a_pass_row(self):
        png(self.crops / "step7-display__SepiaBg.png", 20, 10, mostly(SEPIA))
        png(self.crops / "step9-library__NightBg.png", 20, 10, mostly(NIGHT))
        junit = self.tmp / "run" / "J-X-colours" / "junit.xml"
        code = check_colours.main([str(self.flow), str(self.run), str(junit), "--color-kt", str(self.color_kt)])
        self.assertEqual(code, 0)
        cases = report.collect_cases(self.tmp / "run")
        self.assertEqual(report.result_label(cases[0]), "PASS")

    def test_flow_without_colour_crops_has_nothing_to_check(self):
        self.flow.write_text("appId: x\n---\n- launchApp\n", encoding="utf-8")
        self.assertEqual(check_colours.expected_crops(self.flow), [])


if __name__ == "__main__":
    unittest.main()
