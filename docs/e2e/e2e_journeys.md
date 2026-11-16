# Mirrolit — E2E journeys

Real user journeys for release testing. Each journey is a whole session a reader goes through (10–25 steps);
every verifying step carries a checkpoint "✓ … (IDs)" pointing at the atomic cases in `e2e_test_cases.md`.
Built 2026-11-06. 55 journeys (29 automated with Maestro, 26 manual) cover all
458 catalog cases.

Gate: every `smoke` journey passes on tablet API 35 + phone before each Play upload; all automated journeys
before larger releases (+ tablet API 26); manual journeys on a real device before larger releases.

## Index

| Journey | Title | P | Tags | Steps | Covers |
|---|---|---|---|---|---|
| J-LIB-01 | First launch on the free tier: three formats, the 4th book is blocked, a vanished file | P0 | smoke, full, tablet, phone | 16 | 22 |
| J-LIB-02 | Catalog evening: search, download from Gutenberg and Standard Ebooks, hit the cap, go offline | P0 | smoke, full, network, tablet, phone | 13 | 11 |
| J-LIB-03 | A folder of bad files: every one fails cleanly and none eats a free slot | P0 | smoke, full, tablet, phone | 14 | 9 |
| J-LIB-04 | Picture books and tables with an unlimited (debug Premium) library | P0 | smoke, full, tablet, phone | 13 | 16 |
| J-LIB-05 | Reading an FB2 collection: structure, verse, epigraphs, images, encodings | P0 | smoke, full, tablet, phone | 10 | 12 |
| J-LIB-06 | Classic MOBI shelf: every chapter-splitting rule | P0 | smoke, full, tablet | 8 | 8 |
| J-LIB-07 | Signed-out reader builds a vocabulary (Words), then a crash-free tab tour | P0 | smoke, full, network, tablet, phone | 13 | 12 |
| J-LIB-08 | Arranging the shelf on tablet and phone: hero, filters, search, grid | P1 | full, tablet, phone | 10 | 11 |
| J-LIB-09 | Account lifecycle with email and password: sign-up validation to account deletion | P0 | manual, network, tablet, phone | 15 | 14 |
| J-LIB-10 | A reading day shows up in the Almanac, including an unknown-language book | P1 | full, network, tablet, phone | 8 | 6 |
| J-LIB-11 | Buying Premium from the full-library dialog (Play license tester) | P0 | manual, network, tablet, phone | 9 | 9 |
| J-LIB-12 | Premium across reinstall, a second account, refund, backup and the debug override | P0 | manual, network | 7 | 7 |
| J-LIB-13 | Google account and Drive on a Play-installed build, on a real network | P0 | manual, network, tablet, phone | 14 | 14 |
| J-LIB-14 | A reading week on real hardware: Almanac stats, saved words with audio, screen fit | P1 | manual, network, tablet, phone | 8 | 9 |
| J-LIB-15 | In-app update between two Play builds | P0 | manual, network, phone, tablet | 8 | 7 |
| J-LIB-16 | Upgrading an old install in place (DB v2 / v3 → current) | P0 | manual, tablet | 8 | 7 |
| J-LIB-17 | Release artifact audit before a Play upload | P0 | manual | 9 | 9 |
| J-LIB-18 | Odd files that need a human eye | P1 | manual, tablet | 4 | 4 |
| J-LIB-19 | Removing a book and adding it again: the place and bookmarks are kept unless the reader deletes them | P0 | smoke, full, tablet, phone | 10 | 8 |
| J-RDR-01 | Tablet: first read of an illustrated classic, side-by-side | P0 | smoke, full, tablet | 15 | 16 |
| J-RDR-02 | Phone: stacked reading, progress and bookmarks | P0 | smoke, full, phone | 13 | 17 |
| J-RDR-03 | Rotation and resume: the paragraph never moves | P0 | smoke, full, rotation, phone, tablet | 11 | 8 |
| J-RDR-04 | Reading to the end: 0% to 100% and back | P0 | smoke, full, phone, tablet | 9 | 6 |
| J-RDR-05 | Appearance smoke: margins and one shared theme | P0 | smoke, full, phone, tablet | 9 | 3 |
| J-RDR-06 | Tablet: tuning typography, layout combos and persistence | P1 | full, tablet | 13 | 10 |
| J-RDR-07 | Phone: tuning the stacked reader at large font scale | P1 | full, phone | 10 | 6 |
| J-RDR-08 | Tablet: unusual books and jumps while translation is loading | P1 | full, tablet | 6 | 5 |
| J-RDR-09 | App chrome and the Words screen on phone and tablet | P0 | smoke, full, phone, tablet | 11 | 10 |
| J-RDR-10 | Tablet: working the divider by hand, then in split-screen | P1 | manual, tablet | 11 | 9 |
| J-RDR-11 | Phone: hands-on stacked reading, words and rotation | P0 | manual, phone, rotation | 12 | 12 |
| J-RDR-12 | Orientation lock and first-run defaults on phone and tablet | P0 | manual, phone, tablet, rotation | 8 | 7 |
| J-RDR-13 | Illustrated book: toggles, language switch and session count keep the place | P0 | manual, network | 10 | 7 |
| J-RDR-14 | Upgrade in place from a pre-v6 build: look, position and bookmarks carry over | P0 | manual | 8 | 9 |
| J-RDR-15 | Legacy migration edge cases: rotations, kills and restores | P1 | manual, rotation | 7 | 7 |
| J-RDR-16 | Phone: system bars, themes and the keyboard on Android 15 | P0 | manual, phone | 8 | 6 |
| J-RDR-17 | Tablet: rail and taskbar insets across the API matrix | P0 | manual, tablet | 6 | 5 |
| J-RDR-18 | Paid engine: hiding the translation stops metered requests | P0 | manual, network | 6 | 2 |
| J-RDR-19 | Release visual regression pass (goldens) | P1 | manual | 3 | 2 |
| J-RDR-20 | Known-bug probe: bookmarks, margins, rail, re-added books | P2 | manual, known-bug, tablet | 6 | 6 |
| J-TRN-01 | First run: Offline HQ by default on an English book | P0 | smoke, full, network, phone, tablet | 12 | 12 |
| J-TRN-02 | Word popup: select, drag, save, and the Words list | P0 | smoke, full, network, phone, tablet | 16 | 20 |
| J-TRN-03 | Changing the target language mid-read; packs and engine switches | P0 | smoke, full, network, phone, tablet | 13 | 11 |
| J-TRN-04 | A mixed-language book: every passage in its own language | P0 | smoke, full, network, phone, tablet | 12 | 8 |
| J-TRN-05 | Chinese and Japanese: books, sentences, both on-device engines | P0 | smoke, full, network, tablet, phone | 12 | 6 |
| J-TRN-06 | Book language detection and the "From" override | P0 | smoke, full, network, phone, tablet | 11 | 6 |
| J-TRN-07 | Online-engine keys without spending money: errors, Switch translator, clearing | P0 | smoke, full, network, phone, tablet | 16 | 13 |
| J-TRN-08 | Azure and Quick Translate fields and gating, no LibreTranslate | P0 | smoke, full, network, phone, tablet | 11 | 12 |
| J-TRN-09 | From ML Kit to Offline HQ: empty packs, cache, unsupported pair, a Russian book into German | P1 | full, network, tablet | 19 | 11 |
| J-TRN-10 | Jumping around: bookmarks, huge chapters, other books, odd books | P1 | full, network, phone, tablet | 10 | 4 |
| J-TRN-11 | A DeepL reader: real key, usage, popup save, hidden pane | P0 | manual, network, phone, tablet | 15 | 18 |
| J-TRN-12 | Paid chapter fill and switching between paid engines | P1 | manual, network, rotation, tablet | 10 | 7 |
| J-TRN-13 | Reading on a bad connection | P1 | manual, network, phone, tablet | 13 | 12 |
| J-TRN-14 | Broken packs, full storage, stalled server, broken native library | P1 | manual, network | 11 | 5 |
| J-TRN-15 | Upgrades, restores, 32-bit devices and the security audit | P1 | manual, network | 10 | 11 |
| J-TRN-16 | Real-device feel: first ML Kit download, gestures, speech, edge languages | P2 | manual, network, phone, tablet | 12 | 13 |

# Mirrolit — E2E journeys: Library, Import, Parsers, Errors, Premium, Account, Catalog, Words, Updates, Almanac, Data

Composed 2026-11-06 from the atomic catalog `e2e/e2e_test_cases.md` (prefixes LIB, IMP, PRS, ERR, PRM, ACC, CAT,
WRD, UPD, ALM, DAT). Each journey is one realistic session. A step that verifies something ends with
`→ ✓ <what must be true> (<atomic IDs>)`. A journey fails at its first failed checkpoint; record the ID(s) that failed.

- **Automated journeys** (J-LIB-01 … J-LIB-10, J-LIB-19) run under Maestro on a **debug** build; real network is allowed where
  tagged `network`. Account coverage there is screens + validation + a throwaway email/password account only.
- **Manual journeys** (J-LIB-11 … J-LIB-18) need a real Google account, Play Billing, two Play builds, an old APK,
  or a release artifact.
- **Known bugs** are listed at the end; they never block a release.

Priority: a journey is **P0** if any checkpoint in it is P0. Tag `smoke` = P0 and automatable (runs before every Play upload).

## Fixtures

Put all fixtures flat in the device's `/sdcard/Download/` (`adb push`, as `e2e/run.sh` does — no `mirrolit-fixtures` subfolder; the system file picker's own "Recent"/"Downloads" already lists them there) before the run. Names below are used in steps.

| Fixture | File | What is special about it |
|---|---|---|
| F-ALICE | `alice-illustrated.epub` | Gutenberg illustrated "Alice": OPF cover, cover-only first page, plates inside chapters, inline SVG, decorative drop-cap, `dc:description`, pg-boilerplate; first paragraph starts "Alice was beginning to get very tired" |
| F-COVER-B | `cover-b.epub` | Small EPUB with its own, visibly different cover (blue) — used against F-ALICE for cover bleed |
| F-IMGCH | `image-chapter.epub` | EPUB with one chapter file that holds only an illustration, no `h1`–`h3` |
| F-PICS | `pictures-only.epub` | Book made only of images, no text, no language metadata |
| F-NODESC | `no-description.epub` | No `dc:description`; first body paragraph > 40 chars |
| F-TINY | `tiny.epub` | No description; first paragraph ≤ 40 chars |
| F-FOREIGN | `english-with-french.epub` | English novel with an embedded French passage in chapter 1 |
| F-RUR | `rur-cs.epub` | Čapek "R.U.R." (Czech), dialogue laid out in HTML tables |
| F-ILIAD | `iliad-el.epub` | Gutenberg Greek "Iliad", verse in tables with line numbers, one huge chapter |
| F-FB2 | `full-features.fb2` (UTF-8) | `<annotation>`, title/first/last name, `<coverpage>` + inline `<image>` (base64 `<binary>`), body `<epigraph>`, section epigraph with `<text-author>`, wrapper `<section>` with only child sections, `<poem>` 3+ `<v>`, `<subtitle>`, `<cite>` outside epigraph, a `<cite>` > 5000 chars |
| F-FB2-1251 | `russian-1251.fb2` | windows-1251 encoded FB2, Cyrillic first paragraph known |
| F-FB2-XML | `full-features.xml` | Same as F-FB2 but with `.xml` extension / `text/xml` MIME |
| F-FB2-NOCH | `no-chapters.fb2` | Sections exist, none contains a qualifying paragraph |
| F-MOBI | `classic-h2.mobi` | Classic MOBI, no page breaks, 4 `<h2>` headings (also "no native chapter markers") |
| F-MOBI-PB | `pagebreaks.mobi` | 3 `<mbp:pagebreak>`, no headings |
| F-MOBI-PBH | `pagebreaks-and-h2.mobi` | 2 page breaks with `<h2>` inside the regions |
| F-MOBI-H1H2 | `h1-h2.mobi` | No breaks; 3 `<h1>` each with nested `<h2>` |
| F-MOBI-PLAIN | `plain.mobi` | No breaks, no headings |
| F-MOBI-DIST0 | `palmdoc-distance0.mobi` | PalmDOC with a distance-0 token |
| F-HUFF | `huffcdic.mobi` | HUFF/CDIC compression |
| F-AZW3 | `kf8.azw3` | AZW3/KF8 (unsupported) |
| F-DRM | `drm.mobi` | PalmDOC encryption field non-zero |
| F-CORRUPT-EPUB / -FB2 / -MOBI | `corrupt.epub`, `corrupt.fb2`, `corrupt.mobi` | Truncated mid-file / broken zip |
| F-ZIPBOMB | `zipbomb.epub` | Small file inflating well over 300 MB |
| F-HUGE | `oversized.fb2` | Plain file far past a sane book size |
| F-SHORT | `short-header.epub` | Shorter than the header-sniff length |
| F-EMPTY | `empty.epub` | Valid container, no extractable content |
| F-EMPTY-CH1 | `empty-first-chapter.epub` | Malformed: first chapter has zero paragraphs |
| F-PROSE | `plain-prose.epub` | Plain prose, no tables (position regression) |
| F-LONG | `en-novel.epub` | "The River Crossing", 22 chapters; every sentence carries a marker ("(1)", "(2)" … in chapter 1, "(ch3.1)" … in chapter 3), so any paragraph on screen tells which chapter the reader is in |
| F-ZIP-NOTBOOK | `notes.pdf` (in Google Drive) | Unsupported Drive file |

Accounts: `ACC_EXISTING` (pre-created email/password Firebase account, password known), throwaway sign-up address
`mirrolit.e2e+<timestamp>@<test-domain>`, a Google account on the device (manual), a Play license tester (manual).

---

# Automated journeys (Maestro, debug build)

### J-LIB-01 — First launch on the free tier: three formats, the 4th book is blocked, a vanished file

**Priority** P0 · **Tags** smoke, full, tablet, phone · **Devices** tablet API 35, phone emulator (411dp)
**Fixtures** F-ALICE, F-FB2, F-MOBI, F-NODESC
**Preconditions** Fresh install (`adb shell pm clear io.mirrolit.app`), Settings → Premium (debug) off, signed out, fixtures pushed.

