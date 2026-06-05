package com.example.splitreader.domain.model

/** Drives the Account UI: still resolving, signed out, or signed in with a user. */
sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser) : AuthState
}
