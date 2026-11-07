# Mirrolit E2E release gate

Maestro-driven black-box journeys against the real app, traceable to the 445-case catalog
(`docs/e2e/e2e_test_cases.md`) via `docs/e2e/e2e_journeys.md` (54 journeys, 28 automated). Flows live in
`e2e/flows/`, shared steps in `e2e/subflows/`, fixtures in `e2e/books/`.

## How to use (tags)

| Tag | Meaning | When it runs |
|---|---|---|
| `smoke` | P0, automated (Maestro) | before **every** Play upload: tablet API 35 + phone emulator |
| `full` | automated (Maestro) | before larger releases and once per closed-testing cycle: tablet API 35, tablet API 26, phone |
| `manual` | not automatable (real sign-in/purchase, two Play builds, paid API keys, simulated failures, visual judgement) | manual checklist on a real device before larger releases |
| `network` | needs internet (Offline HQ packs, ML Kit models, catalog) | |
| `tablet` / `phone` / `rotation` | device-specific | |
| `known-bug` | documents a known open bug; expected to fail until fixed | reported, never blocks |

Priority: **P0** core flow or regression of a release-blocking bug (a failure blocks the release);
**P1** important; **P2** minor/cosmetic.

Release gate: all `smoke` cases pass on both devices, plus the release smoke on the signed release APK.
Results go to `releases/e2e-<versionName>-<versionCode>.md`.

## Install

```
brew tap mobile-dev-inc/tap
brew install maestro
maestro --version
```

## Run (planned — the runner lands in Task 6; commands below are the target shape)

Available after the runner lands:

```
e2e/run.sh --avd Tab_35_PixelTablet --tags smoke
e2e/run.sh --avd Tab_35_PixelTablet --tags full
e2e/run.sh --avd Tab_26_Nexus9 --tags full
e2e/run.sh --avd Pixel_10_Pro --tags full
e2e/run.sh --build release --tags release
```

Until then, flows can be run directly against a booted emulator:

```
maestro test -e APP_ID=io.mirrolit.app --device <emulator-serial> e2e/flows/<flow>.yaml
```

Every flow/subflow declares `appId: ${APP_ID}`; never hard-code the package name. `run.sh` will write a
JUnit report and a `summary.md` per run under `e2e/reports/` (gitignored) and, for smoke/release runs, a
copy to `releases/e2e-<versionName>-<versionCode>-<device>.md`.

## Manual checklist

The 26 non-automatable journeys (real sign-in/purchase, two-build updates, paid API keys, simulated
failures, visual judgement) live in `docs/e2e/manual_checklist.md` (Task 12).

## Catalog areas

| Prefix | Area | Cases |
|---|---|---|
| LAY | Reader layout | 27 |
| APP | Appearance | 30 |
| POS | Reading position | 25 |
| NAV | Reader navigation | 17 |
| BMK | Bookmarks | 14 |
| ILL | Illustrations | 18 |
| SYS | System UI (insets, bars) | 13 |
| TRN | Translation | 26 |
| OHQ | Offline HQ | 26 |
| POP | Word popup | 26 |
| ONL | Online engines | 22 |
| MLK | ML Kit | 11 |
| LNG | Language detection | 9 |
| SET | Settings | 17 |
| LIB | Library | 22 |
| IMP | Import | 14 |
| PRS | Parsers | 25 |
| ERR | Errors | 17 |
| PRM | Premium | 15 |
| ACC | Account | 21 |
| CAT | Catalog | 15 |
| WRD | Words | 14 |
| UPD | Updates | 6 |
| ALM | Almanac | 9 |
| DAT | Data / upgrade | 6 |
| | **Total** | **445** |

Full case detail: `docs/e2e/e2e_test_cases.md`. Step-by-step journeys: `docs/e2e/e2e_journeys.md`.
