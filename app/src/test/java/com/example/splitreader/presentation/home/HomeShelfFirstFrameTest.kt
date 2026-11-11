package com.example.splitreader.presentation.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import com.example.splitreader.presentation.theme.SplitReaderTheme
import com.example.splitreader.screenshot.ScreenFixtures
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home -> "Your shelf": the first composition of the grid measured each [BookCoverCard] at about
 * half its settled height (evidence from E2E on a tablet, API 35, 320 dpi: hierarchy height 216 px
 * on first render, 482 px after one small swipe; a pixel scan showed the cover — a fixed 140x206dp
 * box, unrelated to any content it holds — genuinely cut at 84 dp). The card's corner delete icon
 * sits past that cut, so it was invisible and absent from the accessibility tree until a scroll
 * forced the grid to remeasure the item.
 *
 * Root cause: `itemsIndexed(...) { index, book -> StaggeredAppear(index = index, modifier =
 * Modifier.animateItem()) { BookCoverCard(...) } }` put `Modifier.animateItem()` — the grid's own
 * lookahead-based placement animator — directly on `StaggeredAppear`'s `AnimatedVisibility`, which
 * is itself a lookahead participant for its enter animation. Stacking both on the same layout node
 * let them disagree about that node's size on its very first measured frame; fixed by moving
 * `animateItem()` onto a separate wrapping `Box` so the two no longer share a node.
 *
 * Reproduces the real transition: [HomeScreen] shows a loading skeleton first, then — the instant
 * `isLoading` flips to false — the real grid with book covers, exactly as `HomeViewModel` drives it.
 * `mainClock.autoAdvance = false` freezes the clock across that flip, so the assertions below see
 * the very first frame of the real grid, the same frame a real first render shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h2200dp-xhdpi")
class HomeShelfFirstFrameTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the first book card is already full height on the first frame after loading`() {
        composeRule.mainClock.autoAdvance = false
        var loading by mutableStateOf(true)
        composeRule.setContent {
            SplitReaderTheme {
                HomeScreen(
                    uiState = ScreenFixtures.homeUiStateRich.copy(isLoading = loading),
                    onOpenFilePicker = {},
                    onOpenFromLibrary = {},
                    onDeleteBook = {},
                    onDismissError = {},
                )
            }
        }

        // Flip loading -> loaded, exactly as HomeViewModel does once the library finishes reading,
        // then only let Compose run the ONE frame it needs to apply the recomposition — never an
        // extra settling frame, which is what would hide a first-frame-only defect.
        loading = false
        composeRule.mainClock.advanceTimeByFrame()

        // Index 1, not 0: book 0 doubles as the "Continue reading" hero, so its title appears
        // twice in the tree (hero + shelf card) and would make the lookup below ambiguous. The
        // shelf grid lists books in the same order, so its "Delete book" icon is also index 1
        // (index 0 is book 0's own shelf card).
        val firstTitle = ScreenFixtures.homeUiStateRich.books[1].title
        val firstFrameHeight = composeRule.onNodeWithText(firstTitle).onParent().fetchSemanticsNode().size.height

        // The corner delete icon must already be in the tree and displayed, not past a cut card.
        composeRule.onAllNodesWithContentDescription("Delete book")[1].assertIsDisplayed()

        // Let every pending animation (StaggeredAppear's enter) finish — the settled state the E2E
        // saw only after one small swipe forced a relayout.
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        val settledHeight = composeRule.onNodeWithText(firstTitle).onParent().fetchSemanticsNode().size.height

        assertTrue(
            "First frame reported ${firstFrameHeight}px but the settled height is ${settledHeight}px " +
                "-- the card was cut on first render.",
            firstFrameHeight >= settledHeight - 1,
        )
    }
}
