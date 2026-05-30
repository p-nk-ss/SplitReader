package com.example.splitreader.domain.repository

import com.example.splitreader.data.local.BookMinutes
import com.example.splitreader.data.local.DailyMinutes
import com.example.splitreader.data.local.LangMinutes
import com.example.splitreader.data.local.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow

/** Records reading sessions and exposes aggregated reading-time statistics. */
interface ReadingSessionRepository {
    suspend fun record(session: ReadingSessionEntity)
    fun observeDailyMinutes(daysBack: Int): Flow<List<DailyMinutes>>
    fun observeWeeklyMinutes(): Flow<List<DailyMinutes>>
    fun observeTimeByBook(daysBack: Int): Flow<List<BookMinutes>>
    fun observeTimeByLang(daysBack: Int): Flow<List<LangMinutes>>
}
