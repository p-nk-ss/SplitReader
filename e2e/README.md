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

### Device class (`tablet` / `phone` tags)

Independently of `--tags`, `run.sh` reads the device's class from `wm size` / `wm density`
(smallest width >= 600dp = tablet) and skips a flow tagged `tablet` but not `phone` on a phone,
and a flow tagged `phone` but not `tablet` on a tablet — logging
`skipping <file> (not tagged for a phone)`. A flow with both tags, or neither (all the library
journeys), runs everywhere. The rule also applies when the flow is named explicitly
(`--tags J-RDR-01` on a phone runs nothing). The rules live in `e2e/flow_select.sh`; check them
with `bash e2e/test_flow_select.sh`.

`run.sh` also passes the class to every flow as `-e DEVICE_CLASS=tablet|phone|unknown`, so a flow
tagged for both can keep a phone-only step behind `when: true: ${DEVICE_CLASS == 'phone'}`
(`J-RDR-03`). Running such a flow with `maestro test` directly needs the same `-e`.

## Shared subflows (`e2e/subflows/`)

One subflow per job; a flow never re-implements one of these inline.

| Subflow | Job |
|---|---|
| `launch_clean.yaml` | Clear Mirrolit's and the system picker's state, launch, wait for "Open book". |
| `pick_file.yaml` (FILE) | "Open book" -> pick the file in the system picker (per API level). Asserts nothing about the result. |
| `import_book.yaml` (FILE, TITLE, FIRST_TEXT) | `pick_file` + dismiss the portrait hint + prove the right book opened (first text, then title in the top bar). |
| `dismiss_portrait_hint.yaml` | Phone only: tick "Don't show again" and close the "Parallel reading…" hint if it is up. |
| `show_bars.yaml` | Show the reader's bars: wait until hidden, one tap, wait for the language chip (retried). |
| `tap_reader_button.yaml` (BUTTON, OPENS) | `show_bars` + tap a top-bar button by id + wait for its dialog's text (retried). |
| `leave_reader.yaml` | `show_bars` + Back (retried), ends on the Library scrolled up to "Open book". |
| `leave_reader_from_catalog.yaml` | The same for a reader opened from the Catalog (Back lands there): then taps "Library". |
| `set_translator.yaml` (NAME) | Open the translator picker (`tap_reader_button`) and choose a provider that needs no key. |
| `wait_translated.yaml` (TEXT, TIMEOUT) | Wait for a translated text. |
| `tap_catalog_search.yaml` | Focus the Catalog's search field. |
| `dismiss_stylus_popup.yaml` | Close the emulator's stylus-handwriting popup if it is up. |
| `premium_debug.yaml` | Turn on Settings -> "Premium (debug)". |

### The reader's bars: never `when: notVisible:`

The reader's top bar and footer hide by themselves 3.5 s after they appear, and a tap on the
page toggles them. Do not ask "are the bars up?" before tapping: while an element is visible,
Maestro takes about 4 s (up to 10 s under host load) to answer a `when: notVisible:` /
`repeat: while: notVisible:` condition — as long as the auto-hide — and one `tapOn: id:` can take
longer than 3.5 s too. So:

- to read the footer or the title: `show_bars.yaml`, then the check as the very next command;
- to tap a top-bar button: `tap_reader_button.yaml` — never `show_bars` + `tapOn: id:`, never a
  bare `tapOn: id: reader:…Button` even "right after" something that showed the bars;
- to leave the reader: `leave_reader.yaml` / `leave_reader_from_catalog.yaml`;
- before any of them the reader must be open: wait for something that does not depend on the
  bars (a paragraph id `original_<ch>_<p>`, a text, the navigation's "Catalog" gone).

The earlier `open_bars.yaml`, `back_to_library.yaml` and `back_to_library_from_catalog.yaml`
(built on `when: notVisible:`) were removed on 2026-11-14 for this reason.

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

## Reference screenshots (`e2e/refs/`)

A reader theme is only colours, so `J-RDR-05` checks "dark" / "light" with `assertScreenshot` on
small crops against `e2e/refs/theme/<device class>-<screen>-<theme>.png`. The crops belong to
`Tab_35_PixelTablet` and `Pixel_10_Pro`; what the comparison can and cannot tell, and how to
re-record (`e2e/refs/record_theme_refs.sh SERIAL`), is in `e2e/refs/README.md`.
