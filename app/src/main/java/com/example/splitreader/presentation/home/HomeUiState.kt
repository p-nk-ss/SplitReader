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
