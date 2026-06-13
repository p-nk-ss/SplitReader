package com.example.splitreader.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitreader.R
import com.example.splitreader.presentation.theme.AnimatedDialog
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.LocalRadii
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.LocalSpacing
import com.example.splitreader.presentation.theme.Newsreader
import com.example.splitreader.presentation.ui.SectionEyebrow

@Composable
fun AuthScreen(
    state: AuthUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onToggleMode: () -> Unit,
    onSubmit: () -> Unit,
    onGoogle: () -> Unit,
    onSendPasswordReset: (String) -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val sp = LocalSpacing.current
    val isRegister = state.mode == AuthMode.REGISTER

    var showReset by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(palette.bg)) {
      Column(
        Modifier
            .align(Alignment.TopCenter)
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = sp.xl, vertical = sp.lg),
    ) {
        Text(
            text = "← ${stringResource(R.string.action_cancel)}",
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
            color = palette.ink3,
            modifier = Modifier
                .clip(RoundedCornerShape(LocalRadii.current.sm))
                .clickable(enabled = !state.isLoading, onClick = onBack)
                .padding(vertical = sp.xs),
        )
        Spacer(Modifier.height(sp.md))

        Text(
            text = stringResource(R.string.account_title).uppercase(),
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            color = palette.ink3,
        )
        Text(
            text = stringResource(
                if (isRegister) R.string.auth_register_title else R.string.auth_sign_in_title,
            ),
            fontFamily = Newsreader,
            fontWeight = FontWeight.SemiBold,
            fontStyle = FontStyle.Italic,
            fontSize = 28.sp,
            color = palette.ink,
        )
        Spacer(Modifier.height(sp.lg))

        if (isRegister) {
            AuthField(
                value = state.name,
                onChange = onNameChange,
                label = stringResource(R.string.auth_name_label),
                placeholder = stringResource(R.string.auth_name_placeholder),
                isPassword = false,
                keyboardType = KeyboardType.Text,
                errorText = null,
                enabled = !state.isLoading,
            )
            Spacer(Modifier.height(sp.md))
        }

        AuthField(
            value = state.email,
            onChange = onEmailChange,
            label = stringResource(R.string.auth_email_label),
            placeholder = stringResource(R.string.auth_email_placeholder),
            isPassword = false,
            keyboardType = KeyboardType.Email,
            errorText = state.emailError?.let { stringResource(it.messageRes()) },
            enabled = !state.isLoading,
        )
        Spacer(Modifier.height(sp.md))
        AuthField(
            value = state.password,
            onChange = onPasswordChange,
            label = stringResource(R.string.auth_password_label),
            placeholder = stringResource(R.string.auth_password_placeholder),
            isPassword = true,
            keyboardType = KeyboardType.Password,
            errorText = state.passwordError?.let { stringResource(it.messageRes()) },
            enabled = !state.isLoading,
        )

        if (!isRegister) {
            Spacer(Modifier.height(sp.xs))
            Text(
                text = stringResource(R.string.auth_forgot_password),
                fontFamily = Newsreader,
                fontSize = 13.sp,
                color = palette.accent,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable(enabled = !state.isLoading) { showReset = true }
                    .padding(vertical = sp.xs),
            )
        }

        state.generalError?.let {
            Spacer(Modifier.height(sp.sm))
            Text(
                text = stringResource(it.messageRes()),
                fontFamily = Newsreader,
                fontSize = 13.sp,
                color = palette.accent,
            )
        }

        Spacer(Modifier.height(sp.lg))
        PrimaryButton(
            label = stringResource(
                if (isRegister) R.string.auth_register_action else R.string.auth_sign_in_action,
            ),
            enabled = !state.isLoading,
            loading = state.isLoading,
            onClick = onSubmit,
        )

        Spacer(Modifier.height(sp.md))
        OrDivider()
        Spacer(Modifier.height(sp.md))

        OutlinedButton(
            label = stringResource(R.string.auth_google_action),
            enabled = !state.isLoading,
            onClick = onGoogle,
        )

        Spacer(Modifier.height(sp.lg))
        Text(
            text = stringResource(
                if (isRegister) R.string.auth_toggle_to_sign_in else R.string.auth_toggle_to_register,
            ),
            fontFamily = Newsreader,
            fontSize = 14.sp,
            color = palette.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !state.isLoading, onClick = onToggleMode)
                .padding(vertical = sp.xs),
        )
      }
    }

    if (showReset) {
        PasswordResetDialog(
            initialEmail = state.email,
            onSend = {
                onSendPasswordReset(it)
                showReset = false
            },
            onDismiss = { showReset = false },
        )
    }
}

