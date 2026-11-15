#!/usr/bin/env bash
# e2e/run.sh — builds the app, boots (or attaches to) an emulator, installs it, then runs the
# selected Maestro flows under e2e/flows ONE AT A TIME — pushing only each flow's own `# books:`
# fixtures before it runs and removing them before the next flow — and writes one merged report.
#
# Usage:
#   e2e/run.sh (--avd NAME | --serial SERIAL) [--build debug|release] [--tags TAGLIST] [--keep-emulator]
#
#   --avd NAME        Boot this AVD (emulator -avd NAME -memory 4096 -partition-size 8192
#                      -no-snapshot-save), wait for it to finish booting, run against it, then
#                      kill it at the end (unless --keep-emulator). Only this session's own
#                      emulator serial is ever addressed, even if other devices (emulators or
#                      real, USB-attached) are already connected.
#   --serial SERIAL   Run against an already-running emulator with this adb serial. Refused
#                      (exit non-zero) unless `adb -s SERIAL shell getprop ro.kernel.qemu` is
#                      "1" — this script is emulator-only, by design decision; it never installs,
#                      uninstalls or otherwise touches a real device.
#   --build TYPE       debug (default) or release. release builds a SIGNED app-release.apk: the
#                      signing files (keystore.properties + the .jks it references) are copied in
#                      from the main checkout only for the build and ALWAYS removed afterwards
#                      (trap on EXIT), never staged into git.
#   --tags TAGLIST      Selects which flow FILES run, by matching each flow's own `tags: [...]`
#                      header (OR semantics — a flow runs if it has ANY tag in TAGLIST; same
#                      semantics as `maestro test --include-tags`, but applied by this script
#                      itself, since flows now run one file at a time rather than as one
#                      `maestro test` invocation over the whole flows/ directory). Omit to run
#                      every flow under e2e/flows. Comma-separated, e.g. "smoke" or
#                      "full,tablet", or a single flow's own id tag, e.g. "J-LIB-01".
#                      Independently of --tags, a flow tagged `tablet` but not `phone` is skipped
#                      on a phone, and one tagged `phone` but not `tablet` is skipped on a tablet
#                      (see flow_select.sh; the skip is logged and listed in summary.md).
#                      A run that selects NO flow is an error (exit 1, "no flow selected"): when
#                      no flow has any of the tags, before anything is built or booted; when the
#                      device rule skipped them all, right after boot — the emulator this run
#                      started is killed first. The same when the device's class cannot be read
#                      from `wm size` / `wm density`: no flow runs on a device of unknown class.
#   --keep-emulator    Don't kill the emulator this run started (--avd only; ignored with
#                      --serial, since that emulator wasn't started by this script).
#   --flow-timeout-min N  Per-flow wall-clock watchdog, in minutes (default 20). If a single
#                      flow's `maestro test` invocation runs longer than this, run.sh kills it
#                      (SIGTERM, then SIGKILL after a 5s grace period if still alive), records
#                      that flow as a FAIL with a synthetic junit.xml testcase ("timed out after
#                      Nm" as the failure message, so it shows up in summary.md same as any other
#                      failure), and moves on to the next selected flow. Confirmed need: a phone
#                      run of J-LIB-02 (2026-11-11) produced no Maestro output for 63 minutes
#                      (network-dependent catalog waits stacking up) with nothing to stop it short
#                      of a human killing the process by hand.
#
# Per-journey fixtures (Task 7a "Take 2"): each flow file declares the books it needs in a
# `# books: a.epub, b.fb2` comment header (see e2e/README.md for the exact syntax). Before each
# selected flow runs, every fixture pushed for the PREVIOUS flow is deleted from
# /sdcard/Download, then only this flow's own books are pushed + media-scanned; a flow with no
# `# books:` header gets nothing pushed. This keeps the device's Downloads folder small
# (one journey's few books, not the whole ~50-fixture suite at once), which is what let
# e2e/subflows/pick_file.yaml's picker-scrolling logic be simplified back down — see the "Take 2"
# section of task-7a-report.md for why "Take 1" (push everything, run the whole directory in one
# `maestro test`) needed multi-minute scroll timeouts and hit DocumentsUI picker-state problems.
#
# Every run writes e2e/reports/<timestamp>/<flow-stem>/{junit.xml,...} (one subdirectory per
# flow that ran) plus a single top-level summary.md/meta.json merging every flow (gitignored).
# For `--build release` or a --tags value that contains "smoke", summary.md is additionally
# copied to <releases dir>/e2e-<versionName>-<versionCode>-<device>.md, where <releases dir> is
# resolved exactly like app/build.gradle.kts resolves `releaseDir` for copyReleaseBundle:
# `mirrolit.releaseDir` in local.properties if set, else `<repo root>/releases`.
#
# Colour checks: a flow that takes crops named `colour-check/<label>__<Constant>` (J-RDR-05's
# themes) has them checked by e2e/check_colours.py right after it ran — each crop's dominant
# pixel must be exactly that constant of the app's Color.kt. The result is its own row,
# "<flow> colours", in summary.md, and a wrong colour fails the run like a failed flow. So does a
# crop that was not taken, a crop name the checker cannot read, and any error of the checker
# itself: each is a FAIL row, never only an exit code.
#
# Exit code is non-zero if ANY selected flow failed (0 = every selected flow passed), so this
# script is CI-usable as a gate. Report generation and cleanup (pushed fixtures removed, emulator
# killed) still run even when one or more flows fail.
set -u -o pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$HERE/.." && pwd)"
APP_ID="io.mirrolit.app"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
EMULATOR_BIN="$HOME/Library/Android/sdk/emulator/emulator"
MAESTRO="$(command -v maestro || true)"
PYTHON3="/opt/homebrew/opt/python@3.12/libexec/bin/python3"
[ -x "$PYTHON3" ] || PYTHON3="$(command -v python3)"

