package com.example.splitreader.di

import com.example.splitreader.data.catalog.GutenbergOpdsApi
import com.example.splitreader.data.repository.CatalogRepositoryImpl
import com.example.splitreader.domain.repository.CatalogRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class GutenbergRetrofit

@Module
@InstallIn(SingletonComponent::class)
object CatalogNetworkModule {

    // Reuses the shared OkHttpClient provided by TranslatorNetworkModule. No converter factory is
    // needed: the OPDS endpoint returns raw XML as a ResponseBody (handled by Retrofit built-ins).
    @Provides @Singleton @GutenbergRetrofit
    fun provideGutenbergRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://www.gutenberg.org/")
            .client(client)
            .build()

    @Provides @Singleton
    fun provideGutenbergApi(@GutenbergRetrofit retrofit: Retrofit): GutenbergOpdsApi =
        retrofit.create(GutenbergOpdsApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogBindingsModule {

    @Binds @Singleton
    abstract fun bindCatalogRepository(impl: CatalogRepositoryImpl): CatalogRepository
}
