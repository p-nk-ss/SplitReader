package com.example.splitreader.data.repository.mapper

import com.example.splitreader.data.local.BookMinutesRow
import com.example.splitreader.data.local.DailyMinutesRow
import com.example.splitreader.data.local.LangMinutesRow
import com.example.splitreader.data.local.ReadingSessionEntity
import com.example.splitreader.domain.model.ReadingSession
import com.example.splitreader.domain.model.stats.BookMinutes
import com.example.splitreader.domain.model.stats.DailyMinutes
import com.example.splitreader.domain.model.stats.LangMinutes

fun ReadingSessionEntity.toDomain(): ReadingSession =
    ReadingSession(bookUri, bookTitle, sourceLang, startedAt, endedAt, durationSeconds, paragraphsRead, id)

fun ReadingSession.toEntity(): ReadingSessionEntity =
    ReadingSessionEntity(id, bookUri, bookTitle, sourceLang, startedAt, endedAt, durationSeconds, paragraphsRead)

fun DailyMinutesRow.toDomain(): DailyMinutes = DailyMinutes(day, minutes)
fun BookMinutesRow.toDomain(): BookMinutes = BookMinutes(title, minutes)
fun LangMinutesRow.toDomain(): LangMinutes = LangMinutes(lang, minutes)
