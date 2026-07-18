# Phase 3A — Domain Models & Mappers (P19) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the domain layer from importing Room `@Entity`/projection types (P19): introduce domain models + data-layer mappers, one aggregate at a time, each ending in a compiling, green build.

**Architecture:** For each aggregate, add a pure domain model (1:1 fields, no `androidx.room`), a data-layer mapper (`toDomain()`/`toEntity()` extension functions), switch the domain repository interface to domain types, map at the `RepositoryImpl` boundary (DAO still speaks entities/rows), and update consumers (use-cases, ViewModels, screens). Fields are identical across entity↔domain, so consumer edits are import/type swaps.

**Tech Stack:** Kotlin 2.0.21, JUnit4 (JVM, `app/src/test`), hand-written tests, no mock framework. Pure refactor — no behavior/schema change.

## Global Constraints

- Domain models live in `domain/model/`, carry **no `androidx.room` imports** and no Room annotations.
- Mappers live in the **data layer** (`data/repository/mapper/`) as extension functions — data may depend on domain, never the reverse.
- **id/time convention** (resolves a spec §3.1 nuance): domain models constructed on a write path by a use-case (`SavedWord`, `ReadingSession`) keep `id: Long = 0` as an "unpersisted" sentinel (Room autoGenerates on insert). Time fields (`createdAt`/`updatedAt`/`savedAt`) are set **explicitly by the use-case** (it already decides "now"); mappers copy every field 1:1 and never regenerate. Behavior must be byte-identical to today.
- Read-only domain models (`LibraryBook`, `Bookmark`) are produced only by mappers; their `id`/timestamps always come from the entity.
- DAOs are unchanged except the `ReadingSessionDao` projection rename (`*Minutes` → `*MinutesRow`).
- Each task ends: `:app:compileDebugKotlin` + `:app:testDebugUnitTest` green, and `domain/**` no longer imports that aggregate's `@Entity`/projection.
- Commit trailer on every commit: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

---

## File Structure

- `domain/model/LibraryBook.kt`, `Bookmark.kt`, `Note.kt`, `SavedWord.kt`, `ReadingSession.kt` — **new** domain models.
- `domain/model/stats/ReadingStats.kt` — **new** `DailyMinutes`/`BookMinutes`/`LangMinutes` domain projections.
- `data/repository/mapper/{Book,Bookmark,Note,SavedWord,ReadingSession}Mappers.kt` — **new** mappers.
- `domain/repository/{BookLibrary,Bookmark,Note,SavedWord,ReadingSession}Repository.kt` — **modify** (domain types).
- `data/repository/*RepositoryImpl.kt` — **modify** (map at boundary).
- `data/local/ReadingSessionDao.kt` — **modify** (`*Minutes` → `*MinutesRow`).
- `domain/usecase/{AddNote,SaveWord,EndReadingSession}UseCase.kt` — **modify** (build domain types).
- Consumers: `presentation/reader/ReaderViewModel.kt`, `presentation/words/{WordsViewModel,WordsScreen}.kt`, `presentation/almanac/{AlmanacViewModel,AlmanacScreen}.kt`, `presentation/home/HomeViewModel.kt`.
- Tests: `app/src/test/.../data/repository/mapper/*MapperTest.kt`.

---

## Task 1: LibraryBook

**Files:**
- Create: `app/src/main/java/com/example/splitreader/domain/model/LibraryBook.kt`
- Create: `app/src/main/java/com/example/splitreader/data/repository/mapper/BookMappers.kt`
- Create: `app/src/test/java/com/example/splitreader/data/repository/mapper/BookMapperTest.kt`
- Modify: `domain/repository/BookLibraryRepository.kt`, `data/repository/BookLibraryRepositoryImpl.kt`

**Interfaces:**
- Produces: `data class LibraryBook(...)`, `fun BookEntity.toDomain(): LibraryBook`, `BookLibraryRepository.getAllBooks(): Flow<List<LibraryBook>>`.

- [ ] **Step 1: Domain model**

`domain/model/LibraryBook.kt`:
```kotlin
package com.example.splitreader.domain.model

/** A book in the user's library (a persisted library row), distinct from the parsed [Book]. */
data class LibraryBook(
    val uri: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val lastOpenedAt: Long,
    val chapterCount: Int,
    val synopsis: String?,
)
```

