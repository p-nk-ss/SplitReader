package com.example.splitreader.data.local

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * v5 -> v6 adds `books.contentKey` (the SHA-256 of the book file, for the Same book lookup). A
 * database built from the committed v5 schema, with a book in the library and a removed one, must
 * open at v6 with both rows, their bookmarks and their removal intact and no content key yet. Room validates the migrated tables against the entities on
 * open, so a migration that drifts from [BookEntity] fails here too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class Migration5To6Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-5-6.db"

    @After
    fun cleanup() { context.getDatabasePath(name).delete() }

    /** Creates the database exactly as Room 5 did, from `app/schemas/.../5.json`. */
    private fun createV5() {
        val schema = JSONObject(File("schemas/${AppDatabase::class.java.name}/5.json").readText())
            .getJSONObject("database")
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                raw.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    raw.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) raw.execSQL(setup.getString(i))
            raw.execSQL(
                "INSERT INTO books (uri, title, author, coverPath, lastOpenedAt, chapterCount, synopsis, removedAt) " +
                    "VALUES ('content://book/1', 'War and Peace', 'Tolstoy', '/covers/1.jpg', 111, 42, 'long', NULL), " +
                    "('content://book/2', 'Anna Karenina', 'Tolstoy', NULL, 222, 8, NULL, 999)"
            )
            raw.execSQL(
                "INSERT INTO bookmarks (bookUri, chapterIndex, paragraphIndex, label, createdAt) " +
                    "VALUES ('content://book/1', 3, 5, 'ch3', 333)"
            )
            raw.version = 5
        }
    }

    @Test
    fun `a v5 library opens at v6 with its books, removed books and bookmarks kept and no content keys`() = runTest {
        createV5()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val sdb = db.openHelper.writableDatabase // migrates, then validates against the entities
            assertEquals(6, sdb.version)
            sdb.query("SELECT uri, title, removedAt, contentKey FROM books ORDER BY uri").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("War and Peace", c.getString(1))
                assertTrue(c.isNull(2))
                assertTrue("a book from before v6 has no content key", c.isNull(3))
                assertTrue(c.moveToNext())
                assertEquals(999L, c.getLong(2))
                assertTrue(c.isNull(3))
            }
            assertEquals(listOf("content://book/1"), db.bookDao().getAllBooks().first().map { it.uri })
            assertEquals(1, db.bookmarkDao().listForBook("content://book/1").size)
        } finally {
            db.close()
        }
    }
}
