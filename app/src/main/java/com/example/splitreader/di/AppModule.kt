package com.example.splitreader.di

import com.example.splitreader.data.repository.BookLibraryRepositoryImpl
import com.example.splitreader.data.repository.EntitlementRepositoryImpl
import com.example.splitreader.data.repository.BookmarkRepositoryImpl
import com.example.splitreader.data.repository.NoteRepositoryImpl
import com.example.splitreader.data.repository.ReadingSessionRepositoryImpl
import com.example.splitreader.data.repository.SavedWordRepositoryImpl
import com.example.splitreader.domain.repository.BookLibraryRepository
import com.example.splitreader.domain.repository.EntitlementRepository
import com.example.splitreader.domain.repository.BookmarkRepository
import com.example.splitreader.domain.repository.NoteRepository
import com.example.splitreader.domain.repository.ReadingSessionRepository
import com.example.splitreader.domain.repository.SavedWordRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideBookLibraryRepository(impl: BookLibraryRepositoryImpl): BookLibraryRepository = impl

    @Provides @Singleton
    fun provideEntitlementRepository(impl: EntitlementRepositoryImpl): EntitlementRepository = impl

    @Provides @Singleton
    fun provideBookmarkRepository(impl: BookmarkRepositoryImpl): BookmarkRepository = impl

    @Provides @Singleton
    fun provideNoteRepository(impl: NoteRepositoryImpl): NoteRepository = impl

    @Provides @Singleton
    fun provideSavedWordRepository(impl: SavedWordRepositoryImpl): SavedWordRepository = impl

    @Provides @Singleton
    fun provideReadingSessionRepository(impl: ReadingSessionRepositoryImpl): ReadingSessionRepository = impl
}
