package com.example.splitreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TranslationCacheEntity::class,
        BookEntity::class,
        BookmarkEntity::class,
        NoteEntity::class,
        SavedWordEntity::class,
        ReadingSessionEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun translationDao(): TranslationDao
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun noteDao(): NoteDao
    abstract fun savedWordDao(): SavedWordDao
    abstract fun readingSessionDao(): ReadingSessionDao
}
