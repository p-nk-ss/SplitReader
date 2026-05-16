package com.example.splitreader.di

import com.example.splitreader.data.repository.BookLibraryRepositoryImpl
import com.example.splitreader.data.repository.TranslationRepositoryImpl
import com.example.splitreader.domain.parser.EpubParser
import com.example.splitreader.domain.parser.Fb2Parser
import com.example.splitreader.domain.repository.BookLibraryRepository
import com.example.splitreader.domain.repository.TranslationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFb2Parser(): Fb2Parser = Fb2Parser()

    @Provides
    @Singleton
    fun provideEpubParser(): EpubParser = EpubParser()

    @Provides
    @Singleton
    fun provideTranslationRepository(impl: TranslationRepositoryImpl): TranslationRepository = impl

    @Provides
    @Singleton
    fun provideBookLibraryRepository(impl: BookLibraryRepositoryImpl): BookLibraryRepository = impl
}
