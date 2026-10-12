package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.model.Language
import com.example.splitreader.fakes.FakeSavedWordRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The use case has no translator dependency: it stores the translation the popup already shows. */
class SaveWordUseCaseTest {

    private val words = FakeSavedWordRepository()

    private suspend fun save(word: String, translation: String = "бежать"): SaveWordResult =
        SaveWordUseCase(words)(
            word = word,
            contextSnippet = "Run, run",
            sourceLang = Language.ENGLISH,
            targetLang = Language.RUSSIAN,
            bookUri = "/b",
            bookTitle = "T",
            chapterIndex = 0,
            paragraphIndex = 0,
            translation = translation,
        )

    @Test
    fun `the popup's translation is saved as is`() = runTest {
        val result = save("Run,", translation = "бежать")

        assertEquals(SaveWordResult.SAVED, result)
        val saved = words.words.value.single()
        assertEquals("run", saved.word)
        assertEquals("бежать", saved.translation)
    }

    @Test
    fun `blank and duplicate words behave as before`() = runTest {
        assertEquals(SaveWordResult.EMPTY, save(" ,. "))
        assertEquals(SaveWordResult.SAVED, save("Run"))
        assertEquals(SaveWordResult.DUPLICATE, save("run!"))
        assertEquals(1, words.words.value.size)
    }
}
