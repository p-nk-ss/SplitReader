# DB Migration v2→v3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the data-loss hole where a missing `MIGRATION_2_3` combined with `fallbackToDestructiveMigration()` silently wipes the database when a v2-schema install upgrades.

**Architecture:** Enable Room schema export (Room Gradle plugin) so the migration SQL is derived from an authoritative baseline schema; add the missing `MIGRATION_2_3` (creates the four v3 tables), extract all migrations into a testable `Migrations` object, gate destructive fallback to debug builds, and prove data survival with an instrumented v2→v4 test.

**Tech Stack:** Kotlin 2.0.21, Room 2.7.0 (KSP), Hilt 2.52, AndroidX Test (AndroidJUnit4), Gradle version catalogs.

## Global Constraints

- Room version: **2.7.0** (`libs.versions.toml` `room = "2.7.0"`); any new Room artifact/plugin uses `version.ref = "room"`.
- Kotlin **2.0.21**, KSP **2.0.21-1.0.28**; Room uses **KSP**, not KAPT.
- minSdk **26**, targetSdk/compileSdk **36**.
- Tests: JUnit4, **hand-written fakes, no mock framework**; instrumented tests use `androidx.test.ext.junit.runners.AndroidJUnit4` under `app/src/androidTest`.
- Source package `com.example.splitreader`; DB class `com.example.splitreader.data.local.AppDatabase`; DB file name `splitreader.db`; current `version = 4`.
- Commit message trailer on every commit: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

---

## File Structure

- `gradle/libs.versions.toml` — add Room Gradle **plugin** alias + `room-testing` **library** alias.
- `build.gradle.kts` (root) — declare the Room plugin `apply false`.
- `app/build.gradle.kts` — apply Room plugin, add `room { schemaDirectory(...) }`, register `schemas` as androidTest assets, add `room-testing` test dep.
- `app/src/main/java/com/example/splitreader/data/local/AppDatabase.kt` — flip `exportSchema = true`.
- `app/src/main/java/com/example/splitreader/data/local/Migrations.kt` — **new**: all migrations (`1_2`, `2_3`, `3_4`) + `ALL`, exposed for the DB builder and tests.
- `app/src/main/java/com/example/splitreader/di/DatabaseModule.kt` — reference `Migrations.ALL`; debug-only destructive fallback; drop the inline migration vals.
- `app/schemas/com.example.splitreader.data.local.AppDatabase/4.json` — **new**, generated + committed.
- `app/src/androidTest/java/com/example/splitreader/data/local/MigrationTest.kt` — **new**: v2→v4 data-preservation test.

---

## Task 1: Enable Room schema export and commit the baseline schema

**Files:**
- Modify: `gradle/libs.versions.toml` (`[libraries]`, `[plugins]`)
- Modify: `build.gradle.kts` (root `plugins` block)
- Modify: `app/build.gradle.kts` (plugins, new `room {}` block, androidTest assets, deps)
- Modify: `app/src/main/java/com/example/splitreader/data/local/AppDatabase.kt:16`
- Create: `app/schemas/com.example.splitreader.data.local.AppDatabase/4.json` (generated)

