# Continue Reading synopsis + wider button — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fill the empty center of the Library "Continue Reading" hero with the book's description (falling back to its first paragraph), and widen the right column so the "Continue reading" button no longer wraps to two lines and the "Last opened" card widens with it.

**Architecture:** A single nullable `synopsis` field is computed once at parse/import time (`description ?: firstParagraph`, normalized + clamped) and stored in Room, so the Library screen needs no file I/O. A shared `SynopsisExtractor` helper does the normalization; each parser feeds it. The hero layout widens its right column to 190dp and pins the progress block to the bottom of a cover-height center column, with the synopsis filling the gap.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room 2.7.0, Hilt, Jsoup (HTML stripping), JUnit (JVM unit tests).

---

## File structure

| File | Responsibility | Change |
|------|----------------|--------|
| `domain/parser/SynopsisExtractor.kt` | Pure helper: normalize/clamp text, pick description-or-paragraph | **Create** |
| `app/src/test/java/.../domain/parser/SynopsisExtractorTest.kt` | JVM unit tests for the helper | **Create** |
| `domain/model/Book.kt` | Domain model gains `synopsis` | Modify |
| `data/local/BookEntity.kt` | Room entity gains `synopsis` | Modify |
| `data/local/AppDatabase.kt` | DB version 3 → 4 | Modify |
| `di/DatabaseModule.kt` | Add + register `MIGRATION_3_4` | Modify |
| `data/repository/BookLibraryRepositoryImpl.kt` | Persist `synopsis` in `saveBook` | Modify |
| `presentation/home/HomeUiState.kt` | `BookItem` gains `synopsis` | Modify |
| `presentation/home/HomeViewModel.kt` | Map entity `synopsis` → `BookItem` | Modify |
| `domain/parser/EpubParser.kt` | Parse `dc:description`, build synopsis | Modify |
| `domain/parser/Fb2Parser.kt` | Capture `<annotation>`, build synopsis | Modify |
| `domain/parser/MobiParser.kt` | EXTH 103, build synopsis | Modify |
| `presentation/home/HomeScreen.kt` | Widen right column to 190dp; synopsis + bottom-pin in center | Modify |

**No change needed:** catalog code — its books are parsed by `EpubParser` (`CatalogViewModel.downloadAndOpen`), so they inherit `synopsis` automatically.

**Verification commands** (run from repo root in PowerShell):
- Unit tests: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.splitreader.domain.parser.SynopsisExtractorTest"`
- Compile: `.\gradlew.bat :app:compileDebugKotlin`
- Full build: `.\gradlew.bat :app:assembleDebug`

---

## Task 1: `SynopsisExtractor` helper (TDD)

**Files:**
- Create: `app/src/main/java/com/example/splitreader/domain/parser/SynopsisExtractor.kt`
- Test: `app/src/test/java/com/example/splitreader/domain/parser/SynopsisExtractorTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/example/splitreader/domain/parser/SynopsisExtractorTest.kt`:

```kotlin
package com.example.splitreader.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SynopsisExtractorTest {

    @Test
    fun `normalize returns null for null or blank`() {
        assertNull(SynopsisExtractor.normalize(null))
        assertNull(SynopsisExtractor.normalize("   "))
    }

    @Test
    fun `normalize strips html and collapses whitespace`() {
        val raw = "<p>The story   of  <b>Emma</b>.</p>"
        assertEquals("The story of Emma.", SynopsisExtractor.normalize(raw))
    }

    @Test
    fun `normalize clamps long text and appends ellipsis`() {
        val result = SynopsisExtractor.normalize("a".repeat(400))!!
        assertEquals(281, result.length) // 280 chars + ellipsis
        assertEquals('…', result.last())
    }

    @Test
    fun `build prefers description over paragraph`() {
        val result = SynopsisExtractor.build(
            description = "A real description here.",
            paragraphs = listOf("This is the opening paragraph of the book body."),
        )
        assertEquals("A real description here.", result)
    }

    @Test
    fun `build falls back to first meaningful paragraph`() {
        val result = SynopsisExtractor.build(
            description = null,
            paragraphs = listOf("Short.", "This opening paragraph is clearly long enough to qualify."),
        )
        assertEquals("This opening paragraph is clearly long enough to qualify.", result)
    }

    @Test
    fun `build returns null when nothing qualifies`() {
        assertNull(SynopsisExtractor.build(null, listOf("tiny", "also")))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.splitreader.domain.parser.SynopsisExtractorTest"`
