package com.example.splitreader.presentation.profile

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitreader.R
import com.example.splitreader.domain.model.AuthState
import com.example.splitreader.presentation.auth.messageRes

@Composable
fun ProfileRoute(
    onBack: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val verificationSentMsg = stringResource(R.string.account_verification_sent)
    val deletedMsg = stringResource(R.string.account_deleted)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val msg = when (event) {
                AccountEvent.VerificationSent -> verificationSentMsg
                AccountEvent.Deleted -> deletedMsg
                is AccountEvent.Error -> context.getString(event.type.messageRes())
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    // Leave the profile once the user is no longer signed in (sign-out or deletion).
    LaunchedEffect(authState) {
        if (authState is AuthState.SignedOut) onBack()
    }

    ProfileScreen(
        authState = authState,
        ui = ui,
        onBack = onBack,
        onSignOut = viewModel::signOut,
        onResendVerification = viewModel::resendVerification,
        onRefreshUser = viewModel::refreshUser,
        onDeleteAccount = viewModel::deleteAccount,
        onReauthPassword = viewModel::reauthWithPasswordThenDelete,
        onReauthGoogle = { viewModel.reauthWithGoogleThenDelete(context) },
        onDismissReauth = viewModel::dismissReauth,
    )
}
