package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.model.FallbackReason
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.Resolution
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.domain.repository.TranslationRepository
import com.example.splitreader.domain.translator.ModelPair
import com.example.splitreader.domain.translator.OfflineEngineUnavailableException
import com.example.splitreader.domain.translator.OfflinePackDownloadException
import java.io.IOException
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun http429(): HttpException =
    HttpException(Response.error<String>(429, "rate limited".toResponseBody(null)))

/**
 * Translates by wrapping the text. Throws [error] for paragraphs listed in [failOn] (every call)
 * and for the first N calls of paragraphs in [transientFailures]. Records every call.
 */
private class FakeTranslationRepository(
    private val failOn: Set<String> = emptySet(),
    private val transientFailures: MutableMap<String, Int> = mutableMapOf(),
    private val error: (String) -> Exception = { IOException("network hiccup on $it") },
    private val prepareProgress: List<Float> = emptyList(),
    private val prepareError: Exception? = null,
    private val resolution: (TranslationProvider) -> Resolution = { Resolution.Ready(it) },
) : TranslationRepository {
    val calls = mutableListOf<String>()
    /** The provider each [translate] call was made with, parallel to [calls]. */
    val served = mutableListOf<TranslationProvider>()

    override fun resolve(provider: TranslationProvider, source: Language, target: Language) = resolution(provider)

    override suspend fun translate(text: String, source: Language, target: Language, provider: TranslationProvider): String {
        calls += text
        served += provider
        val remaining = transientFailures[text] ?: 0
        if (remaining > 0) {
            transientFailures[text] = remaining - 1
            throw error(text)
        }
        if (text in failOn) throw error(text)
        return "t:$text"
    }

    override fun prepare(source: Language, target: Language, provider: TranslationProvider): Flow<Float> = flow {
        prepareProgress.forEach { emit(it) }
        prepareError?.let { throw it }
    }

    override suspend fun cachedCount() = 0

    override suspend fun clearCache() = Unit
}

class TranslateTextUseCaseTest {

    private fun TestScope.useCase(
        repo: TranslationRepository,
    ) = TranslateTextUseCase(repo, StandardTestDispatcher(testScheduler))

    @Test
    fun `the provider passed in decides whether the offline model is prepared`() = runTest {
        val repo = FakeTranslationRepository(prepareProgress = listOf(0.5f))
        val offline = useCase(repo)(listOf("a"), Language.ENGLISH, Language.RUSSIAN, TranslationProvider.MLKIT, 0, 0).toList()
        val online = useCase(repo)(listOf("a"), Language.ENGLISH, Language.RUSSIAN, TranslationProvider.QUICK_TRANSLATE, 0, 0).toList()
        assertTrue("on-device provider must prepare its model", offline.any { it is TranslationState.DownloadingModel })
        assertTrue("network provider must not prepare a model", online.none { it is TranslationState.DownloadingModel })
    }

    @Test
    fun `a single failing paragraph does not abort the rest of the segment`() = runTest {
        val repo = FakeTranslationRepository(failOn = setOf("b"))
        val states = useCase(repo)(listOf("a", "b", "c"), Language.ENGLISH, Language.RUSSIAN, TranslationProvider.QUICK_TRANSLATE, 0, 2).toList()

        val partials = states.filterIsInstance<TranslationState.Partial>()
        assertEquals(listOf(0 to "t:a", 2 to "t:c"), partials.map { it.index to it.text })
        assertEquals(1, states.count { it is TranslationState.Error })
        assertTrue("error must come after the surviving partials", states.last() is TranslationState.Error)
    }

    @Test
    fun `three consecutive failures abort the segment without trying the rest`() = runTest {
        val repo = FakeTranslationRepository(failOn = setOf("b", "c", "d"))
        val states = useCase(repo)(listOf("a", "b", "c", "d", "e"), Language.ENGLISH, Language.RUSSIAN, TranslationProvider.QUICK_TRANSLATE, 0, 4).toList()

        assertEquals(listOf("a", "b", "c", "d"), repo.calls) // gives up before "e"
        val partials = states.filterIsInstance<TranslationState.Partial>()
        assertEquals(listOf(0), partials.map { it.index })
        assertEquals(1, states.count { it is TranslationState.Error })
    }

    @Test
    fun `interleaved failures never trip the consecutive-failure cutoff`() = runTest {
        val repo = FakeTranslationRepository(failOn = setOf("b", "d"))
        val states = useCase(repo)(listOf("a", "b", "c", "d", "e"), Language.ENGLISH, Language.RUSSIAN, TranslationProvider.QUICK_TRANSLATE, 0, 4).toList()

        assertEquals(listOf("a", "b", "c", "d", "e"), repo.calls)
        val partials = states.filterIsInstance<TranslationState.Partial>()
        assertEquals(listOf(0, 2, 4), partials.map { it.index })
    }

