package com.example.splitreader.di

import com.example.splitreader.data.catalog.FailoverGutenbergClient
import com.example.splitreader.data.catalog.GutenbergOpdsApi
import com.example.splitreader.data.catalog.gutendex.GutendexApi
import com.example.splitreader.data.catalog.StandardEbooksCatalogClient
import com.example.splitreader.data.repository.CatalogRepositoryImpl
import com.example.splitreader.domain.catalog.CatalogSourceClient
import com.example.splitreader.domain.model.CatalogSource
import com.example.splitreader.domain.repository.CatalogRepository
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class GutenbergRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class GutendexRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class GutenbergClient
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class StandardEbooksClient

/** Hilt map key for the per-source [CatalogSourceClient] multibinding (mirrors TranslationProviderKey). */
@MapKey
annotation class CatalogSourceKey(val value: CatalogSource)

/**
 * Project Gutenberg's OPDS policy requires every client to send a User-Agent that identifies the
 * app and a contact address; clients without one are blocked. Same string for every install — it
 * names the app and the developer's contact, not the user. Standard Ebooks needs no special header
 * but we send the same polite identifier.
 */
private const val CATALOG_USER_AGENT = "SplitReader/1.0 (+https://github.com/p-nk-ss/SplitReader)"

@Module
@InstallIn(SingletonComponent::class)
object CatalogNetworkModule {

    // Derives a per-source client from the shared OkHttpClient (TranslatorNetworkModule), adding a
    // User-Agent. Keeps the contact header off the translation endpoints.
    @Provides @Singleton @GutenbergClient
    fun provideGutenbergClient(base: OkHttpClient): OkHttpClient =
        base.newBuilder()
            .addInterceptor(Interceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", CATALOG_USER_AGENT)
                        .build()
                )
            })
            .build()

    @Provides @Singleton @StandardEbooksClient
    fun provideStandardEbooksClient(base: OkHttpClient): OkHttpClient =
        base.newBuilder()
            .addInterceptor(Interceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", CATALOG_USER_AGENT)
                        .build()
                )
            })
            .build()

    // No converter factory is needed: the OPDS endpoint returns raw XML as a ResponseBody
    // (handled by Retrofit built-ins).
    @Provides @Singleton @GutenbergRetrofit
    fun provideGutenbergRetrofit(@GutenbergClient client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://www.gutenberg.org/")
            .client(client)
            .build()

    @Provides @Singleton
    fun provideGutenbergApi(@GutenbergRetrofit retrofit: Retrofit): GutenbergOpdsApi =
        retrofit.create(GutenbergOpdsApi::class.java)

    // The Gutenberg tab's fallback while gutenberg.org is down (FailoverGutenbergClient). Same
    // identifying User-Agent as gutenberg.org (the @GutenbergClient OkHttpClient); JSON via Gson.
    @Provides @Singleton @GutendexRetrofit
    fun provideGutendexRetrofit(@GutenbergClient client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://gutendex.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides @Singleton
    fun provideGutendexApi(@GutendexRetrofit retrofit: Retrofit): GutendexApi =
        retrofit.create(GutendexApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogBindingsModule {

    @Binds @Singleton
    abstract fun bindCatalogRepository(impl: CatalogRepositoryImpl): CatalogRepository

    @Binds
    @IntoMap
    @CatalogSourceKey(CatalogSource.GUTENBERG)
    abstract fun bindGutenbergClient(impl: FailoverGutenbergClient): CatalogSourceClient

    @Binds
    @IntoMap
    @CatalogSourceKey(CatalogSource.STANDARD_EBOOKS)
    abstract fun bindStandardEbooksClient(impl: StandardEbooksCatalogClient): CatalogSourceClient
}
