package com.example.splitreader.domain.model

/** The signed-in user, mapped from FirebaseUser so the rest of the app never sees Firebase types. */
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val isEmailVerified: Boolean,
    val isFromGoogle: Boolean,
)