    @Test
    fun `cancellation propagates instead of being swallowed as a translation failure`() = runTest {
        val repo = object : TranslationRepository {
            val calls = mutableListOf<String>()
            override fun resolve(provider: TranslationProvider, source: Language, target: Language) =
                Resolution.Ready(provider)
            override suspend fun translate(text: String, source: Language, target: Language, provider: TranslationProvider): String {
                calls += text
                if (text == "b") throw kotlinx.coroutines.CancellationException("worker cancelled")
                return "t:$text"
            }
            override fun prepare(source: Language, target: Language, provider: TranslationProvider) = emptyFlow<Float>()
            override suspend fun cachedCount() = 0
            override suspend fun clearCache() = Unit
        }
        val states = mutableListOf<TranslationState>()
        var cancelled = false
        try {
            useCase(repo)(
                listOf("a", "b", "c"), Language.ENGLISH, Language.RUSSIAN, TranslationProvider.QUICK_TRANSLATE, 0, 2,
            ).toList(states)
        } catch (e: kotlinx.coroutines.CancellationException) {
            cancelled = true
        }
        assertTrue("cancellation must propagate to the collector", cancelled)
        assertEquals(listOf("a", "b"), repo.calls) // no work after the cancel
        assertEquals(0, states.count { it is TranslationState.Error })
    }

    @Test
    fun `unofficial-provider 429 reads as a temporary rate limit, not an exhausted quota`() = runTest {
        val repo = FakeTranslationRepository(failOn = setOf("a"), error = { http429() })
        val states = useCase(repo)(
            listOf("a"), Language.ENGLISH, Language.RUSSIAN, TranslationProvider.QUICK_TRANSLATE, 0, 0,
        ).toList()

        val message = states.filterIsInstance<TranslationState.Error>().single().message
        assertTrue("expected rate-limit wording, got: $message", message.contains("rate-limited"))
        assertTrue("free-endpoint 429 must not claim an exhausted quota: $message", !message.contains("quota"))
    }

    @Test
    fun `paid-provider 429 keeps the quota wording`() = runTest {
        val repo = FakeTranslationRepository(failOn = setOf("a"), error = { http429() })
        val states = useCase(repo)(
            listOf("a"), Language.ENGLISH, Language.RUSSIAN, TranslationProvider.DEEPL, 0, 0,
        ).toList()

        val message = states.filterIsInstance<TranslationState.Error>().single().message
        assertTrue("expected quota wording for a keyed provider, got: $message", message.contains("quota"))
    }

    @Test
    fun `a 429 is retried after a backoff and can still succeed`() = runTest {
        val repo = FakeTranslationRepository(transientFailures = mutableMapOf("a" to 1), error = { http429() })
        val states = useCase(repo)(listOf("a"), Language.ENGLISH, Language.RUSSIAN, TranslationProvider.QUICK_TRANSLATE, 0, 0).toList()

        assertEquals(listOf("a", "a"), repo.calls)
        assertEquals(listOf(0 to "t:a"), states.filterIsInstance<TranslationState.Partial>().map { it.index to it.text })
        assertEquals(0, states.count { it is TranslationState.Error })
        assertTrue("retry must wait before re-hitting the endpoint", currentTime > 0)
    }

    @Test
    fun `429 retries are capped before the paragraph counts as failed`() = runTest {
        val repo = FakeTranslationRepository(failOn = setOf("a"), error = { http429() })
        val states = useCase(repo)(listOf("a"), Language.ENGLISH, Language.RUSSIAN, TranslationProvider.QUICK_TRANSLATE, 0, 0).toList()

        assertEquals("one initial attempt + two backoff retries", listOf("a", "a", "a"), repo.calls)
        assertEquals(1, states.count { it is TranslationState.Error })
    }

    @Test
    fun `plain network failures are not retried with backoff`() = runTest {
        val repo = FakeTranslationRepository(failOn = setOf("a")) // IOException
        useCase(repo)(listOf("a", "b"), Language.ENGLISH, Language.RUSSIAN, TranslationProvider.QUICK_TRANSLATE, 0, 1).toList()

        assertEquals(listOf("a", "b"), repo.calls)
        assertEquals("no backoff for non-429 failures", 0, currentTime)
    }

    @Test
    fun `a clean segment emits every partial and no error`() = runTest {
        val repo = FakeTranslationRepository()
        val states = useCase(repo)(listOf("a", "b"), Language.ENGLISH, Language.RUSSIAN, TranslationProvider.QUICK_TRANSLATE, 0, 1).toList()

        val partials = states.filterIsInstance<TranslationState.Partial>()
        assertEquals(listOf(0 to "t:a", 1 to "t:b"), partials.map { it.index to it.text })
        assertEquals(0, states.count { it is TranslationState.Error })
    }

