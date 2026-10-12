package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.Resolution
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.repository.TranslationRepository
import com.example.splitreader.fakes.FakeSavedWordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Translates to "t:<text>" unless [resolution] says otherwise. */
private class ResolvingTranslationRepository(
    private val resolution: (TranslationProvider) -> Resolution,
) : TranslationRepository {
    override fun resolve(provider: TranslationProvider, source: Language, target: Language) = resolution(provider)
    override suspend fun translate(text: String, source: Language, target: Language, provider: TranslationProvider) = "t:$text"
    override fun prepare(source: Language, target: Language, provider: TranslationProvider): Flow<Float> = emptyFlow()
    override suspend fun cachedCount() = 0
    override suspend fun clearCache() = Unit
}

class SaveWordUseCaseTest {

    private val words = FakeSavedWordRepository()

    private suspend fun TestScope.invoke(resolution: (TranslationProvider) -> Resolution): SaveWordResult =
        SaveWordUseCase(
            words,
            TranslateTextUseCase(ResolvingTranslationRepository(resolution), StandardTestDispatcher(testScheduler)),
        )(
            word = "Hello,",
            contextSnippet = "Hello, world",
            sourceLang = Language.ENGLISH,
            targetLang = Language.RUSSIAN,
            bookUri = "/b",
            bookTitle = "T",
            chapterIndex = 0,
            paragraphIndex = 0,
            provider = TranslationProvider.DEEPL,
        )

    @Test
    fun `a word whose provider has no key is not saved and reports the failure`() = runTest {
        val result = invoke { Resolution.NotConfigured(it) }

        assertEquals(SaveWordResult.TRANSLATION_FAILED, result)
        assertTrue("saved: ${words.words.value}", words.words.value.isEmpty())
    }

    @Test
    fun `a translated word is saved with its translation`() = runTest {
        val result = invoke { Resolution.Ready(it) }

        assertEquals(SaveWordResult.SAVED, result)
        assertEquals("t:hello", words.words.value.single().translation)
    }
}