Expected: FAIL — compilation error / unresolved reference `SynopsisExtractor`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/example/splitreader/domain/parser/SynopsisExtractor.kt`:

```kotlin
package com.example.splitreader.domain.parser

import org.jsoup.Jsoup

/**
 * Builds the short "synopsis" teaser shown in the Library's Continue Reading hero.
 *
 * Prefers a real book description; falls back to the opening paragraph of the body.
 * Strips any HTML, collapses whitespace, and clamps to a teaser length so it stays
 * a few lines at most.
 */
object SynopsisExtractor {

    private const val MAX_LENGTH = 280
    private const val MIN_PARAGRAPH_LENGTH = 40
    private val WHITESPACE = Regex("\\s+")

    /** Strips HTML/whitespace, trims, clamps to [MAX_LENGTH]; returns null if blank. */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val text = Jsoup.parse(raw).text().replace(WHITESPACE, " ").trim()
        if (text.isBlank()) return null
        return if (text.length <= MAX_LENGTH) {
            text
        } else {
            text.take(MAX_LENGTH).trimEnd().trimEnd(',', '.', ';', ':') + "…"
        }
    }

    /** [description] if present, else the first meaningful body paragraph — normalized. */
    fun build(description: String?, paragraphs: List<String>): String? =
        normalize(description)
            ?: normalize(paragraphs.firstOrNull { it.trim().length >= MIN_PARAGRAPH_LENGTH })
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.splitreader.domain.parser.SynopsisExtractorTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/splitreader/domain/parser/SynopsisExtractor.kt app/src/test/java/com/example/splitreader/domain/parser/SynopsisExtractorTest.kt
git commit -m "feat(parser): add SynopsisExtractor helper for Continue Reading teaser"
```

---

## Task 2: Data model, entity, and Room migration

**Files:**
- Modify: `app/src/main/java/com/example/splitreader/domain/model/Book.kt`
- Modify: `app/src/main/java/com/example/splitreader/data/local/BookEntity.kt`
- Modify: `app/src/main/java/com/example/splitreader/data/local/AppDatabase.kt:15`
- Modify: `app/src/main/java/com/example/splitreader/di/DatabaseModule.kt`

- [ ] **Step 1: Add `synopsis` to the domain model**

In `domain/model/Book.kt`, add the field as the last property:

```kotlin
package com.example.splitreader.domain.model

data class Book(
    val title: String,
    val author: String,
    val chapters: List<Chapter>,
    val filePath: String,
    val coverPath: String? = null,
    val synopsis: String? = null,
)
```

- [ ] **Step 2: Add `synopsis` to the Room entity**

In `data/local/BookEntity.kt`, add the column:

```kotlin
package com.example.splitreader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val lastOpenedAt: Long,
    val chapterCount: Int,
    val synopsis: String? = null,
)
```

- [ ] **Step 3: Bump the database version**

In `data/local/AppDatabase.kt`, change `version = 3` to `version = 4`:

```kotlin
@Database(
    entities = [
        TranslationCacheEntity::class,
        BookEntity::class,
        BookmarkEntity::class,
        NoteEntity::class,
        SavedWordEntity::class,
        ReadingSessionEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
```

- [ ] **Step 4: Add and register `MIGRATION_3_4`**

In `di/DatabaseModule.kt`, add the migration object below the existing `MIGRATION_1_2` (after line 37):

```kotlin
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN synopsis TEXT")
    }
}
```

Then register it in the `addMigrations(...)` call inside `provideDatabase` (currently `.addMigrations(MIGRATION_1_2)`):

```kotlin
            .addMigrations(MIGRATION_1_2, MIGRATION_3_4)
