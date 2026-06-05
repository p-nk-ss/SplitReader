package com.example.splitreader.di

import com.example.splitreader.data.repository.DriveRepositoryImpl
import com.example.splitreader.domain.repository.DriveRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DriveClient

@Module
@InstallIn(SingletonComponent::class)
object DriveNetworkModule {

    // Derives a Drive-only client from the shared OkHttpClient (TranslatorNetworkModule). Auth is a
    // per-request Bearer header, so no interceptor is needed; the longer read timeout just gives big
    // EPUB/MOBI downloads room to finish.
    @Provides @Singleton @DriveClient
    fun provideDriveClient(base: OkHttpClient): OkHttpClient =
        base.newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DriveBindingsModule {

    @Binds @Singleton
    abstract fun bindDriveRepository(impl: DriveRepositoryImpl): DriveRepository
}
