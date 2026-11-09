# Mirrolit E2E (Maestro)

Maestro flows that drive the real app for the release gate described in `docs/e2e/README.md`.

## Install Maestro

```
brew tap mobile-dev-inc/tap
brew install maestro
maestro --version
```

Requires Java 17+ (the Homebrew cask pulls its own JDK if needed).

## Where flows live

- `e2e/flows/` — Maestro flow YAML files (one journey/case per file or subflow).
- `e2e/flows/_probe.yaml` — a throwaway smoke-check flow for verifying the toolchain against the app; not part of the suite (deleted after use).
- `e2e/.gitignore` — ignores `reports/` (Maestro test-run output) and `books/fetched/` (fixture books downloaded at run time, not committed).

Every flow declares `appId: ${APP_ID}` — never hard-code the package name. Run with:

```
maestro test -e APP_ID=io.mirrolit.app --device <emulator-serial> e2e/flows/<flow>.yaml
```

See `docs/e2e/README.md` for the full release gate, tags and planned runner commands.

## Per-journey fixtures (`# books:` header)

`e2e/run.sh` runs every selected flow file ONE AT A TIME, not as one `maestro test` invocation
over the whole `e2e/flows` directory. Before each flow runs, it pushes only that flow's own
fixture books to `/sdcard/Download` (and media-scans them); before the NEXT flow runs, it
deletes exactly the files it just pushed. A flow declares the fixtures it needs with one
comment line, placed anywhere in the file (conventionally right after `tags:`, before the `---`
document separator):

```yaml
appId: ${APP_ID}
name: J-LIB-01 First launch on the free tier
tags: [smoke, full, library, J-LIB-01]
# books: alice-illustrated.epub, full-features.fb2, classic-h2.mobi, no-description.epub
---
```

Rules:
- Exactly one line, starting with `# books:` (no leading whitespace), followed by a
  comma-separated list of basenames. Everything after the first matching line is ignored, and a
  name may NOT wrap onto a second line — keep the whole list on one line (see
  `e2e/flows/library/J-LIB-03.yaml` for a 13-name example).
- A flow with no `# books:` header gets nothing pushed (e.g. `J-LIB-02`, which is entirely live
  catalog/network with no local fixtures) — document why in a plain comment near the top of the
  flow instead, worded so it does NOT start with `# books:` (that prefix is exactly what the
  parser matches).
- Each name is resolved in this order: `e2e/books/<name>` (committed synthetic fixtures),
  `e2e/books/fetched/<name>` (real Gutenberg books, fetched by `e2e/run.sh` before the per-flow
  loop), then `app/src/androidTest/assets/qa-pd/<name>`. A name found in none of the three fails
  the run with a clear message naming the flow and the missing fixture.
- Two pathological fixtures (`zipbomb.epub`, `oversized.fb2`, ~320 MB each) are never committed
  and must be generated once per machine before a flow that names them runs:
  `python3 e2e/books/build_fixtures.py --large` (writes them into `e2e/books/fetched/`, which is
  gitignored). See `e2e/books/SOURCES.md`.

## Tag selection (`--tags`)

`run.sh --tags TAGLIST` still selects which flow FILES run, matching OR-semantics against each
flow's own `tags: [...]` header (the same semantics `maestro test --include-tags` has) — now
implemented by `run.sh` itself (`flow_matches_tags`/`flow_tags`) rather than handed to maestro,
since flows run one file at a time. A single flow's own id is always one of its tags (e.g.
`J-LIB-01`), so `--tags J-LIB-01` runs just that one flow. `run.sh` logs
`skipping <file> (tags don't include any of: <TAGLIST>)` for every flow it excludes — that line
is the proof a non-matching flow was actually skipped, not silently run anyway.

## Picker reset (`launch_clean.yaml`)

`launch_clean.yaml` also runs `- clearState: { appId: ..., optional: true }` against BOTH
possible DocumentsUI package names before launching Mirrolit, because the system file picker
keeps its own cross-invocation state (last-opened root; suspected multi-select-mode
carryover under heavy repeated use) completely independent of Mirrolit's own `clearState`. The
installed package name differs by Android image flavour — confirmed by running, on each
required emulator:

```
adb -s <serial> shell pm list packages | grep documentsui
```

Google APIs images (`Tab_35_PixelTablet`, `Pixel_10_Pro`) ship `com.google.android.documentsui`;
AOSP images (`Tab_26_Nexus9`) ship `com.android.documentsui` instead — never both on the same
image. Both `clearState` calls are `optional: true` so clearing the one package that isn't
installed on a given device/image is a harmless no-op rather than a flow-aborting failure.