- [ ] **Step 2: Mapper**

`data/repository/mapper/BookMappers.kt`:
```kotlin
package com.example.splitreader.data.repository.mapper

import com.example.splitreader.data.local.BookEntity
import com.example.splitreader.domain.model.LibraryBook

fun BookEntity.toDomain(): LibraryBook = LibraryBook(
    uri = uri,
    title = title,
    author = author,
    coverPath = coverPath,
    lastOpenedAt = lastOpenedAt,
    chapterCount = chapterCount,
    synopsis = synopsis,
)
```

- [ ] **Step 3: Mapper test (write first, then run — verify it passes with the mapper above)**

`app/src/test/java/com/example/splitreader/data/repository/mapper/BookMapperTest.kt`:
```kotlin
package com.example.splitreader.data.repository.mapper

import com.example.splitreader.data.local.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BookMapperTest {
    @Test fun toDomain_copiesEveryField() {
        val e = BookEntity("uri://x", "Title", "Author", "cover/p", 123L, 7, "syn")
        val d = e.toDomain()
        assertEquals(e.uri, d.uri)
        assertEquals(e.title, d.title)
        assertEquals(e.author, d.author)
        assertEquals(e.coverPath, d.coverPath)
        assertEquals(e.lastOpenedAt, d.lastOpenedAt)
        assertEquals(e.chapterCount, d.chapterCount)
        assertEquals(e.synopsis, d.synopsis)
    }
}
```
Run: `./gradlew :app:testDebugUnitTest --tests "*BookMapperTest"` → PASS.

- [ ] **Step 4: Switch the interface**

`domain/repository/BookLibraryRepository.kt`: remove `import com.example.splitreader.data.local.BookEntity`, add `import com.example.splitreader.domain.model.LibraryBook`, change the signature:
```kotlin
    fun getAllBooks(): Flow<List<LibraryBook>>
```
(Everything else unchanged — `saveBook(book: Book)` already domain.)

- [ ] **Step 5: Map in the impl**

`data/repository/BookLibraryRepositoryImpl.kt`: change `getAllBooks()` to map, add imports:
```kotlin
import com.example.splitreader.data.repository.mapper.toDomain
import com.example.splitreader.domain.model.LibraryBook
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
```
```kotlin
    override fun getAllBooks(): Flow<List<LibraryBook>> =
        bookDao.getAllBooks().map { list -> list.map { it.toDomain() } }
```
Leave `saveBook`/`touchBook`/etc. unchanged (they still build `BookEntity` internally — that is correct, mapping lives in data).

- [ ] **Step 6: Update consumers**

`HomeViewModel` consumes `getAllBooks()` and reads `it.uri/title/author/coverPath/chapterCount/lastOpenedAt/synopsis` — all present on `LibraryBook` with identical names, and it does not import `BookEntity`. Verify it compiles unchanged.
Run this grep to confirm no other consumer references `BookEntity` as the library-list type:
`grep -rn "getAllBooks\|BookEntity" app/src/main/java/com/example/splitreader/presentation` — update any that treat the list element as `BookEntity` (swap to `LibraryBook`; fields identical).

- [ ] **Step 7: Compile, test, verify domain is clean of this entity**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` → BUILD SUCCESSFUL.
Run: `grep -rn "BookEntity" app/src/main/java/com/example/splitreader/domain || echo "clean"` → `clean`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/splitreader/domain/model/LibraryBook.kt \
  app/src/main/java/com/example/splitreader/data/repository/mapper/BookMappers.kt \
  app/src/test/java/com/example/splitreader/data/repository/mapper/BookMapperTest.kt \
  app/src/main/java/com/example/splitreader/domain/repository/BookLibraryRepository.kt \
  app/src/main/java/com/example/splitreader/data/repository/BookLibraryRepositoryImpl.kt
git commit -m "refactor(domain): LibraryBook model + mapper; BookLibraryRepository returns domain type (P19)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Bookmark

**Files:**
- Create: `domain/model/Bookmark.kt`, `data/repository/mapper/BookmarkMappers.kt`, `app/src/test/.../mapper/BookmarkMapperTest.kt`
- Modify: `domain/repository/BookmarkRepository.kt`, `data/repository/BookmarkRepositoryImpl.kt`, `presentation/reader/ReaderViewModel.kt`

**Interfaces:**
- Produces: `data class Bookmark(...)`, `fun BookmarkEntity.toDomain(): Bookmark`, `BookmarkRepository.observeForBook(uri): Flow<List<Bookmark>>`.

- [ ] **Step 1: Domain model** — `domain/model/Bookmark.kt`:
```kotlin
package com.example.splitreader.domain.model

