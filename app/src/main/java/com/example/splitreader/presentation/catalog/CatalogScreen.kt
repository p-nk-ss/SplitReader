package com.example.splitreader.presentation.catalog

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.splitreader.R
import com.example.splitreader.presentation.theme.FadeInOnAppear
import com.example.splitreader.presentation.theme.MotionTokens
import com.example.splitreader.presentation.theme.animatedSelection
import com.example.splitreader.presentation.theme.pressScale
import com.example.splitreader.presentation.theme.shimmer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitreader.domain.model.CatalogBook
import com.example.splitreader.domain.model.CatalogSource
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.LocalRadii
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.LocalSpacing
import com.example.splitreader.presentation.theme.Newsreader
import com.example.splitreader.presentation.premium.PremiumViewModel
import com.example.splitreader.presentation.premium.PurchaseEventEffect
import com.example.splitreader.presentation.ui.LibraryLimitDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

// ── Entry point ─────────────────────────────────────────────────────────

@Composable
internal fun CatalogRoute(
    onNavigateToReader: (String) -> Unit,
    onNavigateToAuth: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel(),
    driveViewModel: DriveViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val driveState by driveViewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity
    val premiumViewModel: PremiumViewModel = hiltViewModel()
    val price by premiumViewModel.priceText.collectAsStateWithLifecycle()
    PurchaseEventEffect(premiumViewModel)

    // The Authorization PendingIntent (consent + Drive Picker) must be launched from the UI layer,
    // not the ViewModel; the picked file id rides back on the same result.
    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> driveViewModel.onAuthorizationResult(activity, result.data) }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { path -> onNavigateToReader(path) }
    }
    LaunchedEffect(Unit) {
        driveViewModel.navigationEvent.collect { path -> onNavigateToReader(path) }
    }
    LaunchedEffect(Unit) {
        driveViewModel.events.collect { event ->
            when (event) {
                is DriveEvent.LaunchAuthorization ->
                    authLauncher.launch(
                        IntentSenderRequest.Builder(event.pendingIntent.intentSender).build()
                    )
            }
        }
    }

    if (uiState.showLimitDialog || driveState.showLimitDialog) {
        LibraryLimitDialog(
            onDismiss = {
                viewModel.dismissLimitDialog()
                driveViewModel.dismissLimitDialog()
            },
            onUpgrade = {
                viewModel.dismissLimitDialog()
                driveViewModel.dismissLimitDialog()
                premiumViewModel.upgrade(activity)
            },
            priceText = price,
        )
    }

    CatalogScreen(
        uiState = uiState,
        driveState = driveState,
        onQueryChange = viewModel::onQueryChange,
        onSourceSelected = viewModel::onSourceSelected,
        onDownload = viewModel::downloadAndOpen,
        onRetry = viewModel::retry,
        onPickFromDrive = { driveViewModel.onPickFromDriveClicked(activity) },
        onSignInWithGoogle = onNavigateToAuth,
    )
}

