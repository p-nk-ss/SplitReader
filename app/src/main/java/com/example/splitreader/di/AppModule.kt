package com.example.splitreader.di

import com.example.splitreader.data.local.ApiKeyManager
import com.example.splitreader.data.local.ReadingProgressManager
import com.example.splitreader.data.local.TextToSpeechManager
import com.example.splitreader.data.local.TranslationUsageTracker
import com.example.splitreader.data.local.TranslatorEndpoints
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
import com.example.splitreader.domain.repository.ReadingPreferences
import com.example.splitreader.domain.repository.ReadingSessionRepository
import com.example.splitreader.domain.repository.SavedWordRepository
import com.example.splitreader.domain.repository.SpeechSynthesizer
import com.example.splitreader.domain.repository.TranslationUsageStats
import com.example.splitreader.domain.repository.TranslatorEndpointStore
import com.example.splitreader.domain.repository.TranslatorKeyStore
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

    @Provides @Singleton
    fun provideReadingPreferences(impl: ReadingProgressManager): ReadingPreferences = impl

    @Provides @Singleton
    fun provideSpeechSynthesizer(impl: TextToSpeechManager): SpeechSynthesizer = impl

    @Provides @Singleton
    fun provideTranslationUsageStats(impl: TranslationUsageTracker): TranslationUsageStats = impl

    @Provides @Singleton
    fun provideTranslatorKeyStore(impl: ApiKeyManager): TranslatorKeyStore = impl

    @Provides @Singleton
    fun provideTranslatorEndpointStore(impl: TranslatorEndpoints): TranslatorEndpointStore = impl
}
