package com.example.splitreader.di

import android.content.Context
import androidx.room.Room
import com.example.splitreader.data.local.AppDatabase
import com.example.splitreader.data.local.BookDao
import com.example.splitreader.data.local.BookmarkDao
import com.example.splitreader.data.local.Migrations
import com.example.splitreader.data.local.NoteDao
import com.example.splitreader.data.local.ReadingSessionDao
import com.example.splitreader.data.local.SavedWordDao
import com.example.splitreader.data.local.TranslationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "splitreader.db")
            .addMigrations(*Migrations.ALL)
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton
    fun provideTranslationDao(db: AppDatabase): TranslationDao = db.translationDao()

    @Provides @Singleton
    fun provideBookDao(db: AppDatabase): BookDao = db.bookDao()

    @Provides @Singleton
    fun provideBookmarkDao(db: AppDatabase): BookmarkDao = db.bookmarkDao()

    @Provides @Singleton
    fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()

    @Provides @Singleton
    fun provideSavedWordDao(db: AppDatabase): SavedWordDao = db.savedWordDao()

    @Provides @Singleton
    fun provideReadingSessionDao(db: AppDatabase): ReadingSessionDao = db.readingSessionDao()
}
