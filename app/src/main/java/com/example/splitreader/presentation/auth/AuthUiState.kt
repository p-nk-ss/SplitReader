package com.example.splitreader.presentation.auth

import com.example.splitreader.domain.model.AuthErrorType

enum class AuthMode { SIGN_IN, REGISTER }

enum class AuthFieldError { INVALID_EMAIL, PASSWORD_TOO_SHORT }

/** State of the sign-in / register screen. */
data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val emailError: AuthFieldError? = null,
    val passwordError: AuthFieldError? = null,
    val isLoading: Boolean = false,
    val generalError: AuthErrorType? = null,
)

/** One-time effects from [AuthViewModel] consumed by the route. */
sealed interface AuthEvent {
    data object Authenticated : AuthEvent
    data object PasswordResetSent : AuthEvent
}
