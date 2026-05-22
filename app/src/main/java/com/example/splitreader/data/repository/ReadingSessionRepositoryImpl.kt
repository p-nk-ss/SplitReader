package com.example.splitreader.data.repository

import com.example.splitreader.data.local.BookMinutes
import com.example.splitreader.data.local.DailyMinutes
import com.example.splitreader.data.local.LangMinutes
import com.example.splitreader.data.local.ReadingSessionDao
import com.example.splitreader.data.local.ReadingSessionEntity
import com.example.splitreader.domain.repository.ReadingSessionRepository
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingSessionRepositoryImpl @Inject constructor(
    private val dao: ReadingSessionDao,
) : ReadingSessionRepository {

    override suspend fun record(session: ReadingSessionEntity) {
        dao.insert(session)
    }

    override fun observeDailyMinutes(daysBack: Int): Flow<List<DailyMinutes>> {
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysBack.toLong())
        return dao.observeDailyMinutes(since)
    }

    override fun observeWeeklyMinutes(): Flow<List<DailyMinutes>> = observeDailyMinutes(7)

    override fun observeTimeByBook(daysBack: Int): Flow<List<BookMinutes>> {
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysBack.toLong())
        return dao.observeTimeByBook(since)
    }

    override fun observeTimeByLang(daysBack: Int): Flow<List<LangMinutes>> {
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysBack.toLong())
        return dao.observeTimeByLang(since)
    }
}
