package com.example.splitreader.presentation.navigation

/**
 * Test tags for the shell's chrome.
 *
 * One object so production and tests cannot drift apart: renaming a tag here is a compile error at
 * both ends, whereas a string literal duplicated into a test silently stops matching.
 *
 * These address nodes for the window-inset invariant tests, which is how the shell's inset handling
 * is now covered — four separate inset defects shipped in Phase 2b while it was not. See the
 * arm x edge table above `shellInsets` in AppShell.kt for what is covered and what those tests
 * still cannot see.
 */
object ShellTestTags {
    const val STATUS_STRIP = "shell:statusStrip"
    const val BOTTOM_BAR = "shell:bottomBar"
    const val COMPACT_RAIL = "shell:compactRail"
    const val FULL_RAIL = "shell:fullRail"
    const val CONTENT = "shell:content"
    const val RAIL_AVATAR = "shell:railAvatar"
}