```

Leave `.fallbackToDestructiveMigration()` in place (existing behavior; out of scope to change).

- [ ] **Step 5: Compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (no unresolved references; `BookEntity` constructor call in the repository still compiles because `synopsis` has a default).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/splitreader/domain/model/Book.kt app/src/main/java/com/example/splitreader/data/local/BookEntity.kt app/src/main/java/com/example/splitreader/data/local/AppDatabase.kt app/src/main/java/com/example/splitreader/di/DatabaseModule.kt
git commit -m "feat(data): add synopsis column with Room migration 3->4"
```

---

## Task 3: Persist and surface `synopsis`

**Files:**
- Modify: `app/src/main/java/com/example/splitreader/data/repository/BookLibraryRepositoryImpl.kt:17-28`
- Modify: `app/src/main/java/com/example/splitreader/presentation/home/HomeUiState.kt:3-12`
- Modify: `app/src/main/java/com/example/splitreader/presentation/home/HomeViewModel.kt:72-83`

- [ ] **Step 1: Persist `synopsis` in `saveBook`**

In `BookLibraryRepositoryImpl.kt`, add `synopsis = book.synopsis` to the `BookEntity` built in `saveBook`:

```kotlin
    override suspend fun saveBook(book: Book) {
        bookDao.upsert(
            BookEntity(
                uri = book.filePath,
                title = book.title,
                author = book.author,
                coverPath = book.coverPath,
                lastOpenedAt = System.currentTimeMillis(),
                chapterCount = book.chapters.size,
                synopsis = book.synopsis,
            )
        )
    }
```

- [ ] **Step 2: Add `synopsis` to `BookItem`**

In `HomeUiState.kt`, add the field to `BookItem`:

```kotlin
data class BookItem(
    val uri: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val chapterCount: Int,
    val lastChapterIndex: Int = 0,
    val isFinished: Boolean = false,
    val lastOpenedAt: Long = 0L,
    val synopsis: String? = null,
)
```

- [ ] **Step 3: Map `synopsis` in the ViewModel**

In `HomeViewModel.kt`, add `synopsis = it.synopsis` to the `BookItem` built inside the `books.map { ... }` block:

```kotlin
            books = books.map {
                BookItem(
                    uri = it.uri,
                    title = it.title,
                    author = it.author,
                    coverPath = it.coverPath,
                    chapterCount = it.chapterCount,
                    lastChapterIndex = progressManager.getLastChapter(it.uri),
                    isFinished = progressManager.isFinished(it.uri),
                    lastOpenedAt = it.lastOpenedAt,
                    synopsis = it.synopsis,
                )
            },
```

- [ ] **Step 4: Compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/splitreader/data/repository/BookLibraryRepositoryImpl.kt app/src/main/java/com/example/splitreader/presentation/home/HomeUiState.kt app/src/main/java/com/example/splitreader/presentation/home/HomeViewModel.kt
git commit -m "feat(home): carry synopsis from Room through to BookItem"
```

---

## Task 4: EpubParser populates `synopsis`

**Files:**
- Modify: `app/src/main/java/com/example/splitreader/domain/parser/EpubParser.kt`

- [ ] **Step 1: Add `description` to `OpfData`**

In `EpubParser.kt`, add a field to the `OpfData` data class (currently ends with `navItemIds: Set<String>`):

```kotlin
    private data class OpfData(
        val title: String,
        val author: String,
        val spineIds: List<String>,
        val manifestMap: Map<String, String>,
        val coverHref: String?,
        val navItemIds: Set<String>,
        val description: String?,
    )
```

- [ ] **Step 2: Parse `dc:description` in `parseOpf`**

In `parseOpf`, after the `author` is resolved (after line 114) add:

```kotlin
        val description = doc.selectFirst("dc|description, dc\\:description")?.text()
            ?: doc.selectFirst("description")?.text()