/** A paragraph-level bookmark within a book. */
data class Bookmark(
    val id: Long,
    val bookUri: String,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val label: String?,
    val createdAt: Long,
)
```

- [ ] **Step 2: Mapper** — `data/repository/mapper/BookmarkMappers.kt`:
```kotlin
package com.example.splitreader.data.repository.mapper

import com.example.splitreader.data.local.BookmarkEntity
import com.example.splitreader.domain.model.Bookmark

fun BookmarkEntity.toDomain(): Bookmark =
    Bookmark(id, bookUri, chapterIndex, paragraphIndex, label, createdAt)
```

- [ ] **Step 3: Mapper test** — `app/src/test/.../mapper/BookmarkMapperTest.kt`:
```kotlin
package com.example.splitreader.data.repository.mapper

import com.example.splitreader.data.local.BookmarkEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkMapperTest {
    @Test fun toDomain_copiesEveryField() {
        val e = BookmarkEntity(id = 5L, bookUri = "u", chapterIndex = 2, paragraphIndex = 9, label = "lbl", createdAt = 111L)
        val d = e.toDomain()
        assertEquals(e.id, d.id)
        assertEquals(e.bookUri, d.bookUri)
        assertEquals(e.chapterIndex, d.chapterIndex)
        assertEquals(e.paragraphIndex, d.paragraphIndex)
        assertEquals(e.label, d.label)
        assertEquals(e.createdAt, d.createdAt)
    }
}
```
Run: `./gradlew :app:testDebugUnitTest --tests "*BookmarkMapperTest"` → PASS.

- [ ] **Step 4: Interface** — `domain/repository/BookmarkRepository.kt`: remove the `BookmarkEntity` import, add `import com.example.splitreader.domain.model.Bookmark`, change `observeForBook(uri: String): Flow<List<Bookmark>>`. `add/remove/toggle` stay (they take primitives).

- [ ] **Step 5: Impl** — `data/repository/BookmarkRepositoryImpl.kt`: map the read, add imports (`...mapper.toDomain`, `domain.model.Bookmark`, `kotlinx.coroutines.flow.map`):
```kotlin
    override fun observeForBook(uri: String): Flow<List<Bookmark>> =
        dao.observeForBook(uri).map { list -> list.map { it.toDomain() } }
```
`add`/`remove`/`toggle` unchanged (build `BookmarkEntity` internally).

- [ ] **Step 6: Consumer** — `presentation/reader/ReaderViewModel.kt`: replace `import com.example.splitreader.data.local.BookmarkEntity` with `import com.example.splitreader.domain.model.Bookmark`; change every `BookmarkEntity` type reference to `Bookmark` (fields identical — `id/bookUri/chapterIndex/paragraphIndex/label/createdAt`). Find them: `grep -n "BookmarkEntity" app/src/main/java/com/example/splitreader/presentation/reader/ReaderViewModel.kt`.

- [ ] **Step 7: Compile, test, verify** — `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` → SUCCESSFUL; `grep -rn "BookmarkEntity" app/src/main/java/com/example/splitreader/domain || echo clean` → `clean`.

- [ ] **Step 8: Commit** — `refactor(domain): Bookmark model + mapper; BookmarkRepository returns domain type (P19)` with the trailer.

---

## Task 3: Note

**Files:**
- Create: `domain/model/Note.kt`, `data/repository/mapper/NoteMappers.kt`, `app/src/test/.../mapper/NoteMapperTest.kt`
- Modify: `domain/repository/NoteRepository.kt`, `data/repository/NoteRepositoryImpl.kt`, `domain/usecase/AddNoteUseCase.kt`, plus any Note consumer found by grep.

**Interfaces:**
- Produces: `data class Note(...)`, `fun NoteEntity.toDomain(): Note`, `fun Note.toEntity(): NoteEntity`, `NoteRepository` on `Note`.

- [ ] **Step 1: Domain model** — `domain/model/Note.kt`:
```kotlin
package com.example.splitreader.domain.model

