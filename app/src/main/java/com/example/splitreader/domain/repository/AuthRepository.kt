package com.example.splitreader.domain.repository

import android.content.Context
import com.example.splitreader.domain.model.AuthResult
import com.example.splitreader.domain.model.AuthState
import com.example.splitreader.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

/**
 * Authentication backed by Firebase Auth. Optional sign-in: the app works fully offline without it.
 * Methods taking [Context] need an Activity context because Credential Manager shows its own UI.
 */
interface AuthRepository {
    /** Emits the current [AuthState] and every change (sign-in / sign-out / token refresh). */
    val authState: Flow<AuthState>

    /** The user signed in right now, or null. */
    val currentUser: AuthUser?

    suspend fun registerWithEmail(email: String, password: String, displayName: String?): AuthResult
    suspend fun signInWithEmail(email: String, password: String): AuthResult
    suspend fun signInWithGoogle(activityContext: Context): AuthResult

    suspend fun sendEmailVerification(): AuthResult
    /** Reloads the user so [AuthUser.isEmailVerified] reflects a freshly confirmed email. */
    suspend fun reloadUser(): AuthResult
    suspend fun sendPasswordReset(email: String): AuthResult

    fun signOut()

    /** Deletes the account; may return [AuthResult.ReauthRequired] if the session is stale. */
    suspend fun deleteAccount(): AuthResult
    suspend fun reauthenticateWithPassword(password: String): AuthResult
    suspend fun reauthenticateWithGoogle(activityContext: Context): AuthResult

    /** Sign-in methods already registered for an email (e.g. "google.com"), for collision messages. */
    suspend fun signInMethodsForEmail(email: String): List<String>
}
