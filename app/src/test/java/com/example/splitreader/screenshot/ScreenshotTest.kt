package com.example.splitreader.screenshot

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.splitreader.presentation.theme.ReaderThemeKey
import com.example.splitreader.presentation.theme.SplitReaderTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
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
@Config(qualifiers = TABLET, sdk = [34], application = Application::class)
abstract class ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Text-heavy Material chrome (Home/Words/Catalog/Almanac lists + labels) shows sub-pixel
    // anti-aliasing jitter on glyph edges that varies run-to-run — a handful of pixels flip even
    // between a record and an immediately-following verify. An exact (0-tolerance) compare fails on
    // that noise. A small changed-pixel fraction absorbs the AA jitter while still catching real
    // visual regressions (a palette swap, layout shift, or text change moves far more than this).
    //
    // `changeThreshold` is exposed as a `captureScreen` parameter (default unchanged at 1%) because
    // the same AA jitter is proportionally *worse* on a dialog captured on its own: a large title
    // occupies far more of a ~600x650px dialog-only crop than of a ~1000x2300px full-screen golden,
    // so the same handful of jittering edge pixels reads as a bigger fraction of the smaller image.
    // Confirmed by direct pixel diff (not just re-tried until green): re-recording and diffing
    // `reader_vertical_hint_paper_1x` against a fresh capture showed ~2.8% changed pixels confined to
    // the two-line title, with the two images visually indistinguishable side by side.
    @OptIn(ExperimentalRoborazziApi::class)
    private fun roborazziOptions(changeThreshold: Float) = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = changeThreshold),
    )

    @OptIn(ExperimentalRoborazziApi::class)
    fun captureScreen(
        name: String,
        theme: ReaderThemeKey = ReaderThemeKey.PAPER,
        fontScale: Float = 1f,
        rtl: Boolean = false,
        changeThreshold: Float = 0.01f,
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
        // A Compose `Dialog` (used by `EditorialDialog`/`AnimatedDialog`) opens its own window, which
        // Robolectric surfaces as a second `isRoot()` node alongside the main content root — `onRoot()`
        // then throws ("expected exactly '1' node but found '2'"). When that happens the dialog is the
        // thing under test, so capture it specifically instead of the (possibly empty, for
        // dialog-only content lambdas) main root.
        val target = if (composeRule.onAllNodes(isRoot()).fetchSemanticsNodes().size > 1) {
            composeRule.onNode(isDialog())
        } else {
            composeRule.onRoot()
        }
        // NOTE: the Gradle test worker's working directory is the module dir (`app/`), not the repo
        // root, so the path is module-relative (`src/test/screenshots/...`) — an `app/`-prefixed path
        // (as in the original plan draft) resolves to the wrong `app/app/src/test/screenshots/...`.
        target.captureRoboImage("src/test/screenshots/$name.png", roborazziOptions = roborazziOptions(changeThreshold))
    }
}

// Device qualifiers for per-method `@Config` overrides. Robolectric merges a method-level @Config
// over the class-level one, so a test annotated with any of these inherits `sdk = [34]` and the
// stub `application = Application::class` from ScreenshotTest and only swaps the device.
// These must be `const` to be usable in an annotation argument.

/** Pixel-class phone in portrait — the reference device for the compact layout. */
const val PHONE_PORTRAIT = "w411dp-h891dp-420dpi"

/** Narrowest width we support. Stress case: labels and grid cells fail here first. */
const val PHONE_NARROW = "w360dp-h640dp-xhdpi"

/**
 * Phone width, absurd height. Not a real device — a way to render a long scrolling column in
 * full, because `captureRoboImage` snapshots `onRoot()` and is therefore clipped to the device
 * height. Used to cover content that sits below the fold on every real device.
 */
const val PHONE_TALL = "w411dp-h2000dp-420dpi"

/**
 * Phone in landscape: wide enough that [com.example.splitreader.presentation.theme.isCompactWidth]
 * alone would pick the full rail, but short enough that
 * [com.example.splitreader.presentation.theme.isRailTooTall] still forces the icon-only rail
 * fallback. Used to prove `AppShell` actually applies that fallback, not just that the pure
 * predicate computes correctly.
 */
const val PHONE_LANDSCAPE = "w891dp-h411dp-420dpi"

/** Phone width, absurd height — for screens whose content exceeds even PHONE_TALL. */
const val PHONE_XTALL = "w411dp-h4000dp-420dpi"

/** The tablet reference device — and literally the one ScreenshotTest's class-level @Config uses. */
const val TABLET = "w1280dp-h800dp-xhdpi"

/**
 * Tablet width, absurd height — the [TABLET] analogue of [PHONE_TALL]: `DisplaySettingsDialog`'s
 * `maxDialogHeight` is 90% of the device height, and at [TABLET]'s 800dp that scrollable region
 * runs out before reaching the split slider near the bottom of the dialog's content. Same width
 * as [TABLET], so `isCompactWidth` still resolves the same way — only the fold moves.
 */
const val TABLET_TALL = "w1280dp-h2000dp-xhdpi"

/**
 * Tablet in portrait — 800dp wide (~47 characters per column), well above
 * [com.example.splitreader.presentation.theme.COMPACT_WIDTH_THRESHOLD]. Used to prove the
 * width-based vertical-layout trigger keeps the side-by-side split here, not just on phones.
 */
const val TABLET_PORTRAIT = "w800dp-h1280dp-xhdpi"
