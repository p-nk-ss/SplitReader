package com.example.splitreader

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsPropertiesAndroid
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.window.Dialog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Maestro (and plain `adb shell uiautomator dump`) can only address a Compose node by
 * `resource-id` when some ancestor in its OWN semantics tree set
 * [SemanticsPropertiesAndroid.TestTagsAsResourceId]. `MainActivity` does this once, wrapping
 * [com.example.splitreader.presentation.navigation.SplitReaderNavHost]'s root via [e2eRoot] — see
 * the task-1 hierarchy dump, which found every reader top-bar icon button addressable by neither
 * text nor content-description, only a `testTag` that today produces no `resource-id` at all.
 *
 * A `Dialog`/`Popup` (the reader's `EditorialDialog` wrapper: translator picker, display
 * settings, chapters, bookmarks, language picker) opens a SEPARATE `AndroidComposeView`, so its
 * subtree is a disconnected semantics root that does not inherit the outer tree's property — the
 * second test below pins that down, and it is why `EditorialDialog` carries its own [e2eRoot].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TestTagsAsResourceIdTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Walks a node's own semantics tree (never crosses into a different `Dialog`/`Popup` root). */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun hasTestTagsAsResourceIdAncestor(tag: String): Boolean {
        var node: SemanticsNode? = composeRule.onNodeWithTag(tag).fetchSemanticsNode()
        while (node != null) {
            if (node.config.getOrElse(SemanticsPropertiesAndroid.TestTagsAsResourceId) { false }) return true
            node = node.parent
        }
        return false
    }

    @Test
    fun `e2eRoot marks its own subtree's testTagsAsResourceId`() {
        composeRule.setContent {
            Box(Modifier.e2eRoot()) {
                Box(Modifier.testTag("probe"))
            }
        }

        assertTrue(
            "A node tagged under Modifier.e2eRoot() must have an ancestor with " +
                "TestTagsAsResourceId = true, or Maestro/uiautomator cannot address it by resource-id.",
            hasTestTagsAsResourceIdAncestor("probe"),
        )
    }

    @Test
    fun `dialog content does not inherit the host's testTagsAsResourceId`() {
        composeRule.setContent {
            Box(Modifier.e2eRoot()) {
                Dialog(onDismissRequest = {}) {
                    Box(Modifier.testTag("insideDialog"))
                }
            }
        }

        assertFalse(
            "A Dialog opens its own AndroidComposeView — a disconnected semantics root — so the " +
                "host's e2eRoot() does not reach into it. Any dialog that needs resource-ids " +
                "(EditorialDialog) must apply its own e2eRoot().",
            hasTestTagsAsResourceIdAncestor("insideDialog"),
        )
    }

    @Test
    fun `a dialog with its own e2eRoot does carry testTagsAsResourceId`() {
        composeRule.setContent {
            Dialog(onDismissRequest = {}) {
                Box(Modifier.e2eRoot()) {
                    Box(Modifier.testTag("insideDialog2"))
                }
            }
        }

        assertTrue(
            "Applying e2eRoot() inside the dialog's own composition must make its descendants' " +
                "testTags resource-id-addressable, matching EditorialDialog's fix.",
            hasTestTagsAsResourceIdAncestor("insideDialog2"),
        )
    }
}
