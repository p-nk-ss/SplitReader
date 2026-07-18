package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.model.ReadingSession
import com.example.splitreader.domain.repository.ReadingSessionRepository
import javax.inject.Inject

/**
 * Persists a finished reading session. Sessions shorter than [MIN_SESSION_SECONDS] are ignored
 * to avoid recording incidental opens.
 */
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
        if (duration < MIN_SESSION_SECONDS) return
        repository.record(
            ReadingSession(
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

    private companion object {
        const val MIN_SESSION_SECONDS = 15
    }
}
