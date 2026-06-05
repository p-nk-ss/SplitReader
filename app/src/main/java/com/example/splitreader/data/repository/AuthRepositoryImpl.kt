package com.example.splitreader.data.repository

import android.content.Context
import com.example.splitreader.data.auth.GoogleAuthClient
import com.example.splitreader.data.auth.GoogleIdTokenResult
import com.example.splitreader.domain.model.AuthErrorType
import com.example.splitreader.domain.model.AuthResult
import com.example.splitreader.domain.model.AuthState
import com.example.splitreader.domain.model.AuthUser
import com.example.splitreader.domain.repository.AuthRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val googleAuthClient: GoogleAuthClient,
) : AuthRepository {

    override val authState: Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser.toAuthState())
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override val currentUser: AuthUser?
        get() = firebaseAuth.currentUser?.toAuthUser()

    override suspend fun registerWithEmail(email: String, password: String, displayName: String?): AuthResult =
        runCatchingAuth {
            firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await()
            val name = displayName?.trim()
            if (!name.isNullOrEmpty()) {
                val update = UserProfileChangeRequest.Builder().setDisplayName(name).build()
                firebaseAuth.currentUser?.updateProfile(update)?.await()
            }
            firebaseAuth.currentUser?.sendEmailVerification()?.await()
        }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult = runCatchingAuth {
        firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    override suspend fun signInWithGoogle(activityContext: Context): AuthResult =
        when (val tokenResult = googleAuthClient.getGoogleIdToken(activityContext)) {
            is GoogleIdTokenResult.Success -> runCatchingAuth {
                val credential = GoogleAuthProvider.getCredential(tokenResult.idToken, null)
                firebaseAuth.signInWithCredential(credential).await()
            }
            GoogleIdTokenResult.Cancelled -> AuthResult.Error(AuthErrorType.GOOGLE_CANCELLED)
            is GoogleIdTokenResult.Failure -> AuthResult.Error(AuthErrorType.UNKNOWN, tokenResult.cause.message)
        }

    override suspend fun sendEmailVerification(): AuthResult = runCatchingAuth {
        firebaseAuth.currentUser?.sendEmailVerification()?.await()
    }

    override suspend fun reloadUser(): AuthResult = runCatchingAuth {
        firebaseAuth.currentUser?.reload()?.await()
    }

    override suspend fun sendPasswordReset(email: String): AuthResult = runCatchingAuth {
        firebaseAuth.sendPasswordResetEmail(email.trim()).await()
    }

    override fun signOut() {
        firebaseAuth.signOut()
    }

    override suspend fun deleteAccount(): AuthResult = runCatchingAuth {
        firebaseAuth.currentUser?.delete()?.await()
    }

    override suspend fun reauthenticateWithPassword(password: String): AuthResult = runCatchingAuth {
        val user = firebaseAuth.currentUser ?: return AuthResult.Error(AuthErrorType.USER_NOT_FOUND)
        val email = user.email ?: return AuthResult.Error(AuthErrorType.USER_NOT_FOUND)
        val credential = EmailAuthProvider.getCredential(email, password)
        user.reauthenticate(credential).await()
    }

    override suspend fun reauthenticateWithGoogle(activityContext: Context): AuthResult =
        when (val tokenResult = googleAuthClient.getGoogleIdToken(activityContext)) {
            is GoogleIdTokenResult.Success -> runCatchingAuth {
                val user = firebaseAuth.currentUser ?: return AuthResult.Error(AuthErrorType.USER_NOT_FOUND)
                val credential = GoogleAuthProvider.getCredential(tokenResult.idToken, null)
                user.reauthenticate(credential).await()
            }
            GoogleIdTokenResult.Cancelled -> AuthResult.Error(AuthErrorType.GOOGLE_CANCELLED)
            is GoogleIdTokenResult.Failure -> AuthResult.Error(AuthErrorType.UNKNOWN, tokenResult.cause.message)
        }

    override suspend fun signInMethodsForEmail(email: String): List<String> =
        try {
            @Suppress("DEPRECATION")
            firebaseAuth.fetchSignInMethodsForEmail(email.trim()).await().signInMethods ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

    /**
     * Runs a FirebaseAuth call and maps the outcome to [AuthResult]. A stale-session failure becomes
     * [AuthResult.ReauthRequired] (used by [deleteAccount]); everything else maps via [mapException].
     * Inline so the suspending `.await()` calls inside [block] run in the caller's coroutine.
     */
    private inline fun runCatchingAuth(block: () -> Unit): AuthResult =
        try {
            block()
            AuthResult.Success
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            AuthResult.ReauthRequired
        } catch (e: Throwable) {
            mapException(e)
        }

    private fun mapException(e: Throwable): AuthResult.Error {
        val type = when {
            e is FirebaseNetworkException -> AuthErrorType.NETWORK
            e is FirebaseAuthWeakPasswordException -> AuthErrorType.WEAK_PASSWORD
            e is FirebaseAuthUserCollisionException -> AuthErrorType.EMAIL_ALREADY_IN_USE
            e is FirebaseAuthInvalidUserException -> when (e.errorCode) {
                "ERROR_USER_NOT_FOUND", "ERROR_USER_DISABLED" -> AuthErrorType.USER_NOT_FOUND
                else -> AuthErrorType.UNKNOWN
            }
            e is FirebaseAuthInvalidCredentialsException -> when (e.errorCode) {
                "ERROR_INVALID_EMAIL" -> AuthErrorType.INVALID_EMAIL
                else -> AuthErrorType.WRONG_PASSWORD
            }
            e is FirebaseAuthException -> when (e.errorCode) {
                "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
                    AuthErrorType.ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL
                "ERROR_EMAIL_ALREADY_IN_USE" -> AuthErrorType.EMAIL_ALREADY_IN_USE
                "ERROR_WEAK_PASSWORD" -> AuthErrorType.WEAK_PASSWORD
                "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> AuthErrorType.WRONG_PASSWORD
                "ERROR_INVALID_EMAIL" -> AuthErrorType.INVALID_EMAIL
                "ERROR_USER_NOT_FOUND" -> AuthErrorType.USER_NOT_FOUND
                else -> AuthErrorType.UNKNOWN
            }
            else -> AuthErrorType.UNKNOWN
        }
        return AuthResult.Error(type, e.message)
    }

    private fun FirebaseUser.toAuthUser(): AuthUser = AuthUser(
        uid = uid,
        email = email,
        displayName = displayName,
        isEmailVerified = isEmailVerified,
        isFromGoogle = providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID },
    )

    private fun FirebaseUser?.toAuthState(): AuthState =
        this?.let { AuthState.SignedIn(it.toAuthUser()) } ?: AuthState.SignedOut
}
