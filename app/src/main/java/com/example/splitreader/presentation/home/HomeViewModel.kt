package com.example.splitreader.presentation.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitreader.data.local.ReadingProgressManager
import com.example.splitreader.domain.model.ParseResult
import com.example.splitreader.domain.repository.BookLibraryRepository
import com.example.splitreader.domain.usecase.ParseBookUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val parseBookUseCase: ParseBookUseCase,
    private val bookLibraryRepository: BookLibraryRepository,
    private val progressManager: ReadingProgressManager,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState = combine(
        bookLibraryRepository.getAllBooks(),
        _isLoading,
        _errorMessage,
    ) { books, isLoading, errorMessage ->
        HomeUiState(
            books = books.map {
                BookItem(
                    uri = it.uri,
                    title = it.title,
                    author = it.author,
                    coverPath = it.coverPath,
                    chapterCount = it.chapterCount,
                    lastChapterIndex = progressManager.getLastChapter(it.uri),
                )
            },
            isLoading = isLoading,
            errorMessage = errorMessage,
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
        openBook(Uri.parse(uri))
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
