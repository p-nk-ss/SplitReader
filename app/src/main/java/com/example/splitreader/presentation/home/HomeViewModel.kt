package com.example.splitreader.presentation.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.viewModelScope
import com.example.splitreader.R
import com.example.splitreader.data.local.ReadingProgressManager
import com.example.splitreader.domain.model.ParseResult
import com.example.splitreader.domain.repository.BookLibraryRepository
import com.example.splitreader.domain.repository.ReadingSessionRepository
import com.example.splitreader.domain.repository.SavedWordRepository
import com.example.splitreader.domain.usecase.GetStreakUseCase
import com.example.splitreader.domain.usecase.ParseBookUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parseBookUseCase: ParseBookUseCase,
    private val bookLibraryRepository: BookLibraryRepository,
    private val progressManager: ReadingProgressManager,
    private val sessionRepository: ReadingSessionRepository,
    private val savedWordRepository: SavedWordRepository,
    private val getStreakUseCase: GetStreakUseCase,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private data class HomeStats(
        val streakDays: Int = 0,
        val weeklyMinutes: Int = 0,
        val savedWordsThisWeek: Int = 0,
    )

    // combine has standard overloads up to 5 sources, so the three stat flows are
    // collapsed into one HomeStats flow before joining the library/loading/error flows.
    private val statsFlow = combine(
        getStreakUseCase().map { it.current },
        sessionRepository.observeWeeklyMinutes().map { days -> days.sumOf { it.minutes } },
        savedWordRepository.observeAll().map { words ->
            val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            words.count { it.savedAt >= weekAgo }
        },
    ) { streakDays, weeklyMinutes, savedWords ->
        HomeStats(streakDays, weeklyMinutes, savedWords)
    }

    val uiState = combine(
        bookLibraryRepository.getAllBooks(),
        _isLoading,
        _errorMessage,
        statsFlow,
    ) { books, isLoading, errorMessage, stats ->
        HomeUiState(
            books = books.map {
                BookItem(
                    uri = it.uri,
                    title = it.title,
                    author = it.author,
                    coverPath = it.coverPath,
                    chapterCount = it.chapterCount,
                    lastChapterIndex = progressManager.getLastChapter(it.uri),
                    isFinished = progressManager.isFinished(it.uri),
                )
            },
            isLoading = isLoading,
            errorMessage = errorMessage,
            streakDays = stats.streakDays,
            weeklyMinutes = stats.weeklyMinutes,
            savedWordsThisWeek = stats.savedWordsThisWeek,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    private val _navigationEvent = Channel<String>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    private var parseJob: Job? = null

    fun openBook(uri: Uri) {
        // Persist read permission so the URI stays accessible across app restarts
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) { }

        parseJob?.cancel()
        parseJob = viewModelScope.launch {
            parseBookUseCase(uri).collect { result ->
                when (result) {
                    is ParseResult.Loading -> {
                        _isLoading.value = true
                        _errorMessage.value = null
                    }
                    is ParseResult.Success -> {
                        bookLibraryRepository.saveBook(result.book)
                        _isLoading.value = false
                        _navigationEvent.trySend(result.book.filePath)
                    }
                    is ParseResult.Error -> {
                        _isLoading.value = false
                        _errorMessage.value = result.message
                    }
                    is ParseResult.Idle -> _isLoading.value = false
                }
            }
        }
    }

    fun openBookFromLibrary(uri: String) {
        val parsedUri = Uri.parse(uri)
        // Catalog downloads live in app-private storage (file:// URIs) and are always readable;
        // only content:// URIs from the document picker need a persisted read grant.
        if (parsedUri.scheme != "file") {
            val hasPermission = context.contentResolver.persistedUriPermissions
                .any { it.uri == parsedUri && it.isReadPermission }
            if (!hasPermission) {
                _errorMessage.value = context.getString(R.string.error_file_access)
                return
            }
        }
        viewModelScope.launch {
            bookLibraryRepository.touchBook(uri)
            _navigationEvent.trySend(uri)
        }
    }

    fun openLastBook() {
        viewModelScope.launch {
            val lastUri = bookLibraryRepository.getAllBooks().first().firstOrNull()?.uri ?: return@launch
            openBook(Uri.parse(lastUri))
        }
    }

    fun deleteBook(uri: String) {
        viewModelScope.launch { bookLibraryRepository.deleteBook(uri) }
    }

    fun dismissError() {
        _errorMessage.value = null
    }
}
