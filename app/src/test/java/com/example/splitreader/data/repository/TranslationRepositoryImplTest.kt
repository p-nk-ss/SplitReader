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

    // ── Sentences for the on-device engines (found on a tablet emulator, 2026-11-04) ────────────
    // Offline HQ split nothing on "。": a Chinese chapter written as one paragraph went in as one
    // "sentence" and ran for minutes, and Japanese sentences came back glued ("упал.Тот").

    @Test
    fun `a Japanese paragraph goes to Offline HQ sentence by sentence, joined with spaces`() = runTest {
        val result = repo.translate("或日の暮方の事である。一人の下人が雨やみを待っていた。", Language.JAPANESE, Language.RUSSIAN, TranslationProvider.BERGAMOT)
        assertEquals(listOf("或日の暮方の事である。", "一人の下人が雨やみを待っていた。"), bergamot.calls)
        assertEquals("hq:或日の暮方の事である。 hq:一人の下人が雨やみを待っていた。", result)
    }

    @Test
    fun `a long Chinese paragraph never reaches Offline HQ in one piece`() = runTest {
        val paragraph = "空空道人遂向石頭說道：石兄，你這一段故事，據你自己說有些趣味，故編寫在此，意欲問世傳奇。".repeat(60)
        repo.translate(paragraph, Language.CHINESE, Language.RUSSIAN, TranslationProvider.BERGAMOT)
        assertTrue("${bergamot.calls.size} calls", bergamot.calls.size > 1)
        assertTrue("a piece of ${bergamot.calls.maxOf { it.length }} chars", bergamot.calls.all { it.length <= 400 })
        assertEquals(paragraph, bergamot.calls.joinToString(""))
    }

    @Test
    fun `into Chinese or Japanese the sentences join without spaces`() = runTest {
        val result = repo.translate("一人の下人。羅生門の下。", Language.JAPANESE, Language.CHINESE, TranslationProvider.MLKIT)
        assertEquals("ml:一人の下人。ml:羅生門の下。", result)
    }

    @Test
    fun `an ordinary paragraph and any online engine get the paragraph whole`() = runTest {
        repo.translate("One. Two. Three.", Language.ENGLISH, Language.RUSSIAN, TranslationProvider.BERGAMOT)
        repo.translate("一人の下人。羅生門の下。", Language.JAPANESE, Language.RUSSIAN, TranslationProvider.DEEPL)
        assertEquals(listOf("One. Two. Three."), bergamot.calls)
        assertEquals(listOf("一人の下人。羅生門の下。"), deepL.calls)
    }
}
