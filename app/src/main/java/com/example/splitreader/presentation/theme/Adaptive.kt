package com.example.splitreader.presentation.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Width below which a box is considered "compact" by [isCompactWidth]. 600dp is the conventional
 * compact/medium window-size-class boundary, but this constant is applied to whatever width a
 * given call site passes in — see [isCompactWidth] for why that is not always the window width.
 *
 * One example consequence: when `AppShell`'s full window width is compact, the app drops the
 * left navigation rail for a bottom bar. Other call sites (e.g. Words' two-pane/master-detail
 * switch) apply the same threshold to a narrower box, so they flip at a wider window width than
 * 600dp — that is by design, not a bug.
 */
val COMPACT_WIDTH_THRESHOLD: Dp = 600.dp

/**
 * True when [width] is narrow enough for the compact layout.
 *
 * Deliberately NOT a `@Composable` and deliberately NOT reading `Configuration.orientation`:
 * callers pass `BoxWithConstraints.maxWidth`, so split-screen and folded/unfolded postures are
 * covered for free (a half-screen tablet window is compact even though the device is landscape),
 * and the predicate stays unit-testable on the JVM.
 */
fun isCompactWidth(width: Dp): Boolean = width < COMPACT_WIDTH_THRESHOLD
