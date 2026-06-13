package com.example.splitreader.presentation.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.splitreader.data.local.ReadingProgressManager
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.repository.TranslationRepository
import com.example.splitreader.domain.usecase.TranslateTextUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Concern #3: when the reader jumps several chapters forward, the translation of the chapters it
 * already read must stay on screen (the UI renders [ChapterTranslationManager]'s accumulated snapshot).
 *
 * Drives the real manager + [TranslateTextUseCase] with a fake [TranslationRepository] that translates
 * instantly. Uses a real coroutine scope (the use case runs on Dispatchers.IO, so virtual time can't
 * gate it) and polls the emitted snapshots until they reach the expected state.
 */
@RunWith(AndroidJUnit4::class)
class ChapterTranslationManagerRetentionTest {

    private val parasPerChapter = 4
    private val chapterCount = 6

    private fun fakeBook(): Book = Book(
        title = "Fake",
        author = "Author",
        chapters = (0 until chapterCount).map { ch ->
            Chapter(
                index = ch,
                title = "Chapter $ch",
                paragraphs = (0 until parasPerChapter).map { p -> "c${ch}p$p original" },
            )
        },
        filePath = "fake://book",
    )

    /** Marks a paragraph translated so the planner/manager treat it as done. */
    private val fakeRepo = object : TranslationRepository {
        override suspend fun translate(text: String, sourceLanguage: Language, targetLanguage: Language): String =
            "<<$text>>"
    }

    @Test
    fun previousChaptersTranslationSurvivesForwardJump() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = ReadingProgressManager(context)
        val useCase = TranslateTextUseCase(fakeRepo, settings)
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        val manager = ChapterTranslationManager(
            scope = scope,
            translateTextUseCase = useCase,
            isMlKit = { true },              // free engine → translates even while we don't toggle the pane
            isTranslationVisible = { true },
        )
        manager.attach(fakeBook(), Language.ENGLISH, Language.RUSSIAN)

        val latest = AtomicReference<Map<Int, List<String>>>(emptyMap())
        val collector = scope.launch { manager.updates.collect { latest.set(it.translations) } }

        fun chapterFullyTranslated(ch: Int): Boolean {
            val list = latest.get()[ch] ?: return false
            return list.size == parasPerChapter && list.all { it.isNotEmpty() }
        }

        suspend fun awaitUntil(timeoutMs: Long = 10_000, predicate: () -> Boolean): Boolean =
            withTimeoutOrNull(timeoutMs) {
                while (!predicate()) delay(25)
                true
            } ?: false

        try {
            // 1) Read/translate chapter 0.
            manager.onVisibleRange(0, 0, 0, parasPerChapter - 1)
            assertTrue("chapter 0 was never translated", awaitUntil { chapterFullyTranslated(0) })

            // 2) Jump several chapters forward to chapter 5.
            manager.onVisibleRange(5, 0, 5, parasPerChapter - 1)
            assertTrue("chapter 5 was never translated after the jump", awaitUntil { chapterFullyTranslated(5) })

            // 3) The previously-read chapter 0 translation must still be present and complete.
            val snapshot = latest.get()
            assertTrue("chapter 0 translation was dropped on the forward jump", snapshot.containsKey(0))
            assertEquals(
                "chapter 0 translation became incomplete after jumping forward",
                parasPerChapter,
                snapshot[0]?.count { it.isNotEmpty() },
            )
            assertEquals("<<c0p0 original>>", snapshot[0]?.get(0))
        } finally {
            manager.cancelAll()
            collector.cancel()
            scope.cancel()
        }
    }
}
