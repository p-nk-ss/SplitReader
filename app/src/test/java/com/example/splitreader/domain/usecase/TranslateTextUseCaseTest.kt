package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.OrientationLock
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.domain.repository.ReadingPreferences
import com.example.splitreader.domain.repository.TranslationRepository
import com.example.splitreader.domain.translator.ModelPair
import com.example.splitreader.domain.translator.OfflineEngineUnavailableException
import com.example.splitreader.domain.translator.OfflinePackDownloadException
import java.io.IOException
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.StateFlow
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
) : TranslationRepository {
    val calls = mutableListOf<String>()

    override suspend fun translate(text: String, sourceLanguage: Language, targetLanguage: Language): String {
        calls += text
        val remaining = transientFailures[text] ?: 0
        if (remaining > 0) {
            transientFailures[text] = remaining - 1
            throw error(text)
        }
        if (text in failOn) throw error(text)
        return "t:$text"
    }

    override fun prepare(sourceLanguage: Language, targetLanguage: Language): Flow<Float> =
        prepareProgress.asFlow()

    override suspend fun cachedCount() = 0

    override suspend fun clearCache() = Unit
}

/** Everything defaulted; only the translator-provider choice matters to the use case. */
private class FakeReadingPreferences(
    private val provider: TranslationProvider = TranslationProvider.QUICK_TRANSLATE,
) : ReadingPreferences {
    override fun saveProgress(bookUri: String, chapterIndex: Int, scrollPosition: Int, scrollOffset: Int) = Unit
    override fun getLastBookUri(): String? = null
    override fun getLastChapter(bookUri: String) = 0
    override fun getLastScrollPosition(bookUri: String, chapterIndex: Int) = 0
    override fun getLastScrollOffset(bookUri: String, chapterIndex: Int) = 0
    override fun saveExcerpt(bookUri: String, text: String) = Unit
    override fun getExcerpt(bookUri: String): String? = null
    override fun markFinished(bookUri: String) = Unit
    override fun isFinished(bookUri: String) = false
    override fun clearProgress(bookUri: String) = Unit
    override fun saveTargetLanguage(language: Language) = Unit
    override fun getTargetLanguage() = Language.ENGLISH
    override fun saveNavigationSideLeft(isLeft: Boolean) = Unit
    override fun isNavigationLeft() = false
    override val readerThemeName: StateFlow<String> = MutableStateFlow("paper")
    override fun saveReaderTheme(themeName: String) = Unit
    override fun getReaderThemeName() = "paper"
    override val orientationLock: StateFlow<OrientationLock> = MutableStateFlow(OrientationLock.AUTO)
    override fun saveOrientationLock(lock: OrientationLock) = Unit
    override fun getOrientationLock() = OrientationLock.AUTO
    override fun saveLineHeightMultiplier(multiplier: Float) = Unit
    override fun getLineHeightMultiplier() = 1f
    override fun saveSplitRatio(ratio: Float) = Unit
    override fun getSplitRatio() = 0.5f
    override fun saveVerticalSplitRatio(ratio: Float) = Unit
    override fun getVerticalSplitRatio() = 0.5f
    override fun savePortraitHintDismissed(dismissed: Boolean) = Unit
    override fun getPortraitHintDismissed() = true
    override fun saveShowTranslation(show: Boolean) = Unit
    override fun getShowTranslation() = true
    override fun saveShowIllustrations(show: Boolean) = Unit
    override fun getShowIllustrations() = true
    override fun saveHorizontalMargin(margin: Float) = Unit
    override fun getHorizontalMargin() = 0f
    override fun setTranslatorProvider(provider: TranslationProvider) = Unit
    override fun getTranslatorProvider() = provider
    override fun saveTextSize(size: Float) = Unit
    override fun getTextSize() = 16f
    override fun saveReadingFont(name: String) = Unit
    override fun getReadingFontName() = ""
    override fun saveParagraphSpacing(spacing: Float) = Unit
    override fun getParagraphSpacing() = 0f
    override fun saveLetterSpacing(spacing: Float) = Unit
    override fun getLetterSpacing() = 0f
    override fun saveTextIndent(indent: Float) = Unit
    override fun getTextIndent() = 0f
    override fun saveJustifyText(justify: Boolean) = Unit
    override fun getJustifyText() = false
    override fun saveTtsRate(rate: Float) = Unit
    override fun getTtsRate() = 1f
    override fun saveTtsPitch(pitch: Float) = Unit
    override fun getTtsPitch() = 1f
}

