package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.ReadingSession
import com.example.splitreader.domain.model.stats.BookMinutes
import com.example.splitreader.domain.model.stats.DailyMinutes
import com.example.splitreader.domain.model.stats.LangMinutes
import kotlinx.coroutines.flow.Flow

/** Records reading sessions and exposes aggregated reading-time statistics. */
interface ReadingSessionRepository {
    suspend fun record(session: ReadingSession)
    fun observeDailyMinutes(daysBack: Int): Flow<List<DailyMinutes>>
    fun observeWeeklyMinutes(): Flow<List<DailyMinutes>>
    fun observeTimeByBook(daysBack: Int): Flow<List<BookMinutes>>
    fun observeTimeByLang(daysBack: Int): Flow<List<LangMinutes>>
}
