package com.example.splitreader.presentation.reader

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.ChapterImage

/**
 * The single source of the reader's LazyColumn item structure — keys, order, and count.
 *
 * Both the landscape spread and each of the two vertical panes call this, so their item indices
 * are identical **by construction** rather than by anyone keeping two emitters in step. That
 * identity is what lets `chapterItemStarts`, scroll restore, progress persistence, bookmark
 * jumps, `markFinished` and `onVisibleRange` all stay on the top pane's `listState` with no
 * index translation anywhere.
 *
 * Consequence worth knowing before "optimising": the translation pane MUST emit an item for every
 * illustration too, even though it only draws a slim placeholder. Skipping them would shift every
 * subsequent index in that pane and silently desynchronise the two lists.
 *
 * This function owns the `item()` calls and their keys and nothing else; the three slots own
 * their content entirely, including their own trailing spacing.
 */
internal fun LazyListScope.bookItems(
    book: Book,
    showIllustrations: Boolean,
    masthead: @Composable (chapterIndex: Int, chapter: Chapter) -> Unit,
    image: @Composable (chapterIndex: Int, imageIndex: Int, image: ChapterImage) -> Unit,
    paragraph: @Composable (chapterIndex: Int, paragraphIndex: Int, text: String) -> Unit,
) {
    book.chapters.forEachIndexed { chapterIndex, chapter ->
        item(key = "masthead_$chapterIndex") { masthead(chapterIndex, chapter) }

        val images = if (showIllustrations) chapter.images else emptyList()
        chapter.paragraphs.forEachIndexed { idx, original ->
            images.forEachIndexed { imgIdx, img ->
                if (img.anchorParagraph == idx) {
                    item(key = "img_${chapterIndex}_$imgIdx") { image(chapterIndex, imgIdx, img) }
                }
            }
            item(key = "p_${chapterIndex}_$idx") { paragraph(chapterIndex, idx, original) }
        }
        images.forEachIndexed { imgIdx, img ->
            if (img.anchorParagraph >= chapter.paragraphs.size) {
                item(key = "img_${chapterIndex}_$imgIdx") { image(chapterIndex, imgIdx, img) }
            }
        }
    }
    item(key = "end_padding") { Spacer(Modifier.height(48.dp)) }
}
