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

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN synopsis TEXT")
        }
    }

    /** All migrations in ascending order, for the database builder and tests. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
