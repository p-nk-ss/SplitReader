package com.example.splitreader.domain.model

/** Outcome of an auth action. [ReauthRequired] means the user must sign in again before retrying. */
sealed interface AuthResult {
    data object Success : AuthResult
    data class Error(val type: AuthErrorType, val message: String? = null) : AuthResult
    data object ReauthRequired : AuthResult
}

/** Provider-agnostic error categories; the UI maps each to a localized message. */
enum class AuthErrorType {
    INVALID_EMAIL,
    WEAK_PASSWORD,
    WRONG_PASSWORD,
    USER_NOT_FOUND,
    EMAIL_ALREADY_IN_USE,
    ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL,
    NETWORK,
    RECENT_LOGIN_REQUIRED,
    GOOGLE_CANCELLED,
    UNKNOWN,
}