@Composable
private fun AuthField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isPassword: Boolean,
    keyboardType: KeyboardType,
    errorText: String?,
    enabled: Boolean,
) {
    val palette = LocalReaderPalette.current
    val radii = LocalRadii.current
    val sp = LocalSpacing.current
    SectionEyebrow(label)
    Spacer(Modifier.height(sp.xs))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radii.md))
            .background(palette.bg2)
            .border(1.dp, if (errorText != null) palette.accent else palette.edge, RoundedCornerShape(radii.md))
            .padding(horizontal = sp.sm, vertical = sp.sm),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 14.sp, color = palette.ink),
            cursorBrush = SolidColor(palette.ink),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, fontFamily = JetBrainsMono, fontSize = 14.sp, color = palette.ink3)
                }
                inner()
            },
        )
    }
    if (errorText != null) {
        Spacer(Modifier.height(sp.xs))
        Text(errorText, fontFamily = Newsreader, fontSize = 12.sp, color = palette.accent)
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    val palette = LocalReaderPalette.current
    val radii = LocalRadii.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radii.md))
            .background(if (enabled) palette.ink else palette.ink3)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (loading) "…" else label,
            fontFamily = Newsreader,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            color = palette.bg,
        )
    }
}

@Composable
private fun OutlinedButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalReaderPalette.current
    val radii = LocalRadii.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radii.md))
            .background(palette.bg2)
            .border(1.dp, palette.edge, RoundedCornerShape(radii.md))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = Newsreader,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            color = if (enabled) palette.ink else palette.ink3,
        )
    }
}

@Composable
private fun OrDivider() {
    val palette = LocalReaderPalette.current
    val sp = LocalSpacing.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
        Box(Modifier.weight(1f).height(1.dp).background(palette.edge))
        Text(stringResource(R.string.auth_or), fontFamily = JetBrainsMono, fontSize = 11.sp, color = palette.ink3)
        Box(Modifier.weight(1f).height(1.dp).background(palette.edge))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordResetDialog(
    initialEmail: String,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val radii = LocalRadii.current
    val sp = LocalSpacing.current
    var email by remember { mutableStateOf(initialEmail) }
    AnimatedDialog(onDismiss = onDismiss) { dismiss ->
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(palette.bg)
                .border(1.dp, palette.edge, RoundedCornerShape(18.dp))
                .padding(24.dp),
        ) {
            Text(
                stringResource(R.string.auth_reset_title),
                fontFamily = Newsreader,
                fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Italic,
                fontSize = 20.sp,
                color = palette.ink,
            )
            Spacer(Modifier.height(sp.xs))
            Text(
                stringResource(R.string.auth_reset_body),
                fontFamily = Newsreader,
                fontSize = 14.sp,
                color = palette.ink2,
            )
            Spacer(Modifier.height(sp.xs))
            Text(
                stringResource(R.string.auth_reset_spam_hint),
                fontFamily = Newsreader,
                fontStyle = FontStyle.Italic,
                fontSize = 12.sp,
                color = palette.ink3,
            )
            Spacer(Modifier.height(sp.md))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radii.md))
                    .background(palette.bg2)
                    .border(1.dp, palette.edge, RoundedCornerShape(radii.md))
                    .padding(horizontal = sp.sm, vertical = sp.sm),
            ) {
                BasicTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 14.sp, color = palette.ink),
                    cursorBrush = SolidColor(palette.ink),
                    decorationBox = { inner ->
                        if (email.isEmpty()) {
                            Text(
                                stringResource(R.string.auth_email_placeholder),
                                fontFamily = JetBrainsMono,
                                fontSize = 14.sp,
                                color = palette.ink3,
                            )
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(sp.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(sp.xs)) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(radii.md))
                        .background(palette.ink)
                        .clickable { if (email.isNotBlank()) onSend(email) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.auth_reset_action),
                        fontFamily = Newsreader,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = palette.bg,
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(radii.md))
                        .background(palette.bg2)
                        .border(1.dp, palette.edge, RoundedCornerShape(radii.md))
                        .clickable(onClick = dismiss)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.action_cancel),
                        fontFamily = Newsreader,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = palette.ink,
                    )
                }
            }
        }
    }
}
