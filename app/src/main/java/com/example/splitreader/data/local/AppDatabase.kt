package com.example.splitreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TranslationCacheEntity::class, BookEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun translationDao(): TranslationDao
    abstract fun bookDao(): BookDao
}
