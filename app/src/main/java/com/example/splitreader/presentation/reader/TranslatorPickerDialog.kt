package com.example.splitreader.presentation.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitreader.data.local.TranslationUsage
import com.example.splitreader.data.local.UrlResult
import com.example.splitreader.data.local.normalizeLibreUrl
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationProviderCategory
import com.example.splitreader.presentation.theme.DangerTone
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.LocalRadii
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.LocalSpacing
import com.example.splitreader.presentation.theme.Newsreader
import com.example.splitreader.presentation.theme.WarnTone
import com.example.splitreader.presentation.theme.animatedSelection
import androidx.compose.material3.Text

@Composable
internal fun TranslatorPickerDialog(
    state: TranslatorConfigState,
    onSelect: (TranslationProvider) -> Unit,
    onConfigure: (provider: TranslationProvider, key: String?, secondary: String?) -> Unit,
    onClear: (TranslationProvider) -> Unit,
    onResetUsage: (TranslationProvider) -> Unit,
    onTranslateWholeChapter: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var keyDialogTarget by remember { mutableStateOf<TranslationProvider?>(null) }
    var resetConfirmTarget by remember { mutableStateOf<TranslationProvider?>(null) }
    val sp = LocalSpacing.current

    EditorialDialog(eyebrow = "Translator", title = "Choose provider", onDismiss = onDismiss) {
        val free = TranslationProvider.entries.filter { it.category == TranslationProviderCategory.FREE }
        val advanced = TranslationProvider.entries.filter { it.category == TranslationProviderCategory.ADVANCED }

        SectionLabel("Free, no setup")
        Spacer(Modifier.height(sp.xs))
        Column(verticalArrangement = Arrangement.spacedBy(sp.xs)) {
            free.forEach { provider ->
                ProviderRow(
                    provider = provider,
                    selected = provider == state.current,
                    configured = state.configs[provider]?.configured ?: true,
                    usage = state.usage[provider],
                    onSelect = { onSelect(provider) },
                    onConfigure = null,
                    onResetUsage = { resetConfirmTarget = provider },
                )
            }
        }

        Spacer(Modifier.height(sp.md))
        SectionLabel("Advanced (own API key)")
        Spacer(Modifier.height(sp.xs))
        Column(verticalArrangement = Arrangement.spacedBy(sp.xs)) {
            advanced.forEach { provider ->
                val configured = state.configs[provider]?.configured ?: false
                ProviderRow(
                    provider = provider,
                    selected = provider == state.current,
                    configured = configured,
                    usage = state.usage[provider],
                    onSelect = {
                        if (!configured) keyDialogTarget = provider else onSelect(provider)
                    },
                    onConfigure = { keyDialogTarget = provider },
                    onResetUsage = { resetConfirmTarget = provider },
                )
            }
        }

        // Paid providers translate only the visible window + look-ahead to conserve quota/tokens.
        // This is the on-demand escape-hatch to finish the rest of the current chapter.
        if (onTranslateWholeChapter != null && state.current.category == TranslationProviderCategory.ADVANCED) {
            Spacer(Modifier.height(sp.md))
            val palette = LocalReaderPalette.current
            val radii = LocalRadii.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radii.md))
                    .background(palette.ink)
                    .clickable {
                        onTranslateWholeChapter()
                        onDismiss()
                    }
                    .padding(vertical = sp.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Translate the rest of this chapter",
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = palette.bg,
                )
            }
        }
        Spacer(Modifier.height(sp.xs))
    }

    keyDialogTarget?.let { provider ->
        ApiKeyDialog(
            provider = provider,
            existingPresent = state.configs[provider]?.configured ?: false,
            secondaryLabel = provider.secondaryLabel,
            secondaryValue = state.configs[provider]?.secondaryValue.orEmpty(),
            secondaryPlaceholder = provider.secondaryPlaceholder.orEmpty(),
            secondaryIsUrl = provider.secondaryIsUrl,
            helpUrl = provider.helpUrl,
            onSave = { key, secondary ->
                onConfigure(provider, key, secondary)
                onSelect(provider)
                keyDialogTarget = null
            },
            onClear = {
                onClear(provider)
                keyDialogTarget = null
            },
            onDismiss = { keyDialogTarget = null },
        )
    }

    resetConfirmTarget?.let { provider ->
        ResetUsageDialog(
            provider = provider,
            onConfirm = {
                onResetUsage(provider)
                resetConfirmTarget = null
            },
            onDismiss = { resetConfirmTarget = null },
        )
    }

}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val palette = LocalReaderPalette.current
    Text(
        modifier = modifier,
        text = text.uppercase(),
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
        color = palette.ink3,
    )
}

