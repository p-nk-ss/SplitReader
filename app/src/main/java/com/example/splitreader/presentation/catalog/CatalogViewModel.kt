package com.example.splitreader.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitreader.domain.model.CatalogBook
import com.example.splitreader.domain.model.ParseResult
import com.example.splitreader.domain.repository.BookLibraryRepository
import com.example.splitreader.domain.repository.CatalogRepository
import com.example.splitreader.domain.usecase.ParseBookUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 350L

data class CatalogUiState(
    val query: String = "",
    val books: List<CatalogBook> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val downloadingId: Int? = null,
    val hasSearched: Boolean = false,
)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val parseBookUseCase: ParseBookUseCase,
    private val bookLibraryRepository: BookLibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState = _uiState.asStateFlow()

    private val _navigationEvent = Channel<String>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    private var searchJob: Job? = null
    private var downloadJob: Job? = null

    init {
        // Populate with popular books so the screen is useful before the user types anything.
        runSearch("")
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runSearch(query)
        }
    }

    fun retry() = runSearch(_uiState.value.query)

    private fun runSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val page = catalogRepository.search(query, languages = emptyList(), page = 1)
                _uiState.update {
                    it.copy(books = page.books, isLoading = false, hasSearched = true)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasSearched = true,
                        errorMessage = e.message ?: "Search failed",
                    )
                }
            }
        }
    }

    fun downloadAndOpen(book: CatalogBook) {
        if (_uiState.value.downloadingId != null) return
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _uiState.update { it.copy(downloadingId = book.id, errorMessage = null) }
            try {
                val uri = catalogRepository.downloadEpub(book)
                parseBookUseCase(uri).collect { result ->
                    when (result) {
                        is ParseResult.Loading -> Unit
                        is ParseResult.Success -> {
                            bookLibraryRepository.saveBook(result.book)
                            _uiState.update { it.copy(downloadingId = null) }
                            _navigationEvent.trySend(result.book.filePath)
                        }
                        is ParseResult.Error -> _uiState.update {
                            it.copy(downloadingId = null, errorMessage = result.message)
                        }
                        is ParseResult.Idle -> _uiState.update { it.copy(downloadingId = null) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(downloadingId = null, errorMessage = e.message ?: "Download failed")
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