**Interfaces:**
- Consumes: nothing (first task).
- Produces: `app/schemas/.../4.json` baseline schema (authoritative source for Task 2's SQL); `libs.plugins.room`; `libs.room.testing`.

- [ ] **Step 1: Add the Room plugin + test library to the version catalog**

In `gradle/libs.versions.toml`, under `[libraries]` (after the existing `room-compiler` line):
```toml
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
```
Under `[plugins]` (append after `firebase-crashlytics`):
```toml
room = { id = "androidx.room", version.ref = "room" }
```

- [ ] **Step 2: Declare the Room plugin in the root build**

In `build.gradle.kts` (root), add inside the `plugins { }` block:
```kotlin
    alias(libs.plugins.room) apply false
```

- [ ] **Step 3: Apply the plugin, configure schema dir, wire test deps in the app build**

In `app/build.gradle.kts`, add to the `plugins { }` block (after `firebase-crashlytics`):
```kotlin
    alias(libs.plugins.room)
```
Add a top-level `room { }` block immediately AFTER the closing brace of the `android { }` block (it is a plugin extension, not nested in `android`):
```kotlin
room {
    schemaDirectory("$projectDir/schemas")
}
```
Register the schema dir as androidTest assets — add this next to the existing `android.sourceSets.getByName("androidTest")...` line near the bottom of the file:
```kotlin
android.sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
```
Add the test dependency inside `dependencies { }` (next to the other `androidTestImplementation` lines):
```kotlin
    androidTestImplementation(libs.room.testing)
```

- [ ] **Step 4: Enable schema export on the database class**

In `app/src/main/java/com/example/splitreader/data/local/AppDatabase.kt`, change line 16:
```kotlin
    exportSchema = true,
```

- [ ] **Step 5: Generate the schema (build) and verify the JSON exists**

Run: `./gradlew :app:kspDebugKotlin`
Expected: `BUILD SUCCESSFUL`, and the file `app/schemas/com.example.splitreader.data.local.AppDatabase/4.json` now exists.
Verify: `ls app/schemas/com.example.splitreader.data.local.AppDatabase/` shows `4.json`.

> If the build fails with "Schema export directory is not provided", the `room { schemaDirectory(...) }` block was not applied — re-check Step 3.

- [ ] **Step 6: Sanity-check the generated table SQL (reference for Task 2)**

Open `app/schemas/.../4.json` and note the `createSql` strings for tables `bookmarks`, `notes`, `saved_words`, `reading_sessions`. Task 2's SQL must match these structurally (columns, NOT NULL, FK actions, index names). This is your ground truth.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts \
  app/src/main/java/com/example/splitreader/data/local/AppDatabase.kt \
  app/schemas
git commit -m "build(room): enable schema export via Room Gradle plugin

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Add MIGRATION_2_3 (extract migrations) — TDD via v2→v4 data-preservation test

**Files:**
- Create: `app/src/main/java/com/example/splitreader/data/local/Migrations.kt`
- Modify: `app/src/main/java/com/example/splitreader/di/DatabaseModule.kt`
- Create: `app/src/androidTest/java/com/example/splitreader/data/local/MigrationTest.kt`

**Interfaces:**
- Consumes: `app/schemas/.../4.json` (Task 1) as the SQL reference; existing entities in `data/local`.
- Produces: `com.example.splitreader.data.local.Migrations` with public `val MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4` (each `androidx.room.migration.Migration`) and `val ALL: Array<Migration>` — consumed by `DatabaseModule` and by tests.

- [ ] **Step 1: Extract existing migrations into a testable `Migrations` object (no `2_3` yet)**

Create `app/src/main/java/com/example/splitreader/data/local/Migrations.kt`:
```kotlin
package com.example.splitreader.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * All Room schema migrations, exposed (instead of being private in DatabaseModule) so both the
 * database builder and instrumented migration tests share the exact same migration objects.
 */
object Migrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `books` (`uri` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                    "`author` TEXT NOT NULL, `coverPath` TEXT, `lastOpenedAt` INTEGER NOT NULL, " +
                    "`chapterCount` INTEGER NOT NULL, PRIMARY KEY(`uri`))"
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN synopsis TEXT")
        }
    }

    /** All migrations in ascending order, for the database builder and tests. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_3_4)
}
```

Update `app/src/main/java/com/example/splitreader/di/DatabaseModule.kt`: delete the two private `MIGRATION_1_2` / `MIGRATION_3_4` vals (lines 21–43) and their now-unused imports (`androidx.room.migration.Migration`, `androidx.sqlite.db.SupportSQLiteDatabase`), and change the builder to use the shared array:
```kotlin
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "splitreader.db")
            .addMigrations(*Migrations.ALL)
            .fallbackToDestructiveMigration()
            .build()
```
Add the import `import com.example.splitreader.data.local.Migrations` (it already imports other `data.local` types).

- [ ] **Step 2: Write the failing data-preservation test**

Create `app/src/androidTest/java/com/example/splitreader/data/local/MigrationTest.kt`:
```kotlin
package com.example.splitreader.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reproduces the real upgrade path: a database created at the shipped v2 schema (translation_cache
 * + books, no synopsis) must migrate all the way to v4 WITHOUT losing data and WITH the v3 tables
 * created. Before MIGRATION_2_3 exists, opening the Room DB throws (no 2->3 path) and this fails.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val testDbName = "migration-test-v2.db"

    @Before fun clean() = deleteTestDb()
    @After fun cleanup() = deleteTestDb()
    private fun deleteTestDb() {
        context.getDatabasePath(testDbName).let { if (it.exists()) it.delete() }
    }

    @Test
    fun v2Data_survives_migration_to_v4() {
        // 1. Build a raw v2 database (schema as shipped in the initial release) with seed rows.
        val dbFile = context.getDatabasePath(testDbName)
        dbFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { raw ->
            raw.execSQL(
                "CREATE TABLE IF NOT EXISTS `books` (`uri` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                    "`author` TEXT NOT NULL, `coverPath` TEXT, `lastOpenedAt` INTEGER NOT NULL, " +
                    "`chapterCount` INTEGER NOT NULL, PRIMARY KEY(`uri`))"
            )
            raw.execSQL(
                "CREATE TABLE IF NOT EXISTS `translation_cache` (`id` TEXT NOT NULL, " +
                    "`originalText` TEXT NOT NULL, `translatedText` TEXT NOT NULL, " +
                    "`targetLanguage` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            raw.execSQL(
                "INSERT INTO books VALUES ('content://book/1', 'War and Peace', 'Tolstoy', NULL, 111, 42)"
            )
            raw.execSQL(
                "INSERT INTO translation_cache VALUES ('h1', 'Bonjour', 'Hello', 'EN', 222)"
            )
            raw.version = 2
        }

        // 2. Open the real Room database on the same file -> runs 2->3->4.
        val db = Room.databaseBuilder(context, AppDatabase::class.java, testDbName)
            .addMigrations(*Migrations.ALL)
            .build()
        try {
            val sdb = db.openHelper.writableDatabase // triggers migration

            // 3. v2 data survived.
            sdb.query("SELECT title FROM books WHERE uri = 'content://book/1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("War and Peace", c.getString(0))
            }
            sdb.query("SELECT translatedText FROM translation_cache WHERE id = 'h1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Hello", c.getString(0))
            }
            // synopsis column (added in v4) now exists on the migrated books table.
            sdb.query("SELECT synopsis FROM books WHERE uri = 'content://book/1'").use { c ->
                assertTrue(c.moveToFirst())
            }

            // 4. New v3 tables exist and enforce the FK to books.
            sdb.execSQL(
                "INSERT INTO bookmarks (bookUri, chapterIndex, paragraphIndex, label, createdAt) " +
                    "VALUES ('content://book/1', 0, 5, 'ch1', 333)"
            )
            sdb.query("SELECT COUNT(*) FROM bookmarks").use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
        } finally {
            db.close()
        }
    }
}
```

- [ ] **Step 3: Run the test and verify it FAILS (bug reproduced)**

Run (needs a running emulator/device):
`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.splitreader.data.local.MigrationTest`
Expected: FAIL — `IllegalStateException: A migration from 2 to 3 was required but not found` (or the tables/synopsis query throws), because `Migrations.ALL` has no 2→3 path.

- [ ] **Step 4: Add MIGRATION_2_3 and register it**

In `Migrations.kt`, add this val (place it between `MIGRATION_1_2` and `MIGRATION_3_4`). The SQL mirrors the four v3 tables exactly as they appear in `4.json` (verified structurally identical to the current entities):
```kotlin
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`bookUri` TEXT NOT NULL, `chapterIndex` INTEGER NOT NULL, `paragraphIndex` INTEGER NOT NULL, " +
                    "`label` TEXT, `createdAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`bookUri`) REFERENCES `books`(`uri`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_bookUri` ON `bookmarks` (`bookUri`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `notes` (`bookUri` TEXT NOT NULL, `chapterIndex` INTEGER NOT NULL, " +
                    "`paragraphIndex` INTEGER NOT NULL, `body` TEXT NOT NULL, `isHighlight` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`bookUri`, `chapterIndex`, `paragraphIndex`), " +
                    "FOREIGN KEY(`bookUri`) REFERENCES `books`(`uri`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_bookUri` ON `notes` (`bookUri`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `saved_words` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`word` TEXT NOT NULL, `partOfSpeech` TEXT, `sourceLang` TEXT NOT NULL, `targetLang` TEXT NOT NULL, " +
                    "`translation` TEXT NOT NULL, `bookUri` TEXT, `bookTitle` TEXT NOT NULL, " +
                    "`chapterIndex` INTEGER NOT NULL, `paragraphIndex` INTEGER NOT NULL, " +
                    "`contextSnippet` TEXT NOT NULL, `note` TEXT, `savedAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`bookUri`) REFERENCES `books`(`uri`) ON UPDATE NO ACTION ON DELETE SET NULL )"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_words_bookUri` ON `saved_words` (`bookUri`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_words_sourceLang` ON `saved_words` (`sourceLang`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `reading_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`bookUri` TEXT, `bookTitle` TEXT NOT NULL, `sourceLang` TEXT NOT NULL, " +
                    "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, `durationSeconds` INTEGER NOT NULL, " +
                    "`paragraphsRead` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`bookUri`) REFERENCES `books`(`uri`) ON UPDATE NO ACTION ON DELETE SET NULL )"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_startedAt` ON `reading_sessions` (`startedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_bookUri` ON `reading_sessions` (`bookUri`)")
        }
    }
```
Then update `ALL` to include it in order:
```kotlin
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
```

- [ ] **Step 5: Run the test and verify it PASSES**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.splitreader.data.local.MigrationTest`
Expected: PASS. (If Room throws a schema-validation error on open, a column/FK/index in the Step 4 SQL diverges from `4.json` — reconcile it against the `createSql` noted in Task 1 Step 6.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/splitreader/data/local/Migrations.kt \
  app/src/main/java/com/example/splitreader/di/DatabaseModule.kt \
  app/src/androidTest/java/com/example/splitreader/data/local/MigrationTest.kt
git commit -m "fix(db): add MIGRATION_2_3 to stop destructive wipe on v2 upgrade

Extracts migrations into a shared Migrations object and covers the v2->v4
upgrade with an instrumented data-preservation test.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Gate destructive fallback to debug builds

**Files:**
- Modify: `app/src/main/java/com/example/splitreader/di/DatabaseModule.kt`

**Interfaces:**
- Consumes: `Migrations.ALL` (Task 2).
- Produces: release builds with **no** destructive fallback (missing migration → loud `IllegalStateException`); debug builds keep destructive fallback for developer convenience.

- [ ] **Step 1: Make the destructive fallback conditional on `BuildConfig.DEBUG`**

In `DatabaseModule.kt`, add the import:
```kotlin
import com.example.splitreader.BuildConfig
```
Change `provideDatabase` to apply the fallback only in debug:
```kotlin
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "splitreader.db")
            .addMigrations(*Migrations.ALL)
            .apply { if (BuildConfig.DEBUG) fallbackToDestructiveMigration(dropAllTables = true) }
            .build()
```
> If `fallbackToDestructiveMigration(dropAllTables = true)` does not resolve on Room 2.7.0, use the no-arg `fallbackToDestructiveMigration()` (deprecated but functional) and leave a `// TODO: switch to dropAllTables overload` note. Do NOT leave destructive fallback unconditional.

- [ ] **Step 2: Verify debug build still compiles and the migration test still passes**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.
Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.splitreader.data.local.MigrationTest`
Expected: PASS (debug build still has fallback, but the real migration path is exercised and data survives).

- [ ] **Step 3: Verify release compiles without destructive fallback**

Run: `./gradlew :app:assembleRelease`
Expected: `BUILD SUCCESSFUL` (unsigned is fine without `keystore.properties`). This confirms the release variant — where `BuildConfig.DEBUG` is `false` — builds with the fallback branch skipped.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/splitreader/di/DatabaseModule.kt
git commit -m "fix(db): restrict destructive migration fallback to debug builds

Release now fails loudly on a missing migration instead of silently wiping data.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4 (OPTIONAL / bonus): MigrationTestHelper diff-validation with regenerated historical schemas

> Optional per the spec. Adds standard `MigrationTestHelper` schema-diff coverage for `2→3` and `3→4`. Skip if regenerating historical schemas proves impractical — Task 2's data-preservation test already guards the real bug.

**Files:**
- Create (generated): `app/schemas/com.example.splitreader.data.local.AppDatabase/2.json`, `3.json`
- Modify: `app/src/androidTest/java/com/example/splitreader/data/local/MigrationTest.kt`

- [ ] **Step 1: Regenerate `2.json` from the v2 commit in a throwaway worktree**

```bash
git worktree add ../sr-v2 58caba5
```
In `../sr-v2`, temporarily enable schema export the legacy KSP way (that commit predates the plugin): set `exportSchema = true` in its `AppDatabase.kt`, and add to its `app/build.gradle.kts` `defaultConfig`:
```kotlin
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
```
Then:
```bash
cd ../sr-v2 && ./gradlew :app:kspDebugKotlin
```
Copy the produced `app/schemas/com.example.splitreader.data.local.AppDatabase/2.json` into the main worktree's `app/schemas/.../2.json`. (Do not commit any changes inside `../sr-v2`.)

- [ ] **Step 2: Regenerate `3.json` from the v3 commit the same way**

```bash
git worktree add ../sr-v3 db1818d
```
Repeat Step 1's temporary export edits in `../sr-v3`, run `./gradlew :app:kspDebugKotlin`, and copy `3.json` into the main worktree's `app/schemas/.../3.json`.

- [ ] **Step 3: Remove the worktrees**

```bash
git worktree remove ../sr-v2 --force && git worktree remove ../sr-v3 --force
```

- [ ] **Step 4: Add MigrationTestHelper tests**

Append to `MigrationTest.kt` (add imports `androidx.room.testing.MigrationTestHelper`, `androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory`, `androidx.test.platform.app.InstrumentationRegistry`, `org.junit.Rule`):
```kotlin
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate2To3_schemaValidates() {
        helper.createDatabase(SCHEMA_DB, 2).close()
        helper.runMigrationsAndValidate(SCHEMA_DB, 3, true, Migrations.MIGRATION_2_3)
    }

    @Test
    fun migrate3To4_schemaValidates() {
        helper.createDatabase(SCHEMA_DB, 3).close()
        helper.runMigrationsAndValidate(SCHEMA_DB, 4, true, Migrations.MIGRATION_3_4)
    }

    private companion object {
        const val SCHEMA_DB = "schema-migration-test.db"
    }
```
> Note: this uses the Room 2.7 `MigrationTestHelper(instrumentation, databaseClass, openFactory)` constructor. `runMigrationsAndValidate` reads `3.json`/`4.json` from androidTest assets (wired in Task 1 Step 3) and fails if the migrated schema diverges.

- [ ] **Step 5: Run and commit**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.splitreader.data.local.MigrationTest`
Expected: all tests PASS.
```bash
git add app/schemas app/src/androidTest/java/com/example/splitreader/data/local/MigrationTest.kt
git commit -m "test(db): add MigrationTestHelper diff-validation for 2->3 and 3->4

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Definition of Done (maps to spec §5 acceptance criteria)

1. Build generates the schema; `app/schemas/.../4.json` committed. *(Task 1)*
2. `MIGRATION_2_3` registered in order `1_2, 2_3, 3_4`; destructive fallback debug-only. *(Tasks 2, 3)*
3. Instrumented v2→v4 data-preservation test green: book + translation-cache rows survive, `synopsis` column present, `bookmark` FK insert works. *(Task 2)*
4. Existing tests (`AddBookToLibraryUseCaseTest`, `ApiKeyManagerTest`, parser tests) still pass:
   Run `./gradlew :app:testDebugUnitTest` → PASS. *(verify after Task 3)*