log() { printf '[run.sh] %s\n' "$*" >&2; }
die() { printf '[run.sh] ERROR: %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------
AVD=""
SERIAL=""
BUILD_TYPE="debug"
TAGS=""
KEEP_EMULATOR=0
FLOW_TIMEOUT_MIN=20

while [ $# -gt 0 ]; do
  case "$1" in
    --avd) AVD="$2"; shift 2 ;;
    --serial) SERIAL="$2"; shift 2 ;;
    --build) BUILD_TYPE="$2"; shift 2 ;;
    --tags) TAGS="$2"; shift 2 ;;
    --keep-emulator) KEEP_EMULATOR=1; shift ;;
    --flow-timeout-min) FLOW_TIMEOUT_MIN="$2"; shift 2 ;;
    -h|--help) grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done
case "$FLOW_TIMEOUT_MIN" in ''|*[!0-9]*) die "--flow-timeout-min must be a positive integer" ;; esac
[ "$FLOW_TIMEOUT_MIN" -gt 0 ] || die "--flow-timeout-min must be a positive integer"
FLOW_TIMEOUT_SEC=$((FLOW_TIMEOUT_MIN * 60))

[ -n "$AVD" ] || [ -n "$SERIAL" ] || die "one of --avd NAME or --serial SERIAL is required"
[ -z "$AVD" ] || [ -z "$SERIAL" ] || die "--avd and --serial are mutually exclusive"
case "$BUILD_TYPE" in debug|release) ;; *) die "--build must be debug or release" ;; esac
[ -n "$MAESTRO" ] || die "maestro not found on PATH"
[ -x "$ADB" ] || die "adb not found at $ADB"

# ---------------------------------------------------------------------------
# 0. Which flows do the tags select? Decided before anything is built or booted, so a mistyped
# --tags costs a second, not a build and a boot. (The device rule needs the device: step 2.)
#
# flow_tags / flow_matches_tags (--tags, OR semantics) and flow_matches_device (the `tablet` /
# `phone` tags) live in flow_select.sh so e2e/test_flow_select.sh can check them without a device;
# e2e/test_run_select.sh checks what this script does with them.
# ---------------------------------------------------------------------------
# shellcheck source=flow_select.sh
. "$HERE/flow_select.sh"

FLOW_FILES=()
while IFS= read -r f; do
  FLOW_FILES+=("$f")
done < <(find "$REPO_ROOT/e2e/flows" -name '*.yaml' ! -name 'config.yaml' ! -name '_*' | sort)
[ "${#FLOW_FILES[@]}" -gt 0 ] || die "no flow files under $REPO_ROOT/e2e/flows"

TAG_MATCHED_FLOWS=()
for f in "${FLOW_FILES[@]}"; do
  flow_tags_readable "$f" \
    || die "$(basename "$f"): no readable tags — every flow needs one inline line \`tags: [a, b]\` (a YAML block list is not read; the flow would run on every device and match no --tags)"
  if flow_matches_tags "$f"; then
    TAG_MATCHED_FLOWS+=("$f")
  else
    log "skipping $(basename "$f") (tags don't include any of: $TAGS)"
  fi
done
[ "${#TAG_MATCHED_FLOWS[@]}" -gt 0 ] \
  || die "no flow selected: no flow under e2e/flows has any of the tags: $TAGS"