class TranslateTextUseCaseTest {

    private fun TestScope.useCase(
        repo: TranslationRepository,
        provider: TranslationProvider = TranslationProvider.QUICK_TRANSLATE,
    ) = TranslateTextUseCase(repo, FakeReadingPreferences(provider), StandardTestDispatcher(testScheduler))

    @Test
    fun `a single failing paragraph does not abort the rest of the segment`() = runTest {
        val repo = FakeTranslationRepository(failOn = setOf("b"))
        val states = useCase(repo)(listOf("a", "b", "c"), Language.ENGLISH, Language.RUSSIAN, 0, 2).toList()

        val partials = states.filterIsInstance<TranslationState.Partial>()
        assertEquals(listOf(0 to "t:a", 2 to "t:c"), partials.map { it.index to it.text })
        assertEquals(1, states.count { it is TranslationState.Error })
        assertTrue("error must come after the surviving partials", states.last() is TranslationState.Error)
    }

    @Test
    fun `three consecutive failures abort the segment without trying the rest`() = runTest {
        val repo = FakeTranslationRepository(failOn = setOf("b", "c", "d"))
        val states = useCase(repo)(listOf("a", "b", "c", "d", "e"), Language.ENGLISH, Language.RUSSIAN, 0, 4).toList()

        assertEquals(listOf("a", "b", "c", "d"), repo.calls) // gives up before "e"
        val partials = states.filterIsInstance<TranslationState.Partial>()
        assertEquals(listOf(0), partials.map { it.index })
        assertEquals(1, states.count { it is TranslationState.Error })
    }

    @Test
    fun `interleaved failures never trip the consecutive-failure cutoff`() = runTest {
        val repo = FakeTranslationRepository(failOn = setOf("b", "d"))
        val states = useCase(repo)(listOf("a", "b", "c", "d", "e"), Language.ENGLISH, Language.RUSSIAN, 0, 4).toList()

        assertEquals(listOf("a", "b", "c", "d", "e"), repo.calls)
        val partials = states.filterIsInstance<TranslationState.Partial>()
        assertEquals(listOf(0, 2, 4), partials.map { it.index })
    }

