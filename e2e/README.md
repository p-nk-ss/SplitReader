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

The tags line must be an inline list on one line, `tags: [a, b]` (quotes around a tag are fine).
A flow without one, or with a YAML block list, stops the run before anything is built: it would
have no tags for the runner, run on every device and match no `--tags`.

A run that selects **no flow** is an error, never a green run of nothing: `run.sh` exits 1 with
`no flow selected …` — before the build when no flow has any of the tags, right after boot (the
emulator it started is killed first) when the device rule below skipped them all.

### Device class (`tablet` / `phone` tags)

Independently of `--tags`, `run.sh` reads the device's class from `wm size` / `wm density`
(smallest width >= 600dp = tablet) and skips a flow tagged `tablet` but not `phone` on a phone,
and a flow tagged `phone` but not `tablet` on a tablet — logging
`skipping <file> (not tagged for a phone)`. A flow with both tags, or neither (all the library
journeys), runs everywhere. The rule also applies when the flow is named explicitly
(`--tags J-RDR-01` on a phone selects nothing, which is an error — see above). The flows skipped
this way are listed in `summary.md` ("Not run on this phone …"). The rules live in
`e2e/flow_select.sh`; check them with `bash e2e/test_flow_select.sh`, and what `run.sh` does with
them (nothing selected, unreadable class, unreadable tags — the real script against fake tools)
with `bash e2e/test_run_select.sh`.

`run.sh` also passes the class to every flow as `-e DEVICE_CLASS=tablet|phone`, so a flow tagged
for both can keep a phone-only step behind `when: true: ${DEVICE_CLASS == 'phone'}` (`J-RDR-03`).
If the class cannot be read from the device, `run.sh` stops before any flow runs: with an unknown
class every such step would be skipped and the flow would still pass. For the same reason each
two-class flow starts with
`- assertTrue: ${DEVICE_CLASS == 'phone' || DEVICE_CLASS == 'tablet'}`; running one with
`maestro test` directly needs the same `-e DEVICE_CLASS=…`.

## Shared subflows (`e2e/subflows/`)

One subflow per job; a flow never re-implements one of these inline. (One declared exception:
`J-TRN-01` step 1 taps the file in the picker itself, because the pack download it must watch
starts when the book opens and is over before `pick_file.yaml` returns.)

| Subflow | Job |
|---|---|
| `launch_clean.yaml` | Clear Mirrolit's and the system picker's state, launch, wait for "Open book". |
| `pick_file.yaml` (FILE) | "Open book" -> pick the file in the system picker (per API level). Asserts nothing about the result. |
| `pick_file_in_downloads.yaml` (FILE) | The same, but ALWAYS from the picker's "Downloads" root. For a journey that picks one file more than once and needs the app to know it again (see "One file, two uris" below). |
| `import_book.yaml` (FILE, TITLE, FIRST_TEXT) | `pick_file` + dismiss the portrait hint + prove the right book opened (first text, then title in the top bar). |
| `dismiss_portrait_hint.yaml` | Phone only: tick "Don't show again" and close the "Parallel reading…" hint if it is up. |
| `show_bars.yaml` | Show the reader's bars: wait until hidden, one tap, wait for the language chip (retried). |
| `tap_reader_button.yaml` (BUTTON, OPENS) | `show_bars` + tap a top-bar button by id + wait for its dialog's text (retried). |
| `leave_reader.yaml` | `show_bars` + Back (retried), ends on the Library scrolled up to "Open book". |
| `leave_reader_from_catalog.yaml` | The same for a reader opened from the Catalog (Back lands there): then taps "Library". |
| `tap_shows_tap_hides.yaml` (SHOW_AT, HIDE_AT) | Prove that a tap at one point shows the bars and a tap at another HIDES them: both seen within 3 s of the first tap, before the bars' own timer could (retried when too slow). |
| `set_translator.yaml` (NAME) | Open the translator picker (`tap_reader_button`) and choose a provider that needs no key. |
| `wait_translated.yaml` (TEXT, TIMEOUT) | Wait for a translated text. |
| `watch_translation_banner.yaml` | Read the reader's banner ("Preparing translation… N%", "Translating…") until it is gone; results in `output.bn…` (readings under 100 = a pack download, whether a percentage ever went down, …). Asserts nothing itself. |
| `rows_translated.yaml` (CHAPTER, FROM, PATTERN, TIMEOUT) | Every translation slot on screen holds a text matching PATTERN (walks down and up from paragraph FROM); `output.rows` = how many. |
| `show_translation.yaml` (STATE: on \| off) | The reader's Display dialog -> "Show translation" to STATE; the switch must have been in the other state. |
| `cached_translations.yaml` | Restart the app, read Settings -> "Cached translations: N" into `output.cached`, back to the Library. |
| `language_chip_reads.yaml` (SOURCE, TARGET) | The reader's language button reads "SOURCE → TARGET" (`"JA"`, `"RU"`; `"FI"`, `"—"`; `"\\?"`, `"—"`). One showing of the bars and one look per side, up to five attempts each, logged. |
| `target_russian.yaml` | Settings -> Default translation language -> Russian. Needed before a book that is not English: the default target is English, and only an English book switches it to Russian by itself. Ends on Settings. |
| `watch_paragraph_download.yaml` (SLOT) | Read "Downloading… N%" on the "Download and translate" button of a paragraph in another language (inside translation slot SLOT) until it is gone; results in `output.pd…`. Asserts nothing itself. |
| `sentences_translated.yaml` (SLOT_C, SLOT_P, MARK, TIMEOUT) | A paragraph of `ja-novel.epub` / `zh-novel.epub` (one sentence written N times) is translated as one Russian sentence N times, single spaces between: translated a sentence at a time, none run together. |
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
- to assert "a tap hides the bars": `tap_shows_tap_hides.yaml` — a bare "tap, then the bars are
  gone" passes with a dead tap as soon as the check comes 3.5 s after the bars appeared;