@Composable
private fun ProviderRow(
    provider: TranslationProvider,
    selected: Boolean,
    configured: Boolean,
    usage: TranslationUsage?,
    onSelect: () -> Unit,
    onConfigure: (() -> Unit)?,
    onResetUsage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalReaderPalette.current
    val radii = LocalRadii.current
    val sp = LocalSpacing.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radii.md))
            .background(animatedSelection(if (selected) palette.ink else palette.bg2, "providerRowBg"))
            .border(1.dp, animatedSelection(if (selected) palette.ink else palette.edge, "providerRowBorder"), RoundedCornerShape(radii.md))
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = provider.displayName,
                fontFamily = Newsreader,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = if (selected) palette.bg else palette.ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = provider.description,
                fontFamily = Newsreader,
                fontSize = 11.sp,
                color = if (selected) palette.bg.copy(alpha = 0.75f) else palette.ink3,
            )
            if (provider.requiresApiKey) {
                Spacer(Modifier.height(sp.xxs))
                val statusText = if (configured) "Key configured" else "Tap to add API key"
                Text(
                    text = statusText.uppercase(),
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    letterSpacing = 0.4.sp,
                    color = if (selected) palette.bg.copy(alpha = 0.85f) else palette.ink2,
                )
            }
            if (provider.tracksUsage && usage != null && usage.charactersThisMonth > 0L) {
                Spacer(Modifier.height(sp.xxs))
                UsageBar(usage = usage, onDark = selected, onReset = onResetUsage)
            }
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                null,
                tint = palette.bg,
                modifier = Modifier.size(18.dp),
            )
        }
        if (onConfigure != null) {
            Spacer(Modifier.size(sp.xs))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) palette.bg.copy(alpha = 0.18f) else palette.bg)
                    .border(1.dp, if (selected) palette.bg.copy(alpha = 0.35f) else palette.edge, RoundedCornerShape(8.dp))
                    .clickable(onClick = onConfigure),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    null,
                    tint = if (selected) palette.bg else palette.ink2,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
