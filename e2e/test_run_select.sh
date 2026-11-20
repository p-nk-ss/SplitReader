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
  # Device settings keep state in fake/: font_scale and a density override. A "stuck" device
  # ignores the change (fake/font-scale-stuck, fake/density-stuck).
  "shell wm density") cat "$box/fake/wm_density"; [ -f "$box/fake/density_override" ] && echo "Override density: $(cat "$box/fake/density_override")" ;;
  "shell wm density reset") rm -f "$box/fake/density_override" ;;
  "shell wm density "*) [ -f "$box/fake/density-stuck" ] || echo "$4" > "$box/fake/density_override" ;;
  "shell settings get system font_scale") if [ -f "$box/fake/font_scale" ]; then cat "$box/fake/font_scale"; else echo 1.0; fi ;;
  "shell settings put system font_scale "*) [ -f "$box/fake/font-scale-stuck" ] || echo "$6" > "$box/fake/font_scale" ;;
  # The app on the device is signed with another key (a debug build where a release is going,
  # or the other way round) until it is uninstalled: fake/install-incompatible. Any other
  # install failure: fake/install-fails.
  "install -r "*)
    if [ -f "$box/fake/install-fails" ]; then echo "Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]" >&2; exit 1; fi
    if [ -f "$box/fake/install-incompatible" ] && [ ! -f "$box/fake/uninstalled" ]; then
      echo "adb: failed to install app.apk: Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package io.mirrolit.app signatures do not match newer version; ignoring!]" >&2
      exit 1
    fi
    echo "Success" ;;
  "uninstall "*) : > "$box/fake/uninstalled"; echo "Success" ;;
esac
exit 0
SH
  cat > "$box/bin/maestro" <<'SH'
