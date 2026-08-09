package com.example.splitreader.insets

import android.app.Application
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.splitreader.presentation.navigation.AppShell
import com.example.splitreader.presentation.navigation.HOME_ROUTE
import com.example.splitreader.presentation.theme.SplitReaderTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Base for the window-inset invariant tests.
 *
 * The app draws edge-to-edge and consumes insets in five places across three shell arms; before
 * this existed, none of that was testable and four separate inset defects shipped, each found by
 * a human on hardware. The technique — dispatching synthetic insets straight at the
 * AndroidComposeView — was established by InsetInjectionSpikeTest.
 *
 * Subclasses pick which shell arm composes with a per-method `@Config(qualifiers = ...)`.
 *
 * No longer shell-only despite the name: `VerticalReaderInsetTest` extends this for the reader's
 * own panes and composes no [AppShell] at all, using [insets] and `boundsOf` with its own content.
 * What this class actually provides is the dispatch recipe — send a synthetic `WindowInsetsCompat`
 * at the AndroidComposeView, not the decor view, then `waitForIdle()` twice — plus the helpers
 * around it. Keep new inset suites on this base rather than re-deriving that recipe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
abstract class ShellInsetTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Builds the inset set the composed content can be occluded by: system bars plus a cutout. */
    fun insets(
        statusBars: Int = 0,
        navigationBars: Int = 0,
        cutoutLeft: Int = 0,
        cutoutRight: Int = 0,
    ): WindowInsetsCompat = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusBars, 0, 0))
        .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navigationBars))
        .setInsets(
            WindowInsetsCompat.Type.displayCutout(),
            Insets.of(cutoutLeft, statusBars, cutoutRight, 0),
        )
        .build()

    /**
     * Composes [AppShell] with the given route and delivers [insets] to it.
     *
     * Dispatch goes to the AndroidComposeView — the first child of android.R.id.content — and NOT
     * to the decor view, whose ancestors can consume the insets before Compose sees them; and the
     * second waitForIdle is required because Compose applies them on the next composition, not
     * synchronously. Both were established by the spike; removing either silently yields zeros.
     */
    fun composeShell(
        insets: WindowInsetsCompat,
        currentRoute: String? = HOME_ROUTE,
        content: @Composable () -> Unit = {},
    ) {
        composeRule.setContent {
            SplitReaderTheme {
                AppShell(
                    currentRoute = currentRoute,
                    avatarLabel = "M",
                    avatarSubtitle = "mirrolit",
                    onNavigateToHome = {},
                    onNavigateToCatalog = {},
                    onNavigateToAlmanac = {},
                    onNavigateToWords = {},
                    onNavigateToSettings = {},
                    onNavigateToAccount = {},
                    content = content,
                )
            }
        }
        composeRule.waitForIdle()

        val composeView = composeRule.activity
            .findViewById<ViewGroup>(android.R.id.content)
            .getChildAt(0)
        composeRule.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(composeView, insets) }
        composeRule.waitForIdle()
    }

    /** Bounds of the node carrying [tag], in root pixels. */
    fun boundsOf(tag: String): Rect =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
}
