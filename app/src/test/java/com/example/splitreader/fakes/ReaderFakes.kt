package com.example.splitreader.fakes

import android.content.Context
import android.net.Uri
import com.example.splitreader.domain.CrashReporter
import com.example.splitreader.domain.LanguageDetector
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Bookmark
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.domain.model.ReadingSession
import com.example.splitreader.domain.model.SavedWord
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationUsage
import com.example.splitreader.domain.model.stats.BookMinutes
import com.example.splitreader.domain.model.stats.DailyMinutes
import com.example.splitreader.domain.model.stats.LangMinutes
import com.example.splitreader.domain.parser.BookParser
import com.example.splitreader.domain.repository.BookmarkRepository
import com.example.splitreader.domain.repository.LegacyProgress
import com.example.splitreader.domain.repository.LegacyReadingPositionStore
import com.example.splitreader.domain.repository.ReadingSessionRepository
import com.example.splitreader.domain.repository.SavedWordRepository
import com.example.splitreader.domain.repository.SpeechSynthesizer
import com.example.splitreader.domain.repository.TranslationRepository
import com.example.splitreader.domain.repository.TranslationUsageStats
import com.example.splitreader.domain.repository.TranslatorEndpointStore
import com.example.splitreader.domain.repository.TranslatorKeyStore
import java.io.IOException
import java.util.Collections
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Translates to "<target code>:<text>"; fails the paragraphs in [failOn]; records every call. */
internal class FakeTranslationRepository : TranslationRepository {
    val calls: MutableList<Pair<String, Language>> = Collections.synchronizedList(mutableListOf())
    val failOn: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    override suspend fun translate(text: String, sourceLanguage: Language, targetLanguage: Language): String {
        calls += text to targetLanguage
        // A runaway engine would spin the test thread forever; past the limit, report once and park.
        if (calls.size > RUNAWAY_LIMIT) {
            if (calls.size == RUNAWAY_LIMIT + 1) throw AssertionError("runaway engine: >$RUNAWAY_LIMIT calls, last=$text")
            awaitCancellation()
        }
        if (text in failOn) throw IOException("down: $text")
        return "${targetLanguage.code}:$text"
    }
    override fun prepare(sourceLanguage: Language, targetLanguage: Language): Flow<Float> = emptyFlow()
    override suspend fun cachedCount() = 0
    override suspend fun clearCache() = Unit

    fun texts(): List<String> = synchronized(calls) { calls.map { it.first } }

    private companion object {
        const val RUNAWAY_LIMIT = 1_000
    }
}

internal class FakeLanguageDetector(var language: Language = Language.ENGLISH) : LanguageDetector {
    override suspend fun detectLanguage(text: String): Language = language
}

internal class FakeCrashReporter : CrashReporter {
    val recorded: MutableList<Pair<Throwable, String?>> = Collections.synchronizedList(mutableListOf())
    override fun recordNonFatal(throwable: Throwable, message: String?) {
        recorded += throwable to message
    }
}

/** Every book is already migrated, unless [throwOnCheck] makes the check itself fail. */
internal class FakeLegacyReadingPositionStore(var throwOnCheck: Boolean = false) : LegacyReadingPositionStore {
    override fun isReadingPositionMigrated(bookUri: String): Boolean {
        if (throwOnCheck) throw IllegalStateException("boom")
        return true
    }
    override fun legacyProgress(bookUri: String): LegacyProgress? = null
    override fun completeReadingPositionMigration(bookUri: String, position: ReadingPosition?) = Unit
}

internal class FakeBookmarkRepository : BookmarkRepository {
    val bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    private var nextId = 1L

