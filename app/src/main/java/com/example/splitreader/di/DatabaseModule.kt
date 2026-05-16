package com.example.splitreader.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.splitreader.data.local.AppDatabase
import com.example.splitreader.data.local.BookDao
import com.example.splitreader.data.local.TranslationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `books` (
                `uri` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `author` TEXT NOT NULL,
                `coverPath` TEXT,
                `lastOpenedAt` INTEGER NOT NULL,
                `chapterCount` INTEGER NOT NULL,
                PRIMARY KEY(`uri`)
            )
            """.trimIndent()
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "splitreader.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    @Singleton
    fun provideTranslationDao(db: AppDatabase): TranslationDao = db.translationDao()

    @Provides
    @Singleton
    fun provideBookDao(db: AppDatabase): BookDao = db.bookDao()
}