# ---------------------------------------------------------------------------
# Signed release build: copy signing files from the main checkout for the build only, and
# ALWAYS remove the copies on exit, whatever happens afterwards (trap, not a tail cleanup step,
# so a crash or an early `die` still cleans up).
# ---------------------------------------------------------------------------
MAIN_CHECKOUT="/Users/pankaz-mac/StudioProjects/SplitReader"
COPIED_KEYSTORE_PROPS=""
COPIED_JKS=""

cleanup_signing_copies() {
  if [ -n "$COPIED_KEYSTORE_PROPS" ] && [ -f "$COPIED_KEYSTORE_PROPS" ]; then
    rm -f "$COPIED_KEYSTORE_PROPS"
    log "removed copied $COPIED_KEYSTORE_PROPS"
  fi
  if [ -n "$COPIED_JKS" ] && [ -f "$COPIED_JKS" ]; then
    rm -f "$COPIED_JKS"
    log "removed copied $COPIED_JKS"
  fi
}
trap cleanup_signing_copies EXIT

# ---------------------------------------------------------------------------
# 1. Build
# ---------------------------------------------------------------------------
cd "$REPO_ROOT"

if [ "$BUILD_TYPE" = "release" ]; then
  SRC_PROPS="$MAIN_CHECKOUT/keystore.properties"
  [ -f "$SRC_PROPS" ] || die "release build requested but $SRC_PROPS is missing"
  JKS_REL="$(sed -n 's/^storeFile=//p' "$SRC_PROPS" | head -1 | tr -d '\r')"
  [ -n "$JKS_REL" ] || die "storeFile= not found in $SRC_PROPS"
  SRC_JKS="$MAIN_CHECKOUT/$JKS_REL"
  [ -f "$SRC_JKS" ] || die "signing key $SRC_JKS (from storeFile=$JKS_REL) is missing"

  DEST_PROPS="$REPO_ROOT/keystore.properties"
  DEST_JKS="$REPO_ROOT/$JKS_REL"
  # Refuse to overwrite a pre-existing file at either destination: cleanup_signing_copies only
  # ever rm's a path it finds in COPIED_KEYSTORE_PROPS/COPIED_JKS, and those are only ever set
  # right after THIS run's own successful `cp` below — so dying here, before either `cp` runs,
  # guarantees the trap can never delete something this run didn't create itself.
  [ -e "$DEST_PROPS" ] && die "refusing to overwrite pre-existing $DEST_PROPS — remove/move it yourself first (this run would otherwise copy over it, then its EXIT trap would delete it)"
  [ -e "$DEST_JKS" ] && die "refusing to overwrite pre-existing $DEST_JKS — remove/move it yourself first (this run would otherwise copy over it, then its EXIT trap would delete it)"

  mkdir -p "$(dirname "$DEST_JKS")"
  cp "$SRC_PROPS" "$DEST_PROPS"
  COPIED_KEYSTORE_PROPS="$DEST_PROPS"
  cp "$SRC_JKS" "$DEST_JKS"
  COPIED_JKS="$DEST_JKS"
  log "copied signing files into worktree root for the release build (will be removed on exit)"

  log "./gradlew :app:assembleRelease"
  ./gradlew :app:assembleRelease || die "assembleRelease failed"
  APK="$REPO_ROOT/app/build/outputs/apk/release/app-release.apk"
else
  log "./gradlew :app:assembleDebug"
  ./gradlew :app:assembleDebug || die "assembleDebug failed"
  APK="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
fi
[ -f "$APK" ] || die "expected APK not found at $APK"

./gradlew --stop

# ---------------------------------------------------------------------------
# 2. Device: boot an AVD, or attach to an existing emulator serial
# ---------------------------------------------------------------------------
STARTED_EMULATOR=0
EMULATOR_PID=""

emulator_serials() {
  "$ADB" devices | awk 'NR>1 && $1!="" {print $1}'
}

if [ -n "$SERIAL" ]; then
  qemu="$("$ADB" -s "$SERIAL" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r\n')"
  [ "$qemu" = "1" ] || die "--serial $SERIAL is not an emulator (ro.kernel.qemu != 1) — this runner is emulator-only and refuses to touch a real device"
  log "attached to existing emulator $SERIAL"
