#!/usr/bin/env bash
# e2e/refs/record_theme_refs.sh SERIAL — (re)records the theme reference crops that
# e2e/flows/reading/J-RDR-05.yaml compares against (see e2e/refs/README.md).
#
# It runs a temporary copy of J-RDR-05.yaml in which every `assertScreenshot` is a
# `takeScreenshot` of the same crop, on the emulator SERIAL (emulators only), and copies the crops
# to e2e/refs/theme/<device class>-<name>.png. Everything else in the flow still asserts, so a
# run that does not reach the end records nothing. LOOK AT THE NEW PNGs before committing them:
# a reference recorded from a wrong screen makes the journey pass on that wrong screen.
set -eu -o pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
APP_ID="io.mirrolit.app"
SERIAL="${1:?usage: record_theme_refs.sh SERIAL (an emulator with the debug app installed)}"
[ "$("$ADB" -s "$SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" = "1" ] || { echo "ERROR: $SERIAL is not an emulator" >&2; exit 1; }
# shellcheck source=../flow_select.sh
. "$ROOT/e2e/flow_select.sh"
CLASS="$(device_class_from_wm "$("$ADB" -s "$SERIAL" shell wm size 2>/dev/null | tr -d '\r')" \
  "$("$ADB" -s "$SERIAL" shell wm density 2>/dev/null | tr -d '\r')")"
case "$CLASS" in tablet|phone) ;; *) echo "ERROR: unknown device class '$CLASS'" >&2; exit 1 ;; esac

SRC="$ROOT/e2e/flows/reading/J-RDR-05.yaml"
TMP_FLOW="$ROOT/e2e/flows/reading/.record-theme-refs.yaml"   # beside the source: same relative subflow paths
OUT="$(mktemp -d)"
trap 'rm -f "$TMP_FLOW"; "$ADB" -s "$SERIAL" shell rm -f /sdcard/Download/en-novel.epub >/dev/null 2>&1 || true' EXIT
/usr/bin/python3 - "$SRC" "$TMP_FLOW" <<'PY'
import re, sys
s = open(sys.argv[1], encoding="utf-8").read()
s, n = re.subn(r"- assertScreenshot:\n(\s+path: )\.\./\.\./refs/theme/([^\n]+\n\s+cropOn: [^\n]+\n)\s+thresholdPercentage: \d+\n",
               r"- takeScreenshot:\n\1rec/\2", s)
assert n > 0 and "- assertScreenshot:" not in s, "J-RDR-05.yaml: unexpected assertScreenshot layout"
open(sys.argv[2], "w", encoding="utf-8").write(s)
print(f"recording {n} crops")
PY
"$ADB" -s "$SERIAL" push "$ROOT/e2e/books/en-novel.epub" /sdcard/Download/en-novel.epub >/dev/null
"$ADB" -s "$SERIAL" shell "content call --uri content://media/ --method scan_file --arg /sdcard/Download/en-novel.epub" >/dev/null 2>&1 || true
maestro --device "$SERIAL" test -e "APP_ID=$APP_ID" -e "DEVICE_CLASS=$CLASS" --test-output-dir "$OUT" "$TMP_FLOW"
mkdir -p "$HERE/theme"
find "$OUT" -path "*/rec/$CLASS-*.png" -exec cp {} "$HERE/theme/" \;
ls -l "$HERE/theme/" | grep "$CLASS-"
