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

/**
 * Minimum **window** height for the full labeled navigation rail (`EditorialNavigationRail`) to
 * render without clipping.
 *
 * This is compared against `BoxWithConstraints.maxHeight` in `AppShell` — i.e. the whole window —
 * NOT the height the rail itself actually receives. The rail lives in a `Row(weight(1f))` *below*
 * `AppStatusStrip()`, so the window has to be tall enough to cover all three of:
 * - **450dp** for the rail's own content: 32dp of vertical padding, the 44dp wordmark, 25dp of
 *   separators, four 56dp tabs, a flexible spacer, the 56dp Settings tab, a 12dp gap and the
 *   ~57dp avatar.
 * - **30dp** for `AppStatusStrip()` (`sp.statusBar`), which sits above the rail's `Row` and is not
 *   part of the rail.
 * - **~48dp** for the top window inset (status bar / cutout), rounded up, which the strip consumes
 *   via `statusBarsPadding()` before its own 30dp height is measured.
 *
 * 450 + 30 + 48 = 528, rounded up to 530dp. A phone in landscape (e.g. 891 x 411dp) offers a
 * window height well under that, so width alone is not enough to decide: a wide-but-short window
 * must still fall back off the full rail — but not all the way to the bottom bar. At compact
 * height a bottom bar spends the scarce axis while a rail spends the plentiful one, so this case
 * still gets a rail: the icon-only one, which scrolls instead of needing the same 530dp headroom.
 */
val RAIL_MIN_HEIGHT: Dp = 530.dp

/** True when [height] is too short to show the full rail without clipping its lower items. */
fun isRailTooTall(height: Dp): Boolean = height < RAIL_MIN_HEIGHT
