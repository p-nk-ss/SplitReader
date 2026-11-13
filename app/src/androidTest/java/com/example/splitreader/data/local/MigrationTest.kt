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
 * + books, no synopsis) must migrate all the way to the current version (v5) WITHOUT losing data and WITH the v3 tables
 * created. Before MIGRATION_2_3 exists, opening the Room DB throws (no 2->3 path) and this fails.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val testDbName = "migration-test-v2.db"

    /** Builds a database from a committed schema and validates a migrated one; closes them after the test. */
    @get:org.junit.Rule
    val helper = androidx.room.testing.MigrationTestHelper(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Before fun clean() = deleteTestDb()
    @After fun cleanup() = deleteTestDb()
    private fun deleteTestDb() {
        context.getDatabasePath(testDbName).let { if (it.exists()) it.delete() }
    }

    @Test
    fun v2Data_survives_migration_to_current() {
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

        // 2. Open the real Room database on the same file -> runs 2->3->4->5.
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

            // removedAt (added in v5) exists and is NULL: a book from before it is in the library.
            sdb.query("SELECT removedAt FROM books WHERE uri = 'content://book/1'").use { c ->
                assertTrue(c.moveToFirst())
                assertTrue(c.isNull(0))
            }

            // 4. New v3 tables exist and accept a row referencing the surviving book via its FK column.
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

    /**
     * v4 -> v5 alone, from the committed v4 schema (`app/schemas/.../4.json`, an androidTest asset) on
     * the device's own SQLite: a book and its bookmark survive, `removedAt` is NULL (the book is still
     * in the library), and Room validates the migrated tables against the v5 schema.
     */
    @Test
    fun v4Data_survives_migration_to_v5() {
        val name = "migration-test-v4.db"
        context.getDatabasePath(name).delete()
        try {
            helper.createDatabase(name, 4).use { v4 ->
                v4.execSQL(
                    "INSERT INTO books (uri, title, author, coverPath, lastOpenedAt, chapterCount, synopsis) " +
                        "VALUES ('content://book/1', 'War and Peace', 'Tolstoy', '/covers/1.jpg', 111, 42, 'long')"
                )
                v4.execSQL(
                    "INSERT INTO bookmarks (bookUri, chapterIndex, paragraphIndex, label, createdAt) " +
                        "VALUES ('content://book/1', 3, 7, 'ch4', 333)"
                )
            }

            helper.runMigrationsAndValidate(name, 5, true, Migrations.MIGRATION_4_5).use { v5 ->
                v5.query("SELECT title, synopsis, removedAt FROM books WHERE uri = 'content://book/1'").use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals("War and Peace", c.getString(0))
                    assertEquals("long", c.getString(1))
                    assertTrue("a book from before v5 is in the library", c.isNull(2))
                }
                v5.query("SELECT chapterIndex, paragraphIndex FROM bookmarks WHERE bookUri = 'content://book/1'").use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals(3, c.getInt(0))
                    assertEquals(7, c.getInt(1))
                }
            }
        } finally {
            context.getDatabasePath(name).delete()
        }
    }
}
