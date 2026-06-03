# Continue Reading hero — wider button + synopsis fill

**Date:** 2026-06-03
**Status:** Approved (design), pending spec review
**Area:** `presentation/home` (Library), data layer, parsers

## Problem

A recent visual polish pass changed the "Continue reading" CTA so its label now
wraps onto **two lines**, which looks broken. Separately, the whole Continue
Reading hero card reads as **empty in the center** — the cover is 176dp tall but
the title/author/progress text only fills the top portion, leaving a dead zone.

Two issues to fix:

1. The "Continue reading" button wraps to two lines; the "Last opened" card next
   to it should widen symmetrically.
2. The center of the hero is empty and should carry meaningful content.

## Root cause

- **Button wrap:** in `ContinueReadingHero` (`HomeScreen.kt`), the right column is
  locked to `width(160.dp)`. The filled `LibraryTagButton` ("CONTINUE READING",
  JetBrains Mono SemiBold, 11sp, 1.2sp letter-spacing, leading diamond, start=14 /
  end=16 padding) needs ~185–195dp on one line, so 160dp forces a wrap.
- **Empty center:** the center column (`weight(1f)`) holds eyebrow + title +
  author + progress, which is shorter than the 176dp cover, leaving vertical
  whitespace with nothing pinned to the bottom.

## Decisions (from brainstorming)

- **Center content:** the book **description**, not a reading excerpt.
- **Fallback when no description:** the book's **first paragraph**.
- These collapse into a single stored field, `synopsis = description ?: firstParagraph`,
  computed at parse/import time so the Library screen never reads book files.

## Non-goals

- No reading-excerpt-at-scroll-position capture (rejected in favor of description).
- No change to reading, translation, or progress-tracking behavior.
- No new dependencies.
- No redesign of the cover, streak ribbon, header, or grid cards.

## Design

### 1. Data layer — one new nullable field `synopsis`

A single nullable column carries the center text, populated at parse time.

- **`domain/model/Book.kt`** — add `val synopsis: String? = null`.
- **`data/local/BookEntity.kt`** — add `val synopsis: String? = null`.
- **`data/local/AppDatabase.kt`** — bump `version = 3` → `version = 4`.
- **`di/DatabaseModule.kt`** — add `MIGRATION_3_4` and register it:
  ```sql
  ALTER TABLE books ADD COLUMN synopsis TEXT
  ```
  A proper migration is required so existing libraries are **preserved**; a bare
  version bump would fall through to `fallbackToDestructiveMigration()` and wipe
  the `books` table. (Note: 2→3 currently has no migration and rides the
  destructive fallback — out of scope to fix here, but do not remove the fallback.)
- **`data/repository/BookLibraryRepositoryImpl.kt`** — in `saveBook`, pass
  `synopsis = book.synopsis` into the `BookEntity`.
- **`presentation/home/HomeUiState.kt`** — add `val synopsis: String? = null` to
  `BookItem`.
- **`presentation/home/HomeViewModel.kt`** — map `BookEntity.synopsis` →
  `BookItem.synopsis`.

### 2. Parsers populate `synopsis`

Each parser computes `synopsis = description ?: firstParagraph`, where
`firstParagraph` is the first body paragraph long enough to be meaningful
(`length > 40`). A small shared helper normalizes the result:

- strip HTML tags / collapse whitespace,
- trim,
- clamp to ~280 characters (append `…` if truncated),
- return `null` if the result is blank.

Per-parser source of the description:

- **`EpubParser`** — read `dc:description` in `parseOpf` (same selector pattern as
  `dc:title` / `dc:creator`). Fallback: first qualifying paragraph from the parsed
  chapters.
- **`Fb2Parser`** — `<annotation>` text. Same paragraph fallback.
- **`MobiParser`** — EXTH record **103** (description); the `exth` map is already
  parsed. Same paragraph fallback.
- **Catalog (Gutenberg)** — the OPDS `<content>` / summary the catalog code already
  reads; set `synopsis` on the `Book` saved by `CatalogViewModel`.

Helper placement: a small private/internal function near `HtmlChapterExtractor`
(shared parser utilities) so all parsers can reuse the normalization. First-paragraph
fallback is derived from the already-built `chapters` list, so it adds no extra I/O.

Legacy books imported before this change have `synopsis = null` (migration default);
their hero center stays empty but the widened layout still applies. They gain a
synopsis on re-import.

### 3. Layout — `ContinueReadingHero` in `HomeScreen.kt`

- **Right column width:** `width(160.dp)` → `width(190.dp)`. The "Last opened" card
  and the "Continue reading" button are both `fillMaxWidth()` inside this column, so
  one width change fixes the two-line button **and** widens "Last opened"
  symmetrically.
- **Center column:** give the `weight(1f)` column `Modifier.height(176.dp)` to match
  the cover. Insert the synopsis between the author line and the progress block:
  - `Text(book.synopsis)` — `Newsreader`, ~14sp, `palette.ink2`, `lineHeight`
    ~20sp, `maxLines = 4`, `TextOverflow.Ellipsis`. Render only when
    `synopsis != null`.
  - `Spacer(Modifier.weight(1f))` after the synopsis so the existing
    `ProgressRule` + chapter/percent `Row` pin to the **bottom** of the 176dp
    column, aligned with the cover's base.
  - When `synopsis == null`, the spacer alone holds the shape (progress still
    pinned to the bottom).

## Affected files

| File | Change |
|------|--------|
| `domain/model/Book.kt` | add `synopsis: String?` |
| `data/local/BookEntity.kt` | add `synopsis: String?` |
| `data/local/AppDatabase.kt` | `version` 3 → 4 |
| `di/DatabaseModule.kt` | add + register `MIGRATION_3_4` |
| `data/repository/BookLibraryRepositoryImpl.kt` | pass `synopsis` in `saveBook` |
| `presentation/home/HomeUiState.kt` | add `synopsis` to `BookItem` |
| `presentation/home/HomeViewModel.kt` | map `synopsis` |
| `presentation/home/HomeScreen.kt` | widen right column to 190dp; add synopsis + bottom-pin in center |
| `domain/parser/EpubParser.kt` | parse `dc:description` + fallback |
| `domain/parser/Fb2Parser.kt` | parse `<annotation>` + fallback |
| `domain/parser/MobiParser.kt` | EXTH 103 + fallback |
| `domain/parser/HtmlChapterExtractor.kt` (or sibling) | shared synopsis-normalization helper |
| Catalog book mapping (`CatalogViewModel` / repo) | set `synopsis` from OPDS summary |

## Testing / verification

- App compiles (`./gradlew :app:assembleDebug` equivalent).
- Migration 3→4: install previous version with books, upgrade, confirm library
  survives and `synopsis` column exists.
- Import an EPUB with `dc:description` → description shows in center.
- Import an EPUB without a description → first paragraph shows.
- Legacy (pre-migration) book → center empty, button on one line, "Last opened"
  widened, progress pinned to bottom.
- Verify "Continue reading" label renders on **one** line at 190dp.

## Risks

- Some EPUB descriptions contain HTML markup → the normalization helper must strip
  it (covered above).
- Very short books may have no qualifying first paragraph → `synopsis = null` →
  graceful empty center (covered).
