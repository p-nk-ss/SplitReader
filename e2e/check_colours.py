#!/usr/bin/env python3
"""check_colours.py — is each named crop of a flow drawn on exactly the palette colour it names?

Usage:
    check_colours.py FLOW_YAML FLOW_RUN_DIR JUNIT_OUT [--color-kt PATH]

A reader theme is only colours: no node in the accessibility tree says which theme is on, and
Maestro's own `assertScreenshot` tolerates small colour differences (it cannot tell Sepia from
Paper, nor AMOLED from Night). So a flow that has to prove a theme takes small crops instead,

    - takeScreenshot:
        path: colour-check/<label>__<Constant>      # e.g. colour-check/step7-display__SepiaBg
        cropOn: "Display"

where <Constant> is the name of a colour in the app's presentation/theme/Color.kt. A colour that is
not in the palette (a fixture book's own cover, J-LIB-04/05) is written as a literal, `x1E40C8`; and
`notx1E40C8` requires the crop's most frequent pixel NOT to be that colour (a book card that must not
show another book's cover). After the flow
e2e/run.sh calls this script, which reads the list of crops from the flow file itself and requires
for every one of them that

  - the crop was taken (it is somewhere under FLOW_RUN_DIR), and
  - its most frequent pixel is exactly that constant's colour and covers at least half of the
    crop (a crop is one short text on its background: the background is 74–90 % of it).

The result is written to JUNIT_OUT as one testcase "<flow stem> colours", which report.py shows as
its own row of summary.md; exit code 1 when anything is wrong. The constants are read from
Color.kt at run time, so a palette change needs no change here.

Nothing goes unchecked quietly. Each of these is a FAIL row too, not only an exit code:
  - a `path:` line that names the crop folder but not in the form above (an underscore in the
    label, a trailing comment, …) — the crop would otherwise be skipped;
  - a flow in which no crop is found at all (run.sh calls this script only for flows with crops);
  - any error of the checker itself: a PNG it cannot read, a missing Color.kt, a missing flow file.

Running a flow by hand with `maestro test` takes the crops but does NOT check them — run this
script on its output directory afterwards.
"""
import collections
import re
import struct
import sys
import zlib
from datetime import datetime, timezone
from pathlib import Path
from xml.sax.saxutils import escape, quoteattr

HERE = Path(__file__).resolve().parent
DEFAULT_COLOR_KT = HERE.parent / "app/src/main/java/com/example/splitreader/presentation/theme/Color.kt"
CROP_DIR = "colour-check"
MIN_SHARE = 0.5

_CROP_RE = re.compile(r"^\s*path:\s*[\"']?" + CROP_DIR + r"/([A-Za-z0-9-]+)__([A-Za-z0-9]+)[\"']?\s*$", re.M)
# Every line that takes a crop into the folder, whatever follows the folder name (comments apart).
_CROP_LINE_RE = re.compile(r"^(?!\s*#).*" + CROP_DIR + r"/.*$", re.M)
_LITERAL_RE = re.compile(r"^(not)?x([0-9A-Fa-f]{6})$")
_COLOR_RE = re.compile(r"^val\s+(\w+)\s*=\s*Color\(0xFF([0-9A-Fa-f]{6})\)", re.M)


def read_palette(color_kt):
    """{constant name: 'RRGGBB'} for every opaque top-level colour in Color.kt."""
    text = Path(color_kt).read_text(encoding="utf-8")
    return {name: rgb.upper() for name, rgb in _COLOR_RE.findall(text)}


def expected_crops(flow_yaml):
    """[(label, constant)] in flow order, one per `path: colour-check/<label>__<Constant>`."""
    return _CROP_RE.findall(Path(flow_yaml).read_text(encoding="utf-8"))


def unreadable_crop_lines(flow_yaml):
    """The flow's lines that name the crop folder but are not read by expected_crops()."""
    text = Path(flow_yaml).read_text(encoding="utf-8")
    return [line.strip() for line in _CROP_LINE_RE.findall(text) if not _CROP_RE.match(line)]


def dominant_colour(png_path):
    """('RRGGBB', share 0..1) of the most frequent pixel of an 8-bit RGB / RGBA PNG."""
    data = Path(png_path).read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    pos, idat, header = 8, b"", None
    while pos < len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            header = struct.unpack(">IIBBBBB", body)
        elif kind == b"IDAT":
            idat += body
    if header is None:
        raise ValueError("no IHDR chunk")
    width, height, depth, colour_type, _, _, interlace = header
    if depth != 8 or colour_type not in (2, 6) or interlace != 0:
        raise ValueError(f"unsupported PNG (depth {depth}, colour type {colour_type}, interlace {interlace})")
    channels = 3 if colour_type == 2 else 4
    raw = zlib.decompress(idat)
    stride = width * channels
    if width == 0 or height == 0 or len(raw) < height * (1 + stride):
        raise ValueError(f"{len(raw)} bytes of pixel data, {width}x{height} needs {height * (1 + stride)}")
    previous = bytearray(stride)
    counts = collections.Counter()
    offset = 0
    for _ in range(height):
        kind = raw[offset]
        line = bytearray(raw[offset + 1:offset + 1 + stride])
        offset += 1 + stride
        if kind:
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                up = previous[i]
                up_left = previous[i - channels] if i >= channels else 0
                if kind == 1:
                    predictor = left
                elif kind == 2:
                    predictor = up
                elif kind == 3:
                    predictor = (left + up) >> 1
                else:
                    pa, pb, pc = abs(up - up_left), abs(left - up_left), abs(left + up - 2 * up_left)
                    predictor = left if pa <= pb and pa <= pc else (up if pb <= pc else up_left)
                line[i] = (line[i] + predictor) & 255
        for x in range(0, stride, channels):
            counts[bytes(line[x:x + 3])] += 1
        previous = line
    colour, count = counts.most_common(1)[0]
    return colour.hex().upper(), count / (width * height)


