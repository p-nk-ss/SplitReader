package com.example.splitreader.presentation.home

data class BookItem(
    val uri: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val chapterCount: Int,
    val lastChapterIndex: Int = 0,
)

data class HomeUiState(
    val books: List<BookItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val lastBook: BookItem? get() = books.firstOrNull()
}
