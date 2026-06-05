package com.example.splitreader.data.auth

import android.accounts.Account
import android.app.Activity
import android.content.Intent
import com.example.splitreader.domain.model.DrivePickedFile
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** `drive.file` is non-sensitive: the app only ever sees files the user explicitly picks. */
private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

/** Key under which Play Services returns the picked file ids (comma-separated) in the result. */
private const val PICKED_FILE_IDS = "picked_file_ids"

/**
 * Wraps Play Services' Authorization API to authorize the `drive.file` scope **and** show the Drive
 * Picker in one flow. This is the "forward-compat seam" the [GoogleAuthClient] comment anticipated —
 * Drive access is independent of the Firebase ID-token sign-in, so that flow stays untouched.
 *
 * Requesting the scope together with the [AuthorizationRequest.ResourceParameter.PICKER_OAUTH_TRIGGER]
 * resource parameter makes Play Services render the native Drive Picker as part of consent, so a
 * single round-trip yields both the access token and the picked file id(s).
 */
@Singleton
class DriveAuthClient @Inject constructor() {

    /**
     * Builds the authorize-and-pick request. When [accountEmail] is the Google account the user
     * already signed in with, the flow skips the account chooser.
     *
     * No mime-type filter on purpose: `.fb2` (and many `.mobi`) files live in Drive with a generic
     * type like `application/octet-stream`, so filtering by ebook mimes would hide the user's books.
     * The parser validates the pick and reports unsupported files, so showing all files is safe.
     */
    fun buildRequest(accountEmail: String?): AuthorizationRequest {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            // Scope the token to drive.file only, ignoring any previously granted scopes.
            .setOptOutIncludingGrantedScopes(true)
            // CONSENT is required for the native Drive Picker to surface: it's shown as part of the
            // authorization resolution. Without it, authorize() returns the already-granted access
            // with no interactive step, so no file is picked. (No SELECT_ACCOUNT — we bind the
            // account below, so this no longer re-asks WHICH account, only re-confirms access.)
            .setPrompt(AuthorizationRequest.Prompt.CONSENT)
            .addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_OAUTH_TRIGGER, "true")
        // Reuse the account the user signed in with so Play Services doesn't re-ask which to use.
        accountEmail?.let { builder.setAccount(Account(it, "com.google")) }
        return builder.build()
    }

    /**
     * Kicks off the authorize+pick flow. The returned [AuthorizationResult] either already carries
     * the token/picked files (access previously granted) or [AuthorizationResult.hasResolution] is
     * true, in which case its PendingIntent must be launched by the UI to show consent + picker.
     */
    suspend fun authorize(activity: Activity, request: AuthorizationRequest): AuthorizationResult =
        Identity.getAuthorizationClient(activity).authorize(request).await()

    /** Reads the final result after the UI launched the resolution PendingIntent. */
    fun resultFromIntent(activity: Activity, data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)

    /** The comma-separated `picked_file_ids` from the picker, or empty if the user picked nothing. */
    fun pickedFiles(result: AuthorizationResult): List<DrivePickedFile> =
        result.tokenResponseParams
            ?.getString(PICKED_FILE_IDS)
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { DrivePickedFile(fileId = it) }
}