**Steps**
1. Launch the app → ✓ Home shows the empty-library hint, and it names EPUB, FB2 **and MOBI** (IMP-010).
2. Tap the Almanac tab → ✓ "Open a book to start tracking your reading." hint, no charts (ALM-005).
3. Tap the Words tab → ✓ "Long-press any word in the reader to save it to your words." (WRD-009).
4. Tap the Catalog tab → ✓ opens straight on a "POPULAR" list, not the CAT-002 placeholder: `CatalogViewModel.init` runs an empty-query search on open so the "Search by title or author to begin" text never actually shows in steady state (CAT-002 — found stale against the current app, not re-verified here); tap the "Google Drive" source pill → ✓ it shows "Sign in with Google to load books from your Drive." instead of a picker button, there is no separate "FROM YOUR DRIVE" heading on screen (CAT-012).
5. Home → Open book → system picker → `alice-illustrated.epub` (listed directly in Recent, or under Downloads); watch the transition → ✓ goes from loading straight to the book; "Your library is empty" is never flashed (LIB-009).
6. The reader opens on chapter 1 → ✓ no Project Gutenberg licence/header text precedes the narrative (PRS-018). **v26 parser fix** (commits `1f649b3`/`759f484`/`335202d`/`06c7650`/`15e43a6`): chapter 1's own file now starts with "CHAPTER I", the chapter's printed title, and "ALICE was beginning to get very tired…" (PRS-017) intact — previously (confirmed on-device pre-fix) this exact paragraph was silently dropped because the leading drop-cap sat in its own `<div class="figleft">` that `HtmlChapterExtractor` didn't merge into the following text, and the front-matter pages ahead of it could show stray "PAGE"/contents-list text (a table-of-contents or plate-list header row read as a paragraph) — both are fixed (drop caps merge into the following text; contents tables/plate lists of any kind are excluded from readable text). Cover/frontispiece/title page/spine art/list-of-illustrations pages can still precede chapter 1 itself, so the automated check still confirms "So she was considering in her own mind" (the next paragraph) rather than scrolling all the way from app launch to the literal opening sentence — not re-verified as unnecessary after this fix. The literal first-paragraph form of PRS-017 is exercised below by `full-features.fb2`/`classic-h2.mobi`/`no-description.epub` instead, which don't have this front matter.
7. Scroll down about half a screen, press Back → ✓ count label reads "1 VOLUME" (LIB-006); with filter "Reading" Alice is listed, with "Unread" it is not (LIB-007).
8. Look at the Alice grid card and the Continue reading hero → ✓ both show the real Alice cover art (IMP-013); hero centre text is the cleaned `dc:description`, ≤ ~280 chars, ending "…" if clamped (LIB-017); the "CONTINUE READING" button label is on one line (LIB-020).
9. Open book → picker → `full-features.fb2`, then Back to Library → ✓ card shows the exact `<book-title>` and "First Last" author (PRS-020) and the FB2's own cover (PRS-024); hero centre text is the FB2 `<annotation>`, not a first paragraph (LIB-022).
10. Open book → picker → `classic-h2.mobi`; in the reader open the Chapter Picker → ✓ 4 chapters, one per `<h2>`, each starting at its heading (PRS-007); every entry has a readable, non-blank title (PRS-016).
11. Back to Library → ✓ "3 VOLUMES". Open book → picker → `no-description.epub` → ✓ the library-limit dialog explains the 3-book free limit with Upgrade and Got it; after Got it the library still shows 3 volumes and no new card (LIB-001).
12. Tap the Alice card → ✓ opens normally, no limit dialog. Back, then Open book → picker → `alice-illustrated.epub` again → ✓ opens, no limit dialog, still "3 VOLUMES" (LIB-002).
13. Tap the small delete icon in the corner of the Alice card (not a long-press — there is no context menu) → the dialog says the book "will be removed from your library…" → leave the checkbox unticked → Delete → ✓ the card disappears at once, "2 VOLUMES" (LIB-004). (The dialog's checkbox and what it does are J-LIB-19.)
14. Open book → picker → `no-description.epub` → ✓ imports and opens without the limit dialog (LIB-004).
15. Back. Outside the app: `adb shell rm /sdcard/Download/no-description.epub` (or revoke the persisted URI grant with `adb shell cmd uri ...`), then tap that book's card → ✓ a HomeErrorDialog explains the file is missing/unavailable; the tap never silently does nothing (ERR-002).
16. Dismiss the dialog → ✓ app is responsive, other cards still open.

**Covers:** IMP-010, ALM-005, WRD-009, CAT-002, CAT-012, LIB-009, PRS-017, PRS-018, LIB-006, LIB-007, IMP-013, LIB-017, LIB-020, PRS-020, PRS-024, LIB-022, PRS-007, PRS-016, LIB-001, LIB-002, LIB-004, ERR-002

### J-LIB-02 — Catalog evening: search, download from Gutenberg and Standard Ebooks, hit the cap, go offline

**Priority** P0 · **Tags** smoke, full, network, tablet, phone · **Devices** tablet API 35, phone emulator (if Gutenberg fails over IPv6 on the emulator, use a real device — see J-LIB-13)
**Fixtures** none (live catalog)
**Preconditions** Fresh install, free tier (Premium (debug) off), library empty, network on.

**Steps**

Corrected from an earlier draft (verified against `CatalogScreen.kt`/`CatalogViewModel.kt`): the two
sources are separate pills, searched one at a time — "Open Library" (default; backed by the
Project Gutenberg OPDS feed under a different display label, see `CatalogSource.kt`) and
"Standard Ebooks" — there is no single combined feed. A search that succeeds with zero matches
shows "No books found" with **no** Retry action; Retry only appears when the search itself threw
(e.g. offline).

**Fixed in v26** (commit `d39b6ec`, "the search field is labelled by its placeholder, not a
description"): the search field's "Search by title or author…" placeholder is now real text
inside the field's own decoration box, so it reaches the accessibility tree and a plain `tapOn`
for it works — but only while the field is empty, since the placeholder Text is conditional on
`query.isEmpty()`. (Previously — confirmed on-device, see `task-7a-report.md` — the placeholder
was a sibling `Text` the tree never exposed at all, in any state;
`e2e/subflows/tap_catalog_search.yaml` worked around it with a position-based selector.) Because
`CatalogViewModel`'s query state persists across navigation, a later search in the same session
can find the field non-empty (no placeholder showing); confirmed on-device (Tab_35_PixelTablet,
2026-11-10) that this makes a plain placeholder tap fail on a second search.
`tap_catalog_search.yaml` now tries the placeholder text first and falls back to the old
position-based selector when it isn't showing.

1. Tap Catalog → ✓ opens straight on a "POPULAR" list, not the CAT-002 placeholder (see the correction note above: `CatalogViewModel.init` preloads popular books, so "Search by title or author to begin" never actually shows in steady state).
2. Tap the search field → ✓ the keyboard sits directly under the list, no blank band above it (CAT-009).
3. Type "Pride and Prejudice" on the default "Open Library" pill, submit → ✓ results with cover, title, author and a download action (CAT-001).
4. Download the result, tap Open → ✓ it parses and opens in the reader (CAT-004), checked via the reader's language chip rather than chapter-1 text. This particular catalog download is the George Allen illustrated edition (same genre as J-LIB-01's alice-illustrated.epub): genuine front matter (preface, list of illustrations, title page, publisher's device, two illustrations) precedes the text, and multiple paragraph-opening drop caps made literal-text scroll checks unreliable even several screens in (confirmed on-device across three attempts — see `e2e/flows/library/J-LIB-02.yaml`'s step 4 note); PRS-018's boilerplate-stripping is exercised by other journeys' non-illustrated imports instead.
5. Go to another chapter, add a bookmark (Reader top bar → Bookmark), Back → ✓ Pride and Prejudice is in the Library (CAT-004). In the Chapters list, this illustrated edition's rows are labelled with the novel's own printed chapter headings (e.g. "CHAPTER XLIV.") rather than a plain "Chapter 2" — the sequential roman-numeral index on the left counts every parser-level break, including front matter, so it does not match the novel's chapter numbers; the early rows (including the real "chapter 2") also proved unreliable scroll targets on-device, so this step bookmarks a later, reliably-reachable chapter instead — LIB-005 only needs *a* different chapter, not chapter 2 specifically (confirmed on-device, see `e2e/flows/library/J-LIB-02.yaml` step 5).
6. Catalog → switch to the **Standard Ebooks** pill → search "Walden", download, Open → ✓ downloads, opens, appears in the Library (CAT-005). A Standard Ebooks search for "Walden" ranks "Poetry" by Ring Lardner above "Walden" by Thoreau itself (confirmed on-device, reproduced 3x) — the app just displays the search API's own order, so this step opens the second result, not the first (see `e2e/flows/library/J-LIB-02.yaml` step 6).
7. Catalog → switch back to the **Open Library** pill → search "Frankenstein", download → ✓ Library shows "3 VOLUMES".
8. Catalog → search "Moby Dick", tap download → ✓ "Library is full… Delete one to make room — or upgrade…" with Upgrade / Got it; after Got it the book is not in the Library (CAT-007).
9. Catalog → search "Pride and Prejudice", download the same (already-owned) title again → ✓ opens directly, no limit dialog (CAT-008, **fixed in v26**, commit `d0df6c2`: `CatalogViewModel.downloadAndOpen` now resolves the book's deterministic uri via `CatalogRepository.localUriFor` before gating `canAddNew`, so an owned book's uri matches and it opens even at the free-tier cap). Confirmed on-device against the exact repro that used to deterministically show "Library is full" pre-fix; the Library still shows "3 VOLUMES" afterwards (no phantom 4th volume — `EpubDownloader.download` is idempotent). The genuinely-new-4th-book case (step 8, "Moby Dick") is a different title and still correctly shows the limit dialog.
10. In the reader open Bookmarks → ✓ the chapter-2 bookmark from step 5 is still listed (LIB-005).
11. Catalog → search "zxqvbnm qwertyuiop" → ✓ a "No books found" empty-results state, no Retry action, no crash (CAT-003).
12. Turn network off (Maestro `setAirplaneMode: enabled`, or `adb shell svc wifi disable; adb shell svc data disable`), search "Dracula" → ✓ error state with Retry (CAT-003).
13. Turn network on (`setAirplaneMode: disabled`), tap Retry → ✓ results load (CAT-003).

**Covers:** CAT-001, CAT-002, CAT-003, CAT-004, CAT-005, CAT-007, CAT-008, CAT-009, PRS-018, LIB-002, LIB-005

### J-LIB-03 — A folder of bad files: every one fails cleanly and none eats a free slot

**Priority** P0 · **Tags** smoke, full, tablet, phone · **Devices** tablet API 35, phone emulator
**Fixtures** F-CORRUPT-EPUB, F-CORRUPT-FB2, F-CORRUPT-MOBI, F-ZIPBOMB, F-HUGE, F-SHORT, F-HUFF, F-AZW3, F-DRM, F-FB2-NOCH, F-EMPTY, F-ALICE, F-FB2
**Preconditions** Fresh install, free tier, library empty. After every step the expectation "no crash, no ANR, back on a usable Home" applies implicitly.

**Steps**
1. Open book → picker → `corrupt.epub` → ✓ a readable error message (no stack trace), app stays on Home (ERR-004).
2. Same with `corrupt.fb2` → ✓ readable error, no crash (ERR-004).
3. Same with `corrupt.mobi` → ✓ readable error, no crash (ERR-004).
4. Open book → `zipbomb.epub` → ✓ fails with "Book is too large to open safely." within ~30 s; no freeze, no OOM crash (ERR-005, **fixed in v26**, commit `5472c85`: previously — confirmed on-device, see `task-7a-report.md`'s Take 4 — the whole app process crashed with an uncaught `OutOfMemoryError` instead, because `readZipEntries`'s `ByteArrayOutputStream` needed ~1.5x the 300 MB `MAX_DECOMPRESSED` budget in contiguous heap to grow into, well past this device's ~192 MB default-process heap limit; the fix scales the effective budget down to a fraction of the real heap, so `BookTooLargeException` now throws before the buffer can OOM). Does not consume a free-tier slot.
5. Open book → `oversized.fb2` → ✓ fails with "Book is too large to open safely." within ~60 s; no hang, no crash (ERR-006, **fixed in v26**, same commit: `Fb2DocumentBuilder` now has its own per-book `maxChars` budget, closing the gap where nothing bounded FB2's accumulated text before `StringBuilder.toString()` could OOM). Does not consume a free-tier slot.
6. Open book → `short-header.epub` → ✓ no crash during format sniffing; a clear error is shown (ERR-008).
7. Open book → `huffcdic.mobi` → ✓ "This MOBI uses HUFF/CDIC compression, which isn't supported yet." and return to Library (IMP-005).
8. Open book → `kf8.azw3` → ✓ "This looks like an AZW3/KF8 file, which isn't supported yet." (IMP-006).
9. Open book → `drm.mobi` → ✓ "This MOBI is DRM-protected and can't be opened." (IMP-007).
10. Open book → `no-chapters.fb2` → ✓ a clear "no readable chapters" error; no empty book is added (ERR-007).
11. Open book → `empty.epub` → ✓ unlike the files above, this one *does* parse (zero paragraphs isn't a parse error) and auto-opens straight into the reader, same as any successful import; it shows "Nothing to read here" — a visible message that the book has no readable content, not a blank/white page (ERR-003).
12. Back to Library → ✓ none of the files from steps 1–10 has a card.
13. Import `alice-illustrated.epub` and `full-features.fb2` → ✓ both import with no limit dialog (failed imports did not consume free slots).
14. Force-stop and relaunch → ✓ app starts normally; the library is intact.

**Covers:** ERR-003, ERR-004, ERR-005, ERR-006, ERR-007, ERR-008, IMP-005, IMP-006, IMP-007

### J-LIB-04 — Picture books and tables with an unlimited (debug Premium) library

**Priority** P0 · **Tags** smoke, full, tablet, phone · **Devices** tablet API 35, phone emulator
**Fixtures** F-ALICE, F-IMGCH, F-PICS, F-RUR, F-ILIAD, F-COVER-B, F-NODESC, F-PROSE, F-TINY
**Preconditions** Fresh install, library empty. The Iliad step allows a 90 s timeout (see Known bugs, IMP-011).

**Steps**
1. Settings → Premium (debug) → on → ✓ Settings shows Premium active (PRM-013).
2. Import `alice-illustrated.epub`, open from the very start → ✓ the cover appears once and is not repeated as the first page of chapter 1 (IMP-002, PRS-003).
3. Page into the chapter with the first plate → ✓ the illustration sits inside the chapter at its place (IMP-001); scroll through all chapters → no gaps or crashes where inline SVGs were skipped, cover still shown once (PRS-003).
4. At a chapter opening, long-press the decorative first letter → ✓ the drop-cap letter is present as readable, selectable text (PRS-004).
5. Import `image-chapter.epub` → Chapter Picker → ✓ the image-only chapter is listed with a non-blank title; selecting it shows the illustration (IMP-014, PRS-002, PRS-016).
6. Import `pictures-only.epub` → ✓ opens (not blank) and shows every picture (IMP-003); Chapter Picker lists exactly one chapter with all images in order (PRS-005).
7. Import `rur-cs.epub`, go to the first act → ✓ dialogue is shown line by line ("Character — line"), not an empty chapter (PRS-001, IMP-008).
8. Import `iliad-el.epub`, open book 1 → ✓ verse lines are shown with stray line numbers removed (PRS-001, IMP-008).
9. Import `cover-b.epub`, then `no-description.epub` → ✓ library now has 7 books and the limit dialog never appeared (LIB-003).
10. Library grid → ✓ Alice card shows the Alice cover, cover-b shows its blue cover; open each → only its own cover/inline images (LIB-021, PRS-014).
11. Delete Alice and cover-b; re-import cover-b first, then Alice; repeat the delete/re-import cycle twice → ✓ never a swapped or shared cover (LIB-021, PRS-014).
12. Import `plain-prose.epub`, read to the middle of chapter 3, Back, reopen → ✓ same chapter and paragraph; layout unchanged by the table-parsing change (IMP-009).
13. Settings → Premium (debug) → off. Import `tiny.epub` → ✓ the library-limit dialog reappears; no card added (PRM-013).

**Covers:** PRM-013, IMP-001, IMP-002, IMP-003, IMP-008, IMP-009, IMP-014, PRS-001, PRS-002, PRS-003, PRS-004, PRS-005, PRS-014, PRS-016, LIB-003, LIB-021

### J-LIB-05 — Reading an FB2 collection: structure, verse, epigraphs, images, encodings

**Priority** P0 · **Tags** smoke, full, tablet, phone · **Devices** tablet API 35, phone emulator
**Fixtures** F-FB2, F-FB2-1251
**Preconditions** Fresh install, library empty (two books — fits the free tier).

**Steps**
1. Import `full-features.fb2` → Library → ✓ card title and "First Last" author match the metadata (PRS-020); card shows the `<coverpage>` cover, not a placeholder (PRS-024); hero synopsis = `<annotation>` (LIB-022).
2. Open it → ✓ chapter 1 starts with the body epigraph paragraph(s), then the first section text (PRS-019); the first body paragraph matches the source exactly (PRS-017).
3. Open the Chapter Picker → ✓ no entry for the wrapper `<section>`; its leaf sections appear titled with the ancestor prefix ("Title · 1. Sub") (PRS-013); every title is non-blank (PRS-016).
4. Open the section that has its own epigraph → ✓ epigraph and its author-credit line come first, followed immediately by that section's body (PRS-019).
5. Open the section with the inline `<image>` → ✓ the base64 image decodes and renders at its place in the text (IMP-004, PRS-024).
6. Open the poem section → ✓ each `<v>` line is its own paragraph in source order, none merged (PRS-021, PRS-012).
7. In the same chapter find the `<subtitle>` → ✓ shown as an ordinary paragraph in reading order, not dropped, not a chapter title (PRS-022, PRS-012).
8. Find the `<cite>` outside any epigraph → ✓ shown as ordinary body paragraphs (PRS-023).
9. Find the long (> 5000 chars) `<cite>` → ✓ its last sentence is present; nothing cut at 5000 characters (PRS-012).
10. Import `russian-1251.fb2` → ✓ opens; the first paragraph matches the known Cyrillic text exactly, no mojibake (PRS-017); title/author on the card are Cyrillic and correct (PRS-020).

**Covers:** PRS-012, PRS-013, PRS-016, PRS-017, PRS-019, PRS-020, PRS-021, PRS-022, PRS-023, PRS-024, IMP-004, LIB-022

### J-LIB-06 — Classic MOBI shelf: every chapter-splitting rule

**Priority** P0 · **Tags** smoke, full, tablet · **Devices** tablet API 35 (phone optional)
**Fixtures** F-MOBI, F-MOBI-PB, F-MOBI-PBH, F-MOBI-H1H2, F-MOBI-PLAIN
**Preconditions** Fresh install; Settings → Premium (debug) on (5 books).

**Steps**
1. Import `classic-h2.mobi` → Chapter Picker → ✓ chapter count = 4 `<h2>` headings, each chapter starts at its heading (PRS-007); without native markers it fell back to heading detection, not one giant chapter (PRS-006).
2. Open chapter 1 → ✓ first paragraph matches the fixture's known opening sentence exactly (PRS-017).
3. Import `pagebreaks.mobi` → Chapter Picker → ✓ 4 chapters (3 page breaks + 1), each starting where a break was (PRS-008).
4. Import `pagebreaks-and-h2.mobi` → Chapter Picker → ✓ 3 chapters (page breaks only); the `<h2>`s inside do not subdivide (PRS-011).
5. Import `h1-h2.mobi` → Chapter Picker → ✓ 3 chapters (one per `<h1>`); nested `<h2>` adds none (PRS-010).
6. Import `plain.mobi` → ✓ exactly one chapter; the book opens at its start, no crash (PRS-009).
7. For each of the five books glance at the Chapter Picker → ✓ every entry has a readable, non-blank title, never "null" (PRS-016).
8. Settings → Premium (debug) off.

**Covers:** PRS-006, PRS-007, PRS-008, PRS-009, PRS-010, PRS-011, PRS-016, PRS-017

### J-LIB-07 — Signed-out reader builds a vocabulary (Words), then a crash-free tab tour

**Priority** P0 · **Tags** smoke, full, network, tablet, phone · **Devices** tablet API 35, phone emulator (step 9 phone only)
**Fixtures** F-ALICE
**Preconditions** Fresh install, never signed in, default translator (Offline HQ) with the en→ru pack downloadable (network on).

**Steps**
1. Skip sign-in entirely. Import `alice-illustrated.epub`, open it → ✓ the book opens and the translation side resolves without any sign-in prompt (ACC-016).
2. Long-press "sister" in the original text, wait for "Ready", tap Save → ✓ "Saved to dictionary" (WRD-001).
3. Words tab → ✓ header "1 saved word" (singular) (WRD-002); the entry shows "sister" with its translation (WRD-001).
4. Back to the reader, long-press "sister" again → Save → ✓ "Already in your dictionary", not a failure message; Words still has one entry (WRD-008).
5. Long-press a punctuation-only token (the em-dash "—") → Save if offered → ✓ "Nothing to save"; no entry created (WRD-014).
6. Save "rabbit" and "pictures" the same way → Words shows 3 saved words.
7. Words → tap the search field → ✓ no blank band between list and keyboard (WRD-004); type "rab" → ✓ only "rabbit" is listed (WRD-010). Clear the search.
8. Open "rabbit" → note action → type "white, with a waistcoat" → save → ✓ "Note saved"; go to Home and back to Words, reopen "rabbit" → note is still shown (WRD-012).
9. (Phone) Tap "sister" → ✓ detail replaces the list full-screen; press system Back → ✓ returns to the list, not out of the app (WRD-005).
10. Open "pictures" → Delete → confirm → ✓ "Word deleted"; it is gone from the list (WRD-013).
11. Settings → Translator → choose a paid online provider (e.g. DeepL) and leave its API key empty. Reader → long-press "bank" → Save → ✓ a translation-failed toast; Words does **not** contain "bank" with an empty translation (WRD-006). Switch the translator back to Offline HQ.
12. Tab tour: Home → Catalog → Words → Almanac → Settings → open Alice → translate a paragraph → Back; repeat twice; press Home, wait 10 s, return to the app → ✓ no crash/ANR; bottom bar (phone) / rail (tablet) works in every tab; state survives background/foreground (ERR-001).
13. Almanac → ✓ shows a session for today; still never signed in, nothing gated except Drive/sync (ACC-016).

**Covers:** ACC-016, WRD-001, WRD-002, WRD-004, WRD-005, WRD-006, WRD-008, WRD-010, WRD-012, WRD-013, WRD-014, ERR-001

### J-LIB-08 — Arranging the shelf on tablet and phone: hero, filters, search, grid

**Priority** P1 · **Tags** full, tablet, phone · **Devices** tablet API 35 (1280dp), phone emulator at 411dp and 360dp (`adb shell wm density`)
**Fixtures** F-NODESC, F-TINY, F-MOBI-PLAIN, then F-ALICE, F-COVER-B, F-IMGCH, F-PICS, F-FB2
**Preconditions** Fresh install, free tier, library empty, signed out.

**Steps**
1. Import `no-description.epub`, open it, Back → ✓ hero centre text is the book's first paragraph (cleaned/trimmed), not blank (LIB-018).
2. Import `tiny.epub`, open, Back → ✓ hero centre is empty with no placeholder/crash; "CONTINUE READING" on one line; "Last opened" card keeps its widened size; progress row pinned to the bottom (LIB-019, LIB-020).
3. Import `plain.mobi` (single chapter), scroll to about the middle, Back → ✓ its progress reads ≈ 50 % (paragraph-based, not 100 % or 0 %); filter Reading lists it, filter Unread does not (LIB-008).
4. Tap the hero's cover image → ✓ opens at the saved position. Back; tap the hero title text → ✓ opens. Back; tap empty space inside the card → ✓ opens (LIB-010).
5. Tap the Library search field → ✓ keyboard directly under the list, no blank band (LIB-011); type "tiny" → only tiny.epub. Dismiss the keyboard with Back → ✓ the bottom tab bar is back in its normal place (LIB-012).
6. Tap the account avatar → Account screen opens. Tap Catalog, Words, then Home → ✓ the Account screen does not reappear (ACC-019).
7. Settings → Premium (debug) on; import F-ALICE, F-COVER-B, F-IMGCH, F-PICS, F-FB2 (8 books). On the tablet → ✓ Home grid shows 7 columns. On the phone at 411dp and at 360dp → ✓ 2 columns (LIB-013).
8. Phone at 360dp: look at the "Your shelf" header → ✓ filter pills are on one horizontally scrollable line; the header never grows tall (LIB-014); a pill partly cut off at the right edge before scrolling is expected, and after scrolling every pill is reachable (LIB-015).
9. Phone at 360dp (or split screen on the tablet) → ✓ the "CONTINUE READING" label still fits one line (LIB-020).
10. Settings → Premium (debug) off.

**Covers:** LIB-008, LIB-010, LIB-011, LIB-012, LIB-013, LIB-014, LIB-015, LIB-018, LIB-019, LIB-020, ACC-019

### J-LIB-09 — Account lifecycle with email and password: sign-up validation to account deletion

**Priority** P0 · **Tags** manual, network, tablet, phone · **Devices** tablet API 35 (rail), phone emulator (bottom bar)
**Note** Manual by decision (2026-11-06: account is automated as screens and validation only, no real Firebase accounts). When scripting, steps 1–3, 6 and 12 become an automated screens-only journey.
**Fixtures** F-ALICE (one book in the library, to prove local data survives)
**Preconditions** Fresh install, F-ALICE imported, signed out, network on. `ACC_EXISTING` exists; throwaway address `mirrolit.e2e+<timestamp>@<test-domain>` has no account.

**Steps**
1. Tap the account avatar → ✓ "Sign in to sync your library and link Google Drive." with "Sign in or create account".
2. Sign in or create account → switch to Create account → name "E2E", email "not-an-email", any password → Create account → ✓ inline "Enter a valid email address."; nothing submitted (ACC-002).
3. Email = throwaway address, password "short1" → ✓ inline "Password must be at least 8 characters."; nothing submitted (ACC-003).
4. Password "Mirrolit-e2e-1" → Create account → ✓ signed in; Profile shows "Email not verified" (verification email sent) (ACC-001).
5. Profile → Resend → ✓ confirmation that the email was re-sent, with the spam-folder note (ACC-010).
6. Look at the avatar: phone bottom-bar strip and tablet rail → ✓ present and tappable in both layouts (ACC-020).
7. Profile → Sign out → ✓ signed-out state with "Sign in to sync your library and link Google Drive." (ACC-015).
8. Create account with `ACC_EXISTING`'s email → ✓ "An account with this email already exists."; no duplicate (ACC-004).
9. Sign in with `ACC_EXISTING` and a wrong password → ✓ "Incorrect email or password."; still signed out (ACC-006).
10. Tap "Forgot password?" → `ACC_EXISTING` email → Send link → note the message. Repeat with an unregistered address → ✓ the same "If that email is registered, a reset link is on its way…" both times (ACC-012).
11. Sign in with the throwaway address + "Mirrolit-e2e-1" → ✓ Profile shows "Signed in" and "Email not verified" (ACC-005).
12. Tap Home → Catalog → Words → Home → ✓ the Profile screen is not resurrected (ACC-019).
13. Profile → Delete account → confirm the dialog → ✓ a re-authentication prompt asks for the password; deletion does not proceed without it (ACC-013).
14. Enter "Mirrolit-e2e-1" → ✓ "Account deleted"; back in signed-out state; Alice is still in the Library (ACC-014).
15. Sign in with the throwaway address → ✓ "No account found for that email." (ACC-007) — confirms the Firebase record is gone (ACC-014).

**Covers:** ACC-001, ACC-002, ACC-003, ACC-004, ACC-005, ACC-006, ACC-007, ACC-010, ACC-012, ACC-013, ACC-014, ACC-015, ACC-019, ACC-020

### J-LIB-10 — A reading day shows up in the Almanac, including an unknown-language book

**Priority** P1 · **Tags** full, network, tablet, phone · **Devices** tablet API 35, phone emulator
**Fixtures** F-FOREIGN, F-PICS
**Preconditions** Fresh install, no reading sessions, free tier, network on (translation models download).

**Steps**
1. Almanac → ✓ "Open a book to start tracking your reading." with the streak/minutes/words hint, no stray charts (ALM-005). Open each sub-view (week/month/books/languages) → ✓ "No reading sessions yet." / "No data yet." instead of a blank chart (ALM-009).
2. Import `english-with-french.epub`, read chapter 1 for ≥ 3 minutes (scroll slowly).
3. Long-press a word inside the French passage → wait for Ready → Save. Words → open it → ✓ source language is French, not the book's English (WRD-003).
4. Import `pictures-only.epub`, flip through the pictures for ~1 minute, Back.
5. Note today's reading minutes on Home. Open Almanac → ✓ the daily figure equals Home's (ALM-004).
6. Almanac per-book list → ✓ pictures-only book's language reads "Unknown", never "UND" or blank (ALM-001).
7. Scroll through streak and heatmap → ✓ only proper English labels, no raw placeholder/debug text (ALM-002).
8. Switch to a past range with no sessions → ✓ "No reading sessions yet." / "No data yet." (ALM-009).

**Covers:** ALM-001, ALM-002, ALM-004, ALM-005, ALM-009, WRD-003

### J-LIB-19 — Removing a book and adding it again: the place and bookmarks are kept unless the reader deletes them

**Priority** P0 · **Tags** smoke, full, tablet, phone · **Devices** tablet API 35, phone emulator
**Fixtures** F-LONG, F-NODESC, F-PROSE, F-COVER-B
**Preconditions** Fresh install, Settings → Premium (debug) off, signed out, fixtures pushed.

**Steps**
The system picker hands out a different uri for one file from its "Recent" and "Downloads" roots; since versionCode 29 the app knows the file again across them ("Same book"). So `en-novel.epub` is picked from **Recent** in steps 1 and 8b and from **Downloads** in steps 6, 8 and 10.

1. Open book → `en-novel.epub` (from Recent); in the reader open the Chapter Picker → "Chapter 3 — The Long Crossing" → ✓ the reader lands on that chapter's masthead: its title and its first paragraph ("…(ch3.1)…") are on screen (NAV-018).
2. Scroll about a screen into chapter 3; Bookmarks (top bar) → "Bookmark this page" → ✓ the row becomes "Remove bookmark here" and the list shows "Chapter 3 — The Long Crossing · ¶ n". Close, Back to Library.
3. Open book → `no-description.epub`, Back; Open book → `plain-prose.epub`, Back → ✓ "3 VOLUMES" (the free tier is full).
4. Tap the delete icon on "The River Crossing" → ✓ the text reads "… will be removed from your library. Your place, bookmarks and notes are kept in case you add it again." and "Also delete my reading progress, bookmarks and notes" is **not** ticked; tick it → ✓ the text ends "Your place, bookmarks and notes will be deleted with it."; untick it → ✓ the first text is back (LIB-027).
5. Delete (checkbox unticked) → ✓ the card is gone, "2 VOLUMES". Open book → `cover-b.epub` → ✓ it imports and opens without the limit dialog — a removed book holds no slot (LIB-024). Back → "3 VOLUMES".
6. Open book → `en-novel.epub` (the removed book), the library being full again → ✓ "Library is full"; after Got it still "3 VOLUMES" and no card for it (LIB-025).
7. Delete "The Blue Cover Book" → "2 VOLUMES".
8. Open book → `en-novel.epub` from Downloads (it was added from Recent) → ✓ no limit dialog; it opens in chapter 3 where it was left ("…(ch3.n)…" on screen), not at the start; Bookmarks still lists "Chapter 3 — The Long Crossing · ¶ n" (LIB-023, LIB-028). Back → "3 VOLUMES".
8b. With the book in the library and "3 VOLUMES", Open book → `en-novel.epub` from Recent → ✓ no limit dialog; it opens in chapter 3 at its place; back in the Library still "3 VOLUMES" and one card for it (LIB-029).
9. Tap the delete icon on "The River Crossing" → ✓ the checkbox is unticked again (LIB-027); tick it → Delete → "2 VOLUMES".
10. Open book → `en-novel.epub` → ✓ it opens at the start of chapter 1 ("…(1)…" on screen, no chapter 3 text); Bookmarks shows "No bookmarks yet…" (LIB-026). Back → "3 VOLUMES".

**Covers:** NAV-018, LIB-027, LIB-024, LIB-025, LIB-023, LIB-026, LIB-028 (step 8), LIB-029 (step 8b)

---

# Manual journeys

### J-LIB-11 — Buying Premium from the full-library dialog (Play license tester)

**Priority** P0 · **Tags** manual, network, tablet, phone · **Devices** real phone or tablet with the build installed from a Play testing track
**Fixtures** any 7 valid books (F-ALICE, F-FB2, F-MOBI, F-NODESC, F-TINY, F-PROSE, F-COVER-B)
**Preconditions** Play Console access; device account is a license tester that has never bought `premium_unlimited`; library has exactly 3 books; free tier.

**Steps**
1. Play Console → Monetize → Products → In-app products → ✓ `premium_unlimited` exists and is Active, and matches `BillingManager.PREMIUM_PRODUCT_ID` in code (PRM-015).
2. Force-stop the app; relaunch and within the first second import a 4th book and tap Upgrade in the limit dialog → ✓ "Store isn't ready yet. Try again in a moment.", no crash (PRM-010).
3. Wait ~5 s, import the 4th book again → ✓ the dialog's button shows the real store price "Upgrade · <price>", not a placeholder (PRM-001); retrying the purchase now opens the Play sheet (PRM-010).
4. In the Play sheet press Back (cancel) → ✓ "Purchase failed. Please try again."; still free tier, no partial grant (PRM-011).
5. Upgrade → payment "Slow test card, declines after a few minutes" → ✓ "Purchase pending — premium unlocks once it completes."; importing a 4th book is still blocked; after the decline premium is still not granted (PRM-009).
6. Upgrade → "Test card, always approves" → ✓ the dialog closes, "Premium unlocked — enjoy your unlimited library."; the 4th book imports at once (PRM-002).
7. Force-stop, relaunch, import a 5th and 6th book → ✓ no limit dialog; Settings shows Premium active (PRM-003).
8. Airplane mode on, force-stop, relaunch, import a 7th book → ✓ Premium still active offline, no limit dialog (PRM-014).
9. Walk every Settings section → ✓ there is no "Premium (debug)" toggle in this Play-installed build (PRM-005).

**Covers:** PRM-001, PRM-002, PRM-003, PRM-005, PRM-009, PRM-010, PRM-011, PRM-014, PRM-015

### J-LIB-12 — Premium across reinstall, a second account, refund, backup and the debug override

**Priority** P0 · **Tags** manual, network · **Devices** two real devices (A, B) + one device/emulator with a debug build
**Fixtures** any 5 valid books
**Preconditions** Account A bought Premium in J-LIB-11; account B is a license tester that never purchased; Play Console access; Android cloud backup available.

**Steps**
1. Device B (account B, Play build, 3 books): Settings → Premium → Restore purchase → ✓ "No previous purchase found."; still free tier (PRM-012).
2. Device B: Settings → Premium → Upgrade → "Test card, always approves" → ✓ "Premium active · unlimited library"; a 4th book imports (PRM-008).
3. Device A: uninstall, reinstall from Play, import 3 books, then Settings → Restore purchases → ✓ "Premium active · unlimited library"; the 4th import succeeds (PRM-004).
4. Device A: Settings → System → Backup → Back up now (or `adb shell bmgr backupnow io.mirrolit.app`). On a fresh profile signed into a **non-purchasing** account, restore the backup, turn on airplane mode, open Mirrolit → ✓ Premium is NOT shown; the 4th import is blocked until Play's own purchase sync runs (PRM-007).
5. Play Console → Order management → refund account B's order with "Remove entitlement". On device B relaunch the app (online) → ✓ Premium reverts to inactive; a 4th import shows the limit dialog again (PRM-006).
6. Debug build: Settings → Premium (debug) on → import 5 books → ✓ no limit dialog; toggle off → importing another book shows the limit dialog (PRM-013).
7. Debug build, email account signed in with Premium (debug) on: Profile → Delete account → re-auth → ✓ "Account deleted"; no account-tied entitlement survives in Settings, reading data stays on the device (ACC-014).

**Covers:** PRM-004, PRM-006, PRM-007, PRM-008, PRM-012, PRM-013, ACC-014

### J-LIB-13 — Google account and Drive on a Play-installed build, on a real network

**Priority** P0 · **Tags** manual, network, tablet, phone · **Devices** real phone + real tablet; build installed from a Play testing track; a debug build for step 10
**Fixtures** Google Drive: one valid EPUB (with a bookmark plan), one large EPUB (≥ 20 MB), `notes.pdf`
**Preconditions** A Google account on the device; an email/password account `ACC_PW` whose address is also a Google account; a fresh unverified email account `ACC_UNVERIFIED`; free tier with ≤ 2 books.

**Steps**
1. Airplane mode on. Account → Sign in with email/password → ✓ "No connection. Check your network and try again."; no crash, no partial state (ACC-017). Airplane mode off.
2. Account → Continue with Google → pick the device account in the system credential sheet → ✓ signed in, Profile shows the Google email marked verified (ACC-008); no "not registered to use OAuth2.0" error on this Play-signed build (ACC-018).
3. Catalog → FROM YOUR DRIVE → Choose a book from Drive → pick the valid EPUB → ✓ it downloads read-only, opens in the reader and appears in the Library (CAT-010). Add a bookmark in chapter 2.
4. Choose a book from Drive → press Back in the picker → ✓ "Drive access was cancelled." or no lingering error; no partial library entry (CAT-013).
5. Choose a book from Drive → `notes.pdf` → ✓ "That file isn't a supported book (EPUB, FB2, or MOBI)."; nothing added (CAT-011).
6. Choose a book from Drive → the large EPUB, then airplane mode during the download → ✓ "Couldn't load the file from Drive. Check your connection and try again." (CAT-014). Airplane mode off.
7. Re-import the step-3 EPUB from Drive → ✓ opens without the limit dialog; its chapter-2 bookmark is still listed (LIB-005).
8. Look over Catalog and the Drive section → ✓ no Google Play Books import path exists; only Gutenberg, Standard Ebooks and Drive (CAT-015).
9. Catalog → search "Pride and Prejudice" → download the Gutenberg EPUB on the real network → ✓ downloads and appears in the Library (CAT-006).
10. Repeat steps 3 and 9 on a debug build, then `adb shell run-as io.mirrolit.app ls -R files/` and `adb shell ls /sdcard/Download` → ✓ both files are under app-private `files/` (e.g. `files/catalog/<id>.epub`), nothing new in shared storage; App info → Permissions lists no storage write permission (ERR-016).
11. Sign out. Continue with Google choosing the address of `ACC_PW` → ✓ the collision message ("This email is registered with Google — use Continue with Google." per strings) instead of a duplicate/broken account (ACC-009).
12. Sign in as `ACC_UNVERIFIED` → Profile shows "Email not verified". Open the verification link from the inbox in a browser, return to the app, tap Refresh → ✓ "Email verified" without signing out (ACC-011).
13. Sign in with Google again. Add books until the library shows "3 VOLUMES" (the step-3 Drive EPUB among them), then Catalog → Google Drive → pick the step-3 EPUB once more → ✓ it opens at its saved place without the limit dialog; still "3 VOLUMES" (CAT-016).
14. Tidy up: delete the test accounts.

**Covers:** ACC-008, ACC-009, ACC-011, ACC-017, ACC-018, CAT-006, CAT-010, CAT-011, CAT-013, CAT-014, CAT-015, ERR-016, LIB-005, CAT-016

### J-LIB-14 — A reading week on real hardware: Almanac stats, saved words with audio, screen fit

**Priority** P1 · **Tags** manual, network, tablet, phone · **Devices** real phone (Pixel 7) and real tablet with a persistent taskbar (Lenovo / Pixel Tablet)
**Fixtures** F-ALICE, F-FB2, F-ILIAD
**Preconditions** Fresh install, no streak; a paid online translator API key (e.g. DeepL) available; device date may be changed in Developer options if days must be simulated.

**Steps**
1. Day 1: read F-ALICE translated to Russian for 10 minutes → Almanac → ✓ heatmap summary "<N> ACTIVE DAYS · <M> MIN" with M matching the session; today's cell highlighted (ALM-007).
2. Day 1: read F-FB2 translated to German for 5 minutes → Almanac breakdowns → ✓ per-book minutes (BookMinutes) and per-language minutes (LangMinutes) are non-zero and attributed to the right book/language (ALM-008).
3. Day 2 (next day or advanced date): read for a few minutes → Almanac → ✓ streak = 2 consecutive days; encouragement text appears once a prior best exists (ALM-006); the weekly chart shows visibly non-zero bars for both days (ALM-003).
4. Settings → Translator → DeepL, enter the API key. Reader → long-press "curious" → wait for Ready, note the exact translation → Save → Words → open the word → ✓ stored translation is identical to the popup's; proxy/log shows no second translation request (WRD-007).
5. In the word's detail tap Listen → ✓ the word (and/or context) is spoken via TTS at the configured rate/pitch (WRD-011).
6. Phone: open Home, Catalog, Almanac, Settings, Account (sign-in screen), Profile (signed in) and Words; scroll each to the bottom → ✓ no vertical "ribbon of letters", no overlaps, nothing cut off (LIB-016).
7. Tablet with taskbar: open the left menu/rail and scroll to its bottom → ✓ the account button is fully visible above the taskbar (ACC-021).
8. Tablet: import F-ILIAD and open its huge chapter, time until the text is interactive → record the time against the known bug (IMP-011; see Known bugs).

**Covers:** ALM-003, ALM-006, ALM-007, ALM-008, WRD-007, WRD-011, LIB-016, ACC-021, IMP-011

### J-LIB-15 — In-app update between two Play builds

**Priority** P0 · **Tags** manual, network, phone, tablet · **Devices** real device installed from the Play internal track
**Fixtures** F-ALICE with a bookmark and a translated page; one Catalog book (Gutenberg) read to a known place
**Preconditions** Build N installed **from Play**; build N+1 uploaded to the same track and rolled out; a second device/emulator with the same build sideloaded via `adb install`.

**Steps**
1. On the Play-installed device open Home → ✓ banner "A new version of Mirrolit is available" with Update and Not now (UPD-001).
2. Tap Not now → ✓ the banner hides; relaunch the app during the next 3 days → it stays hidden for this versionCode; Settings → About still says an update is available (UPD-003).
3. Settings → About → ✓ status "available" with an Update chip (UPD-006). Tap Update → ✓ About shows "Downloading update… N%" (UPD-006).
4. While it downloads press Home, wait 20 s, return → ✓ the flow reattaches cleanly: no crash, one banner only, progress not lost (UPD-005).
5. When ready → ✓ About shows the Restart chip (UPD-006) and the Home banner offers Restart; tap Restart → ✓ the app relaunches on build N+1 (Settings → About version) (UPD-002).
6. Open F-ALICE → ✓ the reading position, bookmark and translation all work right after the update (UPD-002).
7. Before step 3 note every book's cover, progress and place; after the restart on build N+1 open Home → ✓ every book is still in the library with its own cover (none blank or swapped) and the same progress; each book opens at the place noted and the bookmark is listed (DAT-007).
8. On the sideloaded device open the app ≥ 3 times over the session → ✓ no update banner ever appears and nothing crashes (UPD-004).

**Covers:** UPD-001, UPD-002, UPD-003, UPD-004, UPD-005, UPD-006, DAT-007

### J-LIB-16 — Upgrading an old install in place (DB v2 / v3 → current)

**Priority** P0 · **Tags** manual, tablet · **Devices** tablet emulator API 35 (snapshots per old build)
**Fixtures** F-ALICE, F-PROSE; old APKs: a DB-v2 build (books + translation_cache only) and a DB-v3 build (pre-synopsis)
**Preconditions** Old APKs available from `releases/` history; current debug and release builds.

**Steps**
1. Install the **v2** APK; import F-ALICE; translate one paragraph (cached); note title and translation.
2. `adb install -r` the current build (no uninstall), open → ✓ no crash, no data reset; Alice is still in the Library; the same paragraph's translation appears instantly from the cache without a translator call (DAT-001).
3. Open Alice → add a bookmark → reopen the Bookmarks dialog → ✓ the bookmark is listed; no crash (DAT-002).
4. Make Alice the most recently opened → hero → ✓ centre text empty (legacy synopsis = null) but the widened layout is intact, no placeholder or crash. Re-import `alice-illustrated.epub` → ✓ the synopsis now appears (DAT-004).
5. Wipe; install the **v3** APK; import F-ALICE and F-PROSE; add one bookmark, one note, save one word, read F-PROSE to chapter 4 at a known %; note all of them.
6. `adb install -r` the current build → ✓ the bookmark, note, saved word and today's reading session are all present and attached to the right book, nothing duplicated (DAT-003).
7. Open F-PROSE → ✓ same chapter and progress % as noted on the old build (DAT-006); layout and position unchanged by the v22 table-parsing change (IMP-009).
8. Release build: inspect the Room builder (and/or install it over a DB with an unknown schema version) → ✓ `fallbackToDestructiveMigration` is debug-only; the release build fails loudly instead of silently wiping the library (DAT-005).

**Covers:** DAT-001, DAT-002, DAT-003, DAT-004, DAT-005, DAT-006, IMP-009


### J-LIB-17 — Release artifact audit before a Play upload

**Priority** P0 · **Tags** manual · **Devices** build machine + one real device
**Fixtures** none
**Preconditions** `keystore.properties` present; Play Console access; an online translator API key.

**Steps**
1. Temporarily blank `billingPublicKey` in `keystore.properties`, run `./gradlew :app:bundleRelease` → ✓ the build fails with the documented guard (ERR-012). Restore the key.
2. Run `./gradlew :app:bundleRelease` → ✓ the .aab is signed with the release keystore (`jarsigner -verify -verbose`), and the build has `isMinifyEnabled`/`isShrinkResources` true (mapping.txt present) (ERR-011).
3. Install a release build (bundletool `install-apks`), `adb shell pm list packages | grep mirrolit` → ✓ `io.mirrolit.app`, never `com.example.splitreader` (ERR-010).
4. Walk all Settings sections of the release build → ✓ no "Premium (debug)" toggle (PRM-005).
5. Unzip/decompile the release APK (jadx) and grep for API keys/tokens and `http://` → ✓ no secrets; all translator base URLs are HTTPS; the OkHttp logging interceptor is only in debug code (ERR-013).
6. Search code for `READ_MEDIA_DOCUMENT_VISUAL_USER_SELECTED` → ✓ it has a real call site tied to the import picker on API 33+ (ERR-017).
7. Trigger a non-fatal log on a debug build, then on the release build → ✓ Crashlytics shows only the release event; R8 mapping was uploaded for the release (ERR-014).
8. Release build: save a translator API key, enable backup, `adb shell bmgr backupnow io.mirrolit.app`, inspect the backup set (local transport dump) → ✓ `translator_keys_enc` is excluded; no plaintext secret (ERR-015).
9. Open the Play listing (or Play Console → App content → Privacy policy) and open the privacy policy URL → ✓ the page loads; contact email and content match the current policy (ERR-009).

**Covers:** ERR-009, ERR-010, ERR-011, ERR-012, ERR-013, ERR-014, ERR-015, ERR-017, PRM-005

### J-LIB-18 — Odd files that need a human eye

**Priority** P1 · **Tags** manual, tablet · **Devices** tablet API 35 (debug build) and a real tablet for step 4
**Fixtures** F-MOBI-DIST0, F-FB2-XML, F-EMPTY-CH1, F-ILIAD
**Preconditions** Fresh install, Settings → Premium (debug) on.

**Steps**
1. Import `palmdoc-distance0.mobi`, open it, read the paragraphs around the distance-0 token (offset noted in the fixture README) → ✓ text decodes correctly, no garbage, no crash (IMP-012).
2. Import `full-features.xml` through the picker (generic `text/xml`) → ✓ it opens as a proper FB2: title, author and chapters as for F-FB2, not a raw XML dump (PRS-015).
3. Import `empty-first-chapter.epub`, open it → ✓ the reader shows a valid empty-chapter state and lets you move to chapter 2; no crash or index-out-of-bounds (PRS-025).
4. Import `iliad-el.epub`, open book 1, stopwatch until scrolling is responsive → record the time (IMP-011, known bug — does not block).

**Covers:** IMP-011, IMP-012, PRS-015, PRS-025

---

## Known bugs

| ID | Bug | Where exercised | Expected until fixed |
|---|---|---|---|
| IMP-011 | A book with one very large chapter (Iliad, ~12,700 lines) takes ~15 s–1.5 min to become interactive on emulators although parsing takes ~169 ms | J-LIB-04 step 8 (90 s timeout), J-LIB-14 step 8, J-LIB-18 step 4 | Record the time on real tablet hardware; report, never block |

## Coverage index

| Area | IDs → journey |
|---|---|
| LIB | 001 J01 · 002 J01,J02 · 003 J04 · 004 J01 · 005 J02,J13 · 006 J01 · 007 J01 · 008 J08 · 009 J01 · 010 J08 · 011 J08 · 012 J08 · 013 J08 · 014 J08 · 015 J08 · 016 J14 · 017 J01 · 018 J08 · 019 J08 · 020 J01,J08 · 021 J04 · 022 J01,J05 · 023–027 J19 |
| IMP | 001 J04 · 002 J04 · 003 J04 · 004 J05 · 005 J03 · 006 J03 · 007 J03 · 008 J04 · 009 J04,J16 · 010 J01 · 011 J14,J18 (known bug) · 012 J18 · 013 J01 · 014 J04 |
| PRS | 001–005 J04 · 006 J06 · 007 J01,J06 · 008–011 J06 · 012–013 J05 · 014 J04 · 015 J18 · 016 J01,J04,J05,J06 · 017 J01,J05,J06 · 018 J01,J02 · 019–023 J05 · 024 J01,J05 · 025 J18 |
| ERR | 001 J07 · 002 J01 · 003–008 J03 · 009–015 J17 · 016 J13 · 017 J17 |
| PRM | 001–003 J11 · 004 J12 · 005 J11,J17 · 006–008 J12 · 009–011 J11 · 012–013 J12 (013 also J04) · 014–015 J11 |
| ACC | 001–007 J09 · 008–009 J13 · 010 J09 · 011 J13 · 012–015 J09 (014 also J12) · 016 J07 · 017–018 J13 · 019 J08,J09 · 020 J09 · 021 J14 |
| CAT | 001–005 J02 (002 also J01) · 006 J13 · 007–009 J02 · 010–011 J13 · 012 J01 · 013–015 J13 · 016 J13 |
| WRD | 001–002 J07 · 003 J10 · 004–006 J07 · 007 J14 · 008 J07 · 009 J01 · 010 J07 · 011 J14 · 012–014 J07 |
| UPD | 001–006 J15 |
| ALM | 001–002 J10 · 003 J14 · 004–005 J10 (005 also J01) · 006–008 J14 · 009 J10 |
| DAT | 001–006 J16 · 007 J15 |


# Mirrolit — Reader journeys (LAY · APP · POS · NAV · BMK · ILL · SYS)

Composed from the atomic cases in `e2e/e2e_test_cases.md` (148 cases in this area). Each journey is one
realistic reading session; every verifying step carries an inline checkpoint `→ ✓ … (IDs)`. A journey fails
at its first failed checkpoint; record the failing IDs, not the journey alone.

**Automated** journeys (J-RDR-01 … J-RDR-09) are Maestro-able: no real sign-in/purchase, no paid API keys,
no simulated failures, no two-build upgrades. Device preconditions that Maestro cannot set (system font
scale, seeded prefs) are applied via `adb` before the flow starts. **Manual** journeys (J-RDR-10 … J-RDR-20)
need hands-on gestures, visual judgement, two Play builds, paid keys or seeded legacy data.

## Fixtures

| Fixture | What it is | Used by |
|---|---|---|
| F-ALICE | Gutenberg illustrated "Alice's Adventures in Wonderland" (EPUB): cover-only first page, Rackham-style images before chapter headings, one large plate, one tall plate, multi-chapter | 01, 06, 07, 08, 10, 11, 13 |
| F-LONG | Long multi-chapter English EPUB (≥ 20 chapters, plain text, chapter mastheads) | 02, 03, 04, 05, 08, 11, 12, 16 |
| F-SHORT | Short single-chapter English EPUB (≈ 30 paragraphs) | 04 |
| F-RUR | "R.U.R." play, table-laid-out drama (EPUB) | 08 |
| F-IMGONLY | EPUB/FB2 made only of images, no text | 08 |
| F-IMGFILES | EPUB whose some content files contain only images (no text) | 13 |
| F-FB2 / F-MOBI | Any FB2 and any MOBI novel with chapters | 05, 06 |
| F-EMPTY1 | Degenerate EPUB whose first chapter has zero paragraphs | 15 |
| F-LEGACY | App data captured on a pre-v6 build (Play v5) with F-LONG and F-ALICE opened mid-chapter, bookmarks incl. a labelled one, one just past an illustration, two that collapse onto one paragraph; Night theme, Mono font, size 22, split 0.4, illustrations off, margins 12 | 14, 15 |

Default engine for automated journeys: Offline HQ (default since v23) with the EN→RU pack pre-installed, so
translation works without network keys.

---

## Automated journeys

### J-RDR-01 — Tablet: first read of an illustrated classic, side-by-side
**Priority** P0 · **Tags** smoke, full, tablet · **Devices** tablet API 35 (and API 26 in `full`), landscape lock (default) · **Fixtures** F-ALICE · **Preconditions** clean install; translation on, target RU (defaults); "Show illustrations" on; default margins.

1. On the empty Library tap "Open book" and pick F-ALICE (importing a book opens it) → ✓ the reader shows the very start — the first chapter's number "— 1 —" and title — no jump (POS-023, POS-011).
2. Look at the reader → ✓ the navigation rail is hidden inside the reader (NAV-017); no "ML KIT READY" label anywhere, with the bars hidden or shown (LAY-022).
3. Check the first screen → ✓ the pictures placed before and right after the book's first heading (endpapers, frontispiece, title page) render under the first chapter title, none dropped (ILL-011), at full size — one image pixel per dp, not a thumbnail (ILL-006); the 350×500 cover is not repeated as a page of the first chapter (ILL-003).
4. Back to Library without scrolling → ✓ the "Continue reading" card shows the book at "CH 1 OF 14", 0% (the app has no imported-but-never-opened state); the card's passage is the book's opening text — the proem's first lines, "'Tis two score years since Carroll's art…" — not a paragraph from further in. Open the book again from its card → ✓ still the very start, no false "continue reading" jump (POS-011).
4a. Chapter picker → the second chapter ("— 2 —", text starts right under the title) → ✓ no picture stands between the chapter title and "CHAPTER I" — the cover is not repeated as this chapter's first page (ILL-003). Wait for translation → ✓ original left, translation right, one row per paragraph (LAY-001). (LAY-023 — the "Preparing translation…" banner does not cover the chapter number — is checked in J-TRN-01 step 2: this book's first screen is pictures only, so the banner never comes here.)
5. Chapter picker → the first chapter; scroll down → ✓ the frontispiece and the title page each fit on one screen at full size (ILL-011, ILL-006, ILL-007).
6. Scroll on to the first text ("Printed in England") and two paragraphs further → ✓ each original has its translation beside it; the panes scroll together (LAY-001).
7. Chapter picker → the third chapter; scroll to its first picture → ✓ it sits inside the chapter at its anchor, between the 9th and 10th paragraphs (ILL-001), inline as its own row (ILL-015), rendered at full size, not a thumbnail (ILL-006).
8. *Manual:* scroll so the picture / a chapter title sits under the divider handle → ✓ the handle fades out instead of covering it (ILL-008) — the handle has no node Maestro can see.
9. Scroll to the tall plate ("The Pool of Tears") → ✓ it fits within one screen height at full size (ILL-007, ILL-006).
10. With the bars hidden, tap directly on the plate — beside its centre line: the pane divider's strip lies over the middle of a centred picture and would answer the tap itself → ✓ bars appear (ILL-009); the picture is not resized (ILL-007).
11. Tap on plain text → ✓ bars hide — the same toggle as on the picture; tap the text again → ✓ bars appear, tap the plate → ✓ they hide (NAV-003). Each show-then-hide is done within 3 s, so the bars' own 3.5 s timer cannot be what hid them.
12. Tap the plate once more and let the bars leave → ✓ each time the plate keeps its size and the footer percentage leaves and returns with the bars — no stale padding (SYS-012; the exact pixel position is a visual check). Drag sideways from the spot that was tapped → ✓ the split does not move: the taps were on the plate, not on the divider.
13. Open the reader's Display dialog (Aa); move the "Split position" slider from 50 / 50 to about 35 / 65 → ✓ the value changes live (LAY-004).
14. Close the dialog, Back to Library, reopen F-ALICE → ✓ Display still shows the new split (LAY-004).

**Covers:** POS-011, NAV-017, LAY-022, POS-023, ILL-003, LAY-001, ILL-011, ILL-001, ILL-015, ILL-006, ILL-008, ILL-007, ILL-009, NAV-003, SYS-012, LAY-004

### J-RDR-02 — Phone: stacked reading, progress and bookmarks
**Priority** P0 · **Tags** smoke, full, phone · **Devices** phone emulator API 35, portrait (default) · **Fixtures** F-LONG · **Preconditions** clean install; portrait hint never dismissed with "Don't show again"; translation on (default).

1. On the empty Library tap "Open book" and pick F-LONG (importing a book opens it) → ✓ the "parallel reading" hint appears with "Got it" and a "Don't show again" checkbox (APP-026).
2. Tap "Got it" without ticking the box → ✓ the bottom bar is hidden in the reader (NAV-008); original on top, translation below, a horizontal divider between (LAY-002).
3. With the bars hidden, tap empty page space — the margin beside a paragraph, where no text, title or picture is under the finger — in the top pane → ✓ the bars appear; tap the same spot again → ✓ they hide; the same in the bottom pane (LAY-029). (The space beside the chapter title belongs to the title's row and is step 4.) Each show-then-hide is done within 3 s, so the bars' own 3.5 s timer cannot be what hid them.
4. Tap the chapter title itself → ✓ the bars appear; tap it again → ✓ they hide (LAY-030).
5. Tap the page → ✓ the footer shows a percentage on the phone, 0% at the start (POS-008).
6. Scroll forward three paragraphs at a time (the 4th, 7th, 10th paragraph on top), revealing the bar each time → ✓ the percentage grows inside the chapter — 1%, 2%, 3% — not only at chapter boundaries (POS-003).
7. Open the chapter picker; select chapter 3 → ✓ the view lands on chapter 3's start (NAV-009): the chapter title (masthead) is the first row of the top pane — nothing of chapter 2 above it — with its first paragraph under it, and it stays there while the translation arrives (NAV-018); a bookmark made here reads "Chapter 3 — The Long Crossing · ¶ 1" (removed again).
8. Scroll so the 5th paragraph of chapter 3 is the top paragraph; open Bookmarks (the bookmark icon opens the Bookmarks dialog) and tap "Bookmark this page" → ✓ the button turns into "Remove bookmark here" (BMK-001).
9. Look at the list in the same dialog → ✓ the entry is "Chapter 3 — The Long Crossing · ¶ 5" — the real paragraph number, not shifted by the masthead (BMK-003, BMK-001); its preview text is that paragraph's own text — exactly its first 80 characters (BMK-002).
10. Tap "Remove bookmark here" → ✓ the bookmark is removed at once: the list is empty and the button reads "Bookmark this page" (BMK-005); tap it to re-add the bookmark; Close.
11. Open the chapter picker, go to chapter 1; open Bookmarks, tap the chapter-3 bookmark → ✓ one jump lands exactly on that paragraph (the 5th on top, the 4th off screen); Bookmarks shows "Remove bookmark here" for this page (BMK-004).
12. Back to Library, reopen F-LONG → ✓ the hint shows again because the box was not ticked (APP-028).
13. Tick "Don't show again", tap "Got it"; Back; reopen F-LONG → ✓ no hint (APP-029); the book reopened on the bookmarked paragraph (POS-013).

**Covers:** NAV-008, APP-026, LAY-002, LAY-029, LAY-030, POS-008, POS-003, NAV-009, NAV-018, BMK-001, BMK-003, BMK-002, BMK-005, BMK-004, APP-028, APP-029, POS-013

### J-RDR-03 — Rotation and resume: the paragraph never moves
**Priority** P0 · **Tags** smoke, full, rotation, phone, tablet · **Devices** phone emulator API 35 and tablet API 35 (Maestro `setOrientation`) · **Fixtures** F-LONG · **Preconditions** F-LONG has saved progress mid-chapter (the flow sets it up: chapter 8, a bookmark on the top paragraph, then a jump to that bookmark so the paragraph is exactly on top); portrait hint already suppressed.

1. Settings → Orientation → ✓ three options AUTO / LANDSCAPE / PORTRAIT, in English (APP-024).
2. *Phone:* select LANDSCAPE without restarting → ✓ the app turns to landscape immediately — the navigation becomes the side rail instead of the bottom bar (APP-022). (On a tablet Landscape is the default and nothing turns; manual, J-RDR-12.)
3. *Phone:* kill the app and relaunch → ✓ still landscape, not reset to Portrait (APP-023). Select AUTO (both devices).
4. On the Library, tap "Continue reading" for F-LONG → ✓ opens at the saved paragraph, same as reopening from the Library (POS-016).
5. Note the top paragraph; turn the device → ✓ the same paragraph is on top (NAV-001), and the screen did turn (the paragraph has the turned layout's width).
6. Turn it back; repeat three more times → ✓ the same paragraph stays on top every time, never back to the start (NAV-001); on the phone the layout is side-by-side when landscape and stacked when portrait.
6a. Aa → **Show translation** off; turn the device and turn it back → ✓ one original-only column in both orientations, the same paragraph on top; Aa still shows "Show translation" off (LAY-028). Turn it back on.
7. Back to Library; reopen F-LONG → ✓ it reopens on that same paragraph (NAV-002).
8. Chapter picker → chapter 10, so the first visible row is a chapter masthead; Back; reopen → ✓ the position resolves to the first paragraph below the masthead, offset 0: that paragraph is on top, the masthead is above the screen (POS-013).
9. Bookmarks → the chapter 8 bookmark, then scroll a little so the paragraph's first lines are off-screen; Back; reopen → ✓ same paragraph on top (POS-013; the same pixel offset is a visual check — see the screenshots).
10. Scroll partway into another paragraph (chapter picker → chapter 12, then on past its first paragraph) without leaving the reader; kill the app; relaunch; open F-LONG → ✓ same chapter and paragraph after a full restart (POS-013) — a place that only the scrolling has saved: the "Continue reading" card already says chapter 12, and the page that opens is the one bookmarked just before the kill.

**Covers:** APP-024, APP-022, APP-023, POS-016, NAV-001, NAV-002, POS-013, LAY-028

### J-RDR-04 — Reading to the end: 0% to 100% and back
**Priority** P0 · **Tags** smoke, full, phone, tablet · **Devices** tablet API 35 and phone emulator · **Fixtures** F-SHORT, F-LONG · **Preconditions** F-SHORT never opened; F-LONG saved near its end, not yet read to the end, with one bookmark near the end (the flow sets it up: from chapter 21 it scrolls until the book's last paragraph starts to show and bookmarks that page — on the tablet the whole last chapter fits on one screen, so opening chapter 22 from the chapter picker already is the end of the book; the "Continue reading" card must then read 9x%, not 100%). A bookmark on the true last screen would mean the book is already finished, so that one is made in step 8a.

1. Open F-SHORT (importing a book opens it); tap to show the bar → ✓ footer shows 0% (POS-004).
2. Read on until the 10th paragraph is the top one; show the bar → ✓ the percentage has grown to 30% — 9 of 30 paragraphs are behind (POS-004).
3. Back to Library → ✓ the "Continue reading" card shows F-SHORT with the same % as the footer, and so does F-SHORT's shelf card: "30% · CH 1/1", no "✓" badge (POS-004).
4. Open F-LONG; open the chapter picker, go to chapter 1; open Bookmarks and tap the bookmark near the end → ✓ it lands on exactly that paragraph, one scroll: it is the top one, the paragraph before it is off screen; Bookmarks shows "Remove bookmark here" (BMK-004). Remove the bookmark.
5. Scroll to the very last paragraph → ✓ the footer shows 100% (POS-005).
6. Hide and show the bars five times → ✓ the footer is at 100% every time (POS-007).
7. Back to Library → ✓ the "Continue reading" card shows F-LONG at 100%, matching the footer, and so does its shelf card: "100% · CH 22/22" (POS-005) with the "✓" finished badge (POS-019).
8. Reopen F-LONG → ✓ opened at the end (the last paragraph is on screen), still 100% (POS-019).
8a. Bookmark this page — the book's true last screen; chapter picker → chapter 1; Bookmarks → that bookmark → ✓ one jump ends on the last screen again (the book's last paragraph is on screen) and Bookmarks shows "Remove bookmark here" (BMK-004, the "very last screen" case).
9. Scroll back up two screens; show the bar → ✓ the real, lower percentage is shown, not stuck at 100% (POS-006).

**Covers:** POS-004, BMK-004, POS-005, POS-007, POS-019, POS-006

### J-RDR-05 — Appearance smoke: margins and one shared theme
**Priority** P0 · **Tags** smoke, full, phone, tablet · **Devices** tablet API 35 (`Tab_35_PixelTablet`) and phone emulator (`Pixel_10_Pro`) · **Fixtures** F-LONG · **Preconditions** clean install (`pm clear`); Paper theme.

"The screen is in theme X" is checked exactly: the flow takes a small crop of each screen and `e2e/run.sh` then requires its background to be precisely that theme's colour from the app's palette (`e2e/check_colours.py`; the row "J-RDR-05 colours" of the summary). Paper is told from Sepia and Night from AMOLED. *Manual:* which swatch the Theme row shows as selected — the selection is drawn as a border only and Maestro cannot read it.

1. Settings → Reading → ✓ "Page margins" shows 32dp (APP-001); the screen is Paper.
2. Open F-LONG (importing a book opens it) → ✓ the text edge sits 32dp in from the screen edge — asserted through the paragraph's width (APP-001); the page is Paper.
3. The reader's Display dialog has no "Page margins" slider (the margin is set only in Settings). Settings → Page margins to 12dp, reopen F-LONG → ✓ the text edge moved outward; then to 40dp, reopen → ✓ it moved inward (APP-001).
4. In the reader's Display dialog select Night → ✓ the dialog and the page turn to Night at once (APP-007).
5. Kill and relaunch; reopen F-LONG → ✓ still Night (APP-007).
6. Back to Library, then Almanac, then Words → ✓ all three are Night (APP-008).
7. Settings → Theme → select Sepia; reopen F-LONG and its Display dialog → ✓ the page and the dialog are Sepia, no restart needed (APP-007). *Manual:* the dialog shows the Sepia swatch selected.
8. In the Display dialog select Paper → ✓ the dialog is Paper; go to Settings → ✓ Settings is Paper (APP-007). *Manual:* Settings shows the Paper swatch selected.
9. Select Night from Settings; open Library → ✓ the Library is Night, same result as from the reader (APP-008).

**Covers:** APP-001, APP-007, APP-008

### J-RDR-06 — Tablet: tuning typography, layout combos and persistence
**Priority** P1 · **Tags** full, tablet · **Devices** tablet API 35, landscape · **Fixtures** F-ALICE, F-FB2 · **Preconditions** translation on; default appearance.

1. Open F-FB2; open the Display dialog; drag Text size clearly larger → ✓ text resizes live, no slider jump (APP-010, APP-006).
2. Drag Line height up → ✓ line spacing changes live (APP-011).
3. Drag Letter spacing up → ✓ character spacing changes live (APP-012).
4. Drag Paragraph spacing/indent → ✓ spacing between paragraphs changes live (APP-013).
5. Toggle "Justify text" on → ✓ right edges align; toggle off → ✓ ragged-right returns (APP-014).
6. Pick a different reading font → ✓ the reader's typeface changes (APP-009).
7. Set the split slider to 35/65 → ✓ panes resize.
8. Go to Settings → ✓ the same font is selected and "Justify text" matches the reader (APP-009, APP-014).
9. Kill and relaunch; reopen F-FB2 → ✓ size, line height, letter spacing, paragraph spacing, font, alignment and split 0.35 are all kept (APP-006, APP-010, APP-011, APP-012, APP-013, LAY-006).
10. Set margins 4dp, then 48dp → ✓ the left edge of the original and the right edge of the translation move; divider gutter stays 12dp; nothing clips (APP-003).
11. Combine margins 48dp, font size 30, letter spacing 2, split 30/70 → ✓ nothing clips at this extreme (APP-003).
12. Set margins 12dp; open F-ALICE at a chapter with a heading and a plate → ✓ the picture and chapter title align flush with the body text margin (ILL-005).
13. Set margins 40dp; check the same chapter → ✓ still flush with the text (ILL-005).

**Covers:** APP-010, APP-006, APP-011, APP-012, APP-013, APP-014, APP-009, LAY-006, APP-003, ILL-005

### J-RDR-07 — Phone: tuning the stacked reader at large font scale
**Priority** P1 · **Tags** full, phone · **Devices** phone emulator API 35, portrait · **Fixtures** F-ALICE, F-MOBI · **Preconditions** `adb shell settings put system font_scale 1.3` (Largest); portrait hint suppressed; translation on.

1. Open F-ALICE → ✓ the top-bar language button reads the full "EN → RU", not clipped (LAY-021).
2. Open the Display dialog → ✓ a separate "Vertical split" slider is shown in stacked mode (LAY-012).
3. Note the Split ratio (horizontal) value; set Vertical split to 0.35 → ✓ the top pane shrinks; the horizontal split value is unchanged (LAY-012).
4. Set margins to 4dp, then 48dp → ✓ the outer edge of both panes moves; nothing clipped at either extreme (APP-002).
5. Scroll to the first plate → ✓ it renders in the top panel and a placeholder sits at the same index in the bottom panel.
6. In the Display dialog turn "Show illustrations" off → ✓ the picture disappears from both panels, paragraphs reflow (ILL-016).
7. Turn it back on → ✓ the picture returns in both panels (ILL-016).
8. Kill and relaunch; reopen F-ALICE → ✓ the vertical split is still 0.35 (LAY-013).
9. Toggle "Show translation" off in the Display dialog; open F-MOBI → ✓ a single original-only pane (LAY-010).
10. Change Vertical split and Split ratio in the dialog → ✓ the single-pane view is unaffected (LAY-010). Turn "Show translation" back on; reset `font_scale` to 1.0.

**Covers:** LAY-021, LAY-012, APP-002, ILL-016, LAY-013, LAY-010

### J-RDR-08 — Tablet: unusual books and jumps while translation is loading
**Priority** P1 · **Tags** full, tablet · **Devices** tablet API 35, landscape · **Fixtures** F-RUR, F-IMGONLY, F-LONG · **Preconditions** F-RUR and F-IMGONLY not yet imported; F-LONG has a bookmark in its last chapter, current position at the start, translation cache cleared.

1. Import F-IMGONLY via Library → Import; open it → ✓ it opens (no failure, no blank) and the pictures are shown (ILL-004).
2. Import F-RUR; open it; open the chapter picker and pick Act I → ✓ the view lands on Act I's start (NAV-009); the play renders line by line, not as a table.
3. Bookmark a speech line in the middle of the act → ✓ icon on.
4. Back to Library; reopen F-RUR from its start (chapter picker → chapter 0); open Bookmarks, tap the bookmark → ✓ lands on the same line (BMK-007).
5. Open F-LONG (at the start, translation not cached); open Bookmarks immediately and tap the last-chapter bookmark while the right pane still shows "translating" → ✓ the view lands precisely on the bookmarked paragraph (POS-001, BMK-004).
6. Wait for translation → ✓ the paragraph stays on top; the bookmark icon shows bookmarked (POS-001, BMK-004).

**Covers:** ILL-004, NAV-009, BMK-007, POS-001, BMK-004

### J-RDR-09 — App chrome and the Words screen on phone and tablet
**Priority** P0 · **Tags** smoke, full, phone, tablet · **Devices** phone emulator (compact) and tablet API 35 · **Fixtures** F-LONG · **Preconditions** fresh install (phone: portrait default); signed out. The three saved words are made in the flow, inside step 4 / 11 (a long press on a word in the reader → the popup's "Save").

1. Launch on the phone → ✓ a bottom bar with the 5 items Library, Catalog, Almanac, Words, Settings, in that order (NAV-005).
2. Tap each bottom-bar item in turn, then system Back → ✓ each opens its destination; Back returns to the Library, as on the tablet (NAV-006). In the Catalog every source pill is whole on screen, "Google Drive" on a second line (CAT-017; v29).
3. Tap the avatar ("?") in the top status strip → ✓ the sign-in screen opens (signed out) (NAV-007).
4. Back; open F-LONG → ✓ the bottom bar is hidden in the reader (NAV-017). Save three words; Back.
5. Open Words; tap a saved word → ✓ the list is replaced full-screen by its detail (NAV-010).
6. Perform the system back gesture → ✓ the list returns; the app/screen is not exited (NAV-011).
7. Tap another word; tap the back arrow in the detail header → ✓ the list returns (NAV-012).
8. Tap a third word; tap "Delete" in the detail pane and confirm in the "Delete word" dialog → ✓ the list returns without that word (NAV-014).
9. On the tablet, tap Catalog/Almanac/Words/Settings on the rail, then system Back after each → ✓ the same destinations as on the phone, and Back returns to the Library (NAV-006).
10. On the tablet, open Words and select a word → ✓ master list and detail are side by side, no back arrow (NAV-013). (Runs after step 11, which makes the saved words.)
11. On the tablet, open F-LONG → ✓ the rail is hidden in the reader (NAV-017). Save three words; Back.

**Covers:** NAV-005, NAV-006, NAV-007, NAV-017, NAV-010, NAV-011, NAV-012, NAV-014, NAV-013, CAT-017

---

## Manual journeys

### J-RDR-10 — Tablet: working the divider by hand, then in split-screen
**Priority** P1 · **Tags** manual, tablet · **Devices** real tablet with split-screen support (landscape) · **Fixtures** F-ALICE · **Preconditions** translation on; split 50/50; "Show illustrations" on.

1. Open F-ALICE mid-chapter. Tap (no movement) on the grip strip over the divider → ✓ bars toggle; the split ratio is unchanged (LAY-008).
2. Press the grip strip and drag vertically → ✓ the list scrolls; the divider stays put (LAY-009).
3. Press and hold the grip strip; drag left slowly, then release → ✓ the divider follows live; on release the panes settle at the new ratio once, no jump back (LAY-003).
4. Drag the divider hard to the far left, then to the far right → ✓ the ratio stops at 0.3 / 0.7; no pane collapses (LAY-005).
5. Open the Display dialog; drag the Split ratio slider to both ends → ✓ same 0.3 / 0.7 bounds (LAY-005).
6. In the dialog drag Text size, Line height and Page margins fully to each end → ✓ each stops at its documented min/max, the shown number matches; no way past the range (APP-015).
7. Start dragging the grip, then put a second finger down (or pull down the notification shade) to cancel → ✓ the dragged ratio is kept after the cancel (LAY-007).
8. Note where a plate sits (chapter, which paragraph follows it). Enter split-screen with another app and shrink Mirrolit below half width (<600dp) → ✓ the layout switches to stacked live while the device stays landscape (LAY-024).
9. Find the same plate → ✓ it is at the same chapter/anchor position as in side-by-side (ILL-017).
10. Widen Mirrolit back past 600dp → ✓ side-by-side returns live (LAY-024).
11. Shrink the window to the narrowest split-screen size; with side-by-side still possible (e.g. in a freeform window), drag the divider to an extreme → ✓ no crash, no blank/NaN layout; the ratio clamps (LAY-027).

**Covers:** LAY-008, LAY-009, LAY-003, LAY-005, APP-015, LAY-007, LAY-024, ILL-017, LAY-027

### J-RDR-11 — Phone: hands-on stacked reading, words and rotation
**Priority** P0 · **Tags** manual, phone, rotation · **Devices** real phone (Pixel 7), Orientation = Auto · **Fixtures** F-ALICE, F-LONG · **Preconditions** translation on; portrait hint not suppressed; a TTS voice installed.

1. Open F-LONG in portrait; dismiss the hint with "Got it" (box unticked).
2. Rotate portrait→landscape→portrait without leaving the reader → ✓ the hint does not reappear (APP-027); layout switches stacked→side-by-side→stacked with panels aligned both ways (NAV-004).
3. Drag the horizontal divider row down, release → ✓ the top/bottom heights follow the finger and stay after release (LAY-011).
4. Drag it fully up, then fully down → ✓ ratio stops at 0.3 / 0.7 (LAY-014).
5. Long-press a word in the top (original) panel → ✓ selection appears; Save adds it to Words; the TTS action speaks it, same as in landscape (LAY-016).
6. Long-press a word in the bottom (translation) panel → ✓ no selection/lookup; a tap there toggles the bars (LAY-015).
7. Fling the top panel → ✓ the bottom follows, paragraph boundaries line up; drag the bottom panel → ✓ the top follows (LAY-017).
8. Alternate quickly: scroll top, let it settle, scroll bottom, repeat ×5 → ✓ no oscillation or runaway scroll (LAY-018).
9. Start a fast fling on the top panel and grab the bottom panel mid-deceleration → ✓ the bottom takes over smoothly, no desync or crash (LAY-019).
10. While both panels are mid-sync, rotate twice in quick succession → ✓ no crash; sync re-establishes (LAY-026).
11. Open F-ALICE; scroll to a plate → ✓ the top panel shows the picture; the bottom panel shows a slim placeholder at the same index (LAY-020).
12. Scroll past 3+ plates in a row → ✓ original and translation stay paragraph-for-paragraph aligned, no cumulative drift (ILL-013).

**Covers:** APP-027, NAV-004, LAY-011, LAY-014, LAY-016, LAY-015, LAY-017, LAY-018, LAY-019, LAY-026, LAY-020, ILL-013

### J-RDR-12 — Orientation lock and first-run defaults on phone and tablet
**Priority** P0 · **Tags** manual, phone, tablet, rotation · **Devices** real phone and real tablet, auto-rotate on in Android quick settings · **Fixtures** F-LONG · **Preconditions** app uninstalled; `adb` available.

1. Fresh-install on the phone and launch → ✓ Settings → Orientation shows Portrait; rotating the phone keeps portrait (APP-019).
2. Fresh-install on the tablet and launch → ✓ Orientation shows Landscape; the tablet stays landscape (APP-020).
3. On the phone, Settings → Orientation → Auto; rotate through all four directions → ✓ the app follows freely (APP-021).
4. Select Landscape; try every direction incl. upside-down → ✓ stays landscape (APP-021). Select Portrait; repeat → ✓ stays portrait (APP-021).
5. With Portrait selected, open F-LONG and rotate the phone → ✓ the reader stays portrait (APP-025). Repeat with Landscape (stays landscape) and Auto (follows rotation) → ✓ (APP-025).
6. Open Words, select a word; rotate (Auto) → ✓ the same word is still selected; in the now-wide layout it shows side by side (NAV-015).
7. Close the app; seed an invalid orientation pref (`run-as` edit of the prefs file, value `SIDEWAYS`); launch → ✓ no crash; phone shows Portrait, tablet shows Landscape — not Auto (APP-030).
8. Seed the legacy theme value `DEFAULT`; launch and open F-LONG and Settings → ✓ the theme shows "Paper", no crash or unknown-theme state (APP-018).

**Covers:** APP-019, APP-020, APP-021, APP-025, NAV-015, APP-030, APP-018

### J-RDR-13 — Illustrated book: toggles, language switch and session count keep the place
**Priority** P0 · **Tags** manual, network · **Devices** tablet (landscape) or phone · **Fixtures** F-ALICE, F-IMGFILES · **Preconditions** network on (second language pack/model download); F-ALICE opened once, "Show illustrations" on; translation on, EN→RU.

1. Import F-IMGFILES; open the chapter picker → ✓ image-only content files are not separate chapters; their pictures open at the start of the next chapter (ILL-012).
2. Open F-ALICE from the Library; note the session start (Almanac → today's session, or reading stats).
3. Scroll forward past several plates to a paragraph that follows 2+ plates in chapter 2 → note the top paragraph.
4. Bookmark the paragraph right after a plate → ✓ icon on.
5. Display dialog → "Show illustrations" off, then on → ✓ the same paragraph stays on top (POS-015); the bookmark is still on that paragraph, icon still on (BMK-010).
6. Back to Library; reopen → ✓ opens on the same paragraph (POS-015).
7. Change the target language EN→RU to EN→DE from the top-bar language button → ✓ retranslation starts at the visible paragraph, not N items earlier/later where N = plates above it (POS-014).
8. Back; check the session's "paragraphs read" → ✓ equals the forward distance moved since step 2, ≥ 0 (POS-017).
9. Reopen; read forward ~5 paragraphs; open Bookmarks and jump to a bookmark in chapter 1 (backwards); Back → ✓ the session's "paragraphs read" is 0 or positive, never negative (POS-018).
10. Place a bookmark directly on a plate (top row = the picture); go to chapter 1; open Bookmarks and tap it → ✓ lands on the plate itself, not one item off, while the image decodes (ILL-014).

**Covers:** ILL-012, POS-015, BMK-010, POS-014, POS-017, POS-018, ILL-014

### J-RDR-14 — Upgrade in place from a pre-v6 build: look, position and bookmarks carry over
**Priority** P0 · **Tags** manual · **Devices** real phone or tablet · **Fixtures** F-LEGACY; a second legacy profile where the margins slider was never touched · **Preconditions** Play v5 installed with F-LEGACY data; the current build ready to install over it (`adb install -r`).

1. On v5 note F-LONG's top paragraph and F-ALICE's top paragraph and all bookmark labels/dates.
2. Install the current build over v5; launch → ✓ Night theme, Mono font, size 22, split 0.4 and illustrations off are unchanged (APP-005).
3. Open F-LONG → ✓ the text edge is now 12dp from the screen edge (the previously inert value) (APP-005); it opens on the same paragraph as on v5 (POS-002, POS-020).
4. Open Bookmarks → ✓ the labelled bookmark keeps its label and creation time (BMK-009); the two bookmarks that collapse to one paragraph appear once, the earlier one kept (BMK-008).
5. Tap each old bookmark → ✓ each lands on the same paragraph as on v5 (BMK-006).
6. Back; reopen F-LONG twice more → ✓ the position does not drift on the second/third open (POS-021).
7. Open F-ALICE; turn illustrations on → ✓ chapter numbers match v5 and the saved position is the same paragraph (ILL-002).
8. On the second legacy profile (margins never touched) install over v5 and open a book without touching the slider → ✓ the default 32dp margin reproduces the v5 look (APP-004).

**Covers:** APP-005, POS-002, POS-020, BMK-009, BMK-008, BMK-006, POS-021, ILL-002, APP-004

### J-RDR-15 — Legacy migration edge cases: rotations, kills and restores
**Priority** P1 · **Tags** manual, rotation · **Devices** real phone (Auto orientation); dev/test build for step 7 · **Fixtures** F-LEGACY (fresh copy per step group), F-EMPTY1 · **Preconditions** v5 data as in J-RDR-14, books not yet opened on the new build.

1. Install the current build over v5. Open F-ALICE; on its bookmark just past an illustration check the Bookmarks list → ✓ preview text and "¶ n" are right (POS-009); tap it → ✓ one scroll, no double-scroll (POS-009).
2. Restore fresh v5 data; open F-LONG and rotate immediately during the first load, ×3 → ✓ no crash; bookmarks appear once each, not duplicated or shifted (POS-010).
3. Restore fresh v5 data; open F-LONG and swipe the app away from Recents within 2 s; relaunch and open F-LONG → ✓ position and bookmarks intact and consistent (POS-012).
4. Restore v5 data saved with illustrations shown; on the new build turn "Show illustrations" off before first opening F-ALICE → ✓ opens without a crash; position is correct or off by at most the plate count above it (accepted limitation) (ILL-018).
5. Import F-EMPTY1 and open it → ✓ no crash; it opens at (chapter, 0, 0) (POS-024).
6. Restore an Android backup made before the migration (or clear the migrated flag keeping the legacy keys via `run-as`); open the affected book → ✓ migration runs again, no crash, same converted position as the first time (POS-025).
7. Dev build only: trigger migration and kill the process between the position write and the bookmarks write (debug hook); relaunch and open the book → ✓ position migrated and stable; bookmarks not migrated twice (POS-022).

**Covers:** POS-009, POS-010, POS-012, ILL-018, POS-024, POS-025, POS-022

### J-RDR-16 — Phone: system bars, themes and the keyboard on Android 15
**Priority** P0 · **Tags** manual, phone · **Devices** real phone on Android 15 (API 35), gesture navigation first · **Fixtures** F-LONG · **Preconditions** Paper theme; a few saved words.

1. Launch on Home/Library → ✓ edge-to-edge, nothing under the system bars (SYS-001); the app's status strip sits below the clock/icons (SYS-003).
2. Open F-LONG → ✓ the first line is clear of the status bar (SYS-005); status-bar icons are dark and legible on Paper (SYS-002).
3. Display dialog → Sepia → ✓ icons stay dark (SYS-002). Night → ✓ icons turn light immediately, no restart (SYS-002). AMOLED → ✓ light (SYS-002).
4. Tap to show the footer → ✓ its labels/icons sit above the gesture bar; its background reaches the screen edge (SYS-006).
5. Switch Android to 3-button navigation; scroll to the last line of a chapter; show the footer → ✓ same: content above the buttons, background to the edge (SYS-006).
6. Settings → ✓ edge-to-edge, top and bottom content not obscured (SYS-001).
7. Open Words; tap the search field → ✓ the field stays visible above the keyboard; the bottom bar does not float over the IME (NAV-016).
8. Open Catalog; tap its search field → ✓ same (NAV-016).

**Covers:** SYS-001, SYS-003, SYS-005, SYS-002, SYS-006, NAV-016

### J-RDR-17 — Tablet: rail and taskbar insets across the API matrix
**Priority** P0 · **Tags** manual, tablet · **Devices** real tablet with persistent taskbar (Pixel Tablet / Lenovo / Samsung); tablet AVDs API 26/30/33/35/37 (run after `gradle --stop`); a device or AVD with a display cutout near the rail · **Fixtures** F-LONG · **Preconditions** signed out; taskbar pinned visible.

1. Launch on the real tablet → ✓ the Home/Library status strip sits below the system status bar (SYS-004).
2. Look at the bottom of the rail → ✓ the account button / avatar / "Sign in" is fully visible above the taskbar (SYS-007).
3. Scroll Library and Settings to the very bottom → ✓ the last items stay above the taskbar (SYS-007).
4. On the cutout device, look at the rail → ✓ its background reaches the screen edge with no gap; icons are clear of the cutout (SYS-009).
5. Enter split-screen so the window height is ~450–528dp (icon-only rail) → ✓ the bottom-most rail icon is fully inside the rail, not clipped (SYS-010).
6. Repeat steps 1–3 on each AVD API 26, 30, 33, 35, 37 with the taskbar visible → ✓ no rail/menu content is under the taskbar on any API level (SYS-008).

**Covers:** SYS-004, SYS-007, SYS-009, SYS-010, SYS-008

### J-RDR-18 — Paid engine: hiding the translation stops metered requests
**Priority** P0 · **Tags** manual, network · **Devices** any · **Fixtures** F-LONG · **Preconditions** a paid online provider configured with a real key and its usage counter visible (Settings → translator → usage, or the provider dashboard); not using Offline HQ.

1. Open F-LONG mid-chapter → ✓ translations arrive; note the usage counter.
2. Display dialog → "Show translation" off → ✓ single pane.
3. Scroll through 5 new screens; check the counter → ✓ it has not grown (APP-016).
4. "Show translation" on → ✓ the current screen gets translated; the counter grows again (APP-016).
5. Back; Settings → "Show translation" off; note the counter; open another book → ✓ single pane; counter unchanged after scrolling a few screens (APP-017).
6. Restore "Show translation" on.

**Covers:** APP-016, APP-017

### J-RDR-19 — Release visual regression pass (goldens)
**Priority** P1 · **Tags** manual · **Devices** build machine + tablet emulator · **Fixtures** golden fixtures; an RTL book · **Preconditions** release candidate branch.

1. Run the full screenshot/golden suite → ✓ all goldens pixel-identical, incl. those with AppShell test tags (SYS-013).
2. Review the 8 landscape reader goldens: Paper/Night × 1x/1.3x × LTR/RTL book, split 0.35 → ✓ identical to the pre-stacked-reader baseline (LAY-025).
3. On the tablet emulator, open the RTL book in landscape at split 0.35 in Night at 1.3x → ✓ matches the golden by eye (LAY-025).

**Covers:** SYS-013, LAY-025

---

## Known bugs

Expected to fail until fixed; report, never block. Run as one probing session.

### J-RDR-20 — Known-bug probe: bookmarks, margins, rail, re-added books
**Priority** P2 · **Tags** manual, known-bug, tablet · **Devices** real tablet with gesture navigation · **Fixtures** F-LONG, F-ALICE · **Preconditions** F-LONG marked "Reading" with progress.

1. Open F-LONG; open the chapter picker and pick the chapter already on screen (no scroll happens); tap the bookmark icon → expected: the bookmark is placed on the jump target; known: it may act on a stale target (BMK-011).
2. On a paragraph without a bookmark, double-tap the bookmark icon very fast; open Bookmarks → expected: one bookmark; known: may show two (BMK-012).
3. Scroll to the very end and watch the bookmark icon as the list settles → expected: no flicker; known: it flashes briefly (BMK-013).
4. Set Page margins to 4dp, open F-ALICE at a plate; then 48dp → expected: plates align with the text column; known: plates keep their own 32dp inner padding; centered headings are fine (ILL-010).
5. Look at the bottom of the rail (narrow or full rail branch) → expected: a gap above the gesture bar; known: the avatar can touch the gesture bar (SYS-011).
6. Delete F-LONG from the Library; import it again → expected (by design today): it still shows "Reading" and the old % — document if it resets (BMK-014).

**Covers:** BMK-011, BMK-012, BMK-013, ILL-010, SYS-011, BMK-014

---

## Coverage summary

| Journey | Kind | Priority | IDs |
|---|---|---|---|
| J-RDR-01 | smoke · tablet | P0 | 16 |
| J-RDR-02 | smoke · phone | P0 | 17 |
| J-RDR-03 | smoke · rotation | P0 | 8 |
| J-RDR-04 | smoke | P0 | 6 |
| J-RDR-05 | smoke | P0 | 3 |
| J-RDR-06 | full · tablet | P1 | 10 |
| J-RDR-07 | full · phone | P1 | 6 |
| J-RDR-08 | full · tablet | P1 | 5 |
| J-RDR-09 | smoke · phone + tablet | P0 | 10 |
| J-RDR-10 | manual · tablet | P1 | 9 |
| J-RDR-11 | manual · phone | P0 | 12 |
| J-RDR-12 | manual · orientation | P0 | 7 |
| J-RDR-13 | manual · network | P0 | 7 |
| J-RDR-14 | manual · upgrade | P0 | 9 |
| J-RDR-15 | manual · migration | P1 | 7 |
| J-RDR-16 | manual · phone insets | P0 | 6 |
| J-RDR-17 | manual · tablet insets | P0 | 5 |
| J-RDR-18 | manual · network · paid | P0 | 2 |
| J-RDR-19 | manual · goldens | P1 | 2 |
| J-RDR-20 | manual · known-bug | P2 | 6 |

Notes: APP-016/APP-017 are tagged `smoke`/`full` in the catalog but need a paid key, so they run in manual
J-RDR-18. BMK-014 is tagged `full` but is a known bug, so it sits in J-RDR-20. ILL-002 is tagged `full` but
needs a two-build upgrade, so it runs in J-RDR-14.


# Mirrolit — E2E journeys: translation, Offline HQ, word popup, online engines, ML Kit, language detection, settings

Composed from the atomic cases in `e2e_test_cases.md` (prefixes TRN, OHQ, POP, ONL, MLK, LNG, SET — 137 cases).
Each journey is one realistic reading session; every verifying step carries a checkpoint (✓) with the atomic
IDs it proves. A journey's priority is the highest priority among its checkpoints. Cross-area IDs (WRD, BMK, …)
are cited where a step proves them too, but coverage is only claimed for this area's prefixes.

**Automated** journeys (J-TRN-01 … J-TRN-10) are Maestro-able: real network allowed; no paid API keys (a dummy
key string such as `invalid-key-000` is fine, since it costs nothing), no simulated failures (airplane mode,
proxies, storage filling), no 32-bit device. **Manual** journeys (J-TRN-11 … J-TRN-16) need real paid keys,
failure injection, two Play builds, special devices, or human judgement.

## Shared fixtures

| Fixture | Content | Used by |
|---|---|---|
| `en-novel.epub` | English novel; chapter 1 has 40+ paragraphs, chapter 3 is 3+ screens long, one bookmarkable later chapter | J-01, J-02, J-03, J-07, J-09, J-10, J-11, J-12, J-13 |
| `mixed-lang.epub` | English main text with embedded paragraphs (each ≥ 60 chars unless noted): a German paragraph, a French letter, a Latin epigraph, a Russian paragraph, a short (< 20 chars) Chinese quote, a short Japanese line, and a one-line (< 60 chars) Latin fragment | J-04, J-10, J-16 |
| `ja-poems.epub` | A collection of short Japanese poems (160 lines of 10–13 characters) with a long English preface | J-05 |
| `ja-novel.epub` / `zh-novel.epub` | Long Japanese chapter (multi-sentence paragraphs) / long Chinese chapter (2,000+ chars) | J-05 |
| `fi-book.epub` | Finnish book (untranslatable language) | J-06 |
| `rur-cs.epub` | Čapek, R.U.R. (Czech) | J-06 |
| `don-quixote-es.epub` / `rashomon-ja.epub` | Books whose opening lines mislead language detection | J-06 |
| `ru-book.epub` | Russian book (non-English source for pivot pairs) | J-09, J-13, J-16 |
| `de-book.epub` / `es-book.epub` | German / Spanish books for the detection corpus | J-06 |
| `long-chapter.epub` | One chapter with 500+ paragraphs | J-10 |
| `empty-chapter.epub`, `empty-book.epub`, `pictures-only.epub` | Pathological books | J-10 |
| `no-book.epub` | Norwegian book | J-16 |
| `und-book.epub` | Text whose language can't be detected (e.g. numbers/symbols only) | J-06 |

Default device setup unless a journey says otherwise: arm64 device, app target language **Russian** (language
button reads "EN → RU" on an English book), translation pane visible.

**Request counting (POP-004/005/006/007).** Maestro checks "exactly one request" by counting the selection
translator's debug log line in `adb logcat` between drag start and settle. The debug build has no such line
(`SelectionTranslator.kt` logs nothing, checked 2026-11-15), so that checkpoint is observed manually (one
Translating → Ready transition, no intermediate results) until one is added; the flows assert the end state only.

## Journey index

| ID | Title | P | Kind |
|---|---|---|---|
| J-TRN-01 | First run: Offline HQ by default on an English book | P0 | automated · smoke |
| J-TRN-02 | Word popup: select, drag, save, and the Words list | P0 | automated · smoke |
| J-TRN-03 | Changing the target language mid-read; packs and engine switches | P0 | automated · smoke |
| J-TRN-04 | A mixed-language book: every passage in its own language | P0 | automated · smoke |
| J-TRN-05 | Chinese and Japanese: books, sentences, both on-device engines | P0 | automated · smoke |
| J-TRN-06 | Book language detection and the "From" override | P0 | automated · smoke |
| J-TRN-07 | Online-engine keys without spending money: errors, Switch translator, clearing | P0 | automated · smoke |
| J-TRN-08 | Azure and Quick Translate fields and gating, no LibreTranslate | P0 | automated · smoke |
| J-TRN-09 | From ML Kit to Offline HQ: empty packs, cache, unsupported pair, a Russian book into German | P1 | automated · full |
| J-TRN-10 | Jumping around: bookmarks, huge chapters, other books, odd books | P1 | automated · full |
| J-TRN-11 | A DeepL reader: real key, usage, popup save, hidden pane | P0 | manual |
| J-TRN-12 | Paid chapter fill and switching between paid engines | P1 | manual |
| J-TRN-13 | Reading on a bad connection | P1 | manual |
| J-TRN-14 | Broken packs, full storage, stalled server, broken native library | P1 | manual |
| J-TRN-15 | Upgrades, restores, 32-bit devices and the security audit | P1 | manual |
| J-TRN-16 | Real-device feel: first ML Kit download, gestures, speech, edge languages | P2 | manual |

---

# Automated journeys

### J-TRN-01 — First run: Offline HQ by default on an English book

**Priority:** P0 · **Tags:** smoke, full, network, phone, tablet · **Devices:** tablet API 35, phone emulator (arm64/x86_64)
**Fixtures:** `en-novel.epub`
**Preconditions:** Fresh install (or cleared app data); no translator ever chosen; no Offline HQ packs on the device; target language Russian; online.

**Steps**
1. Launch the app (there is no onboarding: the Library is the first screen) and import `en-novel.epub` from the Library; importing a book opens it. (On a phone the "Parallel reading fits landscape better" hint opens over the book; the flow closes it at once with one point tap on GOT IT, because it hides the banner of step 2, and ticks "Don't show again" when the hint comes back on the next opening of the book, in step 7.)
2. The book opens. → ✓ language button reads "EN → RU"; the banner reads "Preparing translation… N%" and N only grows (the popup, not the banner, says "Downloading language model… N%"); there is no "ML KIT READY" label in the top-right corner or anywhere else (OHQ-001, OHQ-004); while the banner is up it does not cover the chapter number "— 1 —" (LAY-023, a colour crop). On a phone this is watched in portrait, the two panes stacked — the first-run layout. With the bars up there is no "… — translated by ML Kit" strip: Offline HQ itself served.
3. Wait for the download to finish without touching the screen. → ✓ every paragraph on the first screen gets a translation, not only the top one; the banner shows "Translating…" and then goes idle (TRN-001, OHQ-004). (The "Translating…" line is asserted on the tablet; on the phone it is up for well under a second and is only logged.)
4. Open the Translator picker from the reader. → ✓ Offline HQ is the current translator (its card is the inverted one — checked by colour); the list shows ML Kit and Offline HQ ready with no key line, Quick Translate ready (online, no key), and DeepL, Google Cloud and Azure each with a key button ("TAP TO ADD API KEY") (OHQ-001, SET-002). No LibreTranslate card (removed in versionCode 29) is checked by J-TRN-08 step 1.
5. In the picker, look at the ML Kit, Offline HQ and Quick Translate rows. → ✓ none has a usage bar or counter (ONL-016).
6. Tap Offline HQ (already current). → ✓ no key field or "Configure" prompt opens; the picker closes and the reader keeps translating (OHQ-024).
7. Back in the reader, wait 10 s without scrolling, then scroll paragraph by paragraph to the end of chapter 1. → ✓ paragraphs further down already show translations when they come into view (TRN-005). Automated as: after the 10 s the translation cache (Settings → "Cached translations: N", read after an app restart) holds the first screen's paragraphs plus the 8 below it, and every third paragraph down to the chapter's last has its translation within 3 s of being scrolled to; "no Translating delay" as the eye sees it is manual. On the way the 12th paragraph is checked to be in Offline HQ's wording ("паромник"; ML Kit says "паромчик").
8. Open Aa (Appearance) and turn **Show translation** off. → ✓ the reader shows one column of original text.
9. Scroll forward three screens, then turn **Show translation** back on. → ✓ the screens just scrolled through already have translations; Offline HQ is free, so it was not paused (OHQ-025). Automated as: the cache count read before and after the three hidden screens grows.
10. Turn **Show translation** off again, then long-press a word in the original text. → ✓ the popup opens with the word and its Russian translation (POP-002, POP-001); the "Translating…" line in between lasts a fraction of a second (manual).
11. Tap Save. → ✓ toast "Saved to dictionary" (POP-001, WRD-001).
12. Turn **Show translation** back on and close the book. Kill and relaunch the app, reopen the book. → ✓ Settings → Language packs lists EN → RU; no new download banner; translations appear at once; Offline HQ is still current and no "translated by ML Kit" strip shows (OHQ-001, OHQ-004).

**Covers:** OHQ-001, OHQ-004, OHQ-024, OHQ-025, TRN-001, TRN-005, POP-001, POP-002, SET-002, SET-018, ONL-016, LAY-023 (also WRD-001)

### J-TRN-02 — Word popup: select, drag, save, and the Words list

**Priority:** P0 · **Tags:** smoke, full, network, phone, tablet · **Devices:** tablet API 35, phone emulator
**Fixtures:** `en-novel.epub`
**Preconditions:** Offline HQ current; the EN→RU pack installed (the flow starts from a clean install and imports the book, which installs it); Words list empty; online.

**Steps**
1. Open Words from the home screen. → ✓ empty-state hint "Long-press any word in the reader to save it to your words." (WRD-009)
2. Open `en-novel.epub`; long-press the word "river" (any single word). → ✓ the popup opens on that word and shows its translation (POP-016 Ready). With the pack installed the Translating state lasts a fraction of a second, so "while it shows Translating, Save does nothing when tapped" (POP-010) is done in J-TRN-03 step 13, inside a pack download. **Manual:** the plain "Translating…" line (POP-016).
3. Touch the end handle (a new touch that starts on the handle, after the long press was released — `ReaderPane.kt`, `ParagraphItem`; the handles are touch zones under the first and last selected letter, which are underlined) and start dragging it to the right. → ✓ the popup shows a muted "Release to translate" line and no spinner or result while the finger is down (POP-003, POP-016). **Manual:** nothing can look at the screen during a Maestro swipe.
4. Drag the handle out two words, back one, out three, back two — then release once. → ✓ exactly one translation request for the final selection; no flicker of intermediate results (POP-004, POP-005). Automated as one straight drag from "river" to "its": the popup shows "river, though its" as a SENTENCE with one translation; the request count and the out-and-back path are manual.
5. With the phrase showing Ready, look at Save. → ✓ a multi-word selection cannot be saved: its popup has no Save button at all, only Listen (POP-011; `ReaderPane.kt`, `TranslationBubble`).
6. Drag the end handle past the paragraph's last line into the margin and release there. → ✓ the selection runs to the end of the paragraph's text and gets a new translation, no error (POP-006); "exactly one request" is manual.
7. With "river" selected, tap its end handle, then press it still for 0.7 s. → ✓ after each, the same WORD popup with the same translation, no "Translating…" (POP-007; "no new request" itself is MANUAL). Fixed in versionCode 29 — up to v28 the tap closed the popup and the still press replaced the selection with the word under the handle. v29 trade-off: a long press on the word right under the end of a selection does nothing (not exercised).
8. Long-press a single word in paragraph A, note the popup; then long-press the word at the same offset in paragraph B. → ✓ the popup shows paragraph B's own word and translation, nothing left over from A (POP-019).
9. Long-press word A and, before it reaches Ready, long-press word B in another paragraph. → ✓ only word B's result is shown; A's late answer never appears (POP-021). Here: two quick presses back to back and the end state. With A really still in flight: J-TRN-03 step 13b.
10. Long-press a word and, while it is still Translating, tap elsewhere in the text to dismiss; then long-press a different word. → ✓ the tap closes the popup; the dismissed request never shows up in the new popup; the app stays responsive (POP-022). Here the end state only; with the first request really still in flight: J-TRN-03 step 13a.
10a. Long-press "river", then tap the chapter title; tap the title again. → ✓ the first tap only dismisses the selection and the bars stay hidden; the second tap brings the bars up (LAY-030, "an open word selection is dismissed first"). Timed, because bars that did come up would leave by their own 3.5 s timer.
11. Long-press a punctuation-only token (e.g. an em dash "—" between words) and tap Save if offered. → ✓ "Nothing to save"; no entry is created (POP-014). **Manual / not reachable here:** a long press keeps only the letters and digits of the token under the finger and opens no popup when none are left (`ReaderPane.kt`, `onLongPress`); `en-novel.epub` has no such token.
12. Long-press the single word "river", wait for Ready, tap Save. → ✓ toast "Saved to dictionary" (POP-001, WRD-001).
13. Long-press "river" again, wait for Ready, tap Save. → ✓ a duplicate / "already in your dictionary" message, no translation-failure message (POP-013, WRD-008).
14. Go back and open Words. → ✓ "1 saved word"; the entry "river" shows exactly the translation the popup showed (WRD-002, WRD-007).
15. Return to the reader. → ✓ the reader reopens at the same place with translations shown.

**Covers:** POP-001, POP-003, POP-004, POP-005, POP-006, POP-007, POP-010, POP-011, POP-013, POP-014, POP-016, POP-019, POP-021, POP-022, LAY-030 (also WRD-001, WRD-002, WRD-007, WRD-008, WRD-009)

### J-TRN-03 — Changing the target language mid-read; packs and engine switches

**Priority:** P0 · **Tags:** smoke, full, network, phone, tablet · **Devices:** tablet API 35, phone emulator
**Fixtures:** `en-novel.epub`
**Preconditions:** Offline HQ current; only the EN→RU pack installed (EN→DE and EN→FR not installed); online.

**Steps**
1. Open `en-novel.epub` at chapter 1; wait until the screen is translated into Russian.
2. Long-press a word so a Ready popup is open; bring up the bars with a tap on the divider between the panes (a tap on the page would close the popup instead), tap the language button "EN → RU", open the "To" tab and pick German. → ✓ the popup closes at once, so a Russian result can no longer be saved (POP-020).
3. The EN→DE pack downloads; long-press the word "river". → ✓ its popup shows a German translation, with no "corrupted" error (the text is kept: it is neither step 2's Russian nor an error line, and step 5's French must differ from it).
4. Wait for the screen to settle without scrolling. → ✓ every paragraph that was on screen is now German, not only the one at the top (TRN-002).
5. Switch the target to French ("EN → DE" → To → French) and, immediately after confirming it, long-press a word AND scroll to paragraphs not yet translated, without waiting. → ✓ one EN→FR download runs: the popup ("Downloading language model… N%") and the banner ("Preparing translation… N%") show the same download, which never goes down; translation follows with no "corrupted" error (OHQ-012). Once the screen settles — and on the next screen — no paragraph shows German; only French (TRN-004). (The shared-download check was written for the German download of step 3; it is done with the French one because a pack downloads in about 5 s and step 2's look at the closed popup takes 2 of them. "Exactly one" download is not counted: the app logs none. TRN-004's "while paragraphs still show Translating" cannot be arranged with a fast pack.)
6. Switch the target back to Russian. → ✓ Russian appears quickly with no download banner (pack already installed).
7. Open the Translator picker and select ML Kit; return to the reader. → ✓ visible paragraphs are retranslated by ML Kit (wording differs where the engines differ: the 12th paragraph's text changes) and the picker shows ML Kit as current (TRN-003, MLK-010). ML Kit's models do download on the suite's emulators (API 35 tablet, Pixel_10_Pro; checked 2026-11-15).
8. Open the Translator picker and select Offline HQ again. → ✓ no download banner; translations appear immediately (OHQ-005, TRN-003).
9. Open Settings → Translation → Change and pick ML Kit there; open the book → ✓ ML Kit is used at once (SET-001); restart the app (Settings still names ML Kit — MLK-010); open the book. → ✓ ML Kit is used (SET-001). Then Settings → Translation → Change → Offline HQ; open the book. → ✓ Offline HQ is used, no download (SET-001, OHQ-005).
10. Open Settings → Language packs. → ✓ rows "EN → RU · N MB", "EN → DE · N MB", "EN → FR · N MB", each with Delete; footer "Total <sum> MB · Models by Mozilla (MPL-2.0)" (OHQ-009).
11. Tap Delete on "EN → DE". → ✓ the row disappears and the total drops at once (OHQ-011).
12. Back in the reader, switch the target to German. → ✓ a fresh download starts from 0 % with no leftover/corrupted state, and German translation appears when it finishes (OHQ-011, OHQ-004).
13. Go back to the top of chapter 1. (a) Switch the target to Spanish (pack not installed) and at once long-press "river": while the popup still reads "Downloading language model… N%" tap Save → ✓ Save is disabled (greyed out: its button reads enabled=false, and enabled again once a word is translated) and nothing is saved — the popup goes on translating, no toast, and Words is still empty at the end (POP-010); with the request still in flight tap elsewhere in the text → ✓ the popup is dismissed; long-press a word in paragraph 2 → ✓ its popup ends with its own translation, not "river"'s, no error (POP-022). (b) Switch the target to Italian and at once long-press "river", then the word in paragraph 2 → ✓ the popup is the second word's and is still downloading — the first request had not been answered — and it ends with the second word's translation (POP-021). Close the book; Words → "No saved words yet".

**Covers:** POP-020, POP-010, POP-021, POP-022, OHQ-004, OHQ-005, OHQ-009, OHQ-011, OHQ-012, TRN-002, TRN-003, TRN-004, MLK-010, SET-001

### J-TRN-04 — A mixed-language book: every passage in its own language

**Priority:** P0 · **Tags:** smoke, full, network, phone, tablet · **Devices:** tablet API 35, phone emulator
**Fixtures:** `mixed-lang.epub`
**Preconditions:** a clean install — Offline HQ current, no pack installed (the flow asserts that Settings → Language packs is empty), Words list empty; online. Opening this English book makes the target Russian and installs EN→RU.

**Steps**
1. Open `mixed-lang.epub`. → ✓ language button reads "EN → RU"; English paragraphs translate as usual.
2. Scroll to the French letter. → ✓ instead of a translation its slot shows the French text under the label "FR · French, not translated" and a button "Download and translate"; after 30 s without a tap it is still so, and Settings → Language packs lists EN → RU only, the total being that pack's size — nothing was downloaded on its own for the French, German, Chinese or Japanese paragraph (TRN-026). (A paragraph's download is not shown on the reader's banner, so the pack list is the evidence.)
3. Tap "Download and translate". → ✓ the FR→EN pack downloads with a growing percent on that button ("Downloading… N%"), then the letter is translated from French — label "FR · from French", and the Russian reads as a translation of the French text ("…книг…" for *livre*), not a garble of "English" (TRN-026, TRN-011).
4. Scroll to the German paragraph → ✓ still "DE · German, not translated" (the French download did not bring German). Tap "Download and translate"; wait. → ✓ "DE · from German"; the Russian has "снег…" for *Schnee* (TRN-011).
5. Long-press a German word inside that paragraph (its first word, "Lieber"). → ✓ the popup translates it from German ("Дорог…"), not English (POP-024).
6. Tap Save, then open Words. → ✓ the entry is tagged DE as its source language, not EN (POP-025, WRD-003). Go back to the reader → ✓ the German paragraph is translated from German at once, no button (the flow asserts "DE · from German" in its slot and no "Download and translate" there).
7. Scroll to the Latin epigraph. → ✓ the passage is shown unmodified with a small label above it: "LA · Latin, not translated", and no download is offered (TRN-015).
8. Long-press a word inside the Latin epigraph. → ✓ the popup explains why this paragraph isn't translated — "Latin isn't supported for translation" — instead of a blank or a wrong result (POP-023). (On the way down: the Russian paragraph is shown as it is under "RU · original".)
9. Scroll to the short Chinese quote (27 characters: under the 60-character line but over it when a Han character counts as three letters). → ✓ it is recognised as Chinese — labelled "ZH · Chinese, not translated" and offered "Download and translate" — not translated as English into garbage; Settings → Language packs lists EN → RU, FR → EN and DE → EN only (TRN-012, TRN-026).
10. Tap "Download and translate". → ✓ the ZH→EN pack downloads and the quote is translated from Chinese (TRN-012, TRN-026).
11. Scroll to the short Japanese line. → ✓ recognised as Japanese (JA label / "Download and translate"), not as English or Chinese; after tapping it, translated from Japanese (TRN-012).
12. Scroll back to an English paragraph and long-press a word ("And"). → ✓ the popup translates from English again (POP-024; *automated partly:* the popup gives Russian for "And" — that it is translated *from English* is not visible to the flow, the same word through the Japanese model would read the same; *manual* for that half). Settings → Language packs now lists the five packs: EN → RU and the four whose buttons were tapped.

**Covers:** TRN-011, TRN-012, TRN-015, TRN-026, POP-023, POP-024, POP-025 (also WRD-003)

### J-TRN-05 — Chinese and Japanese: books, sentences, both on-device engines

**Priority:** P0 · **Tags:** smoke, full, network, tablet, phone · **Devices:** tablet API 35, phone emulator (see MLK-009 for API 30)
**Fixtures:** `ja-poems.epub`, `ja-novel.epub`, `zh-novel.epub`
**Preconditions:** a clean install — Offline HQ current; target Russian, chosen in Settings → Default translation language (the default target is English; only an English book switches it to Russian by itself); online.

**Steps**
1. Import and open `ja-poems.epub` (a collection of short Japanese poems — 160 lines of 10–13 characters — with a long English preface). → ✓ the language button reads "JA → RU", not "EN → RU" (LNG-003).
2. → ✓ the English preface is handled as embedded English: translated under "EN · from English". Scroll past the preface to the poems. → ✓ the short poems are treated as the book's own Japanese and translated, none labelled as a foreign passage (LNG-003).
3. Long-press inside a poem (a Japanese line has no spaces: the selection is the whole poem). → ✓ the popup translates it from Japanese into Russian — the same Russian the page shows for that poem; Save, then Words → the entry is tagged JA (POP-001).
4. Open `ja-novel.epub` at its long chapter. → ✓ translation starts within ~30 s (the packs are installed since step 1); the banner never sits on "Preparing translation…" (TRN-013).
5. Read three translated paragraphs that hold several Japanese sentences each. → ✓ the sentences are separated; no run-together like "упал.Тот" (TRN-014). Each fixture paragraph is one sentence written 12–17 times, so its translation must be one Russian sentence that many times with single spaces between — a sentence at a time.
6. Scroll forward through the next chapter. → ✓ each paragraph that comes on screen translates without a stall on "Preparing translation…" (TRN-013).
7. Open `zh-novel.epub` at its long chapter. → ✓ "ZH → RU"; translation starts promptly, no hang on "Preparing translation…" (TRN-013).
8. Open the Translator picker and select ML Kit. → ✓ the visible Chinese paragraph is retranslated by ML Kit (another Russian text than Offline HQ's), sentence by sentence; no hang (MLK-002, MLK-001).
9. Go back and open `ja-novel.epub` on ML Kit; wait for the model download and the first screen. → ✓ translation proceeds; sentences are not run together; no "Preparing translation…" hang (MLK-002, TRN-014).
10. Go back to the Library and reopen `ja-poems.epub`. → ✓ still "JA → RU" (LNG-003).
11. In `ja-novel.epub` on ML Kit scroll forward through both chapters. → ✓ every paragraph translates, a sentence at a time; no hang, no run-together sentences (MLK-002).
12. Open the Translator picker and select Offline HQ again. → ✓ the first paragraph reads Offline HQ's translation of step 5 again.

**Covers:** LNG-003, TRN-013, TRN-014, MLK-001, MLK-002, POP-001

### J-TRN-06 — Book language detection and the "From" override

**Priority:** P0 · **Tags:** smoke, full, network, phone, tablet · **Devices:** tablet API 35, phone emulator
**Fixtures:** `es-book.epub`, `de-book.epub`, `ja-novel.epub`, `zh-novel.epub`, `don-quixote-es.epub`, `rashomon-ja.epub`, `rur-cs.epub`, `fi-book.epub`, `und-book.epub` (Premium or a test build without the 3-book limit, see LIB-001)
**Preconditions:** a clean install — Offline HQ current; target Russian, chosen in Settings → Default translation language (the default target is English); the debug Premium switch on (nine books); online. Each book is imported in the step that opens it.

**Steps**
1. Open `don-quixote-es.epub` (non-Spanish front matter). → ✓ the language button reads "ES → RU" — detected from the whole book, not the first lines (LNG-002).
2. Open `rashomon-ja.epub`. → ✓ "JA → RU" (LNG-002).
3. Open `es-book.epub`, `de-book.epub`, `ja-novel.epub`, `zh-novel.epub` in turn. → ✓ the button reads "ES → RU", "DE → RU", "JA → RU", "ZH → RU" respectively (LNG-001).
4. Open `rur-cs.epub`. → ✓ "CS → RU"; Czech text translates (LNG-001).
5. Tap the language button, open the "From" tab (it says "Detected: Čeština") and pick Polish. → ✓ the button reads "PL → RU" and the screen is retranslated from Polish — the same paragraph gets another Russian text (LNG-004). (The "From" tab offers the app's 18 languages; Slovak is not one of them.)
6. Go back to the Library, open `es-book.epub`, then reopen `rur-cs.epub`. → ✓ still "PL → RU" — the override is remembered for this book only; `es-book.epub` still reads "ES → RU" (LNG-004).
7. In `rur-cs.epub` tap the language button → "From" → "Automatic". → ✓ back to "CS → RU"; the paragraph reads its translation from Czech again (LNG-005).
8. Open `fi-book.epub`. → ✓ one column of Finnish text; the language button reads "FI → —"; with the bars up a note "NOT TRANSLATED — This book looks like Finnish, which can't be translated yet…"; no translation pane, no garbled translation (LNG-006).
9. Open the Translator picker, select ML Kit, return to `fi-book.epub`. → ✓ still one column with the same note under ML Kit; then switch back to Offline HQ (LNG-006).
10. Open `und-book.epub`. → ✓ the language button shows "? → —", not "UND" or blank; the note says "The book's language couldn't be detected. Choose it to translate the book."; the "From" tab says "Not detected" (LNG-009).
11. Open Almanac → ✓ the LANGUAGES card lists the time read in `und-book` under "UNKNOWN", never "UND" (LNG-009).

**Covers:** LNG-001, LNG-002, LNG-004, LNG-005, LNG-006, LNG-009

### J-TRN-07 — Online-engine keys without spending money: errors, Switch translator, clearing

**Priority:** P0 · **Tags:** smoke, full, network, phone, tablet · **Devices:** tablet API 35, phone emulator
**Fixtures:** `en-novel.epub`
**Preconditions:** a clean install: Offline HQ current (its EN→RU pack downloads in step 12, the first time it translates); no keys saved for any online engine; online. Uses only a made-up key (`fake-key-e2e-000`), which costs nothing: DeepL gets at most 3 requests per failed screen and one per word popup, Google Cloud at most 3.

**Steps**
1. Settings → Translation → Change; tap DeepL (no key yet). → ✓ the API key dialog opens instead of DeepL becoming current (SET-003).
2. Tap Cancel. → ✓ Offline HQ is still current; DeepL still shows as not configured (SET-003).
3. Tap DeepL again, enter `fake-key-e2e-000`, tap Save. → ✓ the key field shows bullets, never the typed key; the dialog and the picker close (saving selects); DeepL is current and shows KEY CONFIGURED; its key dialog now says "(existing key hidden)" and offers Clear.
4. Open `en-novel.epub` (never opened). → ✓ banner "Invalid DeepL API key — tap Switch translator to update it" with Retry and Switch translator; no paragraph shows ML Kit text under the DeepL label (ONL-002).
5. Tap Retry, then scroll two screens. → ✓ the same Invalid-key banner stays; nothing falls back silently — no translated text appears (ONL-002). *Manual:* that scrolling sends no new request (the debug build has no request log).
6. Long-press a word. → ✓ the popup shows the short error "Invalid DeepL API key" (no "tap Switch translator") instead of a translation; its Save is disabled and a tap on it saves nothing (POP-017, POP-016). *Manual:* muted, not italic.
7. Scroll to a new screen and long-press another word. → ✓ the same invalid-key error — scrolling did not clear it (POP-017).
8. Force-stop and relaunch the app; open Settings → Translation. → ✓ DeepL is still current and still shows as configured; no key re-entry needed (SET-010).
9. Open the reader's Translator dialog and compare with Settings → Translation. → ✓ same current provider (DeepL), same configured/not-configured state for every row; no usage line on either (a made-up key never translates, so nothing is counted) (SET-004).
9a. (Added.) The book is opened with **Show translation** off and DeepL current; wait 10 s and scroll a screen. → ✓ no error banner: nothing is sent to DeepL while the pane is hidden (with this key every request ends in the Invalid-key banner). Turn **Show translation** on. → ✓ the Invalid-key banner appears: requests start only now (TRN-010, request gate). Turn it off again → ✓ the banner stays (it is drawn with the pane hidden, which is what makes "no banner" mean "no request"); on again for step 10. *Manual:* the usage counter of a real paid key.
10. In the reader, tap "Switch translator" on the banner. → ✓ the Translator picker opens showing DeepL as the current, failing provider (SET-008).
11. With a word popup open (it shows the invalid-key error), open the picker from the banner and tap the DeepL row again (already current, no key change). → ✓ the picker closes and the same popup and the same banner are still there — a real provider or key change would have closed the popup; DeepL's config stays as it was (SET-007). *Manual:* no flicker.
12. Tap Offline HQ. → ✓ the popup closes (the provider changed), the banner disappears and the screen that failed now shows Russian translations in Offline HQ's wording, with no "translated by" strip (SET-009).
13. Open the picker, open DeepL's key dialog with its key button and tap Clear (DeepL is not current). → ✓ Offline HQ stays current; DeepL now shows not configured (SET-006).
14. Force-stop and relaunch; open the picker. → ✓ DeepL still shows not configured; reading uses Offline HQ (SET-011).
15. In the reader's picker tap Google Cloud, enter `fake-key-e2e-000`, Save (Google Cloud becomes current). Then tap Clear in Google Cloud's key dialog. → ✓ ML Kit becomes current at once, in the picker that is still open; Google Cloud is never left current without a key; the open page is translated again, in ML Kit's wording (MLK-006).
16. Leave the reader, change Translation to Offline HQ in Settings, and open the book again. → ✓ it translates with Offline HQ. (SET-005 as written — changing the setting *without closing the reader* — cannot be reached: the app's navigation is hidden while a book is open, `AppShell.kt`, and Back is the only way to Settings. Not automated; the reachable half is this step.)

**Covers:** SET-003, SET-004, SET-006, SET-007, SET-008, SET-009, SET-010, SET-011, ONL-002, POP-016, POP-017, MLK-006, TRN-010 (request gate only) — SET-005 not reachable (step 16)

### J-TRN-08 — Azure and Quick Translate fields and gating, no LibreTranslate

**Priority:** P0 · **Tags:** smoke, full, network, phone, tablet · **Devices:** tablet API 35, phone emulator
**Fixtures:** `en-novel.epub`, `prose-one-chapter.epub`
**Preconditions:** a clean install: Offline HQ current; no online keys saved; online. The only key typed is the made-up `fake-key-e2e-000`. Azure never gets a key; Quick Translate (no key) really translates — about a hundred paragraphs in the whole journey.

**Steps**
1. Settings → Translation → Change. → ✓ the picker has no LibreTranslate card (LibreTranslate was removed in versionCode 29; its former steps — cleartext URL rejected, bare host upgraded, https URL kept, Clear with a custom address — are retired with it: ONL-009, ONL-010, ONL-011 "removed in v29"). The one-time notice "LibreTranslate is no longer supported — switched to Offline HQ." after an update from v28 with LibreTranslate selected cannot be set up from a clean install: MANUAL (ONL-024).
2. Open `en-novel.epub`; open the language picker ("To" tab). → ✓ the strip reads "On-device translation · no cloud calls" and "OFFLINE HQ · DOWNLOADED", nothing about ML Kit or online translation (TRN-028). The reader's Translator picker has no LibreTranslate card either.
3.–5. (Removed with LibreTranslate in versionCode 29; the numbers are kept so the steps below keep theirs.) Leave the reader; Settings → Translation still says Offline HQ.
6. Tap Azure. → ✓ the dialog has a separate "Region" field next to the API key (ONL-012 field check). Enter only Region `westeurope`, leave the key empty, Save. → ✓ the app saves it (a region alone enables Save — `TranslatorPickerDialog.kt`, `ApiKeyDialog` `saveEnabled`): Azure is current without a key, its card still says TAP TO ADD API KEY, and its dialog shows `westeurope` again.
7. In the reader. → ✓ a clear "Azure Translator API key isn't set — tap Switch translator to add it" error with Retry and Switch translator, never another engine's text labelled Azure (ONL-013, ONL-001). The language picker's strip reads "Online translation · text is sent to Microsoft Azure", with no model line and no "no cloud calls" (TRN-028).
8. With Azure current and keyless, long-press a word and tap Save. → ✓ the popup shows "Azure Translator API key isn't set"; its Save is disabled, and after a tap on it Words gets no entry (POP-012, WRD-006). *Manual:* that no toast appears (a toast's absence cannot be asserted: it goes by itself). (There is no failure toast: Save stores the popup's translation and is disabled until there is one — `ReaderPane.kt` `TranslationBubble`, `ReaderViewModel.saveWord`.)
9. Switch to Offline HQ via "Switch translator". Open the Translator picker and find Quick Translate. → ✓ the note "Unofficial endpoint — may be rate-limited or break without notice." is on its card (ONL-020).
10. Select Quick Translate. → ✓ the visible screen translates via Quick Translate (Russian again, and not Offline HQ's text of the same paragraph); long-press a word → the popup returns a translation (ONL-021).
11. Turn Aa → **Show translation** off, scroll forward three screens, turn it back on. → ✓ the screens scrolled while hidden had no translations made in the meantime — Quick Translate needs the network, so it is gated like a paid engine (ONL-014). *Automated through Settings → "Cached translations: N":* N is the same before and after the three hidden screens, and larger once the pane is back and the screen is translated. *Manual:* the shimmer placeholders.
12. With Quick Translate still current and the screen translated, rotate there and back. → ✓ the translated paragraphs reappear at once, with no translating placeholder — they come from the cache, not a new request (ONL-023). *Automated (partial):* at the start of chapter 1, in aeroplane mode (10 s for the radios to go down): 5 s after each turn (proven by the paragraph's width) the paragraph on top has the same Russian text, and there is no "Translating…" and no error banner — offline, a new request could only fail, and by then it has. *Manual:* "no translating placeholder" itself — a placeholder that a cache hit shows for under a second cannot be seen by the check; a paid engine's usage counter unchanged by the rotations.
13. Turn **Show translation** off, go back to the Library and open `prose-one-chapter.epub` (never opened; 56 paragraphs in ONE chapter); scroll three screens. Turn **Show translation** on, then read on to the end of the book. → ✓ the visible screen translates; nothing was translated in the background while hidden, and what was read while hidden is not filled in afterwards (TRN-027). *Automated through the cache count:* unchanged by the hidden reading; after the pane comes on it grows by at most the paragraphs from four above the one then on top (g, at least 10) to the end of the book (between 56 − g and 56 − g + 4, three either way allowed for a layout shift; the lower end is timing — the flow scrolls on before the look-behind runs). The book is one chapter so that "the chapter behind it" is the whole hidden read: an engine that fills the current chapter from its start when the pane comes on stores all 56, above the bound for every g ≥ 8. *Manual:* the placeholder on the paragraphs further down; a paid engine's usage counter.
14. Select Offline HQ again (Settings). → ✓ Quick Translate never showed a usage line (true by construction — Quick Translate does not track usage; the flow's look cannot fail); Words has no entry.

**Covers:** ONL-001, ONL-013, ONL-014, ONL-020, ONL-021, ONL-023, POP-012, SET-003, SET-018, TRN-027, TRN-028 (also WRD-006; ONL-012 field check only — full Azure translation in J-TRN-12). ONL-009/010/011: removed in v29. ONL-024: MANUAL.

### J-TRN-09 — From ML Kit to Offline HQ: empty packs, cache, unsupported pair, a Russian book into German

**Priority:** P1 · **Tags:** full, network, tablet · **Devices:** tablet API 35 (ML Kit models download unreliably on API 30, see MLK-009); phone emulator optional
**Fixtures:** `en-novel.epub`, `ru-book.epub`
**Preconditions:** Fresh install / cleared data; online; ML Kit EN→RU model may download during the journey; no Offline HQ packs installed.

**Steps**
1. Launch, finish onboarding. Settings → Translation → Change → ML Kit, before opening any book. → ✓ ML Kit is current (MLK-010).
2. In Settings scroll to "Language packs". → ✓ "No Offline HQ packs downloaded yet. Choose Offline HQ as the translator and packs download as you read." — no list, no total (OHQ-010).
3. Scroll to "About". → ✓ the version, "Formats: EPUB · FB2 · MOBI", "Translation: Google ML Kit · Offline HQ (Mozilla/Bergamot)", and the open-source licences entry (SET-016).
4. Import and open `en-novel.epub`; wait for the model and the first screen. → ✓ the pane fills with ML Kit translations (MLK-001).
5. Force-stop and relaunch; open the book. → ✓ ML Kit is still current; translations come from ML Kit (MLK-010).
6. Turn Aa → **Show translation** off, scroll forward three screens, turn it back on. → ✓ those screens are already translated — ML Kit is free and keeps working while hidden (MLK-003).
7. Settings → Storage. → ✓ "Cached translations: N" with N > 0 (SET-014).
8. Tap Clear and confirm. → ✓ the count reads 0 (SET-014).
9. Return to the same screen in the book. → ✓ the paragraphs re-translate (shimmers, then text) instead of appearing instantly from cache (SET-014).
10. Settings → Translation → Change → Offline HQ. Tap the language button and set "To" to a language Offline HQ has no model for (pick one that the Bergamot catalog lacks, e.g. Hindi). → ✓ a quiet notice strip "Offline HQ doesn't support EN→HI — translated by ML Kit"; paragraphs translate via ML Kit; no error banner (MLK-007).
11. Set the target back to Russian. → ✓ the notice disappears; Offline HQ downloads/uses EN→RU.
12. Close the book. Import `ru-book.epub`, and in Settings set the target language to German (no book is open, so nothing downloads yet).
13. Open `ru-book.epub`. → ✓ language button "RU → DE"; two downloads run one after the other (RU→EN, then EN→DE), the banner percent covering both legs; the first screen takes noticeably longer than an English book did (OHQ-008).
14. Wait for the first screen. → ✓ German text appears in the pane.
15. Open the Translator picker and read Offline HQ's description. → ✓ it says non-English pairs go through English; it does not claim a direct RU→DE model (OHQ-007).
16. Close and reopen the book; jump to another chapter. → ✓ no download banner; translation is quick (OHQ-008).
17. Settings → Language packs. → ✓ rows "RU → EN · N MB" and "EN → DE · N MB" (plus any earlier ones), each with Delete; the footer total equals the sum and credits "Models by Mozilla (MPL-2.0)" (OHQ-009).
18. Delete "RU → EN". → ✓ the row disappears and the total drops (OHQ-011).
19. Reopen `ru-book.epub` at an untranslated chapter. → ✓ only the RU→EN leg downloads again, from 0 %, and German translation follows with no "corrupted" error (OHQ-011, OHQ-008).

**Covers:** MLK-001, MLK-003, MLK-007, MLK-010, OHQ-007, OHQ-008, OHQ-009, OHQ-010, OHQ-011, SET-014, SET-016

### J-TRN-10 — Jumping around: bookmarks, huge chapters, other books, odd books

**Priority:** P1 · **Tags:** full, network, phone, tablet · **Devices:** tablet API 35, tablet API 26, phone emulator
**Fixtures:** `en-novel.epub`, `long-chapter.epub`, `mixed-lang.epub`, `empty-chapter.epub`, `empty-book.epub`, `pictures-only.epub`
**Preconditions:** Offline HQ current with EN→RU; a bookmark in chapter 5 of `en-novel.epub`; online.

**Steps**
1. Open `en-novel.epub` in chapter 1; open Bookmarks and tap the chapter-5 bookmark. → ✓ the landing screen fills with translations without any scroll (TRN-021).
2. Open the chapter picker and pick chapter 8. → ✓ the landing screen translates without a scroll (TRN-021).
3. Note two translated paragraphs. Go back to the Library and open `mixed-lang.epub`. → ✓ only this book's translations appear; nothing from `en-novel` shows, even briefly (TRN-009).
4. Go back to the Library and reopen `en-novel.epub`. → ✓ it opens at chapter 8 with its own translations; nothing from `mixed-lang` leaks in (TRN-009).
5. Open `long-chapter.epub` (500+ paragraphs). Fling far ahead (about paragraph 400) and stop. → ✓ the landing screen translates within a few seconds — at most ~8 paragraphs are processed before it — and is not stuck behind the earlier part of the chapter (TRN-022).
6. Add a bookmark at this screen. Fling back to the chapter start. → ✓ the first screens translate, no stuck shimmers.
7. Open Bookmarks and tap the new bookmark. → ✓ the landing screen (≈ paragraph 400) translates without a scroll (TRN-021, TRN-022).
8. Fling ahead again to about paragraph 480. → ✓ the screen translates promptly, not stuck behind earlier paragraphs (TRN-022).
9. Open `empty-chapter.epub`. → ✓ no crash; the translation banner ends idle once the first non-empty chapter is reached (TRN-024).
10. Open `empty-book.epub`, then `pictures-only.epub`. → ✓ no crash; for each the banner ends idle — no stuck spinner, no error (TRN-024).

**Covers:** TRN-009, TRN-021, TRN-022, TRN-024

---

# Manual journeys

### J-TRN-11 — A DeepL reader: real key, usage, popup save, hidden pane

**Priority:** P0 · **Tags:** manual, network, phone, tablet · **Devices:** a real device with full IPv4/IPv6 (not the default emulator, which lacks IPv6), tablet or phone
**Fixtures:** `en-novel.epub`
**Preconditions:** A valid DeepL API key (free tier is enough); DeepL not configured yet; Offline HQ current; the book saved deep in chapter 4 (open it there once, then close).

**Steps**
1. Reader → Translator picker → DeepL → enter the valid key → Save, then return to the reader at once. → ✓ no flash of "API key isn't set" between Save and the first translation; DeepL appears as current only after the save completed (ONL-006, SET-013).
2. Watch the first screen. → ✓ paragraphs translate via DeepL (DeepL wording, not ML Kit's), no error (ONL-003, SET-013).
3. Scroll through several pages over the real network. → ✓ every page translates consistently (ONL-005).
4. Open the Translator picker. → ✓ under DeepL: "{used} / {limit} this month" (ONL-015). Open Settings → Translation. → ✓ the same current provider, configured state and the same usage numbers (SET-004).
5. Note the counter. Long-press a word, wait for Ready, note the counter, tap Save. → ✓ the counter grew only by the popup's one request, not again on Save; Words holds exactly the text the popup showed (POP-009, WRD-007).
6. Long-press a word, drag the end handle off the paragraph into the margin, release. → ✓ the counter grows by one request (POP-006).
7. Note the counter. Turn **Show translation** off, long-press a word. → ✓ the popup still translates (POP-002). Scroll through five pages. → ✓ the counter does not grow except for that one popup (TRN-010).
8. Turn **Show translation** on. → ✓ the current screen translates and the counter grows again (TRN-010).
8a. Note the counter; rotate the device there and back twice. → ✓ the shown paragraphs reappear at once and the counter is unchanged (ONL-023).
8b. Turn **Show translation** off; open a second, never-opened book; read three screens; note the counter. Turn **Show translation** on. → ✓ the counter did not grow while hidden; after turning on it grows only by the visible screen, not by the screens read while hidden (TRN-027). Return to the first book.
9. Open Translator picker → tap the reset (circular arrow) icon next to DeepL's usage. → ✓ dialog "Reset counter" saying DeepL's real quota is not affected; tap Cancel → the counter is unchanged (ONL-017).
10. Reset again and confirm. → ✓ the usage line reads 0; nothing claims the remote quota changed (ONL-018).
11. Force-stop, relaunch, and open Reader, then Settings, then Home. → ✓ each screen appears immediately (placeholder state at most) with no visible freeze while the key is decrypted (SET-012); DeepL still shows configured and translates (SET-010).
12. Go to the Library and reopen the book (saved deep in chapter 4). Check the counter right away. → ✓ only the visible page's paragraphs were requested; no request for chapter 0 / paragraph 0 (TRN-020).
13. Select Offline HQ again before handing the device on.

**Covers:** ONL-003, ONL-005, ONL-006, ONL-015, ONL-017, ONL-018, SET-004, SET-010, SET-012, SET-013, POP-002, POP-006, POP-009, TRN-010, TRN-020, ONL-023, TRN-027 (also WRD-007)

### J-TRN-12 — Paid chapter fill and switching between paid engines

**Priority:** P1 · **Tags:** manual, network, rotation, tablet · **Devices:** tablet (real device preferred)
**Fixtures:** `en-novel.epub` (chapter 3 long, untranslated)
**Preconditions:** Valid DeepL, Google Cloud and Azure keys (Azure resource in a non-"global" region, e.g. westeurope). Note: "Translate the rest of this chapter" is offered only for paid (advanced) engines and only while the pane is visible.

**Steps**
1. Select DeepL. Open chapter 3 at its top; note DeepL's usage. Translator picker → "Translate the rest of this chapter". → ✓ the picker closes and the chapter fills.
2. Turn **Show translation** off and reopen the Translator picker. → ✓ "Translate the rest of this chapter" is not offered while the pane is hidden; turn the pane back on (TRN-010).
3. Immediately scroll to a paragraph near the chapter's end, ahead of the fill. → ✓ the newly visible paragraphs translate first; the fill never blocks the visible screen (TRN-006).
4. While the fill is still running, rotate the device. → ✓ the panel does not flash empty and refill; the fill continues and finishes once (TRN-008).
5. Scroll the whole chapter. → ✓ every paragraph translated once — none skipped, none doubled or garbled; DeepL usage grew by one request per paragraph, not two (TRN-007).
6. Switch to Google Cloud; translate the next chapter's first screen; reopen the picker. → ✓ Google Cloud usage grew; DeepL usage did not grow after the switch (ONL-019).
7. Select Azure; enter the key and Region `westeurope` in its separate Region field; Save; translate a screen. → ✓ translation succeeds — no region-mismatch error (ONL-012).
8. Switch back to DeepL, translate a screen, reopen the picker. → ✓ DeepL's usage grows again; Google Cloud's does not (ONL-019).
9. Select Quick Translate and long-press 30+ words in quick succession. → ✓ some requests may wait (backoff) but all resolve; "quota exceeded" is never shown for the temporary limit (ONL-004).
10. Select Offline HQ.

**Covers:** TRN-006, TRN-007, TRN-008, TRN-010, ONL-004, ONL-012, ONL-019

### J-TRN-13 — Reading on a bad connection

**Priority:** P1 · **Tags:** manual, network, phone, tablet · **Devices:** real arm64 phone or tablet (airplane mode and Wi-Fi toggles)
**Fixtures:** `en-novel.epub`, `ru-book.epub`
**Preconditions:** Valid DeepL key saved; ML Kit EN→RU model and Offline HQ EN→RU pack installed; EN→DE pack NOT installed; a free/limited connection you can cut.

**Steps**
1. Offline HQ current. Set target German so the EN→DE pack starts downloading; at ~40 % turn Wi-Fi off. → ✓ the download fails with a Retry affordance, not a hang. Turn Wi-Fi on and tap Retry. → ✓ the percent continues from about where it stopped, not from 0 (OHQ-013).
2. Start the download again for a fresh pair (target French), and at ~30 % force-stop the app. Relaunch and open the book. → ✓ the next attempt resumes and completes rather than failing for good (OHQ-015).
3. Turn on airplane mode; set target Italian (pack not installed). → ✓ "Couldn't download the Offline HQ language pack" (specific, reportable) (OHQ-019).
4. Still offline, reopen the book twice and watch for 2 minutes. → ✓ the failed download is not retried in a loop (no repeating banner / no repeated attempts in logcat); a sane error with Retry stays (OHQ-018).
5. Still in airplane mode, set target back to Russian and scroll to new paragraphs. → ✓ Offline HQ translates fully offline; no error banner (OHQ-026).
6. Select ML Kit; scroll to new paragraphs. → ✓ translates offline; the banner may read "On-device translation · no cloud calls" (MLK-011).
7. Turn airplane mode off. Select DeepL. Scroll to a new page and cut the network for ~2 s while it translates, then restore it. → ✓ only the affected paragraph re-translates; the rest of the page is not left untranslated (TRN-016).
8. Turn airplane mode on, scroll to a new page. → ✓ the "TRANSLATION FAILED" banner with Retry over the visible window (TRN-017). Long-press a word. → ✓ the popup shows the Failed state: a short error in muted ink, not italic (POP-016).
9. Turn airplane mode off and tap Retry. → ✓ every paragraph on screen ends up translated, none stuck (TRN-017).
10. Cause one transient failure again (2 s airplane mode on a new screen), restore, and scroll to the next screen without tapping Retry. → ✓ the banner clears and that screen translates normally (TRN-018).
11. Long-press a word during a 2 s outage so the popup shows a transient error; restore the network, scroll, and long-press the same word. → ✓ it translates; the old error is not shown again (POP-018).
12. Open chapter 3 at the top, start "Translate the rest of this chapter", stay on the first screen and cut the network while the fill is off-screen; restore after ~5 s. → ✓ the banner stays idle (no Error) during the background-only failure; scrolling through the chapter later shows the skipped paragraphs translated on a later pass (TRN-019).
13. Select Offline HQ.

**Covers:** OHQ-013, OHQ-015, OHQ-018, OHQ-019, OHQ-026, MLK-011, TRN-016, TRN-017, TRN-018, TRN-019, POP-016, POP-018

### J-TRN-14 — Broken packs, full storage, stalled server, broken native library

**Priority:** P1 · **Tags:** manual, network · **Devices:** debuggable arm64 emulator (run-as access) + a host proxy (mitmproxy or similar) able to stall or corrupt responses
**Fixtures:** `en-novel.epub`, `mixed-lang.epub`
**Preconditions:** Debug build; Offline HQ current; EN→RU installed; proxy configured on the emulator.

**Steps**
1. Set target German (EN→DE not installed). Make the proxy return corrupt bytes for the pack. Immediately long-press a word AND scroll to an untranslated paragraph. → ✓ all waiting callers (popup and pane) get one shared failure reported once, at the end of the attempt; logcat shows no retry storm (OHQ-014).
2. Let EN→DE download cleanly, then corrupt the installed model file (`adb shell run-as … ` overwrite a few bytes) and kill the app. Reopen and translate. → ✓ the SHA-256 mismatch deletes the files and one automatic retry downloads the pack again; translation works (OHQ-016).
3. Repeat step 2 with the proxy serving corrupt bytes for the retry too. → ✓ the reader shows "The Offline HQ pack was corrupted and could not be re-downloaded." (OHQ-016).
4. Make the proxy serve good bytes and tap Retry. → ✓ the pack downloads and German translation appears (OHQ-016).
5. Fill the emulator's storage until free space is below 2× the next pack's size; set target French. → ✓ no download request reaches the proxy; the reader shows "Not enough storage for the Offline HQ pack (needs … MB free)." (OHQ-017).
6. Free the space and tap Retry. → ✓ the download starts and French translation appears (OHQ-017).
7. Make the proxy accept the pack request and send nothing; set target Italian; wait 90+ s. → ✓ the download is aborted as failed: "Couldn't download the Offline HQ language pack (…MB). Check your internet and retry." with Retry — not a hang (OHQ-020).
8. Let the proxy pass traffic again and tap Retry. → ✓ the download completes and Italian translation appears (OHQ-020).
9. Use a debug build/flag that makes the Offline HQ native library fail to load; translate. → ✓ a quiet one-shot strip "Offline HQ isn't available on this device — translated by ML Kit"; text arrives via ML Kit; no crash, no hard error banner (OHQ-023).
10. Break the native session mid-use (debug flag), translate a new screen, then tap Retry if shown. → ✓ ENGINE_UNAVAILABLE recovers via Retry; no crash (OHQ-023).
11. Remove the proxy and debug flags.

**Covers:** OHQ-014, OHQ-016, OHQ-017, OHQ-020, OHQ-023

### J-TRN-15 — Upgrades, restores, 32-bit devices and the security audit

**Priority:** P1 (carries the DeepL variant of P0 ONL-001 and POP-012) · **Tags:** manual, network · **Devices:** arm64 phone A + phone B (restore target), a 32-bit-only emulator (armeabi-v7a/x86), a device/profile where Keystore fails, Play Console
**Fixtures:** `en-novel.epub`; Play build v22 and the current build; the release `.aab`
**Preconditions:** Valid DeepL key; Android backup enabled on phone A.

**Steps**
1. Phone A: install v22, choose ML Kit explicitly, open a book. Update to the current build (Play internal track), open the book, kill and relaunch. → ✓ ML Kit is still used; nothing switched to Offline HQ (OHQ-002). Repeat with DeepL chosen before the update. → ✓ DeepL kept (OHQ-002).
2. 32-bit emulator: sideload the current build (a universal debug APK), never choose a translator, open a book in a new language. → ✓ ML Kit is the default (OHQ-003).
3. On the same emulator: Settings → Translation → Change. → ✓ Offline HQ is not offered; if forced (debug), "Offline HQ isn't available on this device" — no silent or broken state (OHQ-021).
4. Play Console → Device catalog for the release: filter armeabi-v7a-only devices. → ✓ they are excluded ("not compatible"), since .so files ship only for arm64-v8a/x86_64 — documented, not a bug (OHQ-022).
5. Phone A: DeepL selected with the key; run `adb shell bmgr backupnow <package>` and inspect the backup set. → ✓ `translator_keys_enc` is not in the backup; no plaintext key anywhere in it (SET-017).
6. Phone B: restore phone A's backup and open a book. → ✓ DeepL is the provider but its key did not come along; banner "DeepL API key isn't set — tap Switch translator to add it"; no ML Kit text labelled DeepL (ONL-008, ONL-001).
7. Long-press a word and tap Save. → ✓ toast "TRANSLATION_FAILED" (or equivalent); no blank entry in Words (POP-012, WRD-006).
8. Tap Switch translator → DeepL → enter the key → Save. → ✓ the dialog closes and translation proceeds (ONL-008).
9. Keystore-failing profile: enter a DeepL key, cold-restart the app, open a book; repeat the cold start twice. → ✓ each cold start shows a readable "no key" banner (the key lived in memory only) (ONL-007).
10. Release audit on the `.aab`/APK (jadx or apkanalyzer + grep) and the source tree: search for API keys/tokens and `http://` base URLs; check the OkHttp logging interceptor. → ✓ no secrets; every translator base URL is HTTPS; the logging interceptor exists only in debug builds (ONL-022).

**Covers:** OHQ-002, OHQ-003, OHQ-021, OHQ-022, SET-017, ONL-001, ONL-007, ONL-008, ONL-022, POP-012 (also WRD-006)

### J-TRN-16 — Real-device feel: first ML Kit download, gestures, speech, edge languages

**Priority:** P2 (P1 checkpoints: MLK-004, MLK-008, POP-008, POP-015, LNG-007, OHQ-006) — run as P1 · **Tags:** manual, network, phone, tablet · **Devices:** real arm64 phone and tablet (mid/low-end phone preferred), plus a fresh API 30 AVD for step 4
**Fixtures:** `en-novel.epub`, `mixed-lang.epub`, `ru-book.epub`, `no-book.epub`
**Preconditions:** ML Kit EN→ES model not downloaded; Offline HQ EN→PT pack not installed; TTS voices for English and German installed; online.

**Steps**
1. Select ML Kit, set target Spanish, open `en-novel.epub` at an untranslated chapter in portrait. → ✓ the banner shows a "Downloading model" indicator before translations appear (MLK-004); known bug: only "Translating…" for up to ~90 s, no percent (MLK-005).
2. Do not touch the screen while shimmers turn into text. → ✓ the bottom panel stays put; no jump or drift (MLK-008).
3. Select Offline HQ, set target Portuguese, long-press a word. → ✓ the popup shows a spinner with "Downloading language model… N%", then Ready (POP-015).
4. Fresh API 30 AVD: select ML Kit and request a translation needing a model. → known bug: the model may silently fail to download; confirm on a real device instead (MLK-009).
5. Back on the real device, set target Russian. Start a handle drag and, mid-drag, pull down the notification shade (or swipe back). → ✓ the popup is not stuck on "Release to translate"; it resolves or closes (POP-008).
6. With a popup open, tap an illustration, then a margin, then a blank gap between paragraphs. → known bug: the popup only closes on a tap inside text; these taps do nothing (POP-026).
7. Fling rapidly back and forth across many screens for 10–15 s; stop. → ✓ no duplicated/garbled text, no translation on the wrong paragraph, nothing stuck mid-sentence; the banner settles to idle (TRN-023).
8. Settings: set "Speech rate" (and pitch, if present) to a non-default value; open the target-language control separately; restart the app. → ✓ rate/pitch and target language are separate controls and the rate/pitch kept its value (SET-015).
9. Open `mixed-lang.epub`; in the German paragraph select a word and tap Listen. → ✓ spoken with a German voice, at the adjusted rate (TRN-025, SET-015).
10. Scroll to the long Latin epigraph. → ✓ labelled "LA · Latin, not translated". Scroll to the one-line Latin fragment (< 60 chars). → ✓ by design it is treated as the book's English and translated as such (LNG-007).
11. Import and open `no-book.epub` (Norwegian). → known bug: detected as "DA" (Danish) instead of "NO"; no crash (LNG-008).
12. Set target German, open `ru-book.epub`, translate a few paragraphs on Offline HQ. → ✓ RU→EN then EN→DE are ensured in sequence with progress across both legs; a German reader judges the output fluent (OHQ-006). (The single-provider-id cache key is a unit-test concern, not checked here.)

**Covers:** MLK-004, MLK-005, MLK-008, MLK-009, POP-008, POP-015, POP-026, TRN-023, TRN-025, SET-015, LNG-007, LNG-008, OHQ-006

---

## Known bugs

Expected to fail until fixed; reported, never blocking.

| ID | Journey / step | Symptom |
|---|---|---|
| POP-026 | J-TRN-16 step 6 | Tapping an illustration, margin or gap does not close the word popup |
| MLK-005 | J-TRN-16 step 1 | ML Kit model download shows no percentage (only "Translating…" up to ~90 s) |
| MLK-009 | J-TRN-16 step 4 | ML Kit model may silently fail to download on a fresh API 30 emulator |
| LNG-008 | J-TRN-16 step 11 | Norwegian books detected as Danish |

## Notes for the catalog owner

- "Translate the rest of this chapter" exists only in the Translator picker for paid engines (`TranslationProviderCategory.ADVANCED`) and only while the pane is visible, so TRN-006/007/008/019 need a paid key and run manually. TRN-007's precondition "prefer a free/offline provider" cannot be met.
- ONL-001 (P0) can't be reached on a clean install for DeepL, because SET-003 opens the key dialog instead of selecting a keyless provider. It is checked automatically through Azure's Region-only save (J-TRN-08 step 7, if the dialog allows it) and manually for DeepL after a restore (J-TRN-15 step 6).
- POP-004/005/006/007 need a countable request signal in debug builds (see "Request counting" above).

## Coverage

| Prefix | IDs | Journeys |
|---|---|---|
| TRN | 001, 005 | J-01 |
| | 002, 003, 004 | J-03 |
| | 009, 021, 022, 024 | J-10 |
| | 010, 020 | J-11 |
| | 006, 007, 008 | J-12 |
| | 011, 012, 015, 026 | J-04 |
| | 013, 014 | J-05 |
| | 016, 017, 018, 019 | J-13 |
| | 023, 025 | J-16 |
| OHQ | 001, 024, 025 | J-01 |
| | 004 | J-01, J-03 |
| | 005, 012 | J-03 |
| | 007, 008 | J-09 |
| | 009, 011 | J-03, J-09 |
| | 010 | J-09 |
| | 013, 015, 018, 019, 026 | J-13 |
| | 014, 016, 017, 020, 023 | J-14 |
| | 002, 003, 021, 022 | J-15 |
| | 006 | J-16 |
| POP | 001, 002 | J-01 (002 also J-11) |
| | 003, 004, 005, 007, 010, 011, 013, 014, 019, 021, 022 | J-02 |
| | 006 | J-02, J-11 |
| | 009 | J-11 |
| | 012 | J-08, J-15 |
| | 016 | J-02, J-07, J-13 |
| | 017 | J-07 |
| | 018 | J-13 |
| | 020 | J-03 |
| | 023, 024, 025 | J-04 |
| | 008, 015, 026 | J-16 |
| ONL | 002 | J-07 |
| | 001 | J-08, J-15 |
| | 009, 010, 011, 013, 014, 020, 021 | J-08 |
| | 016 | J-01 |
| | 003, 005, 006, 015, 017, 018 | J-11 |
| | 004, 012, 019 | J-12 (012 field check also J-08) |
| | 007, 008, 022 | J-15 |
| MLK | 001, 002 | J-05 |
| | 003, 007 | J-09 |
| | 010 | J-03, J-09 |
| | 006 | J-07 |
| | 011 | J-13 |
| | 004, 005, 008, 009 | J-16 |
| LNG | 001, 002, 004, 005, 006, 009 | J-06 |
| | 003 | J-05 |
| | 007, 008 | J-16 |
| SET | 001 | J-03 |
| | 002 | J-01 |
| | 003 | J-07, J-08 |
| | 004, 010 | J-07, J-11 |
| | 005, 006, 007, 008, 009, 011 | J-07 |
| | 012, 013 | J-11 |
| | 014, 016 | J-09 |
| | 015 | J-16 |
| | 017 | J-15 |
