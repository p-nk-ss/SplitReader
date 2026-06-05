package com.example.splitreader.presentation.auth

import androidx.annotation.StringRes
import com.example.splitreader.R
import com.example.splitreader.domain.model.AuthErrorType

/** Maps a provider-agnostic [AuthErrorType] to a localized message resource. */
@StringRes
fun AuthErrorType.messageRes(): Int = when (this) {
    AuthErrorType.INVALID_EMAIL -> R.string.auth_error_invalid_email
    AuthErrorType.WEAK_PASSWORD -> R.string.auth_error_weak_password
    AuthErrorType.WRONG_PASSWORD -> R.string.auth_error_wrong_password
    AuthErrorType.USER_NOT_FOUND -> R.string.auth_error_user_not_found
    AuthErrorType.EMAIL_ALREADY_IN_USE -> R.string.auth_error_email_in_use
    AuthErrorType.ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL -> R.string.auth_error_google_collision
    AuthErrorType.NETWORK -> R.string.auth_error_network
    AuthErrorType.RECENT_LOGIN_REQUIRED -> R.string.auth_error_recent_login
    AuthErrorType.GOOGLE_CANCELLED, AuthErrorType.UNKNOWN -> R.string.auth_error_unknown
}

/** Maps an inline field-validation error to a localized message resource. */
@StringRes
fun AuthFieldError.messageRes(): Int = when (this) {
    AuthFieldError.INVALID_EMAIL -> R.string.auth_error_email_invalid_inline
    AuthFieldError.PASSWORD_TOO_SHORT -> R.string.auth_error_password_too_short
}