else
  # BEFORE_SERIALS below is only a convenience filter (skip devices we already know about faster
  # while polling) — it is NOT the safety guarantee. The real guard is the simultaneous
  # ro.kernel.qemu=1 + sys.boot_completed=1 check a few lines down: even if a real device's
  # serial were somehow missing from this snapshot, it would still never be mistaken for the AVD
  # we just started, because it will never report ro.kernel.qemu=1.
  BEFORE_SERIALS="$(emulator_serials)"
  log "restarting adb (adb kill-server && adb start-server) before starting a new AVD"
  "$ADB" kill-server
  "$ADB" start-server
  log "booting AVD $AVD"
  nohup "$EMULATOR_BIN" -avd "$AVD" -memory 4096 -partition-size 8192 -no-snapshot-save \
    >/tmp/run.sh.emulator.$$.log 2>&1 &
  EMULATOR_PID=$!
  STARTED_EMULATOR=1

  # 120 tries x 5 s = 10 minutes. E2E_BOOT_WAIT_TRIES exists for e2e/test_run_select.sh only.
  BOOT_WAIT_TRIES="${E2E_BOOT_WAIT_TRIES:-120}"
  SERIAL=""
  tries=0
  while [ $tries -lt "$BOOT_WAIT_TRIES" ]; do
    for s in $(emulator_serials); do
      case " $BEFORE_SERIALS " in
        *" $s "*) continue ;; # was already connected before we started this AVD — never touch it
      esac
      boot="$("$ADB" -s "$s" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')"
      qemu="$("$ADB" -s "$s" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r\n')"
      if [ "$boot" = "1" ] && [ "$qemu" = "1" ]; then
        SERIAL="$s"
        break
      fi
    done
    [ -n "$SERIAL" ] && break
    tries=$((tries + 1))
    sleep 5
  done
  if [ -z "$SERIAL" ]; then
    # No serial to send `emu kill` to (kill_emulator below needs one), so the process itself is
    # ended — otherwise a half-booted emulator stays and takes the next run's RAM and port.
    if [ "$KEEP_EMULATOR" != "1" ]; then
      log "killing the emulator process this run started (pid $EMULATOR_PID): it never booted"
      pkill -P "$EMULATOR_PID" 2>/dev/null || true
      kill "$EMULATOR_PID" 2>/dev/null || true
    fi
    die "AVD $AVD did not finish booting in time"
  fi
  log "AVD $AVD booted as $SERIAL"
fi

kill_emulator() {
  if [ "$STARTED_EMULATOR" = "1" ] && [ "$KEEP_EMULATOR" != "1" ]; then
    log "killing emulator $SERIAL (started by this run)"
    "$ADB" -s "$SERIAL" emu kill 2>/dev/null || true
  elif [ "$STARTED_EMULATOR" = "1" ]; then
    log "--keep-emulator: leaving $SERIAL running"
  fi
}
# From here on a fatal error must not leave the emulator this run started running.
die_after_boot() { kill_emulator; die "$@"; }

# Device class: tablet | phone (smallest width >= 600dp is a tablet). It decides which flows run
# and which of a two-class flow's steps run (`when: ${DEVICE_CLASS == 'phone'}`), so without it
# nothing runs: a flow on a device of unknown class would skip its device-specific steps and
# still pass. `wm` is asked a few times — right after boot it can answer late.
DEVICE_CLASS=""
tries=0
while [ -z "$DEVICE_CLASS" ] && [ $tries -lt 5 ]; do
  [ $tries -eq 0 ] || sleep 2
  DEVICE_CLASS="$(device_class_from_wm "$("$ADB" -s "$SERIAL" shell wm size 2>/dev/null | tr -d '\r')" \
    "$("$ADB" -s "$SERIAL" shell wm density 2>/dev/null | tr -d '\r')")"
  tries=$((tries + 1))
done
case "$DEVICE_CLASS" in
  tablet|phone) log "device class: $DEVICE_CLASS" ;;
  *) die_after_boot "cannot tell the device class of $SERIAL (tablet or phone) from \`wm size\` / \`wm density\` — no flow runs on a device of unknown class" ;;
esac

SELECTED_FLOWS=()
SKIPPED_FOR_DEVICE=()
for f in "${TAG_MATCHED_FLOWS[@]}"; do
  if flow_matches_device "$f" "$DEVICE_CLASS"; then
    SELECTED_FLOWS+=("$f")
  else
    log "skipping $(basename "$f") (not tagged for a $DEVICE_CLASS)"
    SKIPPED_FOR_DEVICE+=("$(basename "$f" .yaml)")
  fi
done
# An explicit count, never "${SELECTED_FLOWS[@]}" on an empty array: under `set -u` bash 3.2 (the
# macOS /bin/bash) calls that an unbound variable and dies on the spot, bash 4.4+ runs zero flows
# and reports a green run of nothing.
[ "${#SELECTED_FLOWS[@]}" -gt 0 ] \
  || die_after_boot "no flow selected (tags: ${TAGS:-none}; device: $DEVICE_CLASS): every flow with those tags is tagged for the other device class — ${SKIPPED_FOR_DEVICE[*]}"

