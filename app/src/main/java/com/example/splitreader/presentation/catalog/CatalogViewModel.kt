package com.example.splitreader.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitreader.domain.CrashReporter
import com.example.splitreader.domain.catalog.CatalogUnavailableException
import com.example.splitreader.domain.model.CatalogBook
import com.example.splitreader.domain.model.CatalogSource
import com.example.splitreader.domain.model.ParseResult
import com.example.splitreader.domain.repository.CatalogRepository
import com.example.splitreader.domain.usecase.AddBookToLibraryUseCase
import com.example.splitreader.domain.usecase.AddResult
import com.example.splitreader.domain.usecase.ParseBookUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
    /** The query [books] was loaded for: while a newer search loads or after it fails, not [query]. */
    val listQuery: String = "",
    /** [errorMessage] is a failed search (the screen offers Retry), not a failed download. */
    val searchFailed: Boolean = false,
) {
    /** What the list on screen is: labelled by the query it was loaded for, not the field's text. */
    val listLabel: String get() = if (listQuery.isBlank()) "POPULAR" else "RESULTS"
}

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
        _uiState.update { it.copy(selectedSource = source, books = emptyList(), listQuery = "", hasSearched = false) }
        runSearch(_uiState.value.query)
    }

    fun retry() = runSearch(_uiState.value.query)

    private fun runSearch(query: String) {
        searchJob?.cancel()
        val source = _uiState.value.selectedSource
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, searchFailed = false) }
            try {
                val page = catalogRepository.search(query, source = source, page = 1)
                _uiState.update {
                    it.copy(books = page.books, listQuery = query, isLoading = false, hasSearched = true)
                }
            } catch (e: CancellationException) {
                // A newer search replaced this one: its state is the newer search's, not an error.
                throw e
            } catch (e: Exception) {
                // Superseded, yet its call failed anyway: a blocking call (OkHttp execute()) is not
                // interrupted by cancellation, so it can throw a real IOException afterwards. The
                // state belongs to the newer search; rethrow instead of writing an error over it.
                ensureActive()
                // A list already on screen stays, labelled by its own query (listQuery); the error
                // shows above it, with Retry.
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasSearched = true,
                        errorMessage = e.message ?: "Search failed",
                        searchFailed = true,
                    )
                }
            }
        }
    }

    fun downloadAndOpen(book: CatalogBook) {
        if (_uiState.value.downloadingId != null) return
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            // The catalog book's uri is deterministic from its source+id, so we know it before
            // downloading — letting a free-tier user at the limit re-open a book they already own
            // (an upsert, never "new") instead of hitting the limit dialog for it.
            val uri = catalogRepository.localUriFor(book)
            // Gate before downloading so a free-tier user at the limit never pulls the file.
            if (!addBookToLibraryUseCase.canAddNew(uri.toString())) {
                _uiState.update { it.copy(showLimitDialog = true) }
                return@launch
            }
            _uiState.update { it.copy(downloadingId = book.id, errorMessage = null, searchFailed = false) }
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
            } catch (e: CancellationException) {
                _uiState.update { it.copy(downloadingId = null) }
                throw e
            } catch (e: CatalogUnavailableException) {
                // The source is down (maintenance): expected, not a crash, so it is not reported.
                _uiState.update { it.copy(downloadingId = null, errorMessage = e.message) }
            } catch (e: Exception) {
                crashReporter.recordNonFatal(e, "Catalog download failed: ${book.id}")
                _uiState.update {
                    it.copy(downloadingId = null, errorMessage = e.message ?: "Download failed")
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null, searchFailed = false) }
    }

    fun dismissLimitDialog() {
        _uiState.update { it.copy(showLimitDialog = false) }
    }
}
