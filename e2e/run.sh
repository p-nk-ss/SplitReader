#!/usr/bin/env bash
# e2e/run.sh — builds the app, boots (or attaches to) an emulator, installs it, pushes the
# fixture books, runs a Maestro tag set against e2e/flows/, and writes a report.
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
#   --tags TAGLIST      Passed straight through to `maestro test --include-tags TAGLIST`. Omit to
#                      run every flow under e2e/flows regardless of tag. Comma-separated, e.g.
#                      "smoke" or "full,tablet".
#   --keep-emulator    Don't kill the emulator this run started (--avd only; ignored with
#                      --serial, since that emulator wasn't started by this script).
#
# Every run writes e2e/reports/<timestamp>/{junit.xml,summary.md,meta.json,...} (gitignored).
# For `--build release` or a --tags value that contains "smoke", summary.md is additionally
# copied to <releases dir>/e2e-<versionName>-<versionCode>-<device>.md, where <releases dir> is
# resolved exactly like app/build.gradle.kts resolves `releaseDir` for copyReleaseBundle:
# `mirrolit.releaseDir` in local.properties if set, else `<repo root>/releases`.
#
# Exit code is Maestro's own exit code (0 = all included flows passed), so this script is
# CI-usable as a gate. Report generation and cleanup (pushed fixtures removed, emulator killed)
# still run even when Maestro reports failures.
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

while [ $# -gt 0 ]; do
  case "$1" in
    --avd) AVD="$2"; shift 2 ;;
    --serial) SERIAL="$2"; shift 2 ;;
    --build) BUILD_TYPE="$2"; shift 2 ;;
    --tags) TAGS="$2"; shift 2 ;;
    --keep-emulator) KEEP_EMULATOR=1; shift ;;
    -h|--help) grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[ -n "$AVD" ] || [ -n "$SERIAL" ] || die "one of --avd NAME or --serial SERIAL is required"
[ -z "$AVD" ] || [ -z "$SERIAL" ] || die "--avd and --serial are mutually exclusive"
case "$BUILD_TYPE" in debug|release) ;; *) die "--build must be debug or release" ;; esac
[ -n "$MAESTRO" ] || die "maestro not found on PATH"
[ -x "$ADB" ] || die "adb not found at $ADB"

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

  SERIAL=""
  tries=0
  while [ $tries -lt 120 ]; do
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
  [ -n "$SERIAL" ] || die "AVD $AVD did not finish booting in time"
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

# ---------------------------------------------------------------------------
# 3. Install, fixtures, media scan
# ---------------------------------------------------------------------------
log "adb install -r $APK"
"$ADB" -s "$SERIAL" install -r "$APK" || { kill_emulator; die "install failed"; }

API="$("$ADB" -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r\n')"
log "device API level: $API"

RUN="$REPO_ROOT/e2e/reports/$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$RUN"

log "fetching real (non-synthetic) fixture books"
if ! "$REPO_ROOT/e2e/books/fetch_fixtures.sh"; then
  log "WARNING: fetch_fixtures.sh reported a failure (network/Gutenberg?) — continuing with" \
      "whatever fixtures are already present under e2e/books/fetched/"
fi

log "listing /sdcard/Download before pushing fixtures"
"$ADB" -s "$SERIAL" shell ls -la /sdcard/Download > "$RUN/sdcard_download_before.txt" 2>&1 || true

PUSHED_LIST="$RUN/pushed_files.txt"
: > "$PUSHED_LIST"

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

push_fixture() {
  local local_path="$1"
  local bn
  bn="$(basename "$local_path")"
  # adb push's remote argument goes straight to the sync protocol, not through a remote shell,
  # so (unlike media_scan/rm below) it needs no remote-shell quoting — a space or quote in $bn
  # survives it literally.
  "$ADB" -s "$SERIAL" push "$local_path" "/sdcard/Download/$bn" >/dev/null || die "push failed: $local_path"
  media_scan "/sdcard/Download/$bn"
  echo "$bn" >> "$PUSHED_LIST"
}

log "pushing e2e/books/* fixtures (excluding .py/.sh/.md and fetched/, pushed separately)"
for f in "$REPO_ROOT"/e2e/books/*; do
  bn="$(basename "$f")"
  case "$bn" in
    *.py|*.sh|*.md|fetched) continue ;;
  esac
  [ -f "$f" ] && push_fixture "$f"
done

if [ -d "$REPO_ROOT/e2e/books/fetched" ]; then
  log "pushing e2e/books/fetched/*"
  for f in "$REPO_ROOT"/e2e/books/fetched/*; do
    [ -f "$f" ] && push_fixture "$f"
  done
fi

QA_PD_BOOK="$REPO_ROOT/app/src/androidTest/assets/qa-pd/gutenberg-11.epub"
if [ -f "$QA_PD_BOOK" ]; then
  log "pushing $QA_PD_BOOK"
  push_fixture "$QA_PD_BOOK"
else
  log "WARNING: $QA_PD_BOOK not found, skipping"
fi

log "pushed $(wc -l < "$PUSHED_LIST" | tr -d ' ') fixture file(s) to /sdcard/Download"

# ---------------------------------------------------------------------------
# 4. Maestro
# ---------------------------------------------------------------------------
MAESTRO_ARGS=(--device "$SERIAL" test -e "APP_ID=$APP_ID" "$REPO_ROOT/e2e/flows" \
  --format junit --output "$RUN/junit.xml" --test-output-dir "$RUN")
if [ -n "$TAGS" ]; then
  MAESTRO_ARGS+=(--include-tags "$TAGS")
fi

log "maestro ${MAESTRO_ARGS[*]}"
"$MAESTRO" "${MAESTRO_ARGS[@]}"
MAESTRO_EXIT=$?
log "maestro exit code: $MAESTRO_EXIT"

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
# 6. Cleanup: remove only what we pushed, then (maybe) kill the emulator
# ---------------------------------------------------------------------------
log "removing pushed fixtures from /sdcard/Download"
# `< /dev/null` on the adb call matters: without it, `adb shell` inherits this loop's own
# stdin (redirected from $PUSHED_LIST below) and reads the REST of that file as its own input,
# starving every subsequent `read` — only the first line would ever get removed.
while IFS= read -r bn; do
  if [ -n "$bn" ]; then
    qpath="$(remote_quote "/sdcard/Download/$bn")"
    "$ADB" -s "$SERIAL" shell "rm -f $qpath" < /dev/null >/dev/null 2>&1
  fi
done < "$PUSHED_LIST"

kill_emulator

FINAL_EXIT="$MAESTRO_EXIT"
if [ "$REPORT_FAILED" = "1" ] && [ "$FINAL_EXIT" -eq 0 ]; then
  # Maestro itself passed, but we have no usable summary.md — still a failed run, not a clean
  # one; a maestro exit code that was already non-zero takes priority and is kept as-is.
  FINAL_EXIT=1
fi
exit "$FINAL_EXIT"