# ---------------------------------------------------------------------------
# 3. Install
# ---------------------------------------------------------------------------
log "adb install -r $APK"
"$ADB" -s "$SERIAL" install -r "$APK" || die_after_boot "install failed"

API="$("$ADB" -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r\n')"
log "device API level: $API"

RUN="$REPO_ROOT/e2e/reports/$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$RUN"

log "fetching real (non-synthetic) fixture books"
if ! "$REPO_ROOT/e2e/books/fetch_fixtures.sh"; then
  log "WARNING: fetch_fixtures.sh reported a failure (network/Gutenberg?) — continuing with" \
      "whatever fixtures are already present under e2e/books/fetched/"
fi

log "listing /sdcard/Download before pushing any fixtures"
"$ADB" -s "$SERIAL" shell ls -la /sdcard/Download > "$RUN/sdcard_download_before.txt" 2>&1 || true

# ---------------------------------------------------------------------------
# 4. Per-journey fixtures + per-flow maestro runs (Task 7a "Take 2")
#
# Each flow file under e2e/flows declares the fixtures it needs in a `# books: a.epub, b.fb2`
# comment line (see e2e/README.md for the exact syntax). Flows now run ONE AT A TIME: before
# each selected flow, every fixture pushed for the PREVIOUS flow is deleted from
# /sdcard/Download, then only THIS flow's own books are pushed (+ media-scanned) and
# `maestro test` runs against that single flow file alone; a flow with no `# books:` header
# gets nothing pushed. This replaces "Take 1" (push all ~50 fixtures up front, then one
# `maestro test` over the whole flows/ directory), which made DocumentsUI's Downloads grid
# large enough to need multi-minute scroll timeouts and occasional picker-state corruption —
# see task-7a-report.md. Each flow's own junit.xml/test-output lands under its own $RUN/<stem>/
# subdirectory so report.py (find_junit_paths()/collect_cases()) can build one merged
# summary.md across every flow afterwards.
#
# Flow selection by --tags still works: it is now run.sh's OWN filter (flow_matches_tags, OR
# semantics against each flow's `tags: [...]` header — the same semantics maestro's own
# --include-tags has), since flows run one file at a time rather than as one `maestro test`
# invocation over a whole directory. A skipped flow is logged explicitly (steps 0 and 2), which is
# this script's proof that a non-matching flow was actually skipped, not silently run anyway.
# ---------------------------------------------------------------------------

# `adb shell` concatenates ALL of its argv (space-joined) into one command line that the
# DEVICE's own shell then tokenizes again — a basename containing a space or a shell
# metacharacter (quote, $, ;, …) would come apart differently there than it did locally, so a
# raw, unquoted "/sdcard/Download/$bn" is not safe to hand to `adb shell` as one of several argv
# words. remote_quote wraps a path in single quotes for that REMOTE shell (escaping any embedded
# single quote as '"'"'), and every call site below passes the whole command as ONE already-quoted
# string so there is nothing left for either shell to re-split.
remote_quote() {
  printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\"'\"'/g")"
}

media_scan() {
  local remote="$1"
  local qpath
  qpath="$(remote_quote "$remote")"
  if [ "$API" -ge 31 ] 2>/dev/null; then
    "$ADB" -s "$SERIAL" shell "content call --uri content://media/ --method scan_file --arg $qpath" >/dev/null 2>&1
  else
    "$ADB" -s "$SERIAL" shell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d 'file://'$qpath" >/dev/null 2>&1
  fi
}

PUSHED_LIST="$RUN/pushed_files.txt"
: > "$PUSHED_LIST"

push_fixture() {
  local local_path="$1"
  local bn
  bn="$(basename "$local_path")"
  # adb push's remote argument goes straight to the sync protocol, not through a remote shell,
  # so (unlike media_scan/rm below) it needs no remote-shell quoting — a space or quote in $bn
  # survives it literally.
  "$ADB" -s "$SERIAL" push "$local_path" "/sdcard/Download/$bn" >/dev/null || die_after_boot "push failed: $local_path"
  media_scan "/sdcard/Download/$bn"
  echo "$bn" >> "$PUSHED_LIST"
}

