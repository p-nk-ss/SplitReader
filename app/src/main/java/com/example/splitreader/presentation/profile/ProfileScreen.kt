package com.example.splitreader.presentation.profile

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitreader.R
import com.example.splitreader.domain.model.AuthState
import com.example.splitreader.domain.model.AuthUser
import com.example.splitreader.presentation.theme.AnimatedDialog
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.LocalRadii
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.LocalSpacing
import com.example.splitreader.presentation.theme.Newsreader

@Composable
fun ProfileScreen(
    authState: AuthState,
    ui: AccountUiState,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onResendVerification: () -> Unit,
    onRefreshUser: () -> Unit,
    onDeleteAccount: () -> Unit,
    onReauthPassword: (String) -> Unit,
    onReauthGoogle: () -> Unit,
    onDismissReauth: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val radii = LocalRadii.current
    val sp = LocalSpacing.current
    val user = (authState as? AuthState.SignedIn)?.user

    var showDeleteConfirm by remember { mutableStateOf(false) }

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
                .clip(RoundedCornerShape(radii.sm))
                .clickable(onClick = onBack)
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
            text = stringResource(R.string.profile_title),
            fontFamily = Newsreader,
            fontWeight = FontWeight.SemiBold,
            fontStyle = FontStyle.Italic,
            fontSize = 28.sp,
            color = palette.ink,
        )
        Spacer(Modifier.height(sp.lg))

        if (user == null) {
            Text(
                text = stringResource(R.string.account_signed_out_hint),
                fontFamily = Newsreader,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                color = palette.ink2,
            )
            return@Column
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(radii.md))
                .background(palette.bg2)
                .border(1.dp, palette.edge, RoundedCornerShape(radii.md))
                .padding(sp.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(palette.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = user.avatarInitial(),
                        fontFamily = Newsreader,
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Italic,
                        fontSize = 18.sp,
                        color = palette.bg,
                    )
                }
                Spacer(Modifier.size(sp.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = user.email ?: user.displayName ?: stringResource(R.string.account_signed_in_as),
                        fontFamily = Newsreader,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = palette.ink,
                    )
                    if (user.isFromGoogle || user.isEmailVerified) {
                        Text(
                            text = "✓ ${stringResource(R.string.account_email_verified)}",
                            fontFamily = JetBrainsMono,
                            fontSize = 11.sp,
                            color = palette.ink3,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.account_email_unverified),
                            fontFamily = JetBrainsMono,
                            fontSize = 11.sp,
                            color = palette.accent,
                        )
                    }
                }
            }

            if (!user.isFromGoogle && !user.isEmailVerified) {
                Spacer(Modifier.height(sp.md))
                Row(horizontalArrangement = Arrangement.spacedBy(sp.xs)) {
                    OutlinedAction(
                        label = stringResource(R.string.account_verify_resend),
                        onClick = onResendVerification,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedAction(
                        label = stringResource(R.string.account_verify_refresh),
                        onClick = onRefreshUser,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(sp.md))
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.edge))
            Spacer(Modifier.height(sp.md))

            OutlinedAction(
                label = stringResource(R.string.account_sign_out),
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(sp.sm))
            Text(
                text = stringResource(R.string.account_delete),
                fontFamily = Newsreader,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = palette.accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radii.md))
                    .clickable(enabled = !ui.isWorking) { showDeleteConfirm = true }
                    .padding(vertical = 12.dp),
            )
        }
      }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.account_delete_title),
            body = stringResource(R.string.account_delete_body),
            confirmLabel = stringResource(R.string.account_delete_confirm),
            onConfirm = {
                showDeleteConfirm = false
                onDeleteAccount()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    if (ui.showReauth) {
        if (user?.isFromGoogle == true) {
            ConfirmDialog(
                title = stringResource(R.string.auth_reauth_title),
                body = stringResource(R.string.auth_reauth_body),
                confirmLabel = stringResource(R.string.auth_reauth_google_action),
                onConfirm = onReauthGoogle,
                onDismiss = onDismissReauth,
            )
        } else {
            ReauthPasswordDialog(
                onConfirm = onReauthPassword,
                onDismiss = onDismissReauth,
            )
        }
    }
}

private fun AuthUser.avatarInitial(): String =
    (email ?: displayName)?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

@Composable
private fun OutlinedAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalReaderPalette.current
    val radii = LocalRadii.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radii.md))
            .background(palette.bg)
            .border(1.dp, palette.edge, RoundedCornerShape(radii.md))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = palette.ink)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val radii = LocalRadii.current
    val sp = LocalSpacing.current
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
                title,
                fontFamily = Newsreader,
                fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Italic,
                fontSize = 20.sp,
                color = palette.ink,
            )
            Spacer(Modifier.height(sp.xs))
            Text(body, fontFamily = Newsreader, fontSize = 14.sp, color = palette.ink2)
            Spacer(Modifier.height(sp.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(sp.xs)) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(radii.md))
                        .background(palette.ink)
                        .clickable(onClick = onConfirm)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(confirmLabel, fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = palette.bg)
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
                    Text(stringResource(R.string.action_cancel), fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = palette.ink)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReauthPasswordDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val radii = LocalRadii.current
    val sp = LocalSpacing.current
    var password by remember { mutableStateOf("") }
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
                stringResource(R.string.auth_reauth_title),
                fontFamily = Newsreader,
                fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Italic,
                fontSize = 20.sp,
                color = palette.ink,
            )
            Spacer(Modifier.height(sp.xs))
            Text(stringResource(R.string.auth_reauth_body), fontFamily = Newsreader, fontSize = 14.sp, color = palette.ink2)
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
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 14.sp, color = palette.ink),
                    cursorBrush = SolidColor(palette.ink),
                    decorationBox = { inner ->
                        if (password.isEmpty()) {
                            Text(
                                stringResource(R.string.auth_password_placeholder),
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
                        .clickable { if (password.isNotBlank()) onConfirm(password) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.auth_reauth_password_action), fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = palette.bg)
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
                    Text(stringResource(R.string.action_cancel), fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = palette.ink)
                }
            }
        }
    }
}
