package com.example.splitreader.presentation.catalog

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitreader.R
import com.example.splitreader.data.auth.DriveAuthClient
import com.example.splitreader.domain.model.AuthState
import com.example.splitreader.domain.model.DrivePickedFile
import com.example.splitreader.domain.model.ParseResult
import com.example.splitreader.domain.repository.AuthRepository
import com.example.splitreader.domain.repository.BookLibraryRepository
import com.example.splitreader.domain.repository.DriveRepository
import com.example.splitreader.domain.usecase.ParseBookUseCase
import com.google.android.gms.auth.api.identity.AuthorizationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DriveUiState(
    val isSignedInWithGoogle: Boolean = false,
    /** True while authorizing, picking, or downloading — drives the section's spinner. */
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
)

/** Asks the Composable to launch the Authorization PendingIntent (consent + Drive Picker UI). */
sealed interface DriveEvent {
    data class LaunchAuthorization(val pendingIntent: PendingIntent) : DriveEvent
}

/**
 * Orchestrates the Drive import: authorize+pick → download → parse → save → navigate. Kept separate
 * from [CatalogViewModel] (which eagerly runs a Gutenberg search) so the gated Drive section can
 * mount independently. The download→parse→save→navigate tail mirrors `CatalogViewModel.downloadAndOpen`.
 *
 * The Activity-result launcher for the consent/picker PendingIntent lives in the Composable (a VM
 * can't own one), so the Activity is passed transiently into the entry points and never retained.
 */
@HiltViewModel
class DriveViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val driveAuthClient: DriveAuthClient,
    private val driveRepository: DriveRepository,
    private val parseBookUseCase: ParseBookUseCase,
    private val bookLibraryRepository: BookLibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriveUiState())
    val uiState = _uiState.asStateFlow()

    private val _navigationEvent = Channel<String>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    private val _events = Channel<DriveEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            authRepository.authState.collect { state ->
                val fromGoogle = (state as? AuthState.SignedIn)?.user?.isFromGoogle == true
                _uiState.update { it.copy(isSignedInWithGoogle = fromGoogle) }
            }
        }
    }

    fun onPickFromDriveClicked(activity: Activity) {
        if (_uiState.value.isBusy) return
        job?.cancel()
        job = viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            try {
                val request = driveAuthClient.buildRequest(authRepository.currentUser?.email)
                val result = driveAuthClient.authorize(activity, request)
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent != null) {
                        // UI launches it; the picked file comes back via onAuthorizationResult().
                        _events.trySend(DriveEvent.LaunchAuthorization(pendingIntent))
                    } else {
                        fail(R.string.drive_error_download)
                    }
                } else {
                    // Access already granted and a file already picked — process inline.
                    process(result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(R.string.drive_error_download)
            }
        }
    }

    fun onAuthorizationResult(activity: Activity, data: Intent?) {
        job?.cancel()
        job = viewModelScope.launch {
            try {
                process(driveAuthClient.resultFromIntent(activity, data))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Dismissing the consent/picker surfaces as an ApiException here.
                _uiState.update {
                    it.copy(isBusy = false, errorMessage = context.getString(R.string.drive_error_cancelled))
                }
            }
        }
    }

    private suspend fun process(result: AuthorizationResult) {
        val token = result.accessToken
        val files = driveAuthClient.pickedFiles(result)
        if (token.isNullOrBlank() || files.isEmpty()) {
            // Consent granted but nothing picked — clear the spinner without an error.
            _uiState.update { it.copy(isBusy = false) }
            return
        }
        downloadAndOpen(files.first(), token)
    }

    private suspend fun downloadAndOpen(file: DrivePickedFile, accessToken: String) {
        try {
            val uri = driveRepository.downloadFile(file, accessToken)
            parseBookUseCase(uri).collect { result ->
                when (result) {
                    is ParseResult.Loading -> Unit
                    is ParseResult.Success -> {
                        bookLibraryRepository.saveBook(result.book)
                        _uiState.update { it.copy(isBusy = false) }
                        _navigationEvent.trySend(result.book.filePath)
                    }
                    // Surface the parser's real reason (unsupported, corrupt, no chapters, …) so
                    // failures are diagnosable instead of hidden behind a generic message.
                    is ParseResult.Error -> _uiState.update {
                        it.copy(isBusy = false, errorMessage = result.message)
                    }
                    is ParseResult.Idle -> _uiState.update { it.copy(isBusy = false) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Show the real reason (e.g. "Drive metadata failed: HTTP 403" when the Drive API is
            // disabled) so download failures are diagnosable instead of a generic message.
            Log.e("DRIVE", "download/parse failed", e)
            _uiState.update {
                it.copy(isBusy = false, errorMessage = e.message ?: context.getString(R.string.drive_error_download))
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun fail(resId: Int) {
        _uiState.update { it.copy(isBusy = false, errorMessage = context.getString(resId)) }
    }
}