# Resolves a fixture basename named in a flow's `# books:` header to its source file, in this
# order: e2e/books/<name> (committed synthetic fixtures), e2e/books/fetched/<name> (real
# Gutenberg books downloaded above), then app/src/androidTest/assets/qa-pd/<name> (the one
# pre-existing fixture that lives outside e2e/books/). Echoes the resolved path and returns
# non-zero if none of the three exist.
resolve_fixture() {
  local name="$1"
  if [ -f "$REPO_ROOT/e2e/books/$name" ]; then
    printf '%s\n' "$REPO_ROOT/e2e/books/$name"
  elif [ -f "$REPO_ROOT/e2e/books/fetched/$name" ]; then
    printf '%s\n' "$REPO_ROOT/e2e/books/fetched/$name"
  elif [ -f "$REPO_ROOT/app/src/androidTest/assets/qa-pd/$name" ]; then
    printf '%s\n' "$REPO_ROOT/app/src/androidTest/assets/qa-pd/$name"
  else
    return 1
  fi
}

# Parses a flow file's `# books: a.epub, b.fb2` comment line, if any, into one basename per
# output line. No output at all (not even an empty line) when the header is absent — the
# caller's `while read` loop then simply never iterates, which is how "no header = no books
# pushed" is implemented.
flow_books() {
  local flow_file="$1"
  local line
  line="$(grep -m1 '^# books:' "$flow_file" || true)"
  [ -z "$line" ] && return 0
  printf '%s\n' "$line" | sed -e 's/^# books:[[:space:]]*//' -e 's/[[:space:]]*$//' \
    | tr ',' '\n' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' -e '/^$/d'
}

# Removes every fixture pushed for the flow that just ran (no-op the first time: PUSHED_LIST
# starts empty). `< /dev/null` matters: without it, `adb shell` would inherit this function's
# own stdin (redirected from $PUSHED_LIST) and read the REST of that file as ITS stdin,
# starving every subsequent `read` in the loop — only the first line would ever get removed.
remove_pushed_fixtures() {
  local bn qpath
  while IFS= read -r bn; do
    if [ -n "$bn" ]; then
      qpath="$(remote_quote "/sdcard/Download/$bn")"
      "$ADB" -s "$SERIAL" shell "rm -f $qpath" < /dev/null >/dev/null 2>&1
    fi
  done < "$PUSHED_LIST"
  : > "$PUSHED_LIST"
}

# Writes a minimal, valid JUnit XML at $1 with a single failing <testcase> (status != SUCCESS,
# so report.py's result_label() calls it FAIL), for a flow the watchdog killed before Maestro
# itself ever got to write (or finish writing) its own junit.xml. $2 is the flow stem (used as
# both the suite/test name and classname — good enough for summary.md's "Journey" column; it
# doesn't need to match a real Maestro id since no screenshot lookup is possible for a flow that
# never produced one), $3 the configured timeout in seconds.
write_timeout_junit() {
  local out_path="$1" stem="$2" timeout_sec="$3"
  local now; now="$(date -u +%Y-%m-%dT%H:%M:%S)"
  cat > "$out_path" <<XML
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="$stem" device="$SERIAL" tests="1" failures="1" time="$timeout_sec" timestamp="$now">
    <testcase id="$stem" name="$stem" classname="$stem" file="$stem.yaml" time="$timeout_sec" timestamp="$now" status="ERROR">
      <failure message="timed out after ${FLOW_TIMEOUT_MIN}m (--flow-timeout-min)">run.sh's watchdog killed this flow's maestro process after $timeout_sec seconds with no completion — see e2e/run.sh --help for --flow-timeout-min. No Maestro output means this is NOT the normal failing-step signal; check the flow's own maestro.log/commands.json up to the kill point, and host/network load at the time, before treating this like an ordinary assertion failure.</failure>
    </testcase>
  </testsuite>
</testsuites>
XML
}

# The same for the colour check: a FAIL row for a checker that ended without writing its own
# junit (python missing, a syntax error, a crash before the first line). Without a row the summary
# would have no "<flow> colours" line and read "all passed" next to a non-zero exit code.
write_checker_failed_junit() {
  local out_path="$1" name="$2" code="$3"
  local now; now="$(date -u +%Y-%m-%dT%H:%M:%S)"
  mkdir -p "$(dirname "$out_path")"
  cat > "$out_path" <<XML
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="$name" tests="1" failures="1" time="0" timestamp="$now">
    <testcase id="$name" name="$name" classname="$name" time="0" timestamp="$now" status="ERROR">
      <failure message="e2e/check_colours.py ended with exit code $code and wrote no result: no colour crop of this flow was checked">e2e/check_colours.py ended with exit code $code and wrote no result: no colour crop of this flow was checked. Its output is in the run's console log.</failure>
    </testcase>
  </testsuite>
</testsuites>
XML
}