@Composable
internal fun CatalogScreen(
    uiState: CatalogUiState,
    driveState: DriveUiState,
    onQueryChange: (String) -> Unit,
    onSourceSelected: (CatalogSource) -> Unit,
    onDownload: (CatalogBook) -> Unit,
    onRetry: () -> Unit,
    onPickFromDrive: () -> Unit,
    onSignInWithGoogle: () -> Unit,
) {
    val sp = LocalSpacing.current
    val palette = LocalReaderPalette.current

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            .padding(horizontal = sp.xxl),
    ) {
        Spacer(Modifier.height(sp.lg))
        Text(
            text = "Catalog",
            fontFamily = Newsreader,
            fontWeight = FontWeight.Medium,
            fontStyle = FontStyle.Italic,
            fontSize = 30.sp,
            color = palette.ink,
        )
        Spacer(Modifier.height(sp.xs))
        Text(
            text = "FREE PUBLIC-DOMAIN BOOKS",
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            color = palette.ink3,
        )
        Spacer(Modifier.height(sp.md))

        // Drive is a UI-only tab (its own pick/auth flow), not a CatalogSource — track it separately.
        var driveSelected by rememberSaveable { mutableStateOf(false) }
        SourceTabs(
            selectedSource = uiState.selectedSource,
            driveSelected = driveSelected,
            onSelectSource = { driveSelected = false; onSourceSelected(it) },
            onSelectDrive = { driveSelected = true },
        )
        Spacer(Modifier.height(sp.md))

        if (driveSelected) {
            DriveSection(
                state = driveState,
                onPick = onPickFromDrive,
                onSignInWithGoogle = onSignInWithGoogle,
            )
        } else {
            SearchField(query = uiState.query, onQueryChange = onQueryChange)
            Spacer(Modifier.height(sp.md))

            when {
                uiState.errorMessage != null && uiState.books.isEmpty() ->
                    CenteredMessage(
                        title = uiState.errorMessage,
                        actionLabel = "Retry",
                        onAction = onRetry,
                    )

                uiState.isLoading && uiState.books.isEmpty() ->
                    CenteredLoader()

                uiState.books.isEmpty() && uiState.hasSearched ->
                    CenteredMessage(title = "No books found", actionLabel = null, onAction = {})

                uiState.books.isEmpty() ->
                    CenteredMessage(
                        title = "Search by title or author to begin",
                        actionLabel = null,
                        onAction = {},
                    )

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = sp.xxl),
                    verticalArrangement = Arrangement.spacedBy(sp.sm),
                ) {
                    item(key = "section-label") {
                        Text(
                            text = if (uiState.query.isBlank()) "POPULAR" else "RESULTS",
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp,
                            color = palette.ink3,
                            modifier = Modifier.padding(bottom = sp.xs),
                        )
                    }
                    items(uiState.books, key = { "${it.source.name}/${it.id}" }) { book ->
                        Box(Modifier.animateItem()) {
                            CatalogRow(
                                book = book,
                                isDownloading = uiState.downloadingId == book.id,
                                downloadEnabled = uiState.downloadingId == null,
                                onDownload = { onDownload(book) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Editorial source switcher: one pill per [CatalogSource] plus a Google Drive pill. The active one
 * fills with the accent. Scrolls horizontally so the labels never clip on narrow screens.
 */
@Composable
private fun SourceTabs(
    selectedSource: CatalogSource,
    driveSelected: Boolean,
    onSelectSource: (CatalogSource) -> Unit,
    onSelectDrive: () -> Unit,
) {
    val sp = LocalSpacing.current
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(sp.sm),
    ) {
        CatalogSource.entries.forEach { source ->
            TabPill(
                label = source.displayName,
                selected = !driveSelected && source == selectedSource,
                onClick = { onSelectSource(source) },
            )
        }
        TabPill(
            label = "Google Drive",
            selected = driveSelected,
            onClick = onSelectDrive,
        )
    }
}

/** A single accent-filled-when-selected pill used by [SourceTabs]. */
@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val sp = LocalSpacing.current
    val palette = LocalReaderPalette.current
    val interaction = remember { MutableInteractionSource() }
    val bg = animatedSelection(if (selected) palette.accent else palette.bg2, "tab-$label")
    Box(
        modifier = Modifier
            .pressScale(interaction)
            .clip(RoundedCornerShape(LocalRadii.current.sm))
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = sp.md, vertical = sp.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            letterSpacing = 0.5.sp,
            color = if (selected) palette.bg else palette.ink2,
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val sp = LocalSpacing.current
    val palette = LocalReaderPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LocalRadii.current.md))
            .background(palette.bg2)
            .padding(horizontal = sp.md, vertical = sp.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = palette.ink3,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(sp.sm))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Search by title or author…",
                    fontFamily = Newsreader,
                    fontSize = 16.sp,
                    color = palette.ink3,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = Newsreader,
                    fontSize = 16.sp,
                    color = palette.ink,
                ),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * "FROM YOUR DRIVE" entry point. Signed in with Google → a tappable row that launches the Drive
 * Picker; otherwise a hint to sign in with Google (Drive authorization is tied to a Google account).
 */
@Composable
private fun DriveSection(
    state: DriveUiState,
    onPick: () -> Unit,
    onSignInWithGoogle: () -> Unit,
) {
    val sp = LocalSpacing.current
    val palette = LocalReaderPalette.current
    Column(Modifier.fillMaxWidth()) {
        if (state.isSignedInWithGoogle) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(LocalRadii.current.md))
                    .background(palette.bg2)
                    .clickable(enabled = !state.isBusy, onClick = onPick)
                    .padding(sp.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 64.dp)
                        .clip(RoundedCornerShape(LocalRadii.current.sm))
                        .background(palette.bg3),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudDownload,
                        contentDescription = null,
                        tint = palette.ink3,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(sp.md))
                Text(
                    text = stringResource(R.string.drive_pick_action),
                    fontFamily = Newsreader,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                    color = palette.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(sp.sm))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(LocalRadii.current.sm))
                        .background(palette.accent)
                        .clickable(enabled = !state.isBusy, onClick = onPick)
                        .padding(horizontal = sp.md, vertical = sp.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isBusy) {
                        CircularProgressIndicator(
                            color = palette.bg,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Text(
                            text = "Open",
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp,
                            color = palette.bg,
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(LocalRadii.current.md))
                    .background(palette.bg2)
                    .clickable(onClick = onSignInWithGoogle)
                    .padding(sp.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudDownload,
                    contentDescription = null,
                    tint = palette.ink3,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(sp.sm))
                Text(
                    text = stringResource(R.string.drive_signed_out_hint),
                    fontFamily = Newsreader,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    color = palette.ink2,
                )
            }
        }
        if (state.errorMessage != null) {
            Spacer(Modifier.height(sp.xs))
            Text(
                text = state.errorMessage,
                fontFamily = Newsreader,
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
                color = palette.accent,
            )
        }
        Spacer(Modifier.height(sp.md))
    }
}

