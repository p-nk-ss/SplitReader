package com.example.splitreader.data.repository

import com.example.splitreader.data.local.ReadingSessionDao
import com.example.splitreader.data.repository.mapper.toDomain
import com.example.splitreader.data.repository.mapper.toEntity
import com.example.splitreader.domain.model.ReadingSession
import com.example.splitreader.domain.model.stats.BookMinutes
import com.example.splitreader.domain.model.stats.DailyMinutes
import com.example.splitreader.domain.model.stats.LangMinutes
import com.example.splitreader.domain.repository.ReadingSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingSessionRepositoryImpl @Inject constructor(
    private val dao: ReadingSessionDao,
) : ReadingSessionRepository {

    override suspend fun record(session: ReadingSession) {
        dao.insert(session.toEntity())
    }

    override fun observeDailyMinutes(daysBack: Int): Flow<List<DailyMinutes>> {
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysBack.toLong())
        return dao.observeDailyMinutes(since).map { it.map { row -> row.toDomain() } }
    }

    override fun observeWeeklyMinutes(): Flow<List<DailyMinutes>> = observeDailyMinutes(7)

    override fun observeTimeByBook(daysBack: Int): Flow<List<BookMinutes>> {
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysBack.toLong())
        return dao.observeTimeByBook(since).map { it.map { row -> row.toDomain() } }
    }

    override fun observeTimeByLang(daysBack: Int): Flow<List<LangMinutes>> {
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysBack.toLong())
        return dao.observeTimeByLang(since).map { it.map { row -> row.toDomain() } }
    }
}
