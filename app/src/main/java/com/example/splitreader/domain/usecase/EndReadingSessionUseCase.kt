package com.example.splitreader.domain.usecase

import com.example.splitreader.data.local.ReadingSessionEntity
import com.example.splitreader.domain.repository.ReadingSessionRepository
import javax.inject.Inject

class EndReadingSessionUseCase @Inject constructor(
    private val repository: ReadingSessionRepository,
) {
    suspend operator fun invoke(
        startedAt: Long,
        bookUri: String?,
        bookTitle: String,
        sourceLang: String,
        paragraphsRead: Int,
    ) {
        val now = System.currentTimeMillis()
        val duration = ((now - startedAt) / 1000).toInt()
        if (duration < 15) return
        repository.record(
            ReadingSessionEntity(
                bookUri = bookUri,
                bookTitle = bookTitle,
                sourceLang = sourceLang,
                startedAt = startedAt,
                endedAt = now,
                durationSeconds = duration,
                paragraphsRead = paragraphsRead,
            )
        )
    }
}