internal fun ApiKeyDialog(
    provider: TranslationProvider,
    existingPresent: Boolean,
    secondaryLabel: String?,
    secondaryValue: String,
    secondaryPlaceholder: String,
    secondaryIsUrl: Boolean,
    helpUrl: String?,
    onSave: (key: String?, secondary: String?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val radii = LocalRadii.current
    val sp = LocalSpacing.current
    var input by remember { mutableStateOf("") }
    var secondaryInput by remember { mutableStateOf(secondaryValue) }
    var urlError by remember { mutableStateOf<String?>(null) }

    EditorialDialog(eyebrow = provider.displayName, title = "API key", onDismiss = onDismiss) {
        Text(
            text = "Paste your ${provider.displayName} API key. Keys are stored encrypted on this device.",
            fontFamily = Newsreader,
            fontSize = 13.sp,
            color = palette.ink2,
        )
        Spacer(Modifier.height(sp.sm))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(radii.md))
                .background(palette.bg2)
                .border(1.dp, palette.edge, RoundedCornerShape(radii.md))
                .padding(horizontal = sp.sm, vertical = sp.sm),
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                textStyle = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontSize = 13.sp,
                    color = palette.ink,
                ),
                cursorBrush = SolidColor(palette.ink),
                decorationBox = { inner ->
                    if (input.isEmpty()) {
                        Text(
                            text = if (existingPresent) "•••••••••• (existing key hidden)" else "Paste key…",
                            fontFamily = JetBrainsMono,
                            fontSize = 13.sp,
                            color = palette.ink3,
                        )
                    }
                    inner()
                },
            )
        }
        if (secondaryLabel != null) {
            Spacer(Modifier.height(sp.sm))
            SectionLabel(secondaryLabel)
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radii.md))
                    .background(palette.bg2)
                    .border(1.dp, palette.edge, RoundedCornerShape(radii.md))
                    .padding(horizontal = sp.sm, vertical = sp.sm),
            ) {
                BasicTextField(
                    value = secondaryInput,
                    onValueChange = { secondaryInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (secondaryIsUrl) KeyboardType.Uri else KeyboardType.Text,
                    ),
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontSize = 13.sp,
                        color = palette.ink,
                    ),
                    cursorBrush = SolidColor(palette.ink),
                    decorationBox = { inner ->
                        if (secondaryInput.isEmpty()) {
                            Text(secondaryPlaceholder, fontFamily = JetBrainsMono, fontSize = 13.sp, color = palette.ink3)
                        }
                        inner()
                    },
                )
            }
            if (urlError != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = urlError!!,
                    fontFamily = Newsreader,
                    fontSize = 11.sp,
                    color = DangerTone,
                )
            }
        }
        Spacer(Modifier.height(sp.md))
        val saveEnabled = input.isNotBlank() || existingPresent ||
            (secondaryLabel != null && secondaryInput.isNotBlank())
        Row(horizontalArrangement = Arrangement.spacedBy(sp.xs), modifier = Modifier.fillMaxWidth()) {
            DialogButton(
                label = "Save",
                primary = true,
                enabled = saveEnabled,
                onClick = {
                    val secondary = if (secondaryLabel != null) secondaryInput.trim() else null
                    val invalid = if (secondaryIsUrl && !secondary.isNullOrBlank())
                        (normalizeLibreUrl(secondary) as? UrlResult.Invalid)?.reason else null
                    if (invalid != null) {
                        urlError = invalid
                    } else {
                        urlError = null
                        onSave(input.trim().ifBlank { null }, secondary)
                    }
                },
            )
            if (existingPresent) {
                DialogButton(label = "Clear", primary = false, enabled = true, onClick = onClear)
            }
            DialogButton(label = "Cancel", primary = false, enabled = true, onClick = onDismiss)
        }
        Spacer(Modifier.height(sp.xxs))
        if (helpUrl != null) {
            Text(
                text = "Get a key: $helpUrl",
                fontFamily = Newsreader,
                fontSize = 11.sp,
                color = palette.ink3,
            )
        }
    }
}

@Composable
private fun UsageBar(usage: TranslationUsage, onDark: Boolean, onReset: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalReaderPalette.current
    val limit = usage.monthlyLimit
    val used = usage.charactersThisMonth
    val label = if (limit != null) {
        "${formatChars(used)} / ${formatChars(limit)} this month"
    } else {
        "${formatChars(used)} this month"
    }
    val tone: Color = when {
        limit == null -> if (onDark) palette.bg.copy(alpha = 0.85f) else palette.ink2
        used >= (limit * 0.9).toLong() -> DangerTone
        used >= (limit * 0.7).toLong() -> WarnTone
        else -> if (onDark) palette.bg.copy(alpha = 0.85f) else palette.ink2
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label.uppercase(),
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            letterSpacing = 0.4.sp,
            color = tone,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.size(6.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onReset),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Refresh,
                contentDescription = "Reset counter",
                tint = tone,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

private fun formatChars(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000 -> "${value / 1_000}K"
    else -> value.toString()
}

@Composable
private fun ResetUsageDialog(
    provider: TranslationProvider,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val sp = LocalSpacing.current
    EditorialDialog(eyebrow = provider.displayName, title = "Reset counter", onDismiss = onDismiss) {
        Text(
            text = "Reset the local character counter for ${provider.displayName}? The provider's actual quota on its servers is not affected — this only resets the in-app estimate.",
            fontFamily = Newsreader,
            fontSize = 13.sp,
            color = palette.ink2,
        )
        Spacer(Modifier.height(sp.md))
        Row(horizontalArrangement = Arrangement.spacedBy(sp.xs), modifier = Modifier.fillMaxWidth()) {
            DialogButton(label = "Reset", primary = true, enabled = true, onClick = onConfirm)
            DialogButton(label = "Cancel", primary = false, enabled = true, onClick = onDismiss)
        }
    }
}

@Composable
private fun DialogButton(label: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalReaderPalette.current
    val radii = LocalRadii.current
    val bg = when {
        !enabled -> palette.bg2
        primary -> palette.ink
        else -> palette.bg2
    }
    val border = if (primary) palette.ink else palette.edge
    val text = when {
        !enabled -> palette.ink3
        primary -> palette.bg
        else -> palette.ink
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radii.md))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(radii.md))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = Newsreader,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = text,
        )
    }
}
