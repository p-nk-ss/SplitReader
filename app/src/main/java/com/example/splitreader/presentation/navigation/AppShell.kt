package com.example.splitreader.presentation.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitreader.presentation.theme.JetBrainsMono
import com.example.splitreader.presentation.theme.LocalRadii
import com.example.splitreader.presentation.theme.LocalReaderPalette
import com.example.splitreader.presentation.theme.LocalSpacing
import com.example.splitreader.presentation.theme.Newsreader
import com.example.splitreader.presentation.theme.isCompactWidth
import com.example.splitreader.presentation.theme.isRailTooTall
import com.example.splitreader.R

/**
 * System bars plus the display cutout — everything that can physically occlude the shell.
 *
 * Deliberately NOT `WindowInsets.safeDrawing`, which also folds in the IME: the keyboard is
 * handled separately and on purpose (the content is padded for it, the bottom bar is not), and
 * bundling it here would silently undo that.
 */
private val shellInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)

@Composable
fun AppShell(
    currentRoute: String?,
    avatarLabel: String,
    avatarSubtitle: String,
    onNavigateToHome: () -> Unit,
    onNavigateToCatalog: () -> Unit,
    onNavigateToAlmanac: () -> Unit,
    onNavigateToWords: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAccount: () -> Unit,
    content: @Composable () -> Unit,
) {
    val isReader = currentRoute?.startsWith("reader") == true
    val sp = LocalSpacing.current
    val palette = LocalReaderPalette.current

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            .windowInsetsPadding(shellInsets.only(WindowInsetsSides.Horizontal)),
    ) {
        when {
            isCompactWidth(maxWidth) -> {
                Column(Modifier.fillMaxSize()) {
                    if (isReader) {
                        // The strip is pure chrome — wordmark and either "ML KIT READY" or the
                        // avatar — and while reading it is only lost page area. But it is also
                        // the app's only consumer of the top inset, so it cannot simply vanish:
                        // without this spacer the first line of text would run under the system
                        // clock. Reclaims the strip's 30/44dp and keeps the inset.
                        Spacer(Modifier.fillMaxWidth().windowInsetsPadding(shellInsets.only(WindowInsetsSides.Top)))
                    } else {
                        AppStatusStrip(
                            height = sp.statusBarCompact,
                            trailing = {
                                CompactAvatar(label = avatarLabel, onClick = onNavigateToAccount)
                            },
                        )
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            // imePadding goes on the content, NOT on the bar: otherwise the bar
                            // rides up on top of the keyboard and covers the field being typed
                            // into (Words/Catalog search).
                            .imePadding(),
                    ) {
                        content()
                    }
                    if (!isReader) {
                        EditorialBottomBar(
                            currentRoute = currentRoute,
                            onNavigateToHome = onNavigateToHome,
                            onNavigateToCatalog = onNavigateToCatalog,
                            onNavigateToAlmanac = onNavigateToAlmanac,
                            onNavigateToWords = onNavigateToWords,
                            onNavigateToSettings = onNavigateToSettings,
                        )
                    }
                }
            }
            isRailTooTall(maxHeight) -> {
                // Wide but short (e.g. a phone in landscape): a bottom bar would spend the
                // scarce axis, so this posture gets a rail instead — just the icon-only one,
                // which doesn't need the full rail's 530dp of headroom. No status strip here in
                // either the reader or elsewhere: the account avatar lives in the rail, so the
                // strip would hold only the wordmark and "ML KIT READY" — decoration, in the
                // posture with the least room. The bare inset spacer keeps the top inset
                // consumed without spending height on chrome.
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.fillMaxWidth().windowInsetsPadding(shellInsets.only(WindowInsetsSides.Top)))
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        if (!isReader) {
                            CompactNavigationRail(
                                currentRoute = currentRoute,
                                avatarLabel = avatarLabel,
                                onNavigateToHome = onNavigateToHome,
                                onNavigateToCatalog = onNavigateToCatalog,
                                onNavigateToAlmanac = onNavigateToAlmanac,
                                onNavigateToWords = onNavigateToWords,
                                onNavigateToSettings = onNavigateToSettings,
                                onNavigateToAccount = onNavigateToAccount,
                            )
                        }
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            content()
                        }
                    }
                }
            }
            else -> {
                Column(Modifier.fillMaxSize()) {
                    if (isReader) {
                        // Same inset trap as the branches above: the strip is the only consumer
                        // of the top inset, so the reader still needs a bare spacer for it.
                        Spacer(Modifier.fillMaxWidth().windowInsetsPadding(shellInsets.only(WindowInsetsSides.Top)))
                    } else {
                        AppStatusStrip()
                    }
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        if (!isReader) {
                            EditorialNavigationRail(
                                currentRoute = currentRoute,
                                avatarLabel = avatarLabel,
                                avatarSubtitle = avatarSubtitle,
                                onNavigateToHome = onNavigateToHome,
                                onNavigateToCatalog = onNavigateToCatalog,
                                onNavigateToAlmanac = onNavigateToAlmanac,
                                onNavigateToWords = onNavigateToWords,
                                onNavigateToSettings = onNavigateToSettings,
                                onNavigateToAccount = onNavigateToAccount,
                            )
                        }
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppStatusStrip(
    height: Dp = LocalSpacing.current.statusBar,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = LocalReaderPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Order matters. background BEFORE the inset padding so the strip's colour extends
            // up underneath the system status bar and cutout (edge-to-edge is on —
            // MainActivity.kt:32); the inset padding BEFORE height so `height` measures the
            // content, not the inset.
            .background(palette.bg)
            .windowInsetsPadding(shellInsets.only(WindowInsetsSides.Top))
            .height(height)
            .drawBehind {
                drawLine(
                    color = palette.edge,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(horizontal = LocalSpacing.current.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "MIRROLIT",
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                color = palette.ink3,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                // Compact: the avatar takes the right slot. "ML KIT READY" is decorative, not a
                // state indicator, and at 360dp it would fight the avatar for room.
                trailing()
            } else {
                Text(
                    text = "ML KIT READY",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = palette.ink3,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun EditorialNavigationRail(
    currentRoute: String?,
    avatarLabel: String,
    avatarSubtitle: String,
    onNavigateToHome: () -> Unit,
    onNavigateToCatalog: () -> Unit,
    onNavigateToAlmanac: () -> Unit,
    onNavigateToWords: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAccount: () -> Unit,
) {
    val sp = LocalSpacing.current
    val palette = LocalReaderPalette.current
    val edgeColor = palette.edge

    Column(
        modifier = Modifier
            .width(sp.railWidth)
            .fillMaxHeight()
            .background(palette.bg2)
            .drawBehind {
                drawLine(
                    color = edgeColor,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(vertical = sp.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Wordmark
        RailWordmark()

        Spacer(Modifier.height(sp.sm))

        // 1 px separator
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(palette.edge)
        )

        Spacer(Modifier.height(sp.sm))

        // Navigation tabs
        RailTab(
            icon = Icons.Outlined.MenuBook,
            label = "Library",
            selected = currentRoute == HOME_ROUTE,
            onClick = onNavigateToHome,
        )
        RailTab(
            icon = Icons.Outlined.Explore,
            label = "Catalog",
            selected = currentRoute == CATALOG_ROUTE,
            onClick = onNavigateToCatalog,
        )
        RailTab(
            icon = Icons.Outlined.BarChart,
            label = "Almanac",
            selected = currentRoute == ALMANAC_ROUTE,
            onClick = onNavigateToAlmanac,
        )
        RailTab(
            icon = Icons.Outlined.StickyNote2,
            label = "Words",
            selected = currentRoute == WORDS_ROUTE,
            onClick = onNavigateToWords,
        )

        Spacer(Modifier.weight(1f))

        // Settings
        RailTab(
            icon = Icons.Outlined.Settings,
            label = "Settings",
            selected = currentRoute == SETTINGS_ROUTE,
            onClick = onNavigateToSettings,
        )

        Spacer(Modifier.height(sp.sm))

        // Avatar → account / profile
        RailAvatar(
            label = avatarLabel,
            subtitle = avatarSubtitle,
            onClick = onNavigateToAccount,
        )
    }
}

/**
 * Icon-only rail for windows too short for the full rail. At compact height a bottom bar costs
 * the scarce axis; a rail costs the plentiful one. Labels are dropped — each icon keeps its
 * contentDescription, so nothing is lost to TalkBack.
 *
 * Scrolls, unlike [EditorialNavigationRail], which is protected from clipping only by a height
 * threshold. Scrolling removes that failure mode outright for this rail. The consequence is that
 * the avatar cannot be pinned to the bottom with `Spacer(weight(1f))` — weight does not work
 * inside a scrolling column — so it simply follows the tabs after a fixed gap.
 */
@Composable
private fun CompactNavigationRail(
    currentRoute: String?,
    avatarLabel: String,
    onNavigateToHome: () -> Unit,
    onNavigateToCatalog: () -> Unit,
    onNavigateToAlmanac: () -> Unit,
    onNavigateToWords: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAccount: () -> Unit,
) {
    val sp = LocalSpacing.current
    val palette = LocalReaderPalette.current
    val edgeColor = palette.edge

    Column(
        modifier = Modifier
            .width(sp.railWidthCompact)
            .fillMaxHeight()
            .background(palette.bg2)
            .drawBehind {
                drawLine(
                    color = edgeColor,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .verticalScroll(rememberScrollState())
            .padding(vertical = sp.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconRailTab(Icons.Outlined.MenuBook, "Library", currentRoute == HOME_ROUTE, onNavigateToHome)
        IconRailTab(Icons.Outlined.Explore, "Catalog", currentRoute == CATALOG_ROUTE, onNavigateToCatalog)
        IconRailTab(Icons.Outlined.BarChart, "Almanac", currentRoute == ALMANAC_ROUTE, onNavigateToAlmanac)
        IconRailTab(Icons.Outlined.StickyNote2, "Words", currentRoute == WORDS_ROUTE, onNavigateToWords)
        IconRailTab(Icons.Outlined.Settings, "Settings", currentRoute == SETTINGS_ROUTE, onNavigateToSettings)
        Spacer(Modifier.height(sp.md))
        CompactAvatar(label = avatarLabel, onClick = onNavigateToAccount)
    }
}

@Composable
private fun IconRailTab(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalReaderPalette.current
    val accentColor = palette.accent
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(if (selected) palette.bg3 else palette.bg2)
            .drawBehind {
                if (selected) {
                    drawLine(
                        color = accentColor,
                        start = Offset(0f, size.height * 0.2f),
                        end = Offset(0f, size.height * 0.8f),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TabContent(icon = icon, label = label, selected = selected, showLabel = false)
    }
}

/**
 * Compact counterpart to [EditorialNavigationRail]. Same five destinations, same navigateToTab
 * callbacks, same selection rule (derived from currentRoute — no new state).
 *
 * Account is deliberately NOT a sixth item: six cells at 360dp leave 60dp each, and the labels
 * start clipping at fontScale 1.3. Account lives in the status strip instead (see CompactAvatar).
 */
@Composable
private fun EditorialBottomBar(
    currentRoute: String?,
    onNavigateToHome: () -> Unit,
    onNavigateToCatalog: () -> Unit,
    onNavigateToAlmanac: () -> Unit,
    onNavigateToWords: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val edgeColor = palette.edge

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // background BEFORE the inset padding so the bar's colour extends down under the
            // gesture bar; padding BEFORE height so `height` measures the content.
            .background(palette.bg2)
            .windowInsetsPadding(shellInsets.only(WindowInsetsSides.Bottom))
            // 64dp keeps the 56dp of content the tabs need while making the system gesture
            // inset a smaller share of the bar's visible height.
            .height(64.dp)
            .drawBehind {
                drawLine(
                    color = edgeColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    ) {
        BottomTab(Icons.Outlined.MenuBook, "Library", currentRoute == HOME_ROUTE, onNavigateToHome)
        BottomTab(Icons.Outlined.Explore, "Catalog", currentRoute == CATALOG_ROUTE, onNavigateToCatalog)
        BottomTab(Icons.Outlined.BarChart, "Almanac", currentRoute == ALMANAC_ROUTE, onNavigateToAlmanac)
        BottomTab(Icons.Outlined.StickyNote2, "Words", currentRoute == WORDS_ROUTE, onNavigateToWords)
        BottomTab(Icons.Outlined.Settings, "Settings", currentRoute == SETTINGS_ROUTE, onNavigateToSettings)
    }
}

@Composable
private fun RowScope.BottomTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val accentColor = palette.accent

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(if (selected) palette.bg3 else palette.bg2)
            .drawBehind {
                if (selected) {
                    // Accent runs along the TOP edge. The rail puts it on the left; the bottom of
                    // a bottom bar is where the gesture pill lives, so it would be swallowed there.
                    drawLine(
                        color = accentColor,
                        start = Offset(size.width * 0.2f, 0f),
                        end = Offset(size.width * 0.8f, 0f),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // maxLines = 1: a wrapped label would push the cell past the 56dp bar height.
        TabContent(icon = icon, label = label, selected = selected, maxLines = 1)
    }
}

@Composable
private fun RailWordmark() {
    // The Mirrolit logo carries its own brown tile + transparent margins, so it reads on both
    // light and dark rails without a theme-inverted variant.
    Image(
        painter = painterResource(R.drawable.mirrolit_logo),
        contentDescription = "Mirrolit",
        modifier = Modifier.size(44.dp),
    )
}

/**
 * The icon-over-label column shared by [RailTab] and [BottomTab]. The two differ only in their
 * outer shell and which edge draws the accent, so only the shell lives in each of them.
 *
 * [maxLines] defaults to unbounded to preserve the rail's existing behaviour exactly: at
 * fontScale 1.3 a long label like "Settings" wraps to two lines inside the 56dp cell, and that
 * is what ships today. The bottom bar passes 1 because a wrapped label there would push the
 * cell past the bar height.
 *
 * [showLabel] defaults to `true` so existing callers (the full rail and the bottom bar) are
 * unchanged. When `false` (the icon-only rail), the label `Text`/`Spacer` are skipped but the
 * icon's `contentDescription` is not — with the visible text gone, that is the only thing
 * TalkBack has left to announce.
 */
@Composable
private fun TabContent(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    maxLines: Int = Int.MAX_VALUE,
    showLabel: Boolean = true,
) {
    val palette = LocalReaderPalette.current
    val contentColor = if (selected) palette.ink else palette.ink3
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        if (showLabel) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                fontFamily = Newsreader,
                fontWeight = FontWeight.Normal,
                fontStyle = FontStyle.Italic,
                fontSize = 11.sp,
                color = contentColor,
                maxLines = maxLines,
            )
        }
    }
}

@Composable
private fun RailTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val accentColor = palette.accent

    Box(
        modifier = Modifier
            .width(56.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(LocalRadii.current.sm))
            .background(if (selected) palette.bg3 else palette.bg2)
            .drawBehind {
                if (selected) {
                    drawLine(
                        color = accentColor,
                        start = Offset(0f, size.height * 0.2f),
                        end = Offset(0f, size.height * 0.8f),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TabContent(icon = icon, label = label, selected = selected)
    }
}

@Composable
private fun RailAvatar(label: String, subtitle: String, onClick: () -> Unit) {
    val palette = LocalReaderPalette.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(LocalRadii.current.sm))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(palette.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontFamily = Newsreader,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                color = palette.bg,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = subtitle,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            color = palette.ink3,
        )
    }
}

/**
 * Compact-shell counterpart to [RailAvatar]: same letter, same accent fill, no caption — the
 * caption is a luxury the vertical rail can afford and a 44dp strip cannot. Same destination:
 * Profile when signed in, Auth when not (resolved in SplitReaderNavHost).
 */
@Composable
private fun CompactAvatar(label: String, onClick: () -> Unit) {
    val palette = LocalReaderPalette.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "Account"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(palette.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontFamily = Newsreader,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                fontSize = 12.sp,
                color = palette.bg,
            )
        }
    }
}