/** A paragraph-anchored note or highlight within a book. */
data class Note(
    val bookUri: String,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val body: String,
    val isHighlight: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
```

- [ ] **Step 2: Mapper (both directions)** — `data/repository/mapper/NoteMappers.kt`:
```kotlin
package com.example.splitreader.data.repository.mapper

import com.example.splitreader.data.local.NoteEntity
import com.example.splitreader.domain.model.Note

fun NoteEntity.toDomain(): Note =
    Note(bookUri, chapterIndex, paragraphIndex, body, isHighlight, createdAt, updatedAt)

fun Note.toEntity(): NoteEntity =
    NoteEntity(bookUri, chapterIndex, paragraphIndex, body, isHighlight, createdAt, updatedAt)
```

- [ ] **Step 3: Mapper round-trip test** — `app/src/test/.../mapper/NoteMapperTest.kt`:
```kotlin
package com.example.splitreader.data.repository.mapper

import com.example.splitreader.data.local.NoteEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteMapperTest {
    @Test fun roundTrip_preservesEveryField() {
        val e = NoteEntity("u", 1, 4, "body", isHighlight = true, createdAt = 10L, updatedAt = 20L)
        assertEquals(e, e.toDomain().toEntity())
    }
}
```
Run: `./gradlew :app:testDebugUnitTest --tests "*NoteMapperTest"` → PASS.

- [ ] **Step 4: Interface** — `domain/repository/NoteRepository.kt`: swap `NoteEntity`→`Note` import + all three signatures (`observeForBook: Flow<List<Note>>`, `upsert(note: Note)`, `delete(note: Note)`).

- [ ] **Step 5: Impl** — `data/repository/NoteRepositoryImpl.kt`:
```kotlin
    override fun observeForBook(uri: String): Flow<List<Note>> =
        dao.observeForBook(uri).map { list -> list.map { it.toDomain() } }
    override suspend fun upsert(note: Note) = dao.upsert(note.toEntity())
    override suspend fun delete(note: Note) = dao.delete(note.toEntity())
```
Add imports (`...mapper.toDomain`, `...mapper.toEntity`, `domain.model.Note`, `kotlinx.coroutines.flow.map`); drop the `NoteEntity` import if now unused.

- [ ] **Step 6: Use-case** — `domain/usecase/AddNoteUseCase.kt`: build a `Note` instead of `NoteEntity`, setting BOTH timestamps to now (preserves the current upsert behavior where `createdAt` also defaults to now on every call):
```kotlin
import com.example.splitreader.domain.model.Note
...
        val now = System.currentTimeMillis()
        repository.upsert(
            Note(
                bookUri = bookUri,
                chapterIndex = chapterIndex,
                paragraphIndex = paragraphIndex,
                body = body,
                isHighlight = isHighlight,
                createdAt = now,
                updatedAt = now,
            )
        )
```
Remove `import com.example.splitreader.data.local.NoteEntity`.

- [ ] **Step 7: Other consumers** — `grep -rn "NoteEntity" app/src/main/java/com/example/splitreader/presentation` and update any (swap to `Note`, fields identical). If none, note that in the report.

- [ ] **Step 8: Compile, test, verify** — build green; `grep -rn "NoteEntity" app/src/main/java/com/example/splitreader/domain || echo clean` → `clean`.

- [ ] **Step 9: Commit** — `refactor(domain): Note model + mappers; NoteRepository/AddNoteUseCase on domain type (P19)` with the trailer.

---

## Task 4: SavedWord

**Files:**
- Create: `domain/model/SavedWord.kt`, `data/repository/mapper/SavedWordMappers.kt`, `app/src/test/.../mapper/SavedWordMapperTest.kt`
- Modify: `domain/repository/SavedWordRepository.kt`, `data/repository/SavedWordRepositoryImpl.kt`, `domain/usecase/SaveWordUseCase.kt`, `presentation/words/WordsViewModel.kt`, `presentation/words/WordsScreen.kt`

**Interfaces:**
- Produces: `data class SavedWord(...)` (with `id: Long = 0`), `fun SavedWordEntity.toDomain(): SavedWord`, `fun SavedWord.toEntity(): SavedWordEntity`, `SavedWordRepository` on `SavedWord`.

- [ ] **Step 1: Domain model** — `domain/model/SavedWord.kt`:
```kotlin
package com.example.splitreader.domain.model

/** A saved vocabulary word. [id] is 0 until persisted (Room autoGenerates on insert). */
data class SavedWord(
    val word: String,
    val sourceLang: String,
    val targetLang: String,
    val translation: String,
    val bookUri: String?,
    val bookTitle: String,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val contextSnippet: String,
    val partOfSpeech: String? = null,
    val note: String? = null,
    val id: Long = 0,
    val savedAt: Long = 0,
)
```
> Field order differs from the entity deliberately: required fields first, sentinel/optional (`id`, `savedAt`, `partOfSpeech`, `note`) with defaults last, so `SaveWordUseCase` can construct one without positional `id`/`savedAt`. The mapper names every argument, so order is irrelevant to correctness.

- [ ] **Step 2: Mapper (both directions)** — `data/repository/mapper/SavedWordMappers.kt`:
```kotlin
package com.example.splitreader.data.repository.mapper

import com.example.splitreader.data.local.SavedWordEntity
import com.example.splitreader.domain.model.SavedWord

fun SavedWordEntity.toDomain(): SavedWord = SavedWord(
    word = word, sourceLang = sourceLang, targetLang = targetLang, translation = translation,
    bookUri = bookUri, bookTitle = bookTitle, chapterIndex = chapterIndex,
    paragraphIndex = paragraphIndex, contextSnippet = contextSnippet,
    partOfSpeech = partOfSpeech, note = note, id = id, savedAt = savedAt,
)

fun SavedWord.toEntity(): SavedWordEntity = SavedWordEntity(
    id = id, word = word, partOfSpeech = partOfSpeech, sourceLang = sourceLang,
    targetLang = targetLang, translation = translation, bookUri = bookUri, bookTitle = bookTitle,
    chapterIndex = chapterIndex, paragraphIndex = paragraphIndex, contextSnippet = contextSnippet,
    note = note, savedAt = savedAt,
)
```

- [ ] **Step 3: Mapper round-trip test** — `app/src/test/.../mapper/SavedWordMapperTest.kt`:
```kotlin
package com.example.splitreader.data.repository.mapper

import com.example.splitreader.data.local.SavedWordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedWordMapperTest {
    @Test fun roundTrip_preservesEveryField() {
        val e = SavedWordEntity(
            id = 3L, word = "w", partOfSpeech = "noun", sourceLang = "en", targetLang = "ru",
            translation = "t", bookUri = "u", bookTitle = "bt", chapterIndex = 1, paragraphIndex = 2,
            contextSnippet = "ctx", note = "n", savedAt = 99L,
        )
        assertEquals(e, e.toDomain().toEntity())
    }
}
```
Run: `./gradlew :app:testDebugUnitTest --tests "*SavedWordMapperTest"` → PASS.

- [ ] **Step 4: Interface** — `domain/repository/SavedWordRepository.kt`: swap `SavedWordEntity`→`SavedWord` in every signature (`observeAll/observeByLang/search: Flow<List<SavedWord>>`, `findByWordAndLang(...): SavedWord?`, `save(word: SavedWord): Long`, `update(word: SavedWord)`, `delete(word: SavedWord)`).

- [ ] **Step 5: Impl** — `data/repository/SavedWordRepositoryImpl.kt`: map reads and writes:
```kotlin
    override fun observeAll(): Flow<List<SavedWord>> = dao.observeAll().map { it.map(SavedWordEntity::toDomain) }
    override fun observeByLang(code: String): Flow<List<SavedWord>> = dao.observeByLang(code).map { it.map(SavedWordEntity::toDomain) }
    override fun search(q: String): Flow<List<SavedWord>> = dao.search(q).map { it.map(SavedWordEntity::toDomain) }
    override fun countByLang(code: String): Flow<Int> = dao.countByLang(code)
    override suspend fun findByWordAndLang(word: String, lang: String): SavedWord? =
        dao.findByWordAndLang(word, lang)?.toDomain()
    override suspend fun save(word: SavedWord): Long = dao.insert(word.toEntity())
    override suspend fun update(word: SavedWord) = dao.update(word.toEntity())
    override suspend fun delete(word: SavedWord) = dao.delete(word.toEntity())
```
Add imports (`...mapper.toDomain`, `...mapper.toEntity`, `domain.model.SavedWord`, `kotlinx.coroutines.flow.map`). Keep `SavedWordEntity` import (used in the `SavedWordEntity::toDomain` reference).

- [ ] **Step 6: Use-case** — `domain/usecase/SaveWordUseCase.kt`: build a `SavedWord` (set `savedAt = System.currentTimeMillis()` explicitly to preserve the entity's former default; omit `id`/`partOfSpeech`/`note` — defaults apply):
```kotlin
import com.example.splitreader.domain.model.SavedWord
...
        savedWordRepository.save(
            SavedWord(
                word = normalized,
                sourceLang = sourceLang.code,
                targetLang = targetLang.code,
                translation = translation,
                bookUri = bookUri,
                bookTitle = bookTitle,
                chapterIndex = chapterIndex,
                paragraphIndex = paragraphIndex,
                contextSnippet = contextSnippet.take(120),
                savedAt = System.currentTimeMillis(),
            )
        )
```
Remove `import com.example.splitreader.data.local.SavedWordEntity`.

- [ ] **Step 7: Consumers** — `presentation/words/WordsViewModel.kt` and `WordsScreen.kt`: replace `import com.example.splitreader.data.local.SavedWordEntity` with `import com.example.splitreader.domain.model.SavedWord`; change every `SavedWordEntity` type reference to `SavedWord` (fields identical). Find them: `grep -rn "SavedWordEntity" app/src/main/java/com/example/splitreader/presentation/words`.

- [ ] **Step 8: Compile, test, verify** — build green; `grep -rn "SavedWordEntity" app/src/main/java/com/example/splitreader/domain || echo clean` → `clean`.

- [ ] **Step 9: Commit** — `refactor(domain): SavedWord model + mappers; SavedWordRepository/SaveWordUseCase on domain type (P19)` with the trailer.

---

## Task 5: ReadingSession + reading stats

**Files:**
- Create: `domain/model/ReadingSession.kt`, `domain/model/stats/ReadingStats.kt`, `data/repository/mapper/ReadingSessionMappers.kt`, `app/src/test/.../mapper/ReadingSessionMapperTest.kt`
- Modify: `data/local/ReadingSessionDao.kt` (`*Minutes`→`*MinutesRow`), `domain/repository/ReadingSessionRepository.kt`, `data/repository/ReadingSessionRepositoryImpl.kt`, `domain/usecase/EndReadingSessionUseCase.kt`, `presentation/almanac/AlmanacViewModel.kt`, `presentation/almanac/AlmanacScreen.kt`, and any HomeViewModel stat usage.

**Interfaces:**
- Produces: `data class ReadingSession(...)` (with `id: Long = 0`), domain `DailyMinutes/BookMinutes/LangMinutes`, `fun ReadingSessionEntity.toDomain()`, `fun ReadingSession.toEntity()`, `*MinutesRow.toDomain()`; `ReadingSessionRepository` on domain types.

- [ ] **Step 1: Rename the DAO projection rows**

`data/local/ReadingSessionDao.kt`: rename the three projection classes and their use in the DAO return types:
```kotlin
data class DailyMinutesRow(val day: String, val minutes: Int)
data class BookMinutesRow(val title: String, val minutes: Int)
data class LangMinutesRow(val lang: String, val minutes: Int)
```
Update `observeDailyMinutes(...): Flow<List<DailyMinutesRow>>`, `observeTimeByBook(...): Flow<List<BookMinutesRow>>`, `observeTimeByLang(...): Flow<List<LangMinutesRow>>`. The `@Query` SQL (column aliases `day`/`title`/`lang`/`minutes`) is unchanged — Room binds by alias. Compile to confirm nothing else referenced the old names besides `ReadingSessionRepositoryImpl` (fixed in Step 5).

- [ ] **Step 2: Domain models**

`domain/model/ReadingSession.kt`:
```kotlin
package com.example.splitreader.domain.model

/** A finished reading session. [id] is 0 until persisted (Room autoGenerates on insert). */
data class ReadingSession(
    val bookUri: String?,
    val bookTitle: String,
    val sourceLang: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Int,
    val paragraphsRead: Int,
    val id: Long = 0,
)
```
`domain/model/stats/ReadingStats.kt`:
```kotlin
package com.example.splitreader.domain.model.stats

/** Aggregated reading minutes for a single calendar day (yyyy-MM-dd). */
data class DailyMinutes(val day: String, val minutes: Int)

/** Aggregated reading minutes for a single book title. */
data class BookMinutes(val title: String, val minutes: Int)

/** Aggregated reading minutes for a single source language. */
data class LangMinutes(val lang: String, val minutes: Int)
```

- [ ] **Step 3: Mappers** — `data/repository/mapper/ReadingSessionMappers.kt`:
```kotlin
package com.example.splitreader.data.repository.mapper

import com.example.splitreader.data.local.BookMinutesRow
import com.example.splitreader.data.local.DailyMinutesRow
import com.example.splitreader.data.local.LangMinutesRow
import com.example.splitreader.data.local.ReadingSessionEntity
import com.example.splitreader.domain.model.ReadingSession
import com.example.splitreader.domain.model.stats.BookMinutes
import com.example.splitreader.domain.model.stats.DailyMinutes
import com.example.splitreader.domain.model.stats.LangMinutes

fun ReadingSessionEntity.toDomain(): ReadingSession =
    ReadingSession(bookUri, bookTitle, sourceLang, startedAt, endedAt, durationSeconds, paragraphsRead, id)

fun ReadingSession.toEntity(): ReadingSessionEntity =
    ReadingSessionEntity(id, bookUri, bookTitle, sourceLang, startedAt, endedAt, durationSeconds, paragraphsRead)

fun DailyMinutesRow.toDomain(): DailyMinutes = DailyMinutes(day, minutes)
fun BookMinutesRow.toDomain(): BookMinutes = BookMinutes(title, minutes)
fun LangMinutesRow.toDomain(): LangMinutes = LangMinutes(lang, minutes)
```

- [ ] **Step 4: Mapper round-trip test** — `app/src/test/.../mapper/ReadingSessionMapperTest.kt`:
```kotlin
package com.example.splitreader.data.repository.mapper

import com.example.splitreader.data.local.DailyMinutesRow
import com.example.splitreader.data.local.ReadingSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingSessionMapperTest {
    @Test fun session_roundTrip_preservesEveryField() {
        val e = ReadingSessionEntity(
            id = 7L, bookUri = "u", bookTitle = "bt", sourceLang = "en",
            startedAt = 100L, endedAt = 200L, durationSeconds = 100, paragraphsRead = 4,
        )
        assertEquals(e, e.toDomain().toEntity())
    }

    @Test fun statsRow_mapsToDomainFields() {
        val d = DailyMinutesRow("2026-07-18", 42).toDomain()
        assertEquals("2026-07-18", d.day)
        assertEquals(42, d.minutes)
    }
}
```
Run: `./gradlew :app:testDebugUnitTest --tests "*ReadingSessionMapperTest"` → PASS.

- [ ] **Step 5: Interface + impl**

`domain/repository/ReadingSessionRepository.kt`: replace all data imports with domain (`ReadingSession`, `stats.DailyMinutes/BookMinutes/LangMinutes`), change `record(session: ReadingSession)` and the three observe returns to domain projection types.
`data/repository/ReadingSessionRepositoryImpl.kt`:
```kotlin
    override suspend fun record(session: ReadingSession) = dao.insert(session.toEntity()).let { }
    override fun observeDailyMinutes(daysBack: Int): Flow<List<DailyMinutes>> {
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysBack.toLong())
        return dao.observeDailyMinutes(since).map { it.map { row -> row.toDomain() } }
    }
    override fun observeWeeklyMinutes(): Flow<List<DailyMinutes>> = observeDailyMinutes(7)
    override fun observeTimeByBook(daysBack: Int): Flow<List<BookMinutes>> {
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysBack.toLong())
        return dao.observeTimeByBook(since).map { it.map { row -> row.toDomain() } }
    }
    override fun observeTimeByLang(daysBack: Int): Flow<List<LangMinutes>> {
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysBack.toLong())
        return dao.observeTimeByLang(since).map { it.map { row -> row.toDomain() } }
    }
