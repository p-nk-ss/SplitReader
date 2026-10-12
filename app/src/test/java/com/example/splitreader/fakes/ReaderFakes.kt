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
import com.example.splitreader.domain.model.Resolution
import com.example.splitreader.domain.model.SavedWord
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslatorConfigState
import com.example.splitreader.domain.model.ProviderConfig
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
import com.example.splitreader.domain.repository.TranslatorSettings
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Translates to "<target code>:<text>"; fails the paragraphs in [failOn]; records every call. */
internal class FakeTranslationRepository : TranslationRepository {
    /** What [resolve] answers for each provider; Ready by default. */
    var resolution: (TranslationProvider) -> Resolution = { Resolution.Ready(it) }
    val calls: MutableList<Pair<String, Language>> = Collections.synchronizedList(mutableListOf())
    /** The provider each [translate] call was asked to serve with, paired with its text. */
    val served: MutableList<Pair<String, TranslationProvider>> = Collections.synchronizedList(mutableListOf())
    val failOn: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    /** A request for a text with a gate here suspends (after being recorded) until the gate completes. */
    val gates: MutableMap<String, CompletableDeferred<Unit>> = Collections.synchronizedMap(mutableMapOf())

    override fun resolve(provider: TranslationProvider, source: Language, target: Language) = resolution(provider)

    override suspend fun translate(text: String, source: Language, target: Language, provider: TranslationProvider): String {
        served += text to provider
        calls += text to target
        // A runaway engine would spin the test thread forever; past the limit, report once and park.
        if (calls.size > RUNAWAY_LIMIT) {
            if (calls.size == RUNAWAY_LIMIT + 1) throw AssertionError("runaway engine: >$RUNAWAY_LIMIT calls, last=$text")
            awaitCancellation()
        }
        gates[text]?.await()
        if (text in failOn) throw IOException("down: $text")
        return "${target.code}:$text"
    }
    override fun prepare(source: Language, target: Language, provider: TranslationProvider): Flow<Float> = emptyFlow()
    override suspend fun cachedCount() = 0
    override suspend fun clearCache() = Unit

    fun texts(): List<String> = synchronized(calls) { calls.map { it.first } }

    fun servedBy(provider: TranslationProvider): List<String> =
        synchronized(served) { served.filter { it.second == provider }.map { it.first } }

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

/**
 * In-memory [TranslatorSettings] on the caller's dispatcher: records each command in [log] and
 * publishes the result to [config]. [writeDelay] runs inside [saveKey] before anything is stored, so a
 * test can hold the key write (e.g. `CompletableDeferred.await()`).
 */
internal class FakeTranslatorSettings(
    current: TranslationProvider = TranslationProvider.MLKIT,
    configured: Set<TranslationProvider> = emptySet(),
) : TranslatorSettings {
    val log: MutableList<String> = Collections.synchronizedList(mutableListOf())
    var writeDelay: suspend () -> Unit = {}
    val mutableConfig = MutableStateFlow(
        TranslatorConfigState(current = current, configs = configured.associateWith { ProviderConfig(configured = true) }),
    )
    override val config: StateFlow<TranslatorConfigState> = mutableConfig

    override suspend fun select(provider: TranslationProvider) {
        log += "select $provider"
        if (mutableConfig.value.current != provider) mutableConfig.update { it.copy(current = provider) }
    }

    override suspend fun saveKey(provider: TranslationProvider, key: String?, secondary: String?) {
        log += "saveKey $provider"
        writeDelay()
        mutableConfig.update {
            it.copy(current = provider, configs = it.configs + (provider to ProviderConfig(true, secondary.orEmpty())))
        }
    }

    override suspend fun clear(provider: TranslationProvider) {
        log += "clear $provider"
        mutableConfig.update {
            it.copy(
                current = if (it.current == provider) TranslationProvider.MLKIT else it.current,
                configs = it.configs + (provider to ProviderConfig(configured = false)),
            )
        }
    }

    override suspend fun resetUsage(provider: TranslationProvider) {
        log += "resetUsage $provider"
    }

    override suspend fun refresh() {
        log += "refresh"
    }
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

/** Claims every `.epub` and returns [book], stamped with the opened uri as its file path; counts [parses]. */
internal class FakeBookParser(val book: Book) : BookParser {
    val parses = AtomicInteger()
    override val supportedExtensions = listOf("epub")
    override suspend fun parse(uri: Uri, context: Context): Book {
        parses.incrementAndGet()
        return book.copy(filePath = uri.toString())
    }
}
