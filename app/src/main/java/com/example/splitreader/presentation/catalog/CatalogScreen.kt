package com.example.splitreader.presentation.catalog

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.splitreader.R
import com.example.splitreader.presentation.theme.FadeInOnAppear
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
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.LocalRadii
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.LocalSpacing
import com.example.splitreader.presentation.theme.Newsreader
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

    CatalogScreen(
        uiState = uiState,
        driveState = driveState,
        onQueryChange = viewModel::onQueryChange,
        onDownload = viewModel::downloadAndOpen,
        onRetry = viewModel::retry,
        onPickFromDrive = { driveViewModel.onPickFromDriveClicked(activity) },
        onSignInWithGoogle = onNavigateToAuth,
    )
}

@Composable
private fun CatalogScreen(
    uiState: CatalogUiState,
    driveState: DriveUiState,
    onQueryChange: (String) -> Unit,
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

        SearchField(query = uiState.query, onQueryChange = onQueryChange)
        Spacer(Modifier.height(sp.md))

        DriveSection(
            state = driveState,
            onPick = onPickFromDrive,
            onSignInWithGoogle = onSignInWithGoogle,
        )

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
                items(uiState.books, key = { it.id }) { book ->
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
        Text(
            text = stringResource(R.string.drive_section_label),
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            color = palette.ink3,
            modifier = Modifier.padding(bottom = sp.xs),
        )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LocalRadii.current.md))
            .background(palette.bg2)
            .clickable(enabled = downloadEnabled, onClick = onDownload)
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
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(LocalRadii.current.sm))
            .background(if (enabled || isDownloading) palette.accent else palette.bg3)
            .clickable(enabled = enabled && !isDownloading, onClick = onClick)
            .padding(horizontal = sp.md, vertical = sp.xs),
        contentAlignment = Alignment.Center,
    ) {
        if (isDownloading) {
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

/** Cover image fetched directly from the Gutenberg cover URL; falls back to a book icon tile. */
@Composable
private fun CoverThumb(url: String?) {
    val palette = LocalReaderPalette.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, url) {
        value = if (url == null) null else withContext(Dispatchers.IO) {
            runCatching {
                URL(url).openStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 64.dp)
            .clip(RoundedCornerShape(LocalRadii.current.sm))
            .background(palette.bg3),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.MenuBook,
                contentDescription = null,
                tint = palette.ink3,
                modifier = Modifier.size(20.dp),
            )
        }
    }
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
