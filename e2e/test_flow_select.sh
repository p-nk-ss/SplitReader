#!/usr/bin/env bash
# Checks e2e/flow_select.sh (the rules run.sh uses to pick flow files) without a device.
# Run: bash e2e/test_flow_select.sh   -> prints "OK (n checks)" and exits 0, or lists failures.
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=flow_select.sh
. "$HERE/flow_select.sh"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
FAILS=0
CHECKS=0

flow() { # flow NAME "tag, tag" -> path
  printf 'appId: ${APP_ID}\nname: %s\ntags: [%s]\n---\n- launchApp\n' "$1" "$2" > "$TMP/$1.yaml"
  printf '%s' "$TMP/$1.yaml"
}
expect() { # expect yes|no DESCRIPTION COMMAND...
  local want="$1" what="$2"; shift 2
  CHECKS=$((CHECKS + 1))
  if "$@"; then got=yes; else got=no; fi
  if [ "$got" != "$want" ]; then
    FAILS=$((FAILS + 1))
    printf 'FAIL: %s (expected %s, got %s)\n' "$what" "$want" "$got"
  fi
}
expect_eq() { # expect_eq WANT GOT DESCRIPTION
  CHECKS=$((CHECKS + 1))
  if [ "$1" != "$2" ]; then
    FAILS=$((FAILS + 1))
    printf 'FAIL: %s (expected "%s", got "%s")\n' "$3" "$1" "$2"
  fi
}

TABLET_ONLY="$(flow tablet-only 'smoke, full, tablet, J-RDR-01')"
PHONE_ONLY="$(flow phone-only 'smoke, full, phone, J-RDR-02')"
BOTH="$(flow both 'smoke, full, rotation, phone, tablet, J-RDR-03')"
NEITHER="$(flow neither 'smoke, full, library, J-LIB-01')"

# Device rule
expect yes "tablet-only flow runs on a tablet"        flow_matches_device "$TABLET_ONLY" tablet
expect no  "tablet-only flow is skipped on a phone"   flow_matches_device "$TABLET_ONLY" phone
expect yes "phone-only flow runs on a phone"          flow_matches_device "$PHONE_ONLY" phone
expect no  "phone-only flow is skipped on a tablet"   flow_matches_device "$PHONE_ONLY" tablet
expect yes "flow tagged for both runs on a tablet"    flow_matches_device "$BOTH" tablet
expect yes "flow tagged for both runs on a phone"     flow_matches_device "$BOTH" phone
expect yes "flow with neither tag runs on a tablet"   flow_matches_device "$NEITHER" tablet
expect yes "flow with neither tag runs on a phone"    flow_matches_device "$NEITHER" phone
expect no  "no device class: a tablet-only flow does not run"  flow_matches_device "$TABLET_ONLY" ""
expect no  "no device class: a two-class flow does not run"    flow_matches_device "$BOTH" ""
expect no  "no device class: an untagged flow does not run"    flow_matches_device "$NEITHER" ""
expect no  "class 'unknown' runs nothing"                      flow_matches_device "$BOTH" unknown

# Device class from `wm size` / `wm density`
expect_eq tablet "$(device_class_from_wm 'Physical size: 2560x1600' 'Physical density: 320')" "Pixel Tablet is a tablet"
expect_eq phone  "$(device_class_from_wm 'Physical size: 1280x2856' 'Physical density: 480')" "Pixel 10 Pro is a phone"
expect_eq tablet "$(device_class_from_wm 'Physical size: 2048x1536' 'Physical density: 320')" "Nexus 9 is a tablet"
expect_eq phone  "$(device_class_from_wm 'Physical size: 1600x2560
Override size: 1080x2400' 'Physical density: 320
Override density: 420')" "an override size/density wins"
expect_eq ""     "$(device_class_from_wm 'error: device offline' '')" "unreadable output gives no class"

# Tags a flow may write with quotes; tags flow_tags cannot read
QUOTED="$TMP/quoted.yaml"
printf 'appId: ${APP_ID}\nname: quoted\ntags: ["smoke", %stablet%s, J-Q]\n---\n- launchApp\n' "'" "'" > "$QUOTED"
BLOCK="$TMP/block.yaml"
printf 'appId: ${APP_ID}\nname: block\ntags:\n  - smoke\n  - tablet\n---\n- launchApp\n' > "$BLOCK"
NOTAGS="$TMP/notags.yaml"
printf 'appId: ${APP_ID}\nname: notags\n---\n- launchApp\n' > "$NOTAGS"
expect_eq "smoke tablet J-Q" "$(flow_tags "$QUOTED" | tr '\n' ' ' | sed 's/ $//')" "quotes around a tag are dropped"
expect no  "a quoted tablet-only flow is skipped on a phone"   flow_matches_device "$QUOTED" phone
expect yes "an inline tags list is readable"          flow_tags_readable "$BOTH"
expect yes "a quoted inline tags list is readable"    flow_tags_readable "$QUOTED"
expect no  "a block-list tags header is not readable" flow_tags_readable "$BLOCK"
expect no  "a flow without tags is not readable"      flow_tags_readable "$NOTAGS"

# --tags rule (unchanged behaviour, now covered)
TAGS=""
expect yes "no --tags selects every flow"             flow_matches_tags "$NEITHER"
TAGS="J-RDR-01"
expect yes "--tags J-RDR-01 selects that flow"        flow_matches_tags "$TABLET_ONLY"
expect no  "--tags J-RDR-01 does not select another"  flow_matches_tags "$PHONE_ONLY"
TAGS="rotation,library"
expect yes "--tags is OR: rotation"                   flow_matches_tags "$BOTH"
expect yes "--tags is OR: library"                    flow_matches_tags "$NEITHER"
expect no  "--tags is OR: neither tag"                flow_matches_tags "$TABLET_ONLY"
TAGS="rotation, library"
expect yes "a space after the comma is ignored"       flow_matches_tags "$NEITHER"

if [ "$FAILS" -gt 0 ]; then
  printf '%d of %d checks failed\n' "$FAILS" "$CHECKS"
  exit 1
fi
printf 'OK (%d checks)\n' "$CHECKS"
