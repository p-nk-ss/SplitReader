package com.example.splitreader.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.splitreader.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of asking the user to pick a Google account via Credential Manager. */
sealed interface GoogleIdTokenResult {
    data class Success(val idToken: String) : GoogleIdTokenResult
    data object Cancelled : GoogleIdTokenResult
    data class Failure(val cause: Throwable) : GoogleIdTokenResult
}

/**
 * Wraps Credential Manager to obtain a Google ID token, which the [AuthRepository] exchanges for a
 * Firebase credential. Uses the modern androidx.credentials stack (not the deprecated GoogleSignInClient).
 *
 * Forward-compat seam: when Google Drive support lands, request Drive access HERE as a separate
 * authorization step (play-services-auth AuthorizationClient.authorize with DriveScopes.DRIVE_READONLY).
 * Authorization is independent of this ID-token authentication, so the sign-in flow below stays untouched.
 */
@Singleton
class GoogleAuthClient @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    private val credentialManager by lazy { CredentialManager.create(appContext) }

    /**
     * Shows the Google account picker and returns an ID token. Needs an Activity [activityContext]
     * because Credential Manager renders its own UI (the singleton only holds the app context).
     */
    suspend fun getGoogleIdToken(activityContext: Context): GoogleIdTokenResult {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(appContext.getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val response = credentialManager.getCredential(activityContext, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleIdTokenResult.Success(googleCredential.idToken)
            } else {
                GoogleIdTokenResult.Failure(IllegalStateException("Unexpected credential type"))
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleIdTokenResult.Cancelled
        } catch (e: GetCredentialException) {
            GoogleIdTokenResult.Failure(e)
        } catch (e: GoogleIdTokenParsingException) {
            GoogleIdTokenResult.Failure(e)
        }
    }
}
