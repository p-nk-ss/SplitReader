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
