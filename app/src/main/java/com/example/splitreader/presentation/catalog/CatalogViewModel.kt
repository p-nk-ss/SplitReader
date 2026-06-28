package com.example.splitreader.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitreader.domain.CrashReporter
import com.example.splitreader.domain.model.CatalogBook
import com.example.splitreader.domain.model.CatalogSource
import com.example.splitreader.domain.model.ParseResult
import com.example.splitreader.domain.repository.CatalogRepository
import com.example.splitreader.domain.usecase.AddBookToLibraryUseCase
import com.example.splitreader.domain.usecase.AddResult
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
    val selectedSource: CatalogSource = CatalogSource.GUTENBERG,
    val books: List<CatalogBook> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val downloadingId: String? = null,
    val hasSearched: Boolean = false,
    val showLimitDialog: Boolean = false,
)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val parseBookUseCase: ParseBookUseCase,
    private val addBookToLibraryUseCase: AddBookToLibraryUseCase,
    private val crashReporter: CrashReporter,
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

    /** Switches the active catalog source, clearing the current list and re-running the query under it. */
    fun onSourceSelected(source: CatalogSource) {
        if (source == _uiState.value.selectedSource) return
        _uiState.update { it.copy(selectedSource = source, books = emptyList(), hasSearched = false) }
        runSearch(_uiState.value.query)
    }

    fun retry() = runSearch(_uiState.value.query)

    private fun runSearch(query: String) {
        searchJob?.cancel()
        val source = _uiState.value.selectedSource
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val page = catalogRepository.search(query, source = source, page = 1)
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
            // Gate before downloading so a free-tier user at the limit never pulls the file.
            if (!addBookToLibraryUseCase.canAddNew(null)) {
                _uiState.update { it.copy(showLimitDialog = true) }
                return@launch
            }
            _uiState.update { it.copy(downloadingId = book.id, errorMessage = null) }
            try {
                val uri = catalogRepository.downloadEpub(book)
                parseBookUseCase(uri).collect { result ->
                    when (result) {
                        is ParseResult.Loading -> Unit
                        is ParseResult.Success -> {
                            _uiState.update { it.copy(downloadingId = null) }
                            when (addBookToLibraryUseCase.add(result.book)) {
                                AddResult.Added -> _navigationEvent.trySend(result.book.filePath)
                                AddResult.LimitReached -> _uiState.update { it.copy(showLimitDialog = true) }
                            }
                        }
                        is ParseResult.Error -> _uiState.update {
                            it.copy(downloadingId = null, errorMessage = result.message)
                        }
                        is ParseResult.Idle -> _uiState.update { it.copy(downloadingId = null) }
                    }
                }
            } catch (e: Exception) {
                crashReporter.recordNonFatal(e, "Catalog download failed: ${book.id}")
                _uiState.update {
                    it.copy(downloadingId = null, errorMessage = e.message ?: "Download failed")
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissLimitDialog() {
        _uiState.update { it.copy(showLimitDialog = false) }
    }
}
