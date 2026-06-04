package com.example.splitreader.presentation.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.splitreader.domain.model.Language
import com.example.splitreader.presentation.reader.TranslatorPickerDialog
import com.example.splitreader.presentation.theme.AmoledPalette
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.LocalRadii
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.LocalSpacing
import com.example.splitreader.presentation.theme.Newsreader
import com.example.splitreader.presentation.theme.NightPalette
import com.example.splitreader.presentation.theme.AnimatedDialog
import com.example.splitreader.presentation.theme.animatedSelection
import com.example.splitreader.presentation.theme.PaperPalette
import com.example.splitreader.presentation.theme.SepiaPalette
import com.example.splitreader.presentation.ui.SectionEyebrow
import com.example.splitreader.presentation.ui.SliderRow
import com.example.splitreader.presentation.ui.ToggleRow
import com.example.splitreader.presentation.ui.TypographyControls

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    SettingsScreen(
        state = state,
        onSetReaderTheme = viewModel::setReaderTheme,
        onSetTargetLanguage = viewModel::setTargetLanguage,
        onSetSplitRatio = viewModel::setSplitRatio,
        onSetShowTranslation = viewModel::setShowTranslation,
        onSetHorizontalMargin = viewModel::setHorizontalMargin,
        onSetReadingFont = viewModel::setReadingFont,
        onSetTextSize = viewModel::setTextSize,
        onSetLineHeight = viewModel::setLineHeight,
        onSetLetterSpacing = viewModel::setLetterSpacing,
        onSetTextIndent = viewModel::setTextIndent,
        onSetParagraphSpacing = viewModel::setParagraphSpacing,
        onSetJustifyText = viewModel::setJustifyText,
        onSelectProvider = viewModel::selectProvider,
        onConfigureProvider = viewModel::configureProvider,
        onClearProvider = viewModel::clearProvider,
        onRefreshTranslationUsage = viewModel::refreshTranslationUsage,
        onResetTranslationUsage = viewModel::resetTranslationUsage,
        onClearCache = viewModel::clearTranslationCache,
        onSetTtsRate = viewModel::setTtsRate,
        onSetTtsPitch = viewModel::setTtsPitch,
        onTestVoice = viewModel::testVoice,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSetReaderTheme: (com.example.splitreader.presentation.theme.ReaderThemeKey) -> Unit,
    onSetTargetLanguage: (Language) -> Unit,
    onSetSplitRatio: (Float) -> Unit,
    onSetShowTranslation: (Boolean) -> Unit,
    onSetHorizontalMargin: (Float) -> Unit,
    onSetReadingFont: (com.example.splitreader.presentation.theme.ReadingFont) -> Unit,
    onSetTextSize: (Float) -> Unit,
    onSetLineHeight: (Float) -> Unit,
    onSetLetterSpacing: (Float) -> Unit,
    onSetTextIndent: (Float) -> Unit,
    onSetParagraphSpacing: (Float) -> Unit,
    onSetJustifyText: (Boolean) -> Unit,
    onSelectProvider: (com.example.splitreader.domain.model.TranslationProvider) -> Unit,
    onConfigureProvider: (com.example.splitreader.domain.model.TranslationProvider, String?, String?) -> Unit,
    onClearProvider: (com.example.splitreader.domain.model.TranslationProvider) -> Unit,
    onRefreshTranslationUsage: () -> Unit,
    onResetTranslationUsage: (com.example.splitreader.domain.model.TranslationProvider) -> Unit,
    onClearCache: () -> Unit,
    onSetTtsRate: (Float) -> Unit,
    onSetTtsPitch: (Float) -> Unit,
    onTestVoice: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val sp = LocalSpacing.current

    var showTranslatorPicker by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = sp.xl, vertical = sp.lg)
            .widthIn(max = 720.dp),
    ) {
        // Header
        Text(
            text = "Preferences",
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            color = palette.ink3,
        )
        Text(
            text = "Settings",
            fontFamily = Newsreader,
            fontWeight = FontWeight.SemiBold,
            fontStyle = FontStyle.Italic,
            fontSize = 28.sp,
            color = palette.ink,
        )
        Spacer(Modifier.height(sp.lg))

        // ── Reading appearance ──────────────────────────────────────────────
        SettingsSection(title = "Reading") {
            SectionEyebrow("Theme")
            Spacer(Modifier.height(sp.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(PaperPalette, SepiaPalette, NightPalette, AmoledPalette).forEach { p ->
                    val selected = state.readerTheme == p.key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(LocalRadii.current.md))
                            .background(p.bg)
                            .border(if (selected) 2.dp else 1.dp, animatedSelection(if (selected) p.ink else p.edge, "themeSwatchBorder"), RoundedCornerShape(LocalRadii.current.md))
                            .clickable { onSetReaderTheme(p.key) }
                            .padding(vertical = sp.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Aa", fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = p.ink)
                            Text(p.displayName.uppercase(), fontFamily = JetBrainsMono, fontSize = 11.sp, color = p.ink2)
                        }
                    }
                }
            }

            Divider()

            TypographyControls(
                readingFont = state.readingFont,
                onSetReadingFont = onSetReadingFont,
                textSize = state.textSize,
                onSetTextSize = onSetTextSize,
                lineHeightMultiplier = state.lineHeightMultiplier,
                onSetLineHeight = onSetLineHeight,
                letterSpacing = state.letterSpacing,
                onSetLetterSpacing = onSetLetterSpacing,
                textIndent = state.textIndent,
                onSetTextIndent = onSetTextIndent,
                paragraphSpacing = state.paragraphSpacing,
                onSetParagraphSpacing = onSetParagraphSpacing,
                justify = state.justifyText,
                onSetJustify = onSetJustifyText,
            )

            Divider()

            SliderRow(
                label = "Split ratio",
                value = state.splitRatio,
                valueLabel = "${(state.splitRatio * 100).toInt()}%",
                valueRange = 0.3f..0.7f,
                onValueChange = onSetSplitRatio,
            )
            Spacer(Modifier.height(sp.sm))
            SliderRow(
                label = "Page margins",
                value = state.horizontalMargin,
                valueLabel = "${state.horizontalMargin.toInt()}dp",
                valueRange = 4f..32f,
                onValueChange = onSetHorizontalMargin,
            )
            Spacer(Modifier.height(sp.sm))
            ToggleRow(
                label = "Show translation",
                sub = "Show the translated pane by default",
                checked = state.showTranslation,
                onToggle = { onSetShowTranslation(!state.showTranslation) },
            )

            Divider()

            SectionEyebrow("Default translation language")
            Spacer(Modifier.height(sp.xs))
            RowValue(
                label = state.targetLanguage.displayName,
                action = "Change",
                onClick = { showLanguagePicker = true },
            )
        }

        Spacer(Modifier.height(sp.lg))

        // ── Translation engine ──────────────────────────────────────────────
        SettingsSection(title = "Translation") {
            RowValue(
                label = state.translatorProvider.displayName,
                action = "Change",
                onClick = { showTranslatorPicker = true },
            )
            Spacer(Modifier.height(sp.xs))
            Text(
                text = "Manage providers, API keys, server URL and monthly usage.",
                fontFamily = Newsreader,
                fontStyle = FontStyle.Italic,
                fontSize = 12.sp,
                color = palette.ink3,
            )
        }

        Spacer(Modifier.height(sp.lg))

        // ── Storage ─────────────────────────────────────────────────────────
        SettingsSection(title = "Storage") {
            RowValue(
                label = "Cached translations: ${state.cachedTranslationCount}",
                action = "Clear",
                onClick = { showClearCacheConfirm = true },
            )
            Spacer(Modifier.height(sp.xs))
            Text(
                text = "Clearing the cache frees space; translations will be re-fetched as you read.",
                fontFamily = Newsreader,
                fontStyle = FontStyle.Italic,
                fontSize = 12.sp,
                color = palette.ink3,
            )
        }

        Spacer(Modifier.height(sp.lg))

        // ── Read aloud ──────────────────────────────────────────────────────
        SettingsSection(title = "Read aloud") {
            SliderRow(
                label = "Speech rate",
                value = state.ttsRate,
                valueLabel = "×${"%.2f".format(state.ttsRate)}",
                valueRange = 0.5f..2.0f,
                onValueChange = onSetTtsRate,
            )
            Spacer(Modifier.height(sp.sm))
            SliderRow(
                label = "Pitch",
                value = state.ttsPitch,
                valueLabel = "×${"%.2f".format(state.ttsPitch)}",
                valueRange = 0.5f..2.0f,
                onValueChange = onSetTtsPitch,
            )
            Spacer(Modifier.height(sp.sm))
            SelectChip(
                label = "Test voice",
                selected = false,
                onClick = onTestVoice,
            )
        }

        Spacer(Modifier.height(sp.lg))

        // ── About ───────────────────────────────────────────────────────────
        AboutSection()

        Spacer(Modifier.height(sp.xxl))
    }

    if (showTranslatorPicker) {
        LaunchedEffect(Unit) { onRefreshTranslationUsage() }
        TranslatorPickerDialog(
            state = state.translatorConfig,
            onSelect = { provider ->
                onSelectProvider(provider)
                showTranslatorPicker = false
            },
            onConfigure = onConfigureProvider,
            onClear = onClearProvider,
            onResetUsage = onResetTranslationUsage,
            onDismiss = { showTranslatorPicker = false },
        )
    }

    if (showLanguagePicker) {
        LanguageGridDialog(
            current = state.targetLanguage,
            onSelect = {
                onSetTargetLanguage(it)
                showLanguagePicker = false
            },
            onDismiss = { showLanguagePicker = false },
        )
    }

    if (showClearCacheConfirm) {
        ConfirmDialog(
            title = "Clear cache",
            body = "Delete all ${state.cachedTranslationCount} cached translations? They will be re-fetched on demand.",
            confirmLabel = "Clear",
            onConfirm = {
                onClearCache()
                showClearCacheConfirm = false
            },
            onDismiss = { showClearCacheConfirm = false },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    val palette = LocalReaderPalette.current
    val radii = LocalRadii.current
    val sp = LocalSpacing.current
    Column {
        Text(
            text = title,
            fontFamily = Newsreader,
            fontWeight = FontWeight.Medium,
            fontStyle = FontStyle.Italic,
            fontSize = 20.sp,
            color = palette.ink,
        )
        Spacer(Modifier.height(sp.sm))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(radii.md))
                .background(palette.bg2)
                .border(1.dp, palette.edge, RoundedCornerShape(radii.md))
                .padding(sp.md),
        ) {
            content()
        }
    }
}

