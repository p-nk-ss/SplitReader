package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.LanguageDetector
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ParagraphNote
import com.example.splitreader.domain.model.Resolution
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.domain.repository.TranslationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A paragraph in another language than the book's (2026-11-04): translated from its own language
 * when the engine can, shown as it is when it is already in the target language or in one that
 * can't be translated, and flagged — not silently downloaded for — when an on-device model is missing.
 */
class TranslateMixedParagraphsTest {

    /** Each paragraph starts with its language code: "de: …". */
    private class ByPrefix : LanguageDetector {
        override suspend fun identify(text: String): String? = null
        override suspend fun identifyConfident(text: String): String? = text.substringBefore(':').takeIf { it.length in 2..7 }
    }

    private class Repo(private val ready: (Language) -> Boolean = { true }) : TranslationRepository {
        val sources = mutableListOf<Pair<String, Language>>()
        val prepared = mutableListOf<Language>()
        override fun resolve(provider: TranslationProvider, source: Language, target: Language) = Resolution.Ready(provider)
        override suspend fun translate(text: String, source: Language, target: Language, provider: TranslationProvider): String {
            sources += text.substringBefore(':') to source
            return "t:$text"
        }
        override fun prepare(source: Language, target: Language, provider: TranslationProvider): Flow<Float> {
            prepared += source
            return emptyFlow()
        }
        override suspend fun isReady(source: Language, target: Language, provider: TranslationProvider) = ready(source)
        override suspend fun cachedCount() = 0
        override suspend fun clearCache() = Unit
    }

    private fun para(code: String) = "$code: a paragraph long enough to be told apart from a short reply or a name."

    private suspend fun kotlinx.coroutines.test.TestScope.partials(repo: Repo, provider: TranslationProvider, vararg codes: String) =
        TranslateTextUseCase(repo, StandardTestDispatcher(testScheduler), ParagraphLanguage(ByPrefix()))(
            codes.map(::para), Language.ENGLISH, Language.RUSSIAN, provider,
        ).filterIsInstance<TranslationState.Partial>().toList().sortedBy { it.index }

    @Test
    fun `a German paragraph in an English book is translated from German`() = runTest {
        val repo = Repo()
        val out = partials(repo, TranslationProvider.BERGAMOT, "en", "de", "en")
        assertEquals(listOf("en" to Language.ENGLISH, "de" to Language.GERMAN, "en" to Language.ENGLISH), repo.sources)
        assertEquals(listOf(null, ParagraphNote.TranslatedFrom(Language.GERMAN), null), out.map { it.note })
    }

    @Test
    fun `a paragraph already in the target language, or untranslatable, is shown as it is`() = runTest {
        val repo = Repo()
        val out = partials(repo, TranslationProvider.BERGAMOT, "ru", "la")
        assertEquals(emptyList<Pair<String, Language>>(), repo.sources)
        assertEquals(listOf(para("ru"), para("la")), out.map { it.text })
        assertEquals(listOf(ParagraphNote.Original("ru"), ParagraphNote.Original("la")), out.map { it.note })
    }

    @Test
    fun `without the on-device model the paragraph is flagged and nothing is downloaded`() = runTest {
        val repo = Repo(ready = { it != Language.GERMAN })
        val out = partials(repo, TranslationProvider.BERGAMOT, "de")
        assertEquals(listOf(ParagraphNote.NeedsModel(Language.GERMAN, TranslationProvider.BERGAMOT)), out.map { it.note })
        assertEquals(listOf(para("de")), out.map { it.text })
        assertEquals("only the book's own pair is prepared", listOf(Language.ENGLISH), repo.prepared)
    }

    @Test
    fun `an online engine translates another language without asking about models`() = runTest {
        val repo = Repo(ready = { false })
        val out = partials(repo, TranslationProvider.DEEPL, "de")
        assertEquals(listOf("de" to Language.GERMAN), repo.sources)
        assertEquals(listOf(ParagraphNote.TranslatedFrom(Language.GERMAN)), out.map { it.note })
    }
}
