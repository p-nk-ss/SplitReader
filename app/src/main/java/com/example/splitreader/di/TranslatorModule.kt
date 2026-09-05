package com.example.splitreader.di

import android.content.Context
import com.example.splitreader.BuildConfig
import com.example.splitreader.data.bergamot.BergamotEngine
import com.example.splitreader.data.bergamot.BergamotManifest
import com.example.splitreader.data.bergamot.BergamotModelStore
import com.example.splitreader.data.bergamot.JniNativeBridge
import com.example.splitreader.data.bergamot.OkHttpPackFetcher
import com.example.splitreader.data.repository.TranslationRepositoryImpl
import com.example.splitreader.data.translator.AzureTranslationProvider
import com.example.splitreader.data.translator.BergamotTranslationProvider
import com.example.splitreader.data.translator.DeepLTranslationProvider
import com.example.splitreader.data.translator.GoogleCloudTranslationProvider
import com.example.splitreader.data.translator.LibreTranslateProvider
import com.example.splitreader.data.translator.MLKitTranslationProvider
import com.example.splitreader.data.translator.QuickTranslateProvider
import com.example.splitreader.data.translator.api.AzureTranslatorApi
import com.example.splitreader.data.translator.api.DeepLApi
import com.example.splitreader.data.translator.api.GoogleCloudApi
import com.example.splitreader.data.translator.api.LibreTranslateApi
import com.example.splitreader.data.translator.api.QuickTranslateApi
import com.example.splitreader.domain.CrashReporter
import com.example.splitreader.domain.IoDispatcher
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.repository.TranslationRepository
import com.example.splitreader.domain.translator.TranslationProviderApi
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class QuickTranslateRetrofit
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

    @Provides @Singleton @QuickTranslateRetrofit
    fun provideQuickTranslateRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit("https://translate.googleapis.com/", client, scalars = true)

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
    fun provideQuickTranslateApi(@QuickTranslateRetrofit retrofit: Retrofit): QuickTranslateApi =
        retrofit.create(QuickTranslateApi::class.java)

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

    @Provides @Singleton
    fun provideBergamotManifest(@ApplicationContext context: Context): BergamotManifest =
        BergamotManifest.parse(context.assets.open("bergamot/manifest.json").bufferedReader().readText())

    @Provides @Singleton
    fun provideBergamotModelStore(
        @ApplicationContext context: Context,
        manifest: BergamotManifest,
        fetcher: OkHttpPackFetcher,
        @IoDispatcher io: CoroutineDispatcher,
    ): BergamotModelStore = BergamotModelStore(File(context.filesDir, "bergamot"), manifest, fetcher, io)

    /**
     * Native calls are not thread-safe, so every model load/translate runs on this one thread.
     * The bridge is passed as a lambda, not a value: the Application injects this engine, so
     * loading the library here would run `System.loadLibrary` on the main thread at every cold
     * start. The engine resolves it on first use instead.
     */
    @Provides @Singleton
    fun provideBergamotEngine(crashReporter: CrashReporter): BergamotEngine = BergamotEngine(
        { JniNativeBridge.loadOrNull { crashReporter.recordNonFatal(it, "bergamot: native library unavailable") } },
        Executors.newSingleThreadExecutor { Thread(it, "bergamot").apply { isDaemon = true } }
            .asCoroutineDispatcher(),
    )

    @Provides @Singleton
    fun provideBergamotProvider(
        store: BergamotModelStore,
        engine: BergamotEngine,
        manifest: BergamotManifest,
    ): BergamotTranslationProvider = BergamotTranslationProvider(store, engine, manifest, store::packDir)
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
    @TranslationProviderKey(TranslationProvider.BERGAMOT)
    abstract fun bindBergamot(impl: BergamotTranslationProvider): TranslationProviderApi

    @Binds
    @IntoMap
    @TranslationProviderKey(TranslationProvider.QUICK_TRANSLATE)
    abstract fun bindQuickTranslate(impl: QuickTranslateProvider): TranslationProviderApi

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