    override fun observeForBook(uri: String): Flow<List<Bookmark>> = bookmarks.map { all -> all.filter { it.bookUri == uri } }
    override suspend fun add(bookUri: String, chapterIndex: Int, paragraphIndex: Int, label: String?) {
        bookmarks.update { it + Bookmark(nextId++, bookUri, chapterIndex, paragraphIndex, label, createdAt = 0L) }
    }
    override suspend fun remove(bookUri: String, chapterIndex: Int, paragraphIndex: Int) {
        bookmarks.update { all -> all.filterNot { it.matches(bookUri, chapterIndex, paragraphIndex) } }
    }
    override suspend fun toggle(bookUri: String, chapterIndex: Int, paragraphIndex: Int) {
        if (bookmarks.value.any { it.matches(bookUri, chapterIndex, paragraphIndex) }) {
            remove(bookUri, chapterIndex, paragraphIndex)
        } else {
            add(bookUri, chapterIndex, paragraphIndex)
        }
    }
    override suspend fun listForBook(bookUri: String): List<Bookmark> = bookmarks.value.filter { it.bookUri == bookUri }
    override suspend fun replaceForBook(bookUri: String, bookmarks: List<Bookmark>) {
        this.bookmarks.update { all -> all.filterNot { it.bookUri == bookUri } + bookmarks }
    }

    private fun Bookmark.matches(uri: String, chapter: Int, paragraph: Int) =
        bookUri == uri && chapterIndex == chapter && paragraphIndex == paragraph
}

internal class FakeTranslatorKeyStore : TranslatorKeyStore {
    override fun getKey(provider: TranslationProvider): String? = null
    override fun setKey(provider: TranslationProvider, value: String?) = Unit
}

internal class FakeTranslatorEndpointStore : TranslatorEndpointStore {
    override fun getSecondary(provider: TranslationProvider): String = ""
    override fun setSecondary(provider: TranslationProvider, value: String?) = Unit
}

internal class FakeTranslationUsageStats : TranslationUsageStats {
    override fun usage(provider: TranslationProvider) = TranslationUsage(0, null)
    override fun reset(provider: TranslationProvider) = Unit
}

internal class FakeSpeechSynthesizer : SpeechSynthesizer {
    override fun speak(text: String, langCode: String) = Unit
    override fun setRate(rate: Float) = Unit
    override fun setPitch(pitch: Float) = Unit
    override fun shutdown() = Unit
}

internal class FakeReadingSessionRepository : ReadingSessionRepository {
    val sessions: MutableList<ReadingSession> = Collections.synchronizedList(mutableListOf())
    override suspend fun record(session: ReadingSession) {
        sessions += session
    }
    override fun observeDailyMinutes(daysBack: Int): Flow<List<DailyMinutes>> = flowOf(emptyList())
    override fun observeWeeklyMinutes(): Flow<List<DailyMinutes>> = flowOf(emptyList())
    override fun observeTimeByBook(daysBack: Int): Flow<List<BookMinutes>> = flowOf(emptyList())
    override fun observeTimeByLang(daysBack: Int): Flow<List<LangMinutes>> = flowOf(emptyList())
}

internal class FakeSavedWordRepository : SavedWordRepository {
    val words = MutableStateFlow<List<SavedWord>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<SavedWord>> = words
    override fun observeByLang(code: String): Flow<List<SavedWord>> = words.map { all -> all.filter { it.sourceLang == code } }
    override fun search(q: String): Flow<List<SavedWord>> = words.map { all -> all.filter { it.word.contains(q, ignoreCase = true) } }
    override fun countByLang(code: String): Flow<Int> = observeByLang(code).map { it.size }
    override suspend fun findByWordAndLang(word: String, lang: String): SavedWord? =
        words.value.firstOrNull { it.word == word && it.sourceLang == lang }
    override suspend fun save(word: SavedWord): Long {
        val id = nextId++
        words.update { it + word.copy(id = id) }
        return id
    }
    override suspend fun update(word: SavedWord) {
        words.update { all -> all.map { if (it.id == word.id) word else it } }
    }
    override suspend fun delete(word: SavedWord) {
        words.update { all -> all.filterNot { it.id == word.id } }
    }
}

/** Claims every `.epub` and returns [book], stamped with the opened uri as its file path. */
internal class FakeBookParser(val book: Book) : BookParser {
    override val supportedExtensions = listOf("epub")
    override suspend fun parse(uri: Uri, context: Context): Book = book.copy(filePath = uri.toString())
}
