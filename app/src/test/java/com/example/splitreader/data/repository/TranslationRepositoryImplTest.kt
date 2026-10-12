package com.example.splitreader.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.data.local.TranslationCacheEntity
import com.example.splitreader.data.local.TranslationDao
import com.example.splitreader.data.local.TranslationUsageTracker
import com.example.splitreader.domain.model.FallbackReason
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.Resolution
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.translator.TranslationProviderApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeTranslationDao : TranslationDao {
    val rows = HashMap<String, TranslationCacheEntity>()
    override suspend fun getCached(id: String): TranslationCacheEntity? = rows[id]
    override suspend fun insert(entity: TranslationCacheEntity) {
        rows[entity.id] = entity
    }
    override suspend fun clearOlderThan(before: Long) {
        rows.values.removeAll { it.timestamp < before }
    }
    override suspend fun clearAll() = rows.clear()
    override suspend fun count(): Int = rows.size
}

private class FakeProvider(
    override val id: TranslationProvider,
    var configured: Boolean = true,
    var supportsPair: Boolean = true,
    private val prefix: String = id.name.lowercase(),
) : TranslationProviderApi {
    val calls = mutableListOf<String>()
    override fun isConfigured(): Boolean = configured
    override fun supports(source: Language, target: Language): Boolean = supportsPair
    override suspend fun translate(text: String, source: Language, target: Language): String {
        calls += text
        return "$prefix:$text"
    }
}

/**
 * The repository translates with exactly the provider it is handed and resolves, without side
 * effects, which engine would serve a pair — the rules behind "no silent ML Kit fallback".
 * `TranslationUsageTracker` is a concrete class over `SharedPreferences`, hence Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TranslationRepositoryImplTest {

    private lateinit var context: Context
    private val mlKit = FakeProvider(TranslationProvider.MLKIT, prefix = "ml")
    private val deepL = FakeProvider(TranslationProvider.DEEPL, prefix = "deepl")
    private val bergamot = FakeProvider(TranslationProvider.BERGAMOT, prefix = "hq")
    private val dao = FakeTranslationDao()
    private lateinit var repo: TranslationRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("translation_usage", Context.MODE_PRIVATE).edit().clear().commit()
        repo = TranslationRepositoryImpl(
            providers = mapOf(
                TranslationProvider.MLKIT to mlKit,
                TranslationProvider.DEEPL to deepL,
                TranslationProvider.BERGAMOT to bergamot,
            ),
            dao = dao,
            usageTracker = TranslationUsageTracker(context),
        )
    }

    @Test
    fun `resolve is Ready when the provider is configured and supports the pair`() {
        assertEquals(
            Resolution.Ready(TranslationProvider.DEEPL),
            repo.resolve(TranslationProvider.DEEPL, Language.ENGLISH, Language.RUSSIAN),
        )
    }

    @Test
    fun `resolve is NotConfigured for a key provider without its key`() {
        deepL.configured = false
        assertEquals(
            Resolution.NotConfigured(TranslationProvider.DEEPL),
            repo.resolve(TranslationProvider.DEEPL, Language.ENGLISH, Language.RUSSIAN),
        )
    }

    @Test
    fun `resolve falls back with ENGINE_UNAVAILABLE for an unavailable keyless engine`() {
        bergamot.configured = false
        assertEquals(
            Resolution.Fallback(TranslationProvider.BERGAMOT, TranslationProvider.MLKIT, FallbackReason.ENGINE_UNAVAILABLE),
            repo.resolve(TranslationProvider.BERGAMOT, Language.ENGLISH, Language.RUSSIAN),
        )
    }

    @Test
    fun `resolve falls back with UNSUPPORTED_PAIR when the provider cannot do the pair`() {
        deepL.supportsPair = false
        assertEquals(
            Resolution.Fallback(TranslationProvider.DEEPL, TranslationProvider.MLKIT, FallbackReason.UNSUPPORTED_PAIR),
            repo.resolve(TranslationProvider.DEEPL, Language.ENGLISH, Language.HINDI),
        )
    }

    @Test
    fun `translate uses exactly the provider passed in and caches under its key`() = runTest {
        val result = repo.translate("a", Language.ENGLISH, Language.RUSSIAN, TranslationProvider.DEEPL)

        assertEquals("deepl:a", result)
        assertEquals(listOf("a"), deepL.calls)
        assertTrue("ML Kit must never be called for an explicit DeepL request", mlKit.calls.isEmpty())
        assertNotNull(
            "cache must hold the DeepL key",
            dao.rows[TranslationCacheKey.compute(TranslationProvider.DEEPL, "a", Language.ENGLISH, Language.RUSSIAN)],
        )
    }

    @Test
    fun `a cache hit for the passed provider skips the network`() = runTest {
        repo.translate("a", Language.ENGLISH, Language.RUSSIAN, TranslationProvider.DEEPL)
        val second = repo.translate("a", Language.ENGLISH, Language.RUSSIAN, TranslationProvider.DEEPL)

        assertEquals("deepl:a", second)
        assertEquals("second call must be served from the cache", 1, deepL.calls.size)
    }

    @Test
    fun `usage is recorded for the provider that served`() = runTest {
        repo.translate("a", Language.ENGLISH, Language.RUSSIAN, TranslationProvider.DEEPL)

        assertEquals(1L, TranslationUsageTracker(context).usage(TranslationProvider.DEEPL).charactersThisMonth)
    }
}
