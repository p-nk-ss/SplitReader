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
)