    @Test
    fun `cancellation propagates instead of being swallowed as a translation failure`() = runTest {
        val repo = object : TranslationRepository {
            val calls = mutableListOf<String>()
            override suspend fun translate(text: String, sourceLanguage: Language, targetLanguage: Language): String {
                calls += text
                if (text == "b") throw kotlinx.coroutines.CancellationException("worker cancelled")
                return "t:$text"
            }
            override fun prepare(sourceLanguage: Language, targetLanguage: Language) = emptyFlow<Float>()
            override suspend fun cachedCount() = 0
            override suspend fun clearCache() = Unit
        }
        val states = mutableListOf<TranslationState>()
        var cancelled = false
        try {
            useCase(repo)(
                listOf("a", "b", "c"), Language.ENGLISH, Language.RUSSIAN, 0, 2,
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
        val states = useCase(repo, TranslationProvider.QUICK_TRANSLATE)(
            listOf("a"), Language.ENGLISH, Language.RUSSIAN, 0, 0,
        ).toList()

        val message = states.filterIsInstance<TranslationState.Error>().single().message
        assertTrue("expected rate-limit wording, got: $message", message.contains("rate-limited"))
        assertTrue("free-endpoint 429 must not claim an exhausted quota: $message", !message.contains("quota"))
    }

    @Test
    fun `paid-provider 429 keeps the quota wording`() = runTest {
        val repo = FakeTranslationRepository(failOn = setOf("a"), error = { http429() })
        val states = useCase(repo, TranslationProvider.DEEPL)(
            listOf("a"), Language.ENGLISH, Language.RUSSIAN, 0, 0,
        ).toList()

        val message = states.filterIsInstance<TranslationState.Error>().single().message
        assertTrue("expected quota wording for a keyed provider, got: $message", message.contains("quota"))
    }

    @Test
    fun `a 429 is retried after a backoff and can still succeed`() = runTest {
        val repo = FakeTranslationRepository(transientFailures = mutableMapOf("a" to 1), error = { http429() })
        val states = useCase(repo)(listOf("a"), Language.ENGLISH, Language.RUSSIAN, 0, 0).toList()

        assertEquals(listOf("a", "a"), repo.calls)
        assertEquals(listOf(0 to "t:a"), states.filterIsInstance<TranslationState.Partial>().map { it.index to it.text })
        assertEquals(0, states.count { it is TranslationState.Error })
        assertTrue("retry must wait before re-hitting the endpoint", currentTime > 0)
    }

    @Test
    fun `429 retries are capped before the paragraph counts as failed`() = runTest {
        val repo = FakeTranslationRepository(failOn = setOf("a"), error = { http429() })
        val states = useCase(repo)(listOf("a"), Language.ENGLISH, Language.RUSSIAN, 0, 0).toList()

        assertEquals("one initial attempt + two backoff retries", listOf("a", "a", "a"), repo.calls)
        assertEquals(1, states.count { it is TranslationState.Error })
    }

    @Test
    fun `plain network failures are not retried with backoff`() = runTest {
        val repo = FakeTranslationRepository(failOn = setOf("a")) // IOException
        useCase(repo)(listOf("a", "b"), Language.ENGLISH, Language.RUSSIAN, 0, 1).toList()

        assertEquals(listOf("a", "b"), repo.calls)
        assertEquals("no backoff for non-429 failures", 0, currentTime)
    }

    @Test
    fun `a clean segment emits every partial and no error`() = runTest {
        val repo = FakeTranslationRepository()
        val states = useCase(repo)(listOf("a", "b"), Language.ENGLISH, Language.RUSSIAN, 0, 1).toList()

        val partials = states.filterIsInstance<TranslationState.Partial>()
        assertEquals(listOf(0 to "t:a", 1 to "t:b"), partials.map { it.index to it.text })
        assertEquals(0, states.count { it is TranslationState.Error })
    }

    @Test
    fun `offline providers emit DownloadingModel with forwarded progress before translating`() = runTest {
        val repo = FakeTranslationRepository(prepareProgress = listOf(0.25f, 1f))
        val states = useCase(repo, TranslationProvider.BERGAMOT)(
            listOf("a"), Language.ENGLISH, Language.RUSSIAN,
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
        val states = useCase(FakeTranslationRepository(), TranslationProvider.QUICK_TRANSLATE)(
            listOf("a"), Language.ENGLISH, Language.RUSSIAN,
        ).toList()

        assertTrue(states.none { it is TranslationState.DownloadingModel })
    }

    @Test
    fun `pack download failure is worded with the pair and size`() = runTest {
        val repo = FakeTranslationRepository(
            failOn = setOf("a"),
            error = { OfflinePackDownloadException(ModelPair(Language.ENGLISH, Language.RUSSIAN), 31_561_787) },
        )
        val err = useCase(repo, TranslationProvider.BERGAMOT)(
            listOf("a"), Language.ENGLISH, Language.RUSSIAN,
        ).toList().last() as TranslationState.Error

        assertEquals(
            "Couldn't download the Offline HQ language pack (en→ru, 31 MB). Check your internet and retry.",
            err.message,
        )
    }

    @Test
    fun `engine unavailable is worded as a fallback to ML Kit`() = runTest {
        val repo = FakeTranslationRepository(
            failOn = setOf("a"),
            error = { OfflineEngineUnavailableException("dlopen") },
        )
        val err = useCase(repo, TranslationProvider.BERGAMOT)(
            listOf("a"), Language.ENGLISH, Language.RUSSIAN,
        ).toList().last() as TranslationState.Error

        assertEquals("Offline HQ isn't available on this device — using ML Kit.", err.message)
    }
}
