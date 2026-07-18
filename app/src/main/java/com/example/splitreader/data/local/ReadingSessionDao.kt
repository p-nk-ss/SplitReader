package com.example.splitreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class DailyMinutesRow(val day: String, val minutes: Int)
data class BookMinutesRow(val title: String, val minutes: Int)
data class LangMinutesRow(val lang: String, val minutes: Int)

@Dao
interface ReadingSessionDao {
    @Insert suspend fun insert(s: ReadingSessionEntity): Long

    @Query("""
        SELECT strftime('%Y-%m-%d', startedAt / 1000, 'unixepoch', 'localtime') AS day,
               SUM(durationSeconds) / 60 AS minutes
        FROM reading_sessions
        WHERE startedAt >= :sinceMillis
        GROUP BY day ORDER BY day
    """)
    fun observeDailyMinutes(sinceMillis: Long): Flow<List<DailyMinutesRow>>

    @Query("""
        SELECT bookTitle AS title, SUM(durationSeconds) / 60 AS minutes
        FROM reading_sessions
        WHERE startedAt >= :sinceMillis AND bookTitle IS NOT NULL
        GROUP BY bookTitle ORDER BY minutes DESC LIMIT 8
    """)
    fun observeTimeByBook(sinceMillis: Long): Flow<List<BookMinutesRow>>

    @Query("""
        SELECT sourceLang AS lang, SUM(durationSeconds) / 60 AS minutes
        FROM reading_sessions
        WHERE startedAt >= :sinceMillis
        GROUP BY sourceLang
    """)
    fun observeTimeByLang(sinceMillis: Long): Flow<List<LangMinutesRow>>
}
