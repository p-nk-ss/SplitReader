package com.example.splitreader.domain.model

data class Chapter(
    val index: Int,
    val title: String,
    val paragraphs: List<String>,
    val epigraphCount: Int = 0,
    val images: List<ChapterImage> = emptyList(),
)

/**
 * An illustration anchored into a chapter's flow. [anchorParagraph] is an index in 0..paragraphs.size:
 * the image renders immediately before paragraphs[anchorParagraph] (== size means after the last
 * paragraph). Images are a parallel list so paragraph indices stay stable for translation/selection.
 */
data class ChapterImage(
    val anchorParagraph: Int,
    val path: String,
    /**
     * True for images the pre-2026-10 parser also produced (EPUB images after a chapter heading;
     * every MOBI/FB2 image). Legacy item-index migration replays the OLD layout and must count only
     * these, or a position saved before images were added converts to an earlier paragraph and
     * neighbouring bookmarks merge. Never persisted: books are parsed on every open.
     */
    val inLegacyLayout: Boolean = true,
)
