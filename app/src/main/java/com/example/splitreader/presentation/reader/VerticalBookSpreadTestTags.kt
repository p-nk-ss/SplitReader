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

/**
 * The reader top bar's display-settings button.
 *
 * Exists so a test can open `DisplaySettingsDialog` the way a user does — through
 * `ReaderContent`'s own state — rather than by calling the dialog directly with a hand-written
 * argument. Task 7's review found that every dialog golden bypassed `ReaderContent`, leaving the
 * one line that actually decides which split slider appears (`vertical = vertical` at the
 * `DisplaySettingsDialog` call site) untested by anything: drop it, swap it or hardcode it and the
 * whole suite still passed.
 *
 * The button is an icon with a null `contentDescription`, so there is nothing else to address it
 * by. (That null is an accessibility gap in its own right — its sibling bookmark button is
 * labelled — but fixing the reader top bar's labels is not this phase's business.)
 */
const val READER_DISPLAY_SETTINGS_BUTTON = "reader:displaySettingsButton"
