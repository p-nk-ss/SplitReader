package com.example.splitreader.presentation.reader

/**
 * Test tags for [VerticalBookSpread]'s own structure.
 *
 * One object so production and tests cannot drift apart: renaming a tag here is a compile error at
 * both ends, whereas a string literal duplicated into a test silently stops matching. Mirrors
 * `ShellTestTags` (`presentation/navigation/ShellTestTags.kt`), which exists for the same reason on
 * the shell's own chrome.
 *
 * These address nodes for the stacked reader's window-inset tests
 * (`insets/VerticalReaderInsetTest.kt`) — the handover Phase 2d's spec left for Phase 3: the shell's
 * insets are covered by `ShellInsetTest` and its subclasses, but the reader draws its own panes
 * edge-to-edge underneath that shell and must consume the top/bottom insets itself.
 */
const val VERTICAL_SPREAD_ROOT = "reader:verticalSpread"
const val VERTICAL_TOP_PANE = "reader:verticalTopPane"
const val VERTICAL_BOTTOM_PANE = "reader:verticalBottomPane"
const val VERTICAL_DIVIDER = "reader:verticalDivider"
