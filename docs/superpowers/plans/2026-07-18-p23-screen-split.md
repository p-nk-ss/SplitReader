# P23 — Split Giant Screen Files Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Break the three giant Compose screen files into cohesive same-package files by MOVING top-level declarations verbatim — no behavior/markup/logic change (P23).

**Architecture:** Kotlin top-level declarations resolve by `package.name`, not by file, so moving them between files in the SAME package needs NO import changes at call sites. The only edit beyond the move is bumping a moved `private` declaration to `internal` when it becomes referenced across the new file boundary (the compiler flags it as "unresolved reference"). Bodies are moved byte-for-byte.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose. Verification is the compiler (every unresolved reference is a build error) + existing unit tests; there are no UI tests.

## Global Constraints

- New files go in the SAME package as the screen (`presentation.reader` / `presentation.home` / `presentation.words`) — declare the correct `package` line.
- Move declarations VERBATIM: do not change bodies, signatures, argument order, markup, values, or `@Composable`/`@OptIn` annotations. Only their file location and (where required) visibility change.
- Raise visibility ONLY as needed: a moved `private` decl referenced from another file becomes `internal` (never `public`). Declarations that are already `internal`/public keep their modifier. Purely file-local `private` helpers stay `private`.
- Do NOT touch call-site imports (moves are within-package). Do NOT edit any screen other than the one in the task.
- Each new file imports exactly what its moved code uses; remove now-unused imports from the core screen file.
- Commit trailer on every commit: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- Verify per task: `:app:compileDebugKotlin :app:testDebugUnitTest` green.

---

## File Structure (after all tasks)

- `presentation/reader/`: `ReaderScreen.kt` (core), `ReaderPane.kt`, `ReaderSelection.kt`, `ReaderChrome.kt`, `ReaderDialogs.kt`.
- `presentation/home/`: `HomeScreen.kt` (core), `HomeCovers.kt`, `HomeSections.kt`.
- `presentation/words/`: `WordsScreen.kt` (core), `WordsMasterPane.kt`, `WordsDetailPane.kt`.

---

## Task 1: ReaderScreen · A — pane + selection

**Files:**
- Create: `app/src/main/java/com/example/splitreader/presentation/reader/ReaderPane.kt`
- Create: `app/src/main/java/com/example/splitreader/presentation/reader/ReaderSelection.kt`
- Modify: `app/src/main/java/com/example/splitreader/presentation/reader/ReaderScreen.kt`

**Declarations to move** (cut from `ReaderScreen.kt`, paste verbatim):
- Into `ReaderPane.kt`: `BookSpread`, `Illustration`, `ParagraphItem`, `TranslationPlaceholder`, `TranslationBubble`, `BubbleChip`, `PageEdgeTap`, `PageGutter`.
- Into `ReaderSelection.kt`: `ParagraphActionsOverlay`, `enum class HandleSide`, `handleAnchor`, `drawMarker` (the `DrawScope.drawMarker` extension), `snapToWordStart`, `snapToWordEnd`.

- [ ] **Step 1: Create `ReaderPane.kt`**

Add `package com.example.splitreader.presentation.reader` and the imports its moved composables use (copy the relevant `androidx.compose.*`, project theme/model imports from `ReaderScreen.kt`). Move the 8 pane declarations here verbatim.

- [ ] **Step 2: Create `ReaderSelection.kt`**

Same package line + needed imports (incl. `androidx.compose.ui.graphics.drawscope.DrawScope`, `java.text.BreakIterator`, etc. as used). Move the 6 selection declarations here verbatim.

- [ ] **Step 3: Remove the moved declarations from `ReaderScreen.kt`**

Delete those 14 declarations from `ReaderScreen.kt`. Remove imports that are now unused there (the compiler warns on unused imports; the build still passes, but clean them for hygiene).

- [ ] **Step 4: Compile; bump visibility on unresolved references**

Run: `./gradlew :app:compileDebugKotlin`.
For each `unresolved reference` / `cannot access '<decl>': it is private in file` error, change that moved declaration's `private` → `internal` (a decl still referenced by the code remaining in `ReaderScreen.kt`, or by the sibling new file). Re-run until `BUILD SUCCESSFUL`.
> Expected candidates needing `internal`: `ParagraphItem`, `TranslationBubble`, `BookSpread` (called from `ReaderContent`), and any pane/selection helper the other new file calls. Only bump what the compiler demands.

