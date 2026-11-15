#!/usr/bin/env bash
# Checks what e2e/run.sh itself does with the selection rules of flow_select.sh — without a
# device, a build or Maestro: nothing selected, an unreadable device class, a flow whose tags
# cannot be read. (test_flow_select.sh checks the rules; this checks the runner around them.)
#
# How: run.sh finds everything relative to its own place and to $HOME, so the test copies it
# (with flow_select.sh, report.py, check_colours.py) into a throwaway tree that has three tiny
# flows and fakes for gradlew, adb, emulator and maestro; each fake writes what it was asked to
# a log. The real run.sh then runs start to end in a second or two.
# Every scenario runs under each bash on this machine: /bin/bash (3.2 on macOS, where expanding
# an empty array under `set -u` is an error) and a newer one if installed (where it is not).
#
# Run: bash e2e/test_run_select.sh   -> prints "OK (n checks)" and exits 0, or lists failures.
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
FAILS=0
CHECKS=0

BASHES=()
for candidate in /bin/bash /opt/homebrew/bin/bash /usr/local/bin/bash /usr/bin/bash; do
  [ -x "$candidate" ] && BASHES+=("$candidate")
done

# --- the throwaway tree -------------------------------------------------------------------
make_sandbox() { # make_sandbox DIR
  local box="$1"
  mkdir -p "$box/e2e/flows/reading" "$box/e2e/books" "$box/app" "$box/fake" \
    "$box/home/Library/Android/sdk/platform-tools" "$box/home/Library/Android/sdk/emulator" "$box/bin"
  cp "$HERE/run.sh" "$HERE/flow_select.sh" "$HERE/report.py" "$box/e2e/"
  [ -f "$HERE/check_colours.py" ] && cp "$HERE/check_colours.py" "$box/e2e/"
  printf 'android {\n  defaultConfig {\n    versionCode = 28\n    versionName = "1.0"\n  }\n}\n' > "$box/app/build.gradle.kts"
  printf '#!/bin/sh\nexit 0\n' > "$box/e2e/books/fetch_fixtures.sh"

  flow() { # flow NAME TAGS-LINE
    printf 'appId: ${APP_ID}\nname: %s\n%s\n---\n- launchApp\n' "$1" "$2" > "$box/e2e/flows/reading/$1.yaml"
  }
  flow J-TAB   'tags: [smoke, tablet, J-TAB]'
  flow J-PHONE 'tags: [smoke, phone, J-PHONE]'
  flow J-BOTH  'tags: [smoke, phone, tablet, J-BOTH]'

  cat > "$box/gradlew" <<'SH'
#!/bin/sh
box="$(cd "$(dirname "$0")" && pwd)"
echo "$*" >> "$box/fake/gradle.log"
case "$*" in *assembleDebug*) mkdir -p "$box/app/build/outputs/apk/debug"; : > "$box/app/build/outputs/apk/debug/app-debug.apk" ;; esac
exit 0
SH
  cat > "$box/home/Library/Android/sdk/emulator/emulator" <<'SH'
#!/bin/sh
box="$(cd "$(dirname "$0")/../../../../.." && pwd)"
echo "$*" >> "$box/fake/emulator.log"
# An AVD that never finishes booting: the process stays, adb never lists it (see the fake adb).
if [ -f "$box/fake/never-boots" ]; then echo $$ > "$box/fake/emulator.pid"; exec sleep 3600; fi
exit 0
SH
  cat > "$box/home/Library/Android/sdk/platform-tools/adb" <<'SH'