    @Test
    fun `offline providers emit DownloadingModel with forwarded progress before translating`() = runTest {
        val repo = FakeTranslationRepository(prepareProgress = listOf(0.25f, 1f))
        val states = useCase(repo)(
            listOf("a"), Language.ENGLISH, Language.RUSSIAN, TranslationProvider.BERGAMOT,
        ).toList()

        assertEquals(
            listOf(
                TranslationState.DownloadingModel(null),
                TranslationState.DownloadingModel(0.25f),
                TranslationState.DownloadingModel(1f),
            ),
            states.filterIsInstance<TranslationState.DownloadingModel>(),
        )
        assertTrue(states.any { it is TranslationState.Partial })
    }

    @Test
    fun `online providers never emit DownloadingModel`() = runTest {
        val states = useCase(FakeTranslationRepository())(
            listOf("a"), Language.ENGLISH, Language.RUSSIAN, TranslationProvider.QUICK_TRANSLATE,
        ).toList()

        assertTrue(states.none { it is TranslationState.DownloadingModel })
    }

    @Test
    fun `pack download failure is worded with the pair and size`() = runTest {
        val repo = FakeTranslationRepository(
            failOn = setOf("a"),
            error = { OfflinePackDownloadException(ModelPair(Language.ENGLISH, Language.RUSSIAN), 31_561_787) },
        )
        val err = useCase(repo)(
            listOf("a"), Language.ENGLISH, Language.RUSSIAN, TranslationProvider.BERGAMOT,
        ).toList().last() as TranslationState.Error

        assertEquals(
            "Couldn't download the Offline HQ language pack (en→ru, 31 MB). Check your internet and retry.",
            err.message,
        )
    }

    @Test
    fun `prepare failure emits the error and translates nothing`() = runTest {
        val repo = FakeTranslationRepository(
            prepareError = OfflinePackDownloadException(ModelPair(Language.ENGLISH, Language.RUSSIAN), 31_561_787),
        )
        val states = useCase(repo)(
            listOf("a", "b"), Language.ENGLISH, Language.RUSSIAN, TranslationProvider.BERGAMOT,
        ).toList()

        assertEquals(
            "Couldn't download the Offline HQ language pack (en→ru, 31 MB). Check your internet and retry.",
            (states.last() as TranslationState.Error).message,
        )
        assertTrue("a pack that never arrived must not translate", states.none { it is TranslationState.Partial })
        assertTrue("the provider must not be called at all", repo.calls.isEmpty())
    }

    @Test
    fun `cancellation during prepare propagates`() = runTest {
        val repo = FakeTranslationRepository(prepareError = kotlinx.coroutines.CancellationException("cancelled"))
        var cancelled = false
        val states = mutableListOf<TranslationState>()
        try {
            useCase(repo)(
                listOf("a"), Language.ENGLISH, Language.RUSSIAN, TranslationProvider.BERGAMOT,
            ).toList(states)
        } catch (e: kotlinx.coroutines.CancellationException) {
            cancelled = true
        }

        assertTrue("cancellation must propagate, not become an error state", cancelled)
        assertEquals(0, states.count { it is TranslationState.Error })
    }

    @Test
    fun `engine unavailable is worded as a fallback to ML Kit`() = runTest {
        val repo = FakeTranslationRepository(
            failOn = setOf("a"),
            error = { OfflineEngineUnavailableException("dlopen") },
        )
        val err = useCase(repo)(
            listOf("a"), Language.ENGLISH, Language.RUSSIAN, TranslationProvider.BERGAMOT,
        ).toList().last() as TranslationState.Error

        assertEquals("Offline HQ isn't available on this device — using ML Kit.", err.message)
    }

    @Test
    fun `a provider without its key emits a clear error and translates nothing`() = runTest {
        val repo = FakeTranslationRepository(resolution = { Resolution.NotConfigured(it) })
        val states = useCase(repo)(listOf("a"), Language.ENGLISH, Language.RUSSIAN, TranslationProvider.DEEPL, 0, 0).toList()
        assertTrue(repo.calls.isEmpty())
        val error = states.single() as TranslationState.Error
        assertTrue(error.message, error.message.contains("API key isn't set"))
    }

    @Test
    fun `a fallback is announced once and the segment is translated by ML Kit`() = runTest {
        val repo = FakeTranslationRepository(resolution = {
            Resolution.Fallback(it, TranslationProvider.MLKIT, FallbackReason.UNSUPPORTED_PAIR)
        })
        val states = useCase(repo)(listOf("a", "b"), Language.ENGLISH, Language.HINDI, TranslationProvider.DEEPL, 0, 1).toList()
        assertEquals(1, states.count { it is TranslationState.FallbackNotice })
        assertEquals(listOf(TranslationProvider.MLKIT, TranslationProvider.MLKIT), repo.served)
    }
}
