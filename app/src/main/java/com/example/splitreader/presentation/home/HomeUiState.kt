package com.example.splitreader.presentation.home

data class BookItem(
    val uri: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val chapterCount: Int,
    val lastChapterIndex: Int = 0,
    val isFinished: Boolean = false,
    val lastOpenedAt: Long = 0L,
    val synopsis: String? = null,
    val excerpt: String? = null,
    /** 0f..1f by paragraph (Book.progressAt), 1f once finished; chapter ratio for books read before it was saved. */
    val progress: Float = 0f,
) {
    /**
     * Read into, "Reading" vs "Unread" on the shelf. The excerpt counts: the reader saves it as soon as
     * the position leaves the very start (Book.isAtStart), and paragraph progress is still 0 inside
     * the first paragraph — the card read "Unread" under a "Continue reading" excerpt.
     */
    val isStarted: Boolean get() = progress > 0f || lastChapterIndex > 0 || excerpt != null
}

/**
 * A library card from what the reader saved for [book]: [finished] reads 100%; otherwise the
 * paragraph [savedProgress], or for books read before it was saved, the chapter ratio.
 */
internal fun bookItemOf(
    book: com.example.splitreader.domain.model.LibraryBook,
    finished: Boolean,
    chapter: Int,
    savedProgress: Float?,
    excerpt: String?,
) = BookItem(
    uri = book.uri,
    title = book.title,
    author = book.author,
    coverPath = book.coverPath,
    chapterCount = book.chapterCount,
    lastChapterIndex = chapter,
    isFinished = finished,
    progress = when {
        finished -> 1f
        else -> savedProgress ?: if (book.chapterCount > 0) chapter.toFloat() / book.chapterCount else 0f
    },
    lastOpenedAt = book.lastOpenedAt,
    synopsis = book.synopsis,
    excerpt = excerpt,
)

data class HomeUiState(
    val books: List<BookItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val streakDays: Int = 0,
    val weeklyMinutes: Int = 0,
    val savedWordsThisWeek: Int = 0,
    val minutesToday: Int = 0,
    val weeklyGoal: Int = 180,
    val userName: String? = null,
    // Rendered strings rather than a timestamp: the composable should not ask the system what
    // time it is, and pinning these in fixtures is what makes the Home goldens deterministic.
    val dateEyebrow: String = "",
    val greetingText: String = "",
) {
    val lastBook: BookItem? get() = books.firstOrNull()
}