```

Then include it in the returned `OpfData` (change the final `return OpfData(...)`):

```kotlin
        return OpfData(title, author, spineIds, manifestMap, coverHref, navItemIds, description)
```

- [ ] **Step 3: Build and pass `synopsis` from `parse`**

In `parse`, replace the final `return Book(...)` (line 98) with:

```kotlin
        val synopsis = SynopsisExtractor.build(opf.description, chapters.flatMap { it.paragraphs })
        return Book(opf.title, opf.author, chapters, uri.toString(), coverPath, synopsis)
```

(`SynopsisExtractor` is in the same package `com.example.splitreader.domain.parser`, so no import is needed.)

- [ ] **Step 4: Compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/splitreader/domain/parser/EpubParser.kt
git commit -m "feat(parser): populate synopsis from EPUB dc:description"
```

---

## Task 5: Fb2Parser populates `synopsis`

**Files:**
- Modify: `app/src/main/java/com/example/splitreader/domain/parser/Fb2Parser.kt`

- [ ] **Step 1: Add annotation-capture state**

In `parseInternal`, alongside the other state declarations (e.g. after `var currentText = StringBuilder()` at line 52) add:

```kotlin
        var insideAnnotation = false
        val annotationText = StringBuilder()
```

- [ ] **Step 2: Set the flag on the `annotation` tags**

In the `START_TAG` `when` block, add a branch (e.g. after the `tagName == "body"` branch at line 98):

```kotlin
                    tagName == "annotation" -> insideAnnotation = true
```

In the `END_TAG` `when` block, add (e.g. after the `tagName == "body"` branch at line 174):

```kotlin
                    tagName == "annotation" -> insideAnnotation = false
```

- [ ] **Step 3: Capture annotation text**

Replace the `XmlPullParser.TEXT` branch (lines 177-183) with a version that captures annotation text *before* the existing `!insideBody` catch-all:

```kotlin
                XmlPullParser.TEXT -> {
                    val text = parser.text ?: ""
                    when {
                        insideCoverBinary -> coverBinaryData?.append(text)
                        insideAnnotation -> annotationText.append(text).append(' ')
                        insideParagraph || insideTitle || insideTextAuthor || !insideBody ->
                            currentText.append(text)
                    }
                }
```

- [ ] **Step 4: Build and pass `synopsis`**

Replace the final `return Book(...)` (line 194) with:

```kotlin
        val synopsis = SynopsisExtractor.build(
            annotationText.toString(),
            chapters.flatMap { it.paragraphs },
        )
        return Book(title = title, author = author, chapters = chapters, filePath = filePath, coverPath = coverPath, synopsis = synopsis)
```

- [ ] **Step 5: Compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/splitreader/domain/parser/Fb2Parser.kt
git commit -m "feat(parser): populate synopsis from FB2 <annotation>"
```

---

## Task 6: MobiParser populates `synopsis`

**Files:**
- Modify: `app/src/main/java/com/example/splitreader/domain/parser/MobiParser.kt:94-97`

- [ ] **Step 1: Build and pass `synopsis`**

In `parse`, after the `coverPath` is computed (line 94) and before the final `return Book(...)` (line 97), build the synopsis from EXTH record 103 (description) with the first-paragraph fallback, then update the return:

```kotlin
        val coverPath = extractCover(pdb, exth, firstImageIndex, uri.toString(), context)

        val synopsis = SynopsisExtractor.build(exth[103], chapters.flatMap { it.paragraphs })

        Log.d("MOBI", "Parsed: title=$title, author=$author, chapters=${chapters.size}, comp=$compression")
        return Book(title, author, chapters, uri.toString(), coverPath, synopsis)
```

- [ ] **Step 2: Compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/splitreader/domain/parser/MobiParser.kt
git commit -m "feat(parser): populate synopsis from MOBI EXTH 103"
```

---

## Task 7: Hero layout — widen right column + synopsis fill

**Files:**
- Modify: `app/src/main/java/com/example/splitreader/presentation/home/HomeScreen.kt:594-732`