- before any of them the reader must be open: wait for something that does not depend on the
  bars (a paragraph id `original_<ch>_<p>`, a text, the navigation's "Catalog" gone).

The earlier `open_bars.yaml`, `back_to_library.yaml` and `back_to_library_from_catalog.yaml`
(built on `when: notVisible:`) were removed on 2026-11-14 for this reason.

### Translation: what a flow can and cannot see

- **Translated or not.** `translation_<chapter>_<paragraph>` is the paragraph's slot; it exists
  while the paragraph still waits. The translated text is a text node inside it: assert
  `text: <pattern>` with `childOf: {id: translation_…}` (`rows_translated.yaml`), never the slot.
- **Which language / which engine.** Model text: match a language (Cyrillic letters; `der|die|das|und`;
  `le|la|les|et|une`), or compare one paragraph's text before and after an engine change.
- **A pack download** is the banner "Preparing translation… N%" (`watch_translation_banner.yaml`);
  it lasts about 5 s, so the command before the watch must be the tap that starts it, by point
  or with `waitToSettleTimeoutMs: 100`.
- **The word popup.** Wait for the translation's text between "WORD" and "Listen", not for
  `{text: "Save", enabled: true}`: the Save text node reads enabled in every state.
- **The popup while it still translates.** With an installed pack that state is shorter than one
  command. A pack that is NOT installed holds it for the ~5 s of its download (the popup reads
  "Downloading language model… N%"): choose a new target by a point tap on its tile, then point
  gestures only, and after each gesture assert that the download line is STILL there — otherwise
  the gesture came too late and proved nothing (`J-TRN-03` step 13). With the bars up the popup
  sits one footer higher than with the bars hidden.
- **Who translated.** The picker's inverted card is the CHOICE. When the chosen engine cannot
  serve, ML Kit does and a strip under the top bar says "… — translated by ML Kit" (only while
  the bars are up): `show_bars`, then `assertNotVisible: ".*translated by.*"`. The strip can be
  produced on an emulator with DeepL + any made-up key + target Hindi (DeepL has no Hindi). `assertNotVisible` waits for a text to go, and the strip goes with the bars: assert the language button right after it, or a strip that was there passes.
- **A percentage that goes down** (`bnDecreased`, `dlDecreased`, `pdDecreased`): the app clamps
  each caller's progress so it never decreases within one download attempt; a lower number can
  only show after an error banner and a new attempt. The checks are right but have never been
  seen true.
- **Off-screen work** (look-ahead, a hidden pane): `cached_translations.yaml`.
- **A paragraph in another language than the book's** has a note line inside its translation
  slot ("FR · French, not translated" + the button "Download and translate", "FR · from French",
  "LA · Latin, not translated", "RU · original"): assert the note `childOf` that slot. Its model
  download shows on the button ("Downloading… N%", `watch_paragraph_download.yaml`), never on
  the banner; "nothing was downloaded on its own" is read from Settings -> Language packs (the
  rows "FR → EN · N MB" and the total), which must also be seen to change once a button is tapped.
- **Button texts are reported in capitals** ("DOWNLOAD AND TRANSLATE", "DOWNLOADING… 20%").
  Maestro's text match ignores case; JavaScript on `maestro.copiedText` does not.
- **The popup by position.** `below: "WORD"` / `above: "Listen"` are vertical bands: a page text
  in that band matches too. Bound the length, or read the result back another way (J-TRN-05
  saves the poem and reads it in Words).
- **The default target is English.** A clean install translates a Japanese book into English
  ("JA → EN"); `target_russian.yaml` first.
- **A heavy book** (a 6,000-character paragraph being translated: `rashomon-ja.epub`): one look
  at the screen takes 2–3 s of the bars' 3.5 s. Read the chip with `language_chip_reads.yaml`,
  leave with the system `back` key.
- **Points.** `swipe` and `point:` take literal whole numbers only (`"740, 333"`, `"30%, 60%"`; no
  decimals, no `${…}`), so a device-specific point is two copies of the command behind
  `when: true: ${DEVICE_CLASS == '…'}`. A swipe that does not move (`duration: 700`,
  `waitToSettleTimeoutMs: 100`) is a 0.9 s long press; `longPressOn` holds for 3–4 s.
- **Widths and pixel points are per AVD.** The `phone` / `tablet` numbers (paragraph widths in
  `J-RDR-03`, `J-TRN-08` step 12, the 2432 of `J-TRN-06`, every literal point) are those of
  Pixel_10_Pro and Tab_35_PixelTablet. Another AVD of the same class (Tab_26_Nexus9) is selected
  by the tags and fails those checks: run the suite on those two AVDs.
- **A flow that turns the device or switches aeroplane mode** declares `onFlowComplete:`
  (`setAirplaneMode: disabled`, `setOrientation: PORTRAIT`) in its header, so a failure in
  between does not leave the emulator offline or turned for the flows after it (`J-LIB-02`,
  `J-RDR-03`, `J-TRN-08`). On the tablet PORTRAIT is the natural (landscape) orientation and
  LANDSCAPE_LEFT the turn to portrait.

### Retries are reported

`show_bars`, `tap_reader_button`, `leave_reader`, `leave_reader_from_catalog` and
`tap_shows_tap_hides` retry, and a retry can hide a tap that did nothing. Each of them logs
`<name> … - attempt N` before every attempt; `e2e/report.py` reads the flows' `maestro.log` and
adds a "Retries" table to `summary.md` for every step that needed more than one attempt (none:
no table). A step that shows up there run after run is a finding, even though its flow passed.

## A slow run: is the Mac's screen locked?

With the Mac's screen locked (or the display asleep) a WINDOWED emulator runs 2–3 times slower:
its frames are drawn through a window nobody composes. Seen 2026-11-15: from the minute the
screen locked every flow took 2.3–3 times its usual time (J-LIB-19 on the tablet 612 s and
647 s instead of 270 s) and the timing-bound checks failed (banner readings, the 3 s tap pairs,
the popup's download window) — with no other load on the host. The same AVD booted with the
same command line plus `-no-window` ran J-LIB-19 in 278 s, screen still locked.
`caffeinate -i -s` keeps the Mac awake but does not keep the screen unlocked.
`run.sh --avd <NAME>` starts a windowed emulator; **`--headless` boots the same AVD with
`-no-window`** and changes nothing else (same flows, same emulator-only guard, the emulator this
run started is still the only one it kills, same summary; with `--serial` the flag is ignored and
the run says so). Every unattended run:

```
caffeinate -i -s e2e/run.sh --avd Tab_35_PixelTablet --headless --tags J-TRN-07
```

A run with durations at twice the usual is void, not a result. The option's parsing and the
emulator command line are checked by `bash e2e/test_run_select.sh` (scenarios J–M).

## Online engines without money (`J-TRN-07`, `J-TRN-08`)

No flow types a real API key, signs in or buys. The only key is the made-up `fake-key-e2e-000`;
DeepL and Google Cloud answer it with an auth error, which is the test. LibreTranslate is made
current only while it points at `libretranslate.example.com` (reserved, does not resolve); its
address is set to `https://libretranslate.com` in Settings with no book open, so nothing is
sent there. Quick Translate needs no key and really translates.

Who gets what: the made-up key really reaches DeepL (about a dozen requests in `J-TRN-07`) and
Google Cloud (up to three) — that is the journey's design, not a breach: they answer with an auth
error and charge nothing. Quick Translate (an unofficial endpoint) gets about a hundred fixture
paragraphs (`J-TRN-08`). Nothing is sent to libretranslate.com or to Azure.

What a flow can see:

- **Current provider.** Settings: the Translation row's label (`text: "DeepL"`,
  `below: "Translation"`, `above: "Manage providers.*"` — "Change" is on two rows of Settings,
  so its tap carries the same `below`/`above`). In a picker: a `colour-check/` crop of the
  card's name, `PaperInk` = current, `PaperBg2` = not.
- **Configured.** The card's line "KEY CONFIGURED" / "TAP TO ADD API KEY", between the card's
  name and the next card's name (`below:` / `above:`). The key dialog of a card with a key
  says "•••••••••• (existing key hidden)" and has Clear; without one "Paste key…" and no Clear.
- **The key is masked**: the field reads back as bullets (`"•{16}"`); the Server URL / Region
  field reads back as typed.
- **A request was made** (made-up key): the error banner. It is drawn with the bars hidden and
  with the translation pane hidden; an invalid-key or missing-key error is persistent (not
  re-sent on scrolling). The popup shows the error's short form — a full-match text tells the
  two apart ("Invalid DeepL API key" vs "Invalid DeepL API key — tap Switch translator…").
- **Nothing was translated in the background** (Quick Translate): `cached_translations.yaml`
  before and after reading with the pane hidden must give the same number. Offline HQ adds
  13–23 over the same screens — the check's observed failure.
- **No new request on rotation**: the device is turned in aeroplane mode (`setAirplaneMode`),
  where a request for a shown paragraph could only end in the "No internet" banner. The command
  returns in ~30 ms, long before the radios are down: wait 10 s after it, and look 5 s after each
  turn (first "Translating…" absent, then the error banner absent) — `assertNotVisible` waits for
  a "Translating…" to go, so a look right after the turn passes on a request still in flight.
  A placeholder that a cache hit shows for under a second is not seen: that half is MANUAL.
- **A provider or key change closes an open word popup**; re-selecting the current provider
  does not. That is how "re-selecting is a no-op" is seen.

Traps: `hideKeyboard` with no keyboard up is a Back press (it closed the picker) — a key dialog
keeps its buttons above the keyboard, so it is not needed there. Settings keeps its scroll
position between visits: after `cached_translations.yaml` it is at "Storage", and the Translation
and Orientation sections are ABOVE (`direction: UP`). `assertNotVisible` on a toast proves nothing: it waits
for the text to go, and a toast goes by itself (it passed right after a real Save). A JS regex result (`.exec`) stored in
`output` comes back null — parse with `substring` / `split`.

MANUAL by nature: anything about a paid engine's monthly usage counter (needs a real key).

## One file, two uris (`pick_file.yaml` vs `pick_file_in_downloads.yaml`)

The system picker hands the app a different uri for the same file depending on the root it is
picked from, and Mirrolit keys a book by its uri:

```
Recent     content://com.android.providers.media.documents/document/document%3A<id>
Downloads  content://com.android.providers.downloads.documents/document/msf%3A<id>
```

`pick_file.yaml` taps the file wherever the picker opens ("Recent" after `launch_clean`) and
goes to "Downloads" only when the file is not on that first screen — and DocumentsUI then
remembers "Downloads" for every later pick of the run. So the root of a pick depends on what
else is in `/sdcard/Download` (files left by an earlier run or a probe fill "Recent": `run.sh`
removes only what it pushed itself) and on the picks before it. For most journeys that does
not matter. For one that picks the SAME file again and expects the app to recognise it — a
removed book added again, `J-LIB-19` — it decides the result: a second pick from another root
is a new book to the app (chapter 1, no bookmarks). Such a journey uses
`pick_file_in_downloads.yaml` for every pick of that file.

Which uri a pick returned is in the flow's `logs/device-logcat.txt`:
`grep 'PickerActionHandler.*onFinished' …` — one line per pick. Look there first when a
re-opened book is "not the same book".

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

## Exact colours (`colour-check/` crops, `e2e/check_colours.py`)

A reader theme is only colours: no node says which theme is on, and Maestro's `assertScreenshot`
tolerates small colour differences (it passes Paper for Sepia and AMOLED for Night). A flow that
has to prove a theme (`J-RDR-05`) takes a small crop instead — one short text on its background —
and names the palette colour the background must have:

```yaml
- takeScreenshot:
    path: colour-check/step7-display__SepiaBg
    cropOn: "Display"
```

The name is `colour-check/<label>__<constant in Color.kt>`; the label is letters, digits and `-`,
and nothing may follow the name on its line (no comment). A crop line the checker cannot read
that way is not skipped: it fails the row.

After such a flow `run.sh` runs `e2e/check_colours.py`: it reads the list of crops from the flow
file, requires every one to have been taken, and requires its most frequent pixel to be exactly
that constant of `app/…/presentation/theme/Color.kt` (and at least half of the crop). The result
is its own row in `summary.md`, `<flow> colours`, and a wrong colour fails the run — as does a
crop that was not taken, a flow in which no crop is found, and any error of the checker itself
(an unreadable PNG, a missing `Color.kt`; if the script dies without a result, `run.sh` writes
the FAIL row). The colour
does not depend on the crop's size, so there are no per-device reference images. A flow run by
hand with `maestro test` takes the crops but does not check them:

```
python3 e2e/check_colours.py e2e/flows/reading/J-RDR-05.yaml <test-output-dir> /tmp/colours.xml
```

Tests: `python3 e2e/test_check_colours.py`.