- [ ] **Step 5: Full unit suite**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` → BUILD SUCCESSFUL; all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/splitreader/presentation/reader/ReaderPane.kt \
  app/src/main/java/com/example/splitreader/presentation/reader/ReaderSelection.kt \
  app/src/main/java/com/example/splitreader/presentation/reader/ReaderScreen.kt
git commit -m "refactor(reader): extract reading pane + selection composables from ReaderScreen (P23)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: ReaderScreen · B — chrome + dialogs

**Files:**
- Create: `presentation/reader/ReaderChrome.kt`, `presentation/reader/ReaderDialogs.kt`
- Modify: `presentation/reader/ReaderScreen.kt`

**Declarations to move** (cut from `ReaderScreen.kt`, verbatim):
- Into `ReaderChrome.kt`: `ReaderTopBar`, `LangChip`, `ChapterMasthead`, `ReaderStatusFooter`, `TranslationBanner`, `TranslationErrorBanner`.
- Into `ReaderDialogs.kt`: `EditorialDialog` (already `internal` — keep), `LanguagePickerDialog`, `DisplaySettingsDialog`, `ChapterPickerDialog`, `BookmarksDialog`, `toRoman`.

- [ ] **Step 1: Create `ReaderChrome.kt`** — package line + needed imports; move the 6 chrome declarations verbatim.

- [ ] **Step 2: Create `ReaderDialogs.kt`** — package line + needed imports; move the 6 dialog/util declarations verbatim. `EditorialDialog` stays `internal` (it is used elsewhere in the package, e.g. by `TranslatorPickerDialog`).

- [ ] **Step 3: Remove the moved declarations from `ReaderScreen.kt`** and prune now-unused imports.

- [ ] **Step 4: Compile; bump visibility on unresolved references**

Run: `./gradlew :app:compileDebugKotlin`. Bump each compiler-flagged moved `private` → `internal` (e.g. `ReaderTopBar`, `ChapterMasthead`, dialogs called from `ReaderContent`). Re-run until `BUILD SUCCESSFUL`.
> `ReaderScreen.kt` now holds only `ReaderRoute`, `ReaderLoadingScreen`, `ReaderErrorScreen`, `ReaderContent`.

- [ ] **Step 5: Full unit suite** → `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**
```bash
git add app/src/main/java/com/example/splitreader/presentation/reader/ReaderChrome.kt \
  app/src/main/java/com/example/splitreader/presentation/reader/ReaderDialogs.kt \
  app/src/main/java/com/example/splitreader/presentation/reader/ReaderScreen.kt
git commit -m "refactor(reader): extract chrome + dialogs from ReaderScreen (P23)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: HomeScreen — covers + sections

**Files:**
- Create: `presentation/home/HomeCovers.kt`, `presentation/home/HomeSections.kt`
- Modify: `presentation/home/HomeScreen.kt`

**Declarations to move** (cut from `HomeScreen.kt`, verbatim):
- Into `HomeCovers.kt`: `enum class CoverMotif`, `CoverSpec` (the cover-spec data holder), `coverSpec`, `BookCover` (public — keep), `drawMotif` (the `DrawScope.drawMotif` extension), `ProgressRule` (public — keep), `BookCoverCard`, `HomeHeaderSkeleton`, `SkeletonCoverCard`.
- Into `HomeSections.kt`: `LibraryHeader`, `LibrarySearchBar`, `NoBooksMatch`, `StreakRibbon`, `StreakBar`, `ContinueReadingHero`, `ShelfHeader`, `FilterPill`, `EmptyLibrary`, `formatLastOpened`.

- [ ] **Step 1: Create `HomeCovers.kt`** — `package com.example.splitreader.presentation.home` + needed imports (incl. `androidx.compose.ui.graphics.drawscope.DrawScope`); move the cover declarations verbatim. `BookCover`/`ProgressRule` keep their public visibility (external packages import them by `presentation.home.BookCover` — unchanged by the move).

- [ ] **Step 2: Create `HomeSections.kt`** — package line + needed imports; move the section declarations verbatim.

- [ ] **Step 3: Remove the moved declarations from `HomeScreen.kt`** and prune now-unused imports. `HomeScreen.kt` keeps `HomeRoute`, `HomeScreen` (and any `BookItem`/state types it defines — leave those unless they're in the moved list; they are not).
> Note: `CoverMotif`/`CoverSpec`/`coverSpec` are consumed by `BookCover`/`BookCoverCard` (moved together) — verify none is referenced from the core screen; if it is, `internal` suffices.

- [ ] **Step 4: Compile; bump visibility on unresolved references**

Run: `./gradlew :app:compileDebugKotlin`. Bump each flagged moved `private` → `internal` (e.g. `BookCoverCard`, `ContinueReadingHero`, `LibraryHeader`, `StreakRibbon` — called from `HomeScreen`). Re-run until `BUILD SUCCESSFUL`.

- [ ] **Step 5: Full unit suite** → `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**
```bash
git add app/src/main/java/com/example/splitreader/presentation/home/HomeCovers.kt \
  app/src/main/java/com/example/splitreader/presentation/home/HomeSections.kt \
  app/src/main/java/com/example/splitreader/presentation/home/HomeScreen.kt
git commit -m "refactor(home): extract covers + sections from HomeScreen (P23)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: WordsScreen — master + detail panes

**Files:**
- Create: `presentation/words/WordsMasterPane.kt`, `presentation/words/WordsDetailPane.kt`
- Modify: `presentation/words/WordsScreen.kt`

**Declarations to move** (cut from `WordsScreen.kt`, verbatim):
- Into `WordsMasterPane.kt`: `MasterPane`, `LangPill`, `MasterSearchField`, `DateGroupHeader`, `WordListItem`, `EmptyMaster`.
- Into `WordsDetailPane.kt`: `DetailPane`, `EmptyDetail`, `WordDetail`, `ContextQuoteCard`, `BookInfoRow`, `ActionRow`, `ActionChip`, `NotesCard`, `NoteDialog`, `relativeDate`.

- [ ] **Step 1: Create `WordsMasterPane.kt`** — `package com.example.splitreader.presentation.words` + needed imports; move the master declarations verbatim.

- [ ] **Step 2: Create `WordsDetailPane.kt`** — package line + needed imports; move the detail declarations verbatim.

- [ ] **Step 3: Remove the moved declarations from `WordsScreen.kt`** and prune now-unused imports. `WordsScreen.kt` keeps `WordsRoute`, `WordsScreen`.

- [ ] **Step 4: Compile; bump visibility on unresolved references**

Run: `./gradlew :app:compileDebugKotlin`. Bump each flagged moved `private` → `internal` (e.g. `MasterPane`, `DetailPane` called from `WordsScreen`; `relativeDate`/`WordDetail` shared across the two panes). Re-run until `BUILD SUCCESSFUL`.

- [ ] **Step 5: Full unit suite** → `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**
```bash
git add app/src/main/java/com/example/splitreader/presentation/words/WordsMasterPane.kt \
  app/src/main/java/com/example/splitreader/presentation/words/WordsDetailPane.kt \
  app/src/main/java/com/example/splitreader/presentation/words/WordsScreen.kt
git commit -m "refactor(words): extract master + detail panes from WordsScreen (P23)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Definition of Done (maps to spec §6)

1. Each core screen file is significantly smaller; the new files are cohesive and named by responsibility (`ReaderPane`/`ReaderSelection`/`ReaderChrome`/`ReaderDialogs`, `HomeCovers`/`HomeSections`, `WordsMasterPane`/`WordsDetailPane`). *(Tasks 1–4)*
2. No behavior/markup change: moved bodies are byte-identical; only file location and (where the compiler demanded it) `private → internal` visibility changed — nothing raised to `public`. *(All tasks)*
3. Call-site imports are unchanged (moves are within-package); external references to `BookCover`/`ProgressRule` still resolve. *(Task 3)*
4. `:app:compileDebugKotlin` + `:app:testDebugUnitTest` green after each task. *(All tasks)*
5. `ReaderViewModel` and all non-target screens are untouched. *(All tasks)*