- [ ] **Step 1: Widen the right column**

In `ContinueReadingHero`, change the right column width from `160.dp` to `190.dp` (currently `modifier = Modifier.width(160.dp)` at line 682):

```kotlin
        // Right column
        Column(
            modifier = Modifier.width(190.dp),
            verticalArrangement = Arrangement.spacedBy(sp.sm),
        ) {
```

(The "Last opened" card and the "Continue reading" button are both `fillMaxWidth()` inside this column, so they widen together — no other change needed for the button wrap or symmetry.)

- [ ] **Step 2: Make the center column cover-height and pin progress to the bottom**

In the same composable, replace the center "Title block" `Column` (lines 623-678, from `// Title block` through the closing `}` of that column) with the version below. It sets the column height to the cover height (176dp), inserts the synopsis after the author, and adds a weighted spacer so the progress block sits at the bottom:

```kotlin
        // Title block
        Column(modifier = Modifier.weight(1f).height(176.dp)) {
            Text(
                text = "CONTINUE READING",
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                color = palette.accent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = book.title,
                fontFamily = Newsreader,
                fontWeight = FontWeight.Medium,
                fontSize = 28.sp,
                letterSpacing = (-0.3).sp,
                lineHeight = 32.sp,
                color = palette.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "by ${book.author}",
                fontFamily = Newsreader,
                fontWeight = FontWeight.Normal,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                color = palette.ink2,
            )
            val synopsis = book.synopsis
            if (synopsis != null) {
                Spacer(Modifier.height(sp.sm))
                Text(
                    text = synopsis,
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = palette.ink2,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            // Progress bar
            ProgressRule(progress = progress, modifier = Modifier.fillMaxWidth().height(3.dp))
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "CH ${book.lastChapterIndex + 1} OF ${book.chapterCount}",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = palette.ink3,
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = palette.ink3,
                )
            }
        }
```

(All referenced symbols — `sp`, `palette`, `progress`, `Newsreader`, `JetBrainsMono`, `TextOverflow`, `FontStyle`, `ProgressRule` — are already in scope / imported in this file.)

- [ ] **Step 3: Compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/splitreader/presentation/home/HomeScreen.kt
git commit -m "feat(home): widen Continue Reading column to 190dp and fill center with synopsis"
```

---

## Task 8: Full build + manual verification

**Files:** none (verification only)

- [ ] **Step 1: Full debug build**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run all unit tests**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (SynopsisExtractorTest passes; no other tests broken).

- [ ] **Step 3: Manual checks on a device/emulator**

Install and verify on the Library screen:
- **Migration:** a library populated before this build still shows all its books after upgrade (no wipe).
- **Button:** the "Continue reading" label renders on **one** line; the "Last opened" card is visibly wider/symmetric.
- **EPUB with description:** import an EPUB that has `<dc:description>` → the description fills the hero center.
- **EPUB without description:** import an EPUB lacking a description → the first paragraph fills the center.
- **Legacy book:** a book imported before this change shows an empty center (no synopsis) but still has the widened layout and bottom-pinned progress.

- [ ] **Step 4: Final commit (if any cleanup was needed)**

```bash
git add -A
git commit -m "chore: verify Continue Reading synopsis build" --allow-empty
```

---

## Self-review notes

- **Spec coverage:** synopsis field (Task 2/3), description parsing per format (Tasks 4-6), first-paragraph fallback (Task 1 `build` + Tasks 4-6), migration preserving libraries (Task 2), 190dp column + symmetric Last opened (Task 7 Step 1), synopsis center + bottom-pinned progress (Task 7 Step 2), catalog inheritance (noted, no task needed). All covered.
- **Type consistency:** `SynopsisExtractor.build(description: String?, paragraphs: List<String>)` and `normalize(raw: String?)` used identically in Tasks 4-6; `Book`/`BookEntity`/`BookItem` all gain `synopsis: String? = null`.
- **No placeholders:** every code step shows full code; every run step shows the command and expected result.
