package com.example.splitreader.presentation.auth

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitreader.R

@Composable
fun AuthRoute(
    onAuthComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resetSentMessage = stringResource(R.string.auth_reset_sent)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AuthEvent.Authenticated -> onAuthComplete()
                AuthEvent.PasswordResetSent ->
                    Toast.makeText(context, resetSentMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    AuthScreen(
        state = state,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onToggleMode = viewModel::toggleMode,
        onSubmit = viewModel::submit,
        onGoogle = { viewModel.signInWithGoogle(context) },
        onSendPasswordReset = viewModel::sendPasswordReset,
        onBack = onBack,
    )
}