#!/bin/sh
box="$(cd "$(dirname "$0")/../../../../.." && pwd)"
echo "$*" >> "$box/fake/adb.log"
[ "$1" = "-s" ] && shift 2
case "$*" in
  devices)
    echo "List of devices attached"
    # The emulator is "up" once run.sh has restarted the adb server for it (or always, for --serial).
    if [ -f "$box/fake/never-boots" ]; then :
    elif [ -f "$box/fake/always-up" ] || grep -q '^start-server$' "$box/fake/adb.log"; then printf 'emulator-5554\tdevice\n'; fi ;;
  "shell getprop sys.boot_completed"|"shell getprop ro.kernel.qemu") echo 1 ;;
  "shell getprop ro.build.version.sdk") echo 35 ;;
  "shell wm size") cat "$box/fake/wm_size" ;;
  "shell wm density") cat "$box/fake/wm_density" ;;
esac
exit 0
SH
  cat > "$box/bin/maestro" <<'SH'
#!/bin/sh
box="$(cd "$(dirname "$0")/.." && pwd)"
echo "$*" >> "$box/fake/maestro.log"
out=""; flow=""
while [ $# -gt 0 ]; do
  case "$1" in --output) out="$2"; shift ;; *.yaml) flow="$1" ;; esac
  shift
done
stem="$(basename "$flow" .yaml)"
[ -n "$out" ] && printf '<?xml version="1.0"?>\n<testsuites><testsuite name="%s" tests="1" failures="0"><testcase id="%s" name="%s" classname="%s" time="1" status="SUCCESS"/></testsuite></testsuites>\n' "$stem" "$stem" "$stem" "$stem" > "$out"
exit 0
SH
  chmod +x "$box/gradlew" "$box/e2e/books/fetch_fixtures.sh" "$box/bin/maestro" \
    "$box/home/Library/Android/sdk/emulator/emulator" "$box/home/Library/Android/sdk/platform-tools/adb"
}

device() { # device BOX tablet|phone|unreadable
  case "$2" in
    tablet) echo 'Physical size: 2560x1600' > "$1/fake/wm_size"; echo 'Physical density: 320' > "$1/fake/wm_density" ;;
    phone)  echo 'Physical size: 1280x2856' > "$1/fake/wm_size"; echo 'Physical density: 480' > "$1/fake/wm_density" ;;
    *)      echo 'error: device offline' > "$1/fake/wm_size"; : > "$1/fake/wm_density" ;;
  esac
}

# run_box BASH BOX ARGS... -> sets EXIT_CODE, OUTPUT (stdout + stderr)
run_box() {
  local bash_bin="$1" box="$2"; shift 2
  OUTPUT="$(HOME="$box/home" PATH="$box/bin:$PATH" "$bash_bin" "$box/e2e/run.sh" "$@" 2>&1)"
  EXIT_CODE=$?
}

check() { # check DESCRIPTION COMMAND...   (passes when the command succeeds)
  local what="$1"; shift
  CHECKS=$((CHECKS + 1))
  if ! "$@"; then
    FAILS=$((FAILS + 1))
    printf 'FAIL [%s]: %s\n' "$LABEL" "$what"
  fi
}
failed()        { [ "$EXIT_CODE" -ne 0 ]; }
passed()        { [ "$EXIT_CODE" -eq 0 ]; }
says()          { printf '%s' "$OUTPUT" | grep -q -- "$1"; }
never_says()    { ! says "$1"; }
logged()        { [ -f "$1" ] && grep -q -- "$2" "$1"; }
never_logged()  { ! logged "$1" "$2"; }
no_file()       { [ ! -e "$1" ]; }
process_gone()  { [ -f "$1" ] && ! kill -0 "$(cat "$1")" 2>/dev/null; }
summary_row()   { grep -q -- "$1" "$(find "$BOX/e2e/reports" -name summary.md 2>/dev/null | head -1)" 2>/dev/null; }

