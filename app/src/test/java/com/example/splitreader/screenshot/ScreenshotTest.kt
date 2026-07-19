package com.example.splitreader.screenshot

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.splitreader.presentation.theme.ReaderThemeKey
import com.example.splitreader.presentation.theme.SplitReaderTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Base harness for Roborazzi + Robolectric screenshot tests.
 *
 * Renders production stateless composables inside [SplitReaderTheme], wrapped with
 * font-scale/layout-direction overrides, and captures a PNG golden under the module's
 * `src/test/screenshots/` (i.e. `app/src/test/screenshots/` from the repo root). Reference
 * device is a tablet landscape (`w1280dp-h800dp-xhdpi`) — see the V0 screenshot-harness plan
 * for rationale.
 */
// Robolectric 4.13 only ships SDK jars up to API 34 (compileSdk/targetSdk here is 36), so the
// test SDK is pinned explicitly; otherwise DefaultSdkPicker rejects targetSdkVersion=36 as
// "> maxSdkVersion=34". The plain `android.app.Application` is used in place of the manifest's
// `SplitReaderApplication` (a `@HiltAndroidApp` that eagerly calls `FirebaseCrashlytics.getInstance()`
// in `onCreate`) because Robolectric has no Firebase backend, which would throw
// `IllegalStateException: Default FirebaseApp is not initialized`.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-xhdpi", sdk = [34], application = Application::class)
abstract class ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    fun captureScreen(
        name: String,
        theme: ReaderThemeKey = ReaderThemeKey.PAPER,
        fontScale: Float = 1f,
        rtl: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                SplitReaderTheme(readerThemeKey = theme) { content() }
            }
        }
        composeRule.waitForIdle()
        // NOTE: the Gradle test worker's working directory is the module dir (`app/`), not the repo
        // root, so the path is module-relative (`src/test/screenshots/...`) — an `app/`-prefixed path
        // (as in the original plan draft) resolves to the wrong `app/app/src/test/screenshots/...`.
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
