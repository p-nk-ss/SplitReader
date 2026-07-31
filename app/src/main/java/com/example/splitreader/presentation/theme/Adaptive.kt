package com.example.splitreader.presentation.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Width below which the app drops the left navigation rail for a bottom bar, and Words collapses
 * from two panes to master/detail. 600dp is the conventional compact/medium boundary.
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
