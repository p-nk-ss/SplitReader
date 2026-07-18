package com.example.splitreader.domain.parser.fb2

/** Pure result of parsing an FB2 event stream; the Android adapter turns this into a Book. */
data class Fb2Document(
    val title: String,
    val author: String,
    val annotation: String,
    val chapters: List<Fb2ChapterData>,
    val coverBinaryId: String?,
    val binaries: Map<String, String>,   // binary id -> raw base64 text (cover + referenced images)
)

data class Fb2ChapterData(
    val index: Int,
    val title: String,
    val paragraphs: List<String>,        // epigraph paragraphs first, then body
    val epigraphCount: Int,
    val imageRefs: List<Pair<Int, String>>,  // (final anchor into paragraphs, binary id)
)
