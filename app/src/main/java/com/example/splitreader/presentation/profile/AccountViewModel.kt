package com.example.splitreader.presentation.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitreader.domain.model.AuthErrorType
import com.example.splitreader.domain.model.AuthResult
import com.example.splitreader.domain.model.AuthState
import com.example.splitreader.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Transient UI state for account management (work-in-progress flag + reauth prompt visibility). */
data class AccountUiState(
    val isWorking: Boolean = false,
    val showReauth: Boolean = false,
)

sealed interface AccountEvent {
    data object Deleted : AccountEvent
    data object VerificationSent : AccountEvent
    data class Error(val type: AuthErrorType) : AccountEvent
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    // Own StateFlow rather than stateIn(repo flow) because FirebaseAuth doesn't fire its listener
    // on reload(); [refreshUser] pushes the freshly-reloaded user so verification status updates.
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState = _authState.asStateFlow()

    private val _ui = MutableStateFlow(AccountUiState())
    val ui = _ui.asStateFlow()

    private val _events = Channel<AccountEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            authRepository.authState.collect { _authState.value = it }
        }
    }

    fun signOut() = authRepository.signOut()

    fun resendVerification() {
        viewModelScope.launch {
            authRepository.sendEmailVerification()
            _events.send(AccountEvent.VerificationSent)
        }
    }

    fun refreshUser() {
        viewModelScope.launch {
            authRepository.reloadUser()
            authRepository.currentUser?.let { _authState.value = AuthState.SignedIn(it) }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _ui.update { it.copy(isWorking = true) }
            when (val result = authRepository.deleteAccount()) {
                AuthResult.Success -> {
                    _ui.update { AccountUiState() }
                    _events.send(AccountEvent.Deleted)
                }
                AuthResult.ReauthRequired -> _ui.update { it.copy(isWorking = false, showReauth = true) }
                is AuthResult.Error -> {
                    _ui.update { it.copy(isWorking = false) }
                    _events.send(AccountEvent.Error(result.type))
                }
            }
        }
    }

    fun reauthWithPasswordThenDelete(password: String) {
        viewModelScope.launch {
            _ui.update { it.copy(isWorking = true) }
            finishReauth(authRepository.reauthenticateWithPassword(password))
        }
    }

    fun reauthWithGoogleThenDelete(activityContext: Context) {
        viewModelScope.launch {
            _ui.update { it.copy(isWorking = true) }
            finishReauth(authRepository.reauthenticateWithGoogle(activityContext))
        }
    }

    fun dismissReauth() = _ui.update { it.copy(showReauth = false, isWorking = false) }

    private suspend fun finishReauth(reauthResult: AuthResult) {
        if (reauthResult != AuthResult.Success) {
            val type = (reauthResult as? AuthResult.Error)?.type ?: AuthErrorType.UNKNOWN
            _ui.update { it.copy(isWorking = false) }
            _events.send(AccountEvent.Error(type))
            return
        }
        when (val deleteResult = authRepository.deleteAccount()) {
            AuthResult.Success -> {
                _ui.update { AccountUiState() }
                _events.send(AccountEvent.Deleted)
            }
            else -> {
                val type = (deleteResult as? AuthResult.Error)?.type ?: AuthErrorType.UNKNOWN
                _ui.update { it.copy(isWorking = false, showReauth = false) }
                _events.send(AccountEvent.Error(type))
            }
        }
    }
}