@Composable
private fun Divider() {
    val palette = LocalReaderPalette.current
    val sp = LocalSpacing.current
    Spacer(Modifier.height(sp.md))
    Box(Modifier.fillMaxWidth().height(1.dp).background(palette.edge))
    Spacer(Modifier.height(sp.md))
}

@Composable
private fun RowValue(label: String, action: String, onClick: () -> Unit) {
    val palette = LocalReaderPalette.current
    val radii = LocalRadii.current
    val sp = LocalSpacing.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontFamily = Newsreader,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            color = palette.ink,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .clip(RoundedCornerShape(radii.md))
                .background(palette.ink)
                .clickable(onClick = onClick)
                .padding(horizontal = sp.md, vertical = sp.xs),
        ) {
            Text(action, fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = palette.bg)
        }
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalReaderPalette.current
    val radii = LocalRadii.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radii.md))
            .background(animatedSelection(if (selected) palette.ink else palette.bg2, "selBg"))
            .border(1.dp, animatedSelection(if (selected) palette.ink else palette.edge, "selBorder"), RoundedCornerShape(radii.md))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = Newsreader,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = if (selected) palette.bg else palette.ink,
        )
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }
    SettingsSection(title = "About") {
        AboutRow("Version", version)
        Spacer(Modifier.height(6.dp))
        AboutRow("Formats", "EPUB · FB2 · MOBI")
        Spacer(Modifier.height(6.dp))
        AboutRow("Translation", "Google ML Kit (on-device)")
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    val palette = LocalReaderPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label.uppercase(), fontFamily = JetBrainsMono, fontSize = 11.sp, letterSpacing = 0.5.sp, color = palette.ink3)
        Text(value, fontFamily = Newsreader, fontSize = 13.sp, color = palette.ink)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageGridDialog(
    current: Language,
    onSelect: (Language) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val radii = LocalRadii.current
    val sp = LocalSpacing.current
    AnimatedDialog(onDismiss = onDismiss) { _ ->
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(palette.bg)
                .border(1.dp, palette.edge, RoundedCornerShape(18.dp))
                .padding(24.dp),
        ) {
            Text("Translate into", fontFamily = JetBrainsMono, fontSize = 11.sp, letterSpacing = 1.sp, color = palette.ink3)
            Spacer(Modifier.height(sp.xxs))
            Text(
                "Default language",
                fontFamily = Newsreader,
                fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Italic,
                fontSize = 20.sp,
                color = palette.ink,
            )
            Spacer(Modifier.height(sp.md))
            Language.entries.chunked(3).forEach { rowLangs ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(sp.xs)) {
                    rowLangs.forEach { lang ->
                        val selected = lang == current
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(radii.md))
                                .background(animatedSelection(if (selected) palette.ink else palette.bg2, "selBg"))
                                .border(1.dp, animatedSelection(if (selected) palette.ink else palette.edge, "selBorder"), RoundedCornerShape(radii.md))
                                .clickable { onSelect(lang) }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                lang.badge,
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = animatedSelection(if (selected) palette.bg else palette.ink, "selText"),
                            )
                            Text(
                                lang.displayName,
                                fontFamily = Newsreader,
                                fontSize = 13.sp,
                                color = animatedSelection(if (selected) palette.bg.copy(alpha = 0.85f) else palette.ink2, "langCellText2"),
                            )
                        }
                    }
                    repeat(3 - rowLangs.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(sp.xs))
            }
        }
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
                    Text("Cancel", fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = palette.ink)
                }
            }
        }
    }
}
