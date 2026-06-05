package com.example.splitreader.presentation.auth

import android.content.Context
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitreader.domain.model.AuthErrorType
import com.example.splitreader.domain.model.AuthResult
import com.example.splitreader.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state = _state.asStateFlow()

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onNameChange(value: String) =
        _state.update { it.copy(name = value, generalError = null) }

    fun onEmailChange(value: String) =
        _state.update { it.copy(email = value, emailError = null, generalError = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, passwordError = null, generalError = null) }

    fun toggleMode() = _state.update {
        it.copy(
            mode = if (it.mode == AuthMode.SIGN_IN) AuthMode.REGISTER else AuthMode.SIGN_IN,
            emailError = null,
            passwordError = null,
            generalError = null,
        )
    }

    fun submit() {
        val current = _state.value
        if (current.isLoading) return
        val emailValid = Patterns.EMAIL_ADDRESS.matcher(current.email.trim()).matches()
        val passwordValid = current.password.length >= MIN_PASSWORD
        if (!emailValid || !passwordValid) {
            _state.update {
                it.copy(
                    emailError = if (!emailValid) AuthFieldError.INVALID_EMAIL else null,
                    passwordError = if (!passwordValid) AuthFieldError.PASSWORD_TOO_SHORT else null,
                )
            }
            return
        }
        val isRegister = current.mode == AuthMode.REGISTER
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, generalError = null) }
            val result =
                if (isRegister) authRepository.registerWithEmail(current.email, current.password, current.name)
                else authRepository.signInWithEmail(current.email, current.password)
            handleResult(result, isRegister)
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, generalError = null) }
            handleResult(authRepository.signInWithGoogle(activityContext), isRegister = false)
        }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            authRepository.sendPasswordReset(email)
            // Always neutral: never reveal whether the address is registered.
            _events.send(AuthEvent.PasswordResetSent)
        }
    }

    private suspend fun handleResult(result: AuthResult, isRegister: Boolean) {
        when (result) {
            AuthResult.Success -> {
                _state.update { it.copy(isLoading = false) }
                _events.send(AuthEvent.Authenticated)
            }
            AuthResult.ReauthRequired -> _state.update {
                it.copy(isLoading = false, generalError = AuthErrorType.RECENT_LOGIN_REQUIRED)
            }
            is AuthResult.Error -> {
                when (result.type) {
                    // User dismissed the Google sheet — not an error worth showing.
                    AuthErrorType.GOOGLE_CANCELLED -> _state.update { it.copy(isLoading = false) }
                    AuthErrorType.EMAIL_ALREADY_IN_USE -> {
                        val shownType =
                            if (isRegister && usesGoogle(_state.value.email))
                                AuthErrorType.ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL
                            else AuthErrorType.EMAIL_ALREADY_IN_USE
                        _state.update { it.copy(isLoading = false, generalError = shownType) }
                    }
                    else -> _state.update { it.copy(isLoading = false, generalError = result.type) }
                }
            }
        }
    }

    private suspend fun usesGoogle(email: String): Boolean =
        authRepository.signInMethodsForEmail(email).any { it.contains("google", ignoreCase = true) }

    private companion object {
        const val MIN_PASSWORD = 8
    }
}
