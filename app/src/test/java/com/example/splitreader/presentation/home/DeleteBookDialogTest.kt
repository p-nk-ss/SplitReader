package com.example.splitreader.presentation.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.splitreader.presentation.theme.SplitReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The delete confirmation keeps the reader's place, bookmarks and notes unless they tick "Also
 * delete my reading progress, bookmarks and notes" -- which starts unticked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class DeleteBookDialogTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val deletes = mutableListOf<Boolean>()

    private fun openDialog() {
        composeRule.setContent {
            SplitReaderTheme {
                BookCoverCard(
                    book = BookItem(uri = "content://b/1", title = "Emma", author = "Jane Austen", coverPath = null, chapterCount = 3),
                    onClick = {},
                    onDelete = { alsoReadingData -> deletes += alsoReadingData },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Delete book").performClick()
    }

    @Test
    fun `the checkbox starts unticked and Delete keeps the reading data`() {
        openDialog()

        composeRule.onNodeWithText("\"Emma\" will be removed from your library.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(DELETE_BOOK_ALSO_DATA_CHECKBOX).assertIsOff()
        composeRule.onNodeWithText("Your place, bookmarks and notes are kept in case you add it again.", substring = true)
            .assertIsDisplayed()

        composeRule.onNodeWithText("Delete").performClick()
        assertEquals(listOf(false), deletes)
    }

    @Test
    fun `ticking the checkbox makes Delete take the reading data too`() {
        openDialog()

        composeRule.onNodeWithText("Also delete my reading progress, bookmarks and notes").performClick()
        composeRule.onNodeWithTag(DELETE_BOOK_ALSO_DATA_CHECKBOX).assertIsOn()

        composeRule.onNodeWithText("Delete").performClick()
        assertEquals(listOf(true), deletes)
    }

    @Test
    fun `Cancel deletes nothing, and the next time the checkbox is unticked again`() {
        openDialog()
        composeRule.onNodeWithTag(DELETE_BOOK_ALSO_DATA_CHECKBOX).performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<Boolean>(), deletes)

        composeRule.onNodeWithContentDescription("Delete book").performClick()
        composeRule.onNodeWithTag(DELETE_BOOK_ALSO_DATA_CHECKBOX).assertIsOff()
    }
}
