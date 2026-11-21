# e2e/flow_select.sh — which flow files a run selects. Sourced by e2e/run.sh; has no side effects
# when sourced, so e2e/test_flow_select.sh can exercise the rules without a device.
#
# Two independent rules, both read from a flow's own `tags: [...]` header line:
#   1. --tags (flow_matches_tags): OR semantics, the same as maestro's --include-tags.
#   2. device class (flow_matches_device): the tags `tablet` and `phone` say where a flow can run.
#        tablet without phone  -> tablet only (skipped on a phone)
#        phone without tablet  -> phone only  (skipped on a tablet)
#        both, or neither      -> every device
#      This rule also holds when the flow is named explicitly (`--tags J-RDR-01` on a phone skips
#      it): a tablet-only journey asserts a layout the phone does not have.

# Parses a flow file's `tags: [a, b, c]` YAML header line into one tag per output line. Quotes
# around a tag (`["smoke", 'tablet']`) are dropped. Only an inline list on the `tags:` line is
# read — see flow_tags_readable.
flow_tags() {
  local flow_file="$1"
  local line
  line="$(grep -m1 '^tags:' "$flow_file" || true)"
  [ -z "$line" ] && return 0
  printf '%s\n' "$line" | sed -n 's/^tags:[[:space:]]*\[\(.*\)\][[:space:]]*$/\1/p' \
    | tr ',' '\n' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' \
        -e 's/^"\(.*\)"$/\1/' -e "s/^'\\(.*\\)'\$/\\1/" -e '/^$/d'
}

# True (exit 0) iff flow_tags can read this flow's tags: it has a `tags: [...]` line with at least
# one tag. A flow without one, or with a YAML block list (`tags:` and `- smoke` lines under it),
# would have NO tags for the two rules below — it would run on every device and match no --tags —
# so run.sh refuses to start when a flow under e2e/flows fails this.
flow_tags_readable() {
  [ -n "$(flow_tags "$1")" ]
}

# True (exit 0) iff TAGS is empty (no filter — every flow runs) or this flow declares at least
# one tag in common with the (comma-separated) requested TAGS — OR semantics, the same as
# maestro's own --include-tags. Needed because flows run one file at a time rather than as one
# `maestro test` invocation over the whole flows/ directory, where --include-tags would
# otherwise do this filtering for us.
flow_matches_tags() {
  local flow_file="$1"
  [ -z "${TAGS:-}" ] && return 0
  local ft wt
  local want_tags=()
  IFS=',' read -r -a want_tags <<< "${TAGS//[[:space:]]/}" # "smoke, full" = "smoke,full"
  while IFS= read -r ft; do
    [ -z "$ft" ] && continue
    for wt in "${want_tags[@]}"; do
      [ "$ft" = "$wt" ] && return 0
    done
  done < <(flow_tags "$flow_file")
  return 1
}

# Prints `tablet` or `phone` from the outputs of `adb shell wm size` and `adb shell wm density`
# ("Physical size: 2560x1600", optionally followed by an "Override size: …" line that wins; same
# for density). Tablet = smallest width >= 600dp, the same breakpoint the app's shell uses.
# Prints nothing when either output can't be read — run.sh then stops: without the class a flow
# would run with its device-specific steps skipped.
device_class_from_wm() {
  local size_out="$1" density_out="$2"
  local size density w h sw
  size="$(printf '%s\n' "$size_out" | sed -n 's/.*size:[[:space:]]*\([0-9]*x[0-9]*\).*/\1/p' | tail -1)"
  density="$(printf '%s\n' "$density_out" | sed -n 's/.*density:[[:space:]]*\([0-9]*\).*/\1/p' | tail -1)"
  [ -n "$size" ] && [ -n "$density" ] && [ "$density" -gt 0 ] || return 0
  w="${size%x*}"; h="${size#*x}"
  sw="$w"; [ "$h" -lt "$sw" ] && sw="$h"
  if [ $((sw * 160 / density)) -ge 600 ]; then echo tablet; else echo phone; fi
}

# True (exit 0) iff the flow can run on a device of class $2 (`tablet` or `phone`). Any other
# class — empty, "unknown", a typo — matches no flow: a flow must never run on a device whose
# class is not known (its `when: ${DEVICE_CLASS == …}` steps would all be skipped and it would
# still pass).
flow_matches_device() {
  local flow_file="$1" class="$2"
  case "$class" in tablet|phone) ;; *) return 1 ;; esac
  local has_tablet=0 has_phone=0 ft
  while IFS= read -r ft; do
    [ "$ft" = "tablet" ] && has_tablet=1
    [ "$ft" = "phone" ] && has_phone=1
  done < <(flow_tags "$flow_file")
  [ "$has_tablet" = "$has_phone" ] && return 0
  [ "$class" = "tablet" ] && [ "$has_tablet" = 1 ] && return 0
  [ "$class" = "phone" ] && [ "$has_phone" = 1 ] && return 0
  return 1
}