def check(flow_yaml, flow_run_dir, color_kt=DEFAULT_COLOR_KT):
    """Returns a list of problems (empty = every crop is on its colour)."""
    palette = read_palette(color_kt)
    names = {rgb: name for name, rgb in reversed(list(palette.items()))}
    problems = [
        f"crop name not understood, so not checked: `{line}` (want path: {CROP_DIR}/<label>__<Constant>, "
        "label of letters, digits and '-', nothing after it)"
        for line in unreadable_crop_lines(flow_yaml)
    ]
    crops = expected_crops(flow_yaml)
    if not crops and not problems:
        problems.append(f"no colour crop found in {Path(flow_yaml).name}: nothing was checked")
    for label, constant in crops:
        literal = _LITERAL_RE.match(constant)
        negated = bool(literal and literal.group(1))
        want = literal.group(2).upper() if literal else palette.get(constant)
        if want is None:
            problems.append(f"{label}: no colour named {constant} in {Path(color_kt).name}")
            continue
        found = sorted(Path(flow_run_dir).glob(f"**/{CROP_DIR}/{label}__{constant}.png"))
        if not found:
            problems.append(f"{label}: crop not taken (expected {constant} #{want})")
            continue
        try:
            got, share = dominant_colour(found[-1])
        except Exception as error:  # noqa: BLE001 — whatever a broken file does, it is a FAIL row
            problems.append(f"{label}: cannot read {found[-1].name}: {error!r}")
            continue
        if negated:
            if got == want:
                problems.append(f"{label}: expected anything but #{want}, the crop is mostly #{got}, {share:.0%} of it")
            continue
        if got != want or share < MIN_SHARE:
            known = f" ({names[got]})" if got in names else ""
            problems.append(
                f"{label}: expected {constant} #{want}, the crop is mostly #{got}{known}, {share:.0%} of it"
            )
    return problems


def write_junit(junit_out, name, crops, problems):
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S")
    junit_out = Path(junit_out)
    junit_out.parent.mkdir(parents=True, exist_ok=True)
    attrs = f"id={quoteattr(name)} name={quoteattr(name)} classname={quoteattr(name)} time=\"0\" timestamp=\"{now}\""
    if problems:
        message = f"colour check: {len(problems)} problem(s), {crops} crop(s) named in the flow: " + "; ".join(problems)
        case = f"<testcase {attrs} status=\"ERROR\">\n      <failure message={quoteattr(message)}>{escape(message)}</failure>\n    </testcase>"
    else:
        case = f"<testcase {attrs} status=\"SUCCESS\"/>"
    junit_out.write_text(
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuites>\n"
        f"  <testsuite name={quoteattr(name)} tests=\"1\" failures=\"{1 if problems else 0}\" time=\"0\" timestamp=\"{now}\">\n"
        f"    {case}\n  </testsuite>\n</testsuites>\n",
        encoding="utf-8",
    )


def main(argv):
    color_kt = DEFAULT_COLOR_KT
    if "--color-kt" in argv:
        at = argv.index("--color-kt")
        color_kt = argv[at + 1]
        argv = argv[:at] + argv[at + 2:]
    if len(argv) != 3:
        print("usage: check_colours.py FLOW_YAML FLOW_RUN_DIR JUNIT_OUT [--color-kt PATH]", file=sys.stderr)
        return 2
    flow_yaml, flow_run_dir, junit_out = argv
    name = f"{Path(flow_yaml).stem} colours"
    # An error of the checker itself must end as a FAIL row: without a junit the summary has no
    # colours row at all and reads "all passed".
    crops = []
    try:
        crops = expected_crops(flow_yaml)
        problems = check(flow_yaml, flow_run_dir, color_kt)
    except Exception as error:  # noqa: BLE001
        problems = [f"checker failed: {error!r}"]
    write_junit(junit_out, name, len(crops), problems)
    if problems:
        print(f"{name}: FAIL")
        for problem in problems:
            print(f"  {problem}")
        return 1
    print(f"{name}: OK ({len(crops)} crops on their palette colour)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