OVERALL_EXIT=0
for flow in "${SELECTED_FLOWS[@]}"; do
  stem="$(basename "$flow" .yaml)"
  log "=== flow $stem ==="

  remove_pushed_fixtures

  books=()
  while IFS= read -r b; do
    books+=("$b")
  done < <(flow_books "$flow")

  if [ "${#books[@]}" -eq 0 ]; then
    log "$stem declares no \`# books:\` header — pushing nothing"
  else
    for name in "${books[@]}"; do
      src="$(resolve_fixture "$name")" \
        || die_after_boot "$stem needs fixture '$name' but it was not found under e2e/books/, e2e/books/fetched/, or app/src/androidTest/assets/qa-pd/"
      push_fixture "$src"
    done
    log "pushed ${#books[@]} fixture(s) for $stem: ${books[*]}"
  fi

  FLOW_RUN="$RUN/$stem"
  mkdir -p "$FLOW_RUN"
  # DEVICE_CLASS (tablet | phone) lets a flow that runs on both keep a phone-only step behind
  # `when: true: ${DEVICE_CLASS == 'phone'}`.
  MAESTRO_ARGS=(--device "$SERIAL" test -e "APP_ID=$APP_ID" -e "DEVICE_CLASS=$DEVICE_CLASS" "$flow" \
    --format junit --output "$FLOW_RUN/junit.xml" --test-output-dir "$FLOW_RUN")

  # Per-flow watchdog: run maestro in the background under this script's own control (no
  # `timeout`/`gtimeout` binary on this Mac — confirmed absent, checked with `command -v`), poll
  # whether it's still alive every 5s against a wall-clock deadline, and kill it (SIGTERM, then
  # SIGKILL after a 5s grace period) if it's exceeded. Needed for real: a phone run of J-LIB-02
  # (2026-11-11) produced zero Maestro output for 63 minutes with nothing to stop it short of a
  # human killing the process by hand - see this flag's --help text above.
  log "maestro ${MAESTRO_ARGS[*]} (watchdog: ${FLOW_TIMEOUT_MIN}m)"
  "$MAESTRO" "${MAESTRO_ARGS[@]}" &
  flow_pid=$!
  flow_start=$(date +%s)
  timed_out=0
  while kill -0 "$flow_pid" 2>/dev/null; do
    elapsed=$(( $(date +%s) - flow_start ))
    if [ "$elapsed" -ge "$FLOW_TIMEOUT_SEC" ]; then
      timed_out=1
      log "$stem: exceeded ${FLOW_TIMEOUT_MIN}m watchdog (pid $flow_pid) — sending SIGTERM"
      kill -TERM "$flow_pid" 2>/dev/null
      sleep 5
      if kill -0 "$flow_pid" 2>/dev/null; then
        log "$stem: still alive after SIGTERM — sending SIGKILL"
        kill -KILL "$flow_pid" 2>/dev/null
      fi
      break
    fi
    sleep 5
  done
  wait "$flow_pid" 2>/dev/null
  flow_exit=$?

  if [ "$timed_out" -eq 1 ]; then
    log "$stem: TIMED OUT after ${FLOW_TIMEOUT_MIN}m — recording as FAIL"
    write_timeout_junit "$FLOW_RUN/junit.xml" "$stem" "$FLOW_TIMEOUT_SEC"
    OVERALL_EXIT=1
  else
    log "$stem maestro exit code: $flow_exit"
    [ "$flow_exit" -eq 0 ] || OVERALL_EXIT=1
  fi

  # Colour crops (see the header): checked here, reported as the row "<stem> colours". Its junit
  # goes into its own subdirectory of $RUN, which is where report.py looks for rows.
  if grep -q 'path:.*colour-check/' "$flow"; then
    COLOURS_JUNIT="$RUN/$stem-colours/junit.xml"
    rm -f "$COLOURS_JUNIT"
    "$PYTHON3" "$REPO_ROOT/e2e/check_colours.py" "$flow" "$FLOW_RUN" "$COLOURS_JUNIT" >&2
    colours_exit=$?
    if [ "$colours_exit" -eq 0 ] && [ -s "$COLOURS_JUNIT" ]; then
      log "$stem: colour crops OK"
    else
      log "$stem: colour crops WRONG, not taken, or not checked (checker exit $colours_exit) — recording as FAIL"
      [ -s "$COLOURS_JUNIT" ] || write_checker_failed_junit "$COLOURS_JUNIT" "$stem colours" "$colours_exit"
      OVERALL_EXIT=1
    fi
  fi
done

remove_pushed_fixtures
MAESTRO_EXIT="$OVERALL_EXIT"
log "overall maestro exit code: $MAESTRO_EXIT"

