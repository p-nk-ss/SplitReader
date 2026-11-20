#!/usr/bin/env python3
"""Coverage check: catalog case IDs vs. the case IDs named in flow comments.

  python3 e2e/check_coverage.py            # exit 0 when both directions match, 1 otherwise

1. Every case the catalog (docs/e2e/e2e_test_cases.md) marks automated - its Tags cell has `smoke`
   or `full` and not `manual` - is named in at least one comment of a flow (e2e/flows/**.yaml) or
   subflow (e2e/subflows/**.yaml).
2. Every case ID named in a flow/subflow comment exists in the catalog.

A mention in a comment counts (the check cannot tell "covered" from "not covered here").
Listed without failing the check: cases tagged both `smoke`/`full` and `manual`, cases with neither,
and flows naming a case tagged `removed`. Each uncovered case is shown with the journey(s) whose
**Covers:** line in docs/e2e/e2e_journeys.md names it.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CATALOG = ROOT / "docs/e2e/e2e_test_cases.md"
JOURNEYS = ROOT / "docs/e2e/e2e_journeys.md"
FLOW_DIRS = [ROOT / "e2e/flows", ROOT / "e2e/subflows"]

ROW = re.compile(r"^\|\s*([A-Z]{3}-\d{3})\s*\|[^|]*\|\s*P\d\s*\|\s*([^|]*)\|")
CASE_ID = re.compile(r"\b([A-Z]{3}-\d{3})\b")


def catalog_cases(path=CATALOG):
    cases = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        m = ROW.match(line)
        if m:
            cases[m.group(1)] = {t.strip() for t in m.group(2).split(",") if t.strip()}
    return cases


def flow_mentions(dirs=FLOW_DIRS):
    """case ID -> set of flow files whose comments name it."""
    found = {}
    for d in dirs:
        for f in sorted(d.rglob("*.yaml")):
            for line in f.read_text(encoding="utf-8").splitlines():
                if "#" not in line:
                    continue
                comment = line[line.index("#"):]
                for cid in CASE_ID.findall(comment):
                    found.setdefault(cid, set()).add(str(f.relative_to(ROOT)))
    return found


def journey_covers(path=JOURNEYS):
    """case ID -> journeys whose **Covers:** line names it."""
    covers, current = {}, None
    for line in path.read_text(encoding="utf-8").splitlines():
        m = re.match(r"^### (J-[A-Z]{3}-\d+|Release smoke)", line)
        if m:
            current = m.group(1)
        elif line.startswith("**Covers:**") and current:
            for cid in CASE_ID.findall(line):
                covers.setdefault(cid, []).append(current)
    return covers


def main():
    cases = catalog_cases()
    mentions = flow_mentions()
    covers = journey_covers()
    automated = {c for c, t in cases.items() if ({"smoke", "full"} & t) and "manual" not in t}
    both = sorted(c for c, t in cases.items() if ({"smoke", "full"} & t) and "manual" in t)
    neither = sorted(c for c, t in cases.items()
                     if not ({"smoke", "full", "manual", "removed"} & t))

    uncovered = sorted(automated - mentions.keys())
    unknown = sorted(set(mentions) - cases.keys())
    removed_named = sorted(c for c in mentions if "removed" in cases.get(c, set()))

    print(f"catalog: {len(cases)} cases, {len(automated)} automated (smoke/full, not manual)")
    print(f"flows/subflows name {len(mentions)} distinct case IDs; "
          f"{len(automated & mentions.keys())}/{len(automated)} automated cases covered")
    print(f"automated cases named in no flow comment: {len(uncovered)}")
    for c in uncovered:
        print(f"  {c}  [{', '.join(sorted(cases[c]))}]  journeys: {', '.join(covers.get(c, ['-']))}")
    print(f"case IDs in flow comments missing from the catalog: {len(unknown)}")
    for c in unknown:
        print(f"  {c}  in {', '.join(sorted(mentions[c]))}")
    if removed_named:
        print(f"note: flows name cases tagged 'removed': {', '.join(removed_named)}")
    if both:
        print(f"note: tagged smoke/full AND manual (not counted as automated): {', '.join(both)}")
    if neither:
        print(f"note: tagged neither smoke/full nor manual: {', '.join(neither)}")
    return 1 if uncovered or unknown else 0


if __name__ == "__main__":
    sys.exit(main())