@Composable
private fun CatalogRow(
    book: CatalogBook,
    isDownloading: Boolean,
    downloadEnabled: Boolean,
    onDownload: () -> Unit,
) {
    val sp = LocalSpacing.current
    val palette = LocalReaderPalette.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(RoundedCornerShape(LocalRadii.current.md))
            .background(palette.bg2)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = downloadEnabled,
                onClick = onDownload,
            )
            .padding(sp.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverThumb(url = book.coverUrl)
        Spacer(Modifier.width(sp.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = book.title,
                fontFamily = Newsreader,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                color = palette.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = book.author,
                fontFamily = Newsreader,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                color = palette.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.languages.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = book.languages.joinToString(" · ") { it.uppercase() },
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp,
                    color = palette.ink3,
                )
            }
        }
        Spacer(Modifier.width(sp.sm))
        DownloadButton(isDownloading = isDownloading, enabled = downloadEnabled, onClick = onDownload)
    }
}

@Composable
private fun DownloadButton(isDownloading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val sp = LocalSpacing.current
    val palette = LocalReaderPalette.current
    val interaction = remember { MutableInteractionSource() }
    val bg = animatedSelection(
        if (enabled || isDownloading) palette.accent else palette.bg3,
        "downloadBtnBg",
    )
    Box(
        modifier = Modifier
            .pressScale(interaction)
            .clip(RoundedCornerShape(LocalRadii.current.sm))
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !isDownloading,
                onClick = onClick,
            )
            .padding(horizontal = sp.md, vertical = sp.xs),
        contentAlignment = Alignment.Center,
    ) {
        // Crossfade so the Read ↔ spinner swap is continuous, not a hard cut.
        Crossfade(
            targetState = isDownloading,
            animationSpec = tween(MotionTokens.Fast, easing = MotionTokens.EaseStandard),
            label = "downloadBtn",
        ) { downloading ->
            if (downloading) {
                CircularProgressIndicator(
                    color = palette.bg,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Text(
                    text = "Read",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp,
                    color = palette.bg,
                )
            }
        }
    }
}

/** Cover image fetched directly from the source's cover URL; falls back to a book icon tile. */
@Composable
private fun CoverThumb(url: String?) {
    val palette = LocalReaderPalette.current
    val shape = RoundedCornerShape(LocalRadii.current.sm)
    // Three-state load so we can shimmer while fetching (vs. an instant gray→image pop).
    val state by produceState<ThumbState>(initialValue = ThumbState.Loading, url) {
        value = if (url == null) {
            ThumbState.Failed
        } else withContext(Dispatchers.IO) {
            runCatching {
                URL(url).openStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            }.getOrNull()?.let { ThumbState.Loaded(it) } ?: ThumbState.Failed
        }
    }
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 64.dp)
            .clip(shape)
            .background(palette.bg3),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = state,
            animationSpec = tween(MotionTokens.Medium, easing = MotionTokens.EaseStandard),
            label = "coverThumb",
        ) { s ->
            when (s) {
                ThumbState.Loading -> Box(Modifier.fillMaxSize().shimmer(shape))
                is ThumbState.Loaded -> Image(
                    bitmap = s.bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                ThumbState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.MenuBook,
                        contentDescription = null,
                        tint = palette.ink3,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private sealed interface ThumbState {
    data object Loading : ThumbState
    data class Loaded(val bmp: ImageBitmap) : ThumbState
    data object Failed : ThumbState
}

@Composable
private fun CenteredLoader() {
    val palette = LocalReaderPalette.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp)
    }
}

@Composable
private fun CenteredMessage(title: String, actionLabel: String?, onAction: () -> Unit) {
    val sp = LocalSpacing.current
    val palette = LocalReaderPalette.current
    FadeInOnAppear(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                fontFamily = Newsreader,
                fontStyle = FontStyle.Italic,
                fontSize = 18.sp,
                color = palette.ink2,
            )
            if (actionLabel != null) {
                Spacer(Modifier.height(sp.md))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(LocalRadii.current.sm))
                        .background(palette.accent)
                        .clickable(onClick = onAction)
                        .padding(horizontal = sp.lg, vertical = sp.xs),
                ) {
                    Text(
                        text = actionLabel,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = palette.bg,
                    )
                }
            }
        }
    }
    }
}