# ---------------------------------------------------------------------------
# 5. Report
# ---------------------------------------------------------------------------
VERSION_NAME="$(sed -n 's/.*versionName = "\(.*\)"/\1/p' "$REPO_ROOT/app/build.gradle.kts" | head -1)"
VERSION_CODE="$(sed -n 's/.*versionCode = \([0-9]*\)/\1/p' "$REPO_ROOT/app/build.gradle.kts" | head -1)"
RUN_DATE="$(date -u +%Y-%m-%d)"

if [ -n "$AVD" ]; then
  DEVICE_LABEL="$AVD ($SERIAL)"
  AVD_JSON="\"$AVD\""
  DEVICE_SLUG="$AVD"
else
  DEVICE_LABEL="$SERIAL"
  AVD_JSON="null"
  DEVICE_SLUG="$SERIAL"
fi

COPY_TO=""
case ",$TAGS," in
  *,smoke,*) NEEDS_COPY=1 ;;
  *) NEEDS_COPY=0 ;;
esac
if [ "$BUILD_TYPE" = "release" ] || [ "$NEEDS_COPY" = "1" ]; then
  RELEASE_DIR="$(sed -n 's/^mirrolit\.releaseDir=//p' "$REPO_ROOT/local.properties" 2>/dev/null | head -1 | tr -d '\r')"
  [ -n "$RELEASE_DIR" ] || RELEASE_DIR="$REPO_ROOT/releases"
  mkdir -p "$RELEASE_DIR"
  COPY_TO="$RELEASE_DIR/e2e-${VERSION_NAME}-${VERSION_CODE}-${DEVICE_SLUG}.md"
fi

SKIPPED_JSON="[]"
if [ "${#SKIPPED_FOR_DEVICE[@]}" -gt 0 ]; then
  skipped_json_parts=""
  for t in "${SKIPPED_FOR_DEVICE[@]}"; do
    skipped_json_parts="${skipped_json_parts}\"${t}\","
  done
  SKIPPED_JSON="[${skipped_json_parts%,}]"
fi

TAGS_JSON="[]"
if [ -n "$TAGS" ]; then
  # Split on commas via IFS into an array, then quote each element explicitly — avoids the
  # unquoted-word-splitting (and, worse, pathname-expansion/globbing) risk of feeding an
  # unquoted command substitution straight to printf.
  IFS=',' read -r -a TAG_ARR <<< "$TAGS"
  tags_json_parts=""
  for t in "${TAG_ARR[@]}"; do
    tags_json_parts="${tags_json_parts}\"${t}\","
  done
  TAGS_JSON="[${tags_json_parts%,}]"
fi

cat > "$RUN/meta.json" <<EOF
{
  "device": "$DEVICE_LABEL",
  "avd": $AVD_JSON,
  "api": $API,
  "build_type": "$BUILD_TYPE",
  "version_name": "$VERSION_NAME",
  "version_code": $VERSION_CODE,
  "date": "$RUN_DATE",
  "device_class": "$DEVICE_CLASS",
  "skipped_for_device": $SKIPPED_JSON,
  "tags": $TAGS_JSON$( [ -n "$COPY_TO" ] && printf ',\n  "copy_to": "%s"' "$COPY_TO" )
}
EOF

log "$PYTHON3 $REPO_ROOT/e2e/report.py $RUN"
REPORT_EXIT=0
"$PYTHON3" "$REPO_ROOT/e2e/report.py" "$RUN" || REPORT_EXIT=$?
REPORT_FAILED=0
if [ "$REPORT_EXIT" -ne 0 ] || [ ! -f "$RUN/summary.md" ]; then
  log "ERROR: report.py failed (exit $REPORT_EXIT) or did not produce $RUN/summary.md — raw results are still at $RUN/junit.xml"
  REPORT_FAILED=1
else
  log "summary: $RUN/summary.md"
  [ -n "$COPY_TO" ] && log "release copy: $COPY_TO"
fi

# ---------------------------------------------------------------------------
# 6. Cleanup: kill the emulator (fixtures were already removed after each flow in step 4, and
# once more just after the loop — PUSHED_LIST is already empty here, so this is belt-and-braces)
# ---------------------------------------------------------------------------
remove_pushed_fixtures

kill_emulator

FINAL_EXIT="$MAESTRO_EXIT"
if [ "$REPORT_FAILED" = "1" ] && [ "$FINAL_EXIT" -eq 0 ]; then
  # Maestro itself passed, but we have no usable summary.md — still a failed run, not a clean
  # one; a maestro exit code that was already non-zero takes priority and is kept as-is.
  FINAL_EXIT=1
fi
exit "$FINAL_EXIT"
