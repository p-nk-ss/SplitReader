# Mirrolit E2E release gate

Maestro-driven black-box journeys against the real app, traceable to the 458-case catalog
(`docs/e2e/e2e_test_cases.md`) via `docs/e2e/e2e_journeys.md` (55 journeys, 29 automated). Flows live in
`e2e/flows/`, shared steps in `e2e/subflows/`, fixtures in `e2e/books/`.

## How to use (tags)

| Tag | Meaning | When it runs |
|---|---|---|
| `smoke` | P0, automated (Maestro) | before **every** Play upload: tablet API 35 + phone emulator |
| `full` | automated (Maestro) | before larger releases and once per closed-testing cycle: tablet API 35, tablet API 26, phone |
| `manual` | not automatable (real sign-in/purchase, two Play builds, paid API keys, simulated failures, visual judgement) | manual checklist on a real device before larger releases |
| `network` | needs internet (Offline HQ packs, ML Kit models, catalog) | |
| `tablet` / `phone` / `rotation` | device-specific | |
| `release` | release smoke, R-01 … R-06, on the signed R8-minified release APK | before **every** Play upload: tablet API 35 |
| `known-bug` | documents a known open bug; expected to fail until fixed | reported, never blocks |

Priority: **P0** core flow or regression of a release-blocking bug (a failure blocks the release);
**P1** important; **P2** minor/cosmetic.

Release gate: all `smoke` cases pass on both devices, plus the release smoke on the signed release APK.
The release smoke is `e2e/flows/release/R-01…R-06` (tag `release`, tablet API 35; see `e2e/README.md`
"Release smoke" and `e2e_journeys.md` "Release smoke"). `--build release` copies the signing files in for the
build only and removes them on exit.
Results go to `releases/e2e-<versionName>-<versionCode>-<device>.md` (see "The release gate" below).

## Install

```
brew tap mobile-dev-inc/tap
brew install maestro
maestro --version
```

## Run

Commands, AVDs, reports and the harness self-tests are in `e2e/README.md` ("Commands"); this file owns
the gate and the process. Every flow/subflow declares `appId: ${APP_ID}`; never hard-code the package name.

## The release gate

Before uploading a bundle to Play:

1. `smoke` on Tab_35_PixelTablet and on Pixel_10_Pro, both `--build debug`; for a larger release also
   `full` on both (and Tab_26_Nexus9 once per closed-testing cycle).
2. `--build release --tags release` on Tab_35_PixelTablet (R-01 … R-06 on the signed, R8-minified APK).
3. The manual checklist (`docs/e2e/manual_checklist.md`) per its "When to run what" table.

Every run must be all PASS. A FAIL passes the gate only when it is explained as **void**: caused by an
outside outage (Gutenberg 503, Quick Translate rate limit, emulator network), written into the report
with the failing step, and re-run when the service is back — the re-run must PASS. A FAIL that is not
void is a bug (app or flow) and blocks the upload. The summaries copied to `releases/` are the evidence.

### Void runs

A run is void, not failed, when it was not testing the app: it took 2x or more its usual duration, had
minute-long gaps between steps (a locked Mac screen — `e2e/README.md` "A slow run"), or hit an outside
outage as above. Delete nothing; note it and re-run.

## How to add a journey

1. **Case:** add (or find) the case ID in `docs/e2e/e2e_test_cases.md`.
2. **Journey step:** add the step to `docs/e2e/e2e_journeys.md` with a `Covers` line naming the case IDs.
3. **Flow:** `e2e/flows/<area>/J-XXX-NN.yaml` — case IDs in comments next to the steps that cover them, fixture
   books in the `# books:` header, an inline `tags: [...]` line including the journey ID (header rules in
   `e2e/README.md`).
4. **Prove it:** a cold PASS on each device class it runs on (tablet and phone), plus one FAIL observed
   by a temporary mutation of the app or flow (break the thing it checks, watch it fail, revert). The mutation
   is never committed.

## Flaky flows

A flaky flow is fixed or quarantined, never ignored and never papered over with a longer wait. Fix it by
finding what the flow cannot see (see the subflow notes in `e2e/README.md`). To quarantine, replace its
`smoke` / `full` / `release` tags with `quarantine` in the `tags:` line and record why in the flow
and in the report. `run.sh` has no special handling of `quarantine`: `--tags` only selects flows that have
one of the listed tags (OR), so a quarantined flow is excluded simply by no longer carrying the gate tags;
run it on purpose with `--tags quarantine` or its ID. A quarantine is a debt: the journey is not covered
until it is fixed, so list it in the release notes of the gate run.

## App bugs found by E2E

Never change app code to make a flow pass. Keep the flow honest: disable the failing steps in a block
headed `# APP BUG:` with the exact failure (step, expected, observed, build), tag the flow `known-bug` if
it cannot pass meanwhile, and re-enable the block when the bug is fixed. Report the bug; the flow keeps
the evidence.

## Manual checklist

The 26 non-automatable journeys (real sign-in/purchase, two-build updates, paid API keys, simulated
failures, visual judgement) live in `docs/e2e/manual_checklist.md`: a fill-in checklist per journey, plus the
gaps the automation leaves (real-key online translators on the Play build, Gutenberg, ML Kit download,
Crashlytics, ScrollHold re-anchoring, MIUI / Android 15 / taskbar / large books). Which
journeys run when: the "When to run what" table in that file (every release: none; larger releases: all P0;
each closed-testing cycle: all).

## Catalog areas

| Prefix | Area | Cases |
|---|---|---|
| LAY | Reader layout | 30 |
| APP | Appearance | 30 |
| POS | Reading position | 25 |
| NAV | Reader navigation | 18 |
| BMK | Bookmarks | 14 |
| ILL | Illustrations | 18 |
| SYS | System UI (insets, bars) | 13 |
| TRN | Translation | 27 |
| OHQ | Offline HQ | 26 |
| POP | Word popup | 26 |
| ONL | Online engines | 23 |
| MLK | ML Kit | 11 |
| LNG | Language detection | 9 |
| SET | Settings | 17 |
| LIB | Library | 27 |
| IMP | Import | 14 |
| PRS | Parsers | 25 |
| ERR | Errors | 17 |
| PRM | Premium | 15 |
| ACC | Account | 21 |
| CAT | Catalog | 16 |
| WRD | Words | 14 |
| UPD | Updates | 6 |
| ALM | Almanac | 9 |
| DAT | Data / upgrade | 7 |
| | **Total** | **458** |

Full case detail: `docs/e2e/e2e_test_cases.md`. Step-by-step journeys: `docs/e2e/e2e_journeys.md`.
