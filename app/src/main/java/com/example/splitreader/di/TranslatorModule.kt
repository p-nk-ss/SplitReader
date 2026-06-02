package com.example.splitreader.di

import com.example.splitreader.BuildConfig
import com.example.splitreader.data.repository.TranslationRepositoryImpl
import com.example.splitreader.data.translator.AzureTranslationProvider
import com.example.splitreader.data.translator.DeepLTranslationProvider
import com.example.splitreader.data.translator.GoogleCloudTranslationProvider
import com.example.splitreader.data.translator.LibreTranslateProvider
import com.example.splitreader.data.translator.MLKitTranslationProvider
import com.example.splitreader.data.translator.api.AzureTranslatorApi
import com.example.splitreader.data.translator.api.DeepLApi
import com.example.splitreader.data.translator.api.GoogleCloudApi
import com.example.splitreader.data.translator.api.LibreTranslateApi
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.repository.TranslationRepository
import com.example.splitreader.domain.translator.TranslationProviderApi
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class GoogleCloudRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class LibreRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DeepLRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AzureRetrofit

@MapKey
annotation class TranslationProviderKey(val value: TranslationProvider)

@Module
@InstallIn(SingletonComponent::class)
object TranslatorNetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Authorization")
            }
            builder.addInterceptor(logging)
        }
        return builder.build()
    }

    private fun buildRetrofit(baseUrl: String, client: OkHttpClient, scalars: Boolean = false): Retrofit {
        val builder = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
        if (scalars) builder.addConverterFactory(ScalarsConverterFactory.create())
        builder.addConverterFactory(GsonConverterFactory.create())
        return builder.build()
    }

    @Provides @Singleton @GoogleCloudRetrofit
    fun provideGoogleCloudRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit("https://translation.googleapis.com/", client)

    @Provides @Singleton @LibreRetrofit
    fun provideLibreRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit("https://libretranslate.com/", client)

    @Provides @Singleton @DeepLRetrofit
    fun provideDeepLRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit("https://api-free.deepl.com/", client)

    @Provides @Singleton @AzureRetrofit
    fun provideAzureRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit("https://api.cognitive.microsofttranslator.com/", client)

    @Provides @Singleton
    fun provideGoogleCloudApi(@GoogleCloudRetrofit retrofit: Retrofit): GoogleCloudApi =
        retrofit.create(GoogleCloudApi::class.java)

    @Provides @Singleton
    fun provideLibreTranslateApi(@LibreRetrofit retrofit: Retrofit): LibreTranslateApi =
        retrofit.create(LibreTranslateApi::class.java)

    @Provides @Singleton
    fun provideDeepLApi(@DeepLRetrofit retrofit: Retrofit): DeepLApi =
        retrofit.create(DeepLApi::class.java)

    @Provides @Singleton
    fun provideAzureTranslatorApi(@AzureRetrofit retrofit: Retrofit): AzureTranslatorApi =
        retrofit.create(AzureTranslatorApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TranslatorBindingsModule {

    @Binds
    @Singleton
    abstract fun bindTranslationRepository(impl: TranslationRepositoryImpl): TranslationRepository

    @Binds
    @IntoMap
    @TranslationProviderKey(TranslationProvider.MLKIT)
    abstract fun bindMlKit(impl: MLKitTranslationProvider): TranslationProviderApi

    @Binds
    @IntoMap
    @TranslationProviderKey(TranslationProvider.LIBRE_TRANSLATE)
    abstract fun bindLibre(impl: LibreTranslateProvider): TranslationProviderApi

    @Binds
    @IntoMap
    @TranslationProviderKey(TranslationProvider.GOOGLE_CLOUD)
    abstract fun bindGoogleCloud(impl: GoogleCloudTranslationProvider): TranslationProviderApi

    @Binds
    @IntoMap
    @TranslationProviderKey(TranslationProvider.DEEPL)
    abstract fun bindDeepL(impl: DeepLTranslationProvider): TranslationProviderApi

    @Binds
    @IntoMap
    @TranslationProviderKey(TranslationProvider.AZURE)
    abstract fun bindAzure(impl: AzureTranslationProvider): TranslationProviderApi
}