#!/bin/sh
box="$(cd "$(dirname "$0")/.." && pwd)"
echo "$*" >> "$box/fake/maestro.log"
# A flow that hangs (the runner is interrupted while it runs): exec, so the pid is the sleep.
[ -f "$box/fake/maestro-hangs" ] && { echo $$ > "$box/fake/maestro.pid"; exec sleep 60; }
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

  # J. --headless: the AVD is booted with -no-window, and nothing else about the run changes
  #    (same other emulator arguments, same flows, class, kill at the end, summary).
  N=$((N + 1)); BOX="$TMP/box$N"; make_sandbox "$BOX"; device "$BOX" tablet
  LABEL="bash $version, --headless on a tablet"
  run_box "$BASH_BIN" "$BOX" --avd Fake_Tablet --headless --tags smoke
  check "exit code is zero"                          passed
  check "the emulator is started without a window"   logged "$BOX/fake/emulator.log" '^-avd Fake_Tablet -memory 4096 -partition-size 8192 -no-snapshot-save -no-window$'
  check "the tablet-only flow ran"                   logged "$BOX/fake/maestro.log" 'J-TAB.yaml'
  check "the two-class flow ran"                     logged "$BOX/fake/maestro.log" 'J-BOTH.yaml'
  check "flows are told the class"                   logged "$BOX/fake/maestro.log" 'DEVICE_CLASS=tablet'
  check "the emulator is killed at the end"          logged "$BOX/fake/adb.log" 'emu kill'
  check "a summary was written"                      says 'summary:'

  # K. without --headless the emulator command line is what it always was (a window).
  N=$((N + 1)); BOX="$TMP/box$N"; make_sandbox "$BOX"; device "$BOX" tablet
  LABEL="bash $version, no --headless"
  run_box "$BASH_BIN" "$BOX" --avd Fake_Tablet --tags smoke
  check "exit code is zero"                          passed
  check "the emulator command line is unchanged"     logged "$BOX/fake/emulator.log" '^-avd Fake_Tablet -memory 4096 -partition-size 8192 -no-snapshot-save$'
  check "no -no-window"                              never_logged "$BOX/fake/emulator.log" 'no-window'

  # L. --headless with --keep-emulator: both hold (headless boot, emulator left running).
  N=$((N + 1)); BOX="$TMP/box$N"; make_sandbox "$BOX"; device "$BOX" tablet
  LABEL="bash $version, --headless --keep-emulator"
  run_box "$BASH_BIN" "$BOX" --avd Fake_Tablet --keep-emulator --headless --tags smoke
  check "exit code is zero"                          passed
  check "the emulator is started without a window"   logged "$BOX/fake/emulator.log" '-no-window$'
  check "the emulator is not killed"                 never_logged "$BOX/fake/adb.log" 'emu kill'

  # M. --headless with --serial: this run starts no emulator, so there is nothing to make
  #    headless — it says so, starts none, and still refuses nothing it ran before.
  N=$((N + 1)); BOX="$TMP/box$N"; make_sandbox "$BOX"; device "$BOX" tablet; : > "$BOX/fake/always-up"
  LABEL="bash $version, --headless with --serial"
  run_box "$BASH_BIN" "$BOX" --serial emulator-5554 --headless --tags J-TAB
  check "exit code is zero"                          passed
  check "says --headless is ignored"                 says '--headless.*ignored'
  check "no emulator was started"                    no_file "$BOX/fake/emulator.log"
  check "an emulator it did not start is not killed" never_logged "$BOX/fake/adb.log" 'emu kill'
  check "the flow ran"                               logged "$BOX/fake/maestro.log" 'J-TAB.yaml'

  # Device-setting headers (`# font_scale:`, `# wm_density:`).
  scaled_flow() { # scaled_flow BOX NAME HEADER-LINES
    printf 'appId: ${APP_ID}\nname: %s\ntags: [full, phone, %s]\n%s---\n- launchApp\n' "$2" "$2" "$3" > "$1/e2e/flows/reading/$2.yaml"
  }
  last_font_scale_put() { grep 'settings put system font_scale' "$BOX/fake/adb.log" | tail -1 | grep -q -- "$1\$"; }
  last_density_cmd()    { grep 'shell wm density [0-9r]' "$BOX/fake/adb.log" | tail -1 | grep -q -- "$1\$"; }

  # N. both headers applied: set before the flow, read back, put back after it.
  N=$((N + 1)); BOX="$TMP/box$N"; make_sandbox "$BOX"; device "$BOX" phone
  scaled_flow "$BOX" J-SCALED $'# font_scale: 1.3\n# wm_density: 569\n'
  LABEL="bash $version, font_scale + wm_density applied"
  run_box "$BASH_BIN" "$BOX" --avd Fake_Phone --tags J-SCALED
  check "exit code is zero"                          passed
  check "the flow ran"                               logged "$BOX/fake/maestro.log" 'J-SCALED.yaml'
  check "font_scale 1.3 was set"                     logged "$BOX/fake/adb.log" 'settings put system font_scale 1.3$'
  check "density 569 was set"                        logged "$BOX/fake/adb.log" 'shell wm density 569$'
  check "font_scale is put back to 1.0 last"         last_font_scale_put '1.0'
  check "density is reset last"                      last_density_cmd 'reset'
  check "the restore is logged"                      says 'restore (after J-SCALED)'
  check "the start-up baseline is logged"            says 'restore (start-up)'

  # O. a malformed header: the run stops before the build, naming the flow and the header.
  for bad in '# font_scale: 1,3' '# font_scale: 1.3x' '# font_scale: 1.3 # large' '#font_scale: 1.3' '# wm_density: 569dp'; do
    N=$((N + 1)); BOX="$TMP/box$N"; make_sandbox "$BOX"; device "$BOX" phone
    scaled_flow "$BOX" J-BADHDR "$bad"$'\n'
    LABEL="bash $version, malformed header [$bad]"
    run_box "$BASH_BIN" "$BOX" --avd Fake_Phone --tags J-BADHDR
    check "exit code is non-zero"                    failed
    check "names the flow"                           says 'J-BADHDR.yaml: malformed'
    check "nothing was built"                        no_file "$BOX/fake/gradle.log"
  done

  # P. the device does not take the font scale: the flow is a FAIL row and never runs.
  N=$((N + 1)); BOX="$TMP/box$N"; make_sandbox "$BOX"; device "$BOX" phone; : > "$BOX/fake/font-scale-stuck"
  scaled_flow "$BOX" J-SCALED $'# font_scale: 1.3\n'
  LABEL="bash $version, font_scale not applied"
  run_box "$BASH_BIN" "$BOX" --avd Fake_Phone --tags J-SCALED,J-BOTH
  check "exit code is non-zero"                      failed
  check "says what was read back"                    says "reads back '1.0'"
  check "the flow did not run"                       never_logged "$BOX/fake/maestro.log" 'J-SCALED.yaml'
  check "the next flow still ran"                    logged "$BOX/fake/maestro.log" 'J-BOTH.yaml'
  check "the summary has the flow as a FAIL"         summary_row 'J-SCALED.*FAIL'

  # Q. the same for the density.
  N=$((N + 1)); BOX="$TMP/box$N"; make_sandbox "$BOX"; device "$BOX" phone; : > "$BOX/fake/density-stuck"
  scaled_flow "$BOX" J-SCALED $'# wm_density: 569\n'
  LABEL="bash $version, wm_density not applied"
  run_box "$BASH_BIN" "$BOX" --avd Fake_Phone --tags J-SCALED
  check "exit code is non-zero"                      failed
  check "the flow did not run"                       never_logged "$BOX/fake/maestro.log" 'J-SCALED.yaml'
  check "the summary has the flow as a FAIL"         summary_row 'J-SCALED.*FAIL'
  check "density is reset last"                      last_density_cmd 'reset'

  # R. the runner is stopped (SIGTERM) while a scaled flow runs: the device is put back, maestro
  #    and the emulator this run started are stopped.
  N=$((N + 1)); BOX="$TMP/box$N"; make_sandbox "$BOX"; device "$BOX" phone; : > "$BOX/fake/maestro-hangs"
  scaled_flow "$BOX" J-SCALED $'# font_scale: 1.3\n# wm_density: 569\n'
  LABEL="bash $version, SIGTERM during a scaled flow"
  HOME="$BOX/home" PATH="$BOX/bin:$PATH" "$BASH_BIN" "$BOX/e2e/run.sh" --avd Fake_Phone --tags J-SCALED > "$BOX/out.txt" 2>&1 &
  runner=$!
  for _ in $(seq 1 150); do [ -s "$BOX/fake/maestro.log" ] && break; sleep 0.2; done
  sleep 1
  kill -TERM "$runner"
  wait "$runner"; EXIT_CODE=$?
  OUTPUT="$(cat "$BOX/out.txt")"
  check "exit code is non-zero"                      failed
  check "the flow had started"                       logged "$BOX/fake/maestro.log" 'J-SCALED.yaml'
  check "says it was interrupted"                    says 'interrupted (SIGTERM)'
  check "font_scale is put back to 1.0 last"         last_font_scale_put '1.0'
  check "density is reset last"                      last_density_cmd 'reset'
  check "aeroplane mode is switched off again at exit" test "$(grep -c 'airplane-mode disable' "$BOX/fake/adb.log")" -ge 2
  check "the restore is logged"                      says 'restore (exit)'
  check "the emulator it started is killed"          logged "$BOX/fake/adb.log" 'emu kill'
  check "the hanging maestro is stopped"            process_gone "$BOX/fake/maestro.pid"

  # S. the emulator has the app signed with the other key (debug where a release goes, or the
  #    other way round): adb refuses the update, run.sh uninstalls it from that emulator and
  #    installs again; the flows run.
  N=$((N + 1)); BOX="$TMP/box$N"; make_sandbox "$BOX"; device "$BOX" tablet; : > "$BOX/fake/install-incompatible"
  LABEL="bash $version, app on the emulator signed with another key"
  run_box "$BASH_BIN" "$BOX" --avd Fake_Tablet --tags J-TAB
  check "exit code is zero"                          passed
  check "the app is uninstalled from the emulator"   logged "$BOX/fake/adb.log" '^-s emulator-5554 uninstall io.mirrolit.app$'
  check "says why it uninstalled"                    says 'signed with another key'
  check "installed twice (refused, then after the uninstall)" test "$(grep -c ' install -r ' "$BOX/fake/adb.log")" -eq 2
  check "the flow ran"                               logged "$BOX/fake/maestro.log" 'J-TAB.yaml'

  # T. any other install failure still stops the run, and nothing is uninstalled.
  N=$((N + 1)); BOX="$TMP/box$N"; make_sandbox "$BOX"; device "$BOX" tablet; : > "$BOX/fake/install-fails"
  LABEL="bash $version, install fails for another reason"
  run_box "$BASH_BIN" "$BOX" --avd Fake_Tablet --tags J-TAB
  check "exit code is non-zero"                      failed
  check "says the install failed"                    says 'install failed'
  check "nothing is uninstalled"                     never_logged "$BOX/fake/adb.log" 'uninstall'
  check "maestro never ran"                          no_file "$BOX/fake/maestro.log"
  check "the emulator it started is killed"          logged "$BOX/fake/adb.log" 'emu kill'

  # U. the same signature mismatch on an emulator this run did NOT start (--serial): its data
  #    (someone's manual-test AVD) is not wiped — the run stops and says what to run by hand.
  N=$((N + 1)); BOX="$TMP/box$N"; make_sandbox "$BOX"; device "$BOX" tablet; : > "$BOX/fake/always-up"
  : > "$BOX/fake/install-incompatible"
  LABEL="bash $version, --serial, app signed with another key"
  run_box "$BASH_BIN" "$BOX" --serial emulator-5554 --tags J-TAB
  check "exit code is non-zero"                      failed
  check "nothing is uninstalled"                     never_logged "$BOX/fake/adb.log" 'uninstall'
  check "says what to run by hand"                   says 'will not uninstall it from an emulator it did not start'
  check "maestro never ran"                          no_file "$BOX/fake/maestro.log"
  check "the attached emulator is not killed"        never_logged "$BOX/fake/adb.log" 'emu kill'
done

if [ "$FAILS" -gt 0 ]; then
  printf '%d of %d checks failed\n' "$FAILS" "$CHECKS"
  exit 1
fi
printf 'OK (%d checks, bash: %s)\n' "$CHECKS" "${BASHES[*]}"
