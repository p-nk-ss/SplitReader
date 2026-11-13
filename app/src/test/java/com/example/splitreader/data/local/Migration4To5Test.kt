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
 * v4 -> v5 adds `books.removedAt` (a deleted book whose bookmarks and notes are kept). A database
 * built from the committed v4 schema, with a book and its bookmark, must open at v5 with both intact
 * and the book still in the library. Room validates the migrated tables against the entities on
 * open, so a migration that drifts from [BookEntity] fails here too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class Migration4To5Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-4-5.db"

    @After
    fun cleanup() { context.getDatabasePath(name).delete() }

    /** Creates the database exactly as Room 4 did, from `app/schemas/.../4.json`. */
    private fun createV4() {
        val schema = JSONObject(File("schemas/${AppDatabase::class.java.name}/4.json").readText())
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
                "INSERT INTO books (uri, title, author, coverPath, lastOpenedAt, chapterCount, synopsis) " +
                    "VALUES ('content://book/1', 'War and Peace', 'Tolstoy', '/covers/1.jpg', 111, 42, 'long')"
            )
            raw.execSQL(
                "INSERT INTO bookmarks (bookUri, chapterIndex, paragraphIndex, label, createdAt) " +
                    "VALUES ('content://book/1', 3, 5, 'ch3', 333)"
            )
            raw.version = 4
        }
    }

    @Test
    fun `a v4 library opens at v5 with its books still in the library and their bookmarks kept`() = runTest {
        createV4()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val sdb = db.openHelper.writableDatabase // migrates, then validates against the entities
            assertEquals(5, sdb.version)
            sdb.query("SELECT title, coverPath, removedAt FROM books WHERE uri = 'content://book/1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("War and Peace", c.getString(0))
                assertEquals("/covers/1.jpg", c.getString(1))
                assertTrue("an existing book is not a removed one", c.isNull(2))
            }
            assertEquals(listOf("content://book/1"), db.bookDao().getAllBooks().first().map { it.uri })
            assertEquals(1, db.bookDao().count())
            assertEquals(1, db.bookmarkDao().listForBook("content://book/1").size)
        } finally {
            db.close()
        }
    }
}