N=0
for BASH_BIN in "${BASHES[@]}"; do
  version="$("$BASH_BIN" -c 'echo $BASH_VERSION')"

  # A. --tags names no flow at all: stop before anything is built or booted.
  N=$((N + 1)); BOX="$TMP/box$N"; make_sandbox "$BOX"; device "$BOX" phone
  LABEL="bash $version, --tags matches no flow"
  run_box "$BASH_BIN" "$BOX" --avd Fake_Phone --tags J-NOPE
  check "exit code is non-zero"                      failed
  check "says that no flow was selected"             says 'no flow selected'
  check "names the tags"                             says 'J-NOPE'
  check "no bash 'unbound variable' crash"           never_says 'unbound variable'
  check "nothing was built"                          no_file "$BOX/fake/gradle.log"
  check "no emulator was started"                    no_file "$BOX/fake/emulator.log"

  # B. --tags names a tablet-only flow, the device is a phone: stop, and kill the emulator.
  N=$((N + 1)); BOX="$TMP/box$N"; make_sandbox "$BOX"; device "$BOX" phone
  LABEL="bash $version, tablet-only flow on a phone"
  run_box "$BASH_BIN" "$BOX" --avd Fake_Phone --tags J-TAB
  check "exit code is non-zero"                      failed
  check "says that no flow was selected"             says 'no flow selected'
  check "names the device class"                     says 'phone'
  check "no bash 'unbound variable' crash"           never_says 'unbound variable'
  check "the emulator it started is killed"          logged "$BOX/fake/adb.log" 'emu kill'
  check "maestro never ran"                          no_file "$BOX/fake/maestro.log"
  check "no summary of an empty run"                 never_says 'summary:'

  # C. the device class cannot be read: stop before any flow, kill the emulator.
  N=$((N + 1)); BOX="$TMP/box$N"; make_sandbox "$BOX"; device "$BOX" unreadable
  LABEL="bash $version, unreadable device class"
  run_box "$BASH_BIN" "$BOX" --avd Fake_Phone --tags smoke
  check "exit code is non-zero"                      failed
  check "says the device class is unknown"           says 'device class'
  check "the emulator it started is killed"          logged "$BOX/fake/adb.log" 'emu kill'
  check "maestro never ran"                          no_file "$BOX/fake/maestro.log"

  # D. the ordinary run still works: a tablet runs the tablet and the two-class flow.
  N=$((N + 1)); BOX="$TMP/box$N"; make_sandbox "$BOX"; device "$BOX" tablet
  LABEL="bash $version, smoke on a tablet"
  run_box "$BASH_BIN" "$BOX" --avd Fake_Tablet --tags smoke
  check "exit code is zero"                          passed
  check "the tablet-only flow ran"                   logged "$BOX/fake/maestro.log" 'J-TAB.yaml'
  check "the two-class flow ran"                     logged "$BOX/fake/maestro.log" 'J-BOTH.yaml'
  check "the phone-only flow did not run"            never_logged "$BOX/fake/maestro.log" 'J-PHONE.yaml'
  check "flows are told the class"                   logged "$BOX/fake/maestro.log" 'DEVICE_CLASS=tablet'
  check "the emulator is killed at the end"          logged "$BOX/fake/adb.log" 'emu kill'
  SUMMARY="$(find "$BOX/e2e/reports" -name summary.md 2>/dev/null | head -1)"
  check "a summary was written"                      test -n "$SUMMARY"
  check "the summary names the flow skipped for the device" grep -q 'J-PHONE' "${SUMMARY:-/dev/null}"

  # E. --serial (an emulator this run did not start): nothing selected is an error too, and that
  #    emulator is left alone.
  N=$((N + 1)); BOX="$TMP/box$N"; make_sandbox "$BOX"; device "$BOX" tablet; : > "$BOX/fake/always-up"
  LABEL="bash $version, --serial, phone-only flow on a tablet"
  run_box "$BASH_BIN" "$BOX" --serial emulator-5554 --tags J-PHONE
  check "exit code is non-zero"                      failed
  check "says that no flow was selected"             says 'no flow selected'
  check "an emulator it did not start is not killed" never_logged "$BOX/fake/adb.log" 'emu kill'
  check "maestro never ran"                          no_file "$BOX/fake/maestro.log"

  # F. a flow whose tags line is not an inline list: refuse to run (it would otherwise have no
  #    tags: run on every device and match no --tags).
  N=$((N + 1)); BOX="$TMP/box$N"; make_sandbox "$BOX"; device "$BOX" tablet
  printf 'appId: ${APP_ID}\nname: J-BLOCK\ntags:\n  - smoke\n  - phone\n---\n- launchApp\n' > "$BOX/e2e/flows/reading/J-BLOCK.yaml"
  LABEL="bash $version, a flow with a block-list tags header"
  run_box "$BASH_BIN" "$BOX" --avd Fake_Tablet --tags smoke
  check "exit code is non-zero"                      failed
  check "names the flow"                             says 'J-BLOCK.yaml'
  check "nothing was built"                          no_file "$BOX/fake/gradle.log"

  # G. the colour checker itself breaks (here: it dies before writing anything). The run fails AND
  #    the summary says so — a summary without the colours row reads "all passed".
  N=$((N + 1)); BOX="$TMP/box$N"; make_sandbox "$BOX"; device "$BOX" tablet
  printf 'appId: ${APP_ID}\nname: J-COL\ntags: [smoke, tablet, J-COL]\n---\n- takeScreenshot:\n    path: colour-check/step1-x__PaperBg\n' > "$BOX/e2e/flows/reading/J-COL.yaml"
  printf 'raise SystemExit("boom")\n' > "$BOX/e2e/check_colours.py"
  LABEL="bash $version, the colour checker crashes without a junit"
  run_box "$BASH_BIN" "$BOX" --avd Fake_Tablet --tags J-COL
  check "exit code is non-zero"                      failed
  check "the summary has the colours row"            summary_row 'J-COL colours'
  check "the colours row is a FAIL"                  summary_row 'J-COL colours.*FAIL'
  check "the summary does not read all passed"       summary_row '1 failed'

  # H. the same with the real checker and no Color.kt in the tree (the app moved or renamed it).
  N=$((N + 1)); BOX="$TMP/box$N"; make_sandbox "$BOX"; device "$BOX" tablet
  printf 'appId: ${APP_ID}\nname: J-COL\ntags: [smoke, tablet, J-COL]\n---\n- takeScreenshot:\n    path: colour-check/step1-x__PaperBg\n' > "$BOX/e2e/flows/reading/J-COL.yaml"
  LABEL="bash $version, the colour checker finds no Color.kt"
  run_box "$BASH_BIN" "$BOX" --avd Fake_Tablet --tags J-COL
  check "exit code is non-zero"                      failed
  check "the colours row is a FAIL"                  summary_row 'J-COL colours.*FAIL'

  # I. the AVD never finishes booting: the emulator process this run started must not stay.
  N=$((N + 1)); BOX="$TMP/box$N"; make_sandbox "$BOX"; device "$BOX" tablet; : > "$BOX/fake/never-boots"
  LABEL="bash $version, the AVD never boots"
  E2E_BOOT_WAIT_TRIES=1 run_box "$BASH_BIN" "$BOX" --avd Fake_Tablet --tags smoke
  check "exit code is non-zero"                      failed
  check "says the AVD did not boot"                  says 'did not finish booting'
  check "the emulator process it started is gone"    process_gone "$BOX/fake/emulator.pid"
  check "maestro never ran"                          no_file "$BOX/fake/maestro.log"
  [ -f "$BOX/fake/emulator.pid" ] && kill "$(cat "$BOX/fake/emulator.pid")" 2>/dev/null
done

if [ "$FAILS" -gt 0 ]; then
  printf '%d of %d checks failed\n' "$FAILS" "$CHECKS"
  exit 1
fi
printf 'OK (%d checks, bash: %s)\n' "$CHECKS" "${BASHES[*]}"