```
Fix imports: drop old `data.local.{Daily,Book,Lang}Minutes`, add `domain.model.ReadingSession`, `domain.model.stats.*`, `data.repository.mapper.toDomain`, `kotlinx.coroutines.flow.map`. `record` returns `Unit` — `dao.insert(...)` returns `Long`; discard it (`.let { }` above, or wrap in a block body). Keep `ReadingSessionEntity`/`*Row` imports only if still referenced.
> `record`'s current body `dao.insert(session)` returns `Long` but the override is `suspend fun record(...)` returning `Unit` — today it compiles because `dao.insert(session)` is the last expr and `record` was `Unit` via block inference. Preserve `Unit`: use a block body `{ dao.insert(session.toEntity()) }` so the `Long` is discarded.

- [ ] **Step 6: Use-case** — `domain/usecase/EndReadingSessionUseCase.kt`: build a `ReadingSession` (omit `id` — default 0):
```kotlin
import com.example.splitreader.domain.model.ReadingSession
...
        repository.record(
            ReadingSession(
                bookUri = bookUri,
                bookTitle = bookTitle,
                sourceLang = sourceLang,
                startedAt = startedAt,
                endedAt = now,
                durationSeconds = duration,
                paragraphsRead = paragraphsRead,
            )
        )
```
Remove `import com.example.splitreader.data.local.ReadingSessionEntity`.

- [ ] **Step 7: Consumers** — update presentation to the domain stats types:
`grep -rn "DailyMinutes\|BookMinutes\|LangMinutes" app/src/main/java/com/example/splitreader/presentation` — in `AlmanacViewModel.kt`, `AlmanacScreen.kt` (and any HomeViewModel usage), replace `import com.example.splitreader.data.local.{Daily,Book,Lang}Minutes` with `import com.example.splitreader.domain.model.stats.{...}`. Fields identical (`day/title/lang/minutes`). `HomeViewModel` consumes `observeWeeklyMinutes()/observeDailyMinutes()` and reads `.minutes` — verify it compiles (it does not import the projection type explicitly, so likely unchanged).

- [ ] **Step 8: Compile, test, verify** — `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` → SUCCESSFUL. Verify domain clean of all entities/rows:
`grep -rn "ReadingSessionEntity\|MinutesRow\|data.local.DailyMinutes\|data.local.BookMinutes\|data.local.LangMinutes" app/src/main/java/com/example/splitreader/domain || echo clean` → `clean`.

- [ ] **Step 9: Commit** — `refactor(domain): ReadingSession + stats domain models & mappers; repo/use-case on domain types (P19)` with the trailer.

---

## Definition of Done (maps to spec §6)

1. Every persisted aggregate (LibraryBook, Bookmark, Note, SavedWord, ReadingSession) has a domain model + data-layer mapper; repository interfaces speak domain types; `RepositoryImpl` maps at the boundary. *(Tasks 1–5)*
2. `domain/**` imports no `@Entity` (`BookEntity/BookmarkEntity/NoteEntity/SavedWordEntity/ReadingSessionEntity`) and no projection rows (`*MinutesRow`). Verify: `grep -rn "Entity\|MinutesRow" app/src/main/java/com/example/splitreader/domain` shows only domain-model/`*UseCase` names, no `data.local` types. *(All tasks)*
3. **Known deferred to 3B:** `TranslateTextUseCase` still imports `data.local.ReadingProgressManager` (a capability, not a Room model). Full `domain/**`-clean-of-`data` is a 3B outcome. *(Out of scope here)*
4. Behavior unchanged; `:app:testDebugUnitTest` green; `:app:compileDebugKotlin` succeeds. *(All tasks)*
5. Each aggregate's mapper is covered by a JVM round-trip/field test. *(Tasks 1–5)*
