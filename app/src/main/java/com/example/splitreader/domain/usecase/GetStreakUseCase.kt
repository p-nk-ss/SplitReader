package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.repository.ReadingSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class StreakResult(val current: Int, val longest: Int)

class GetStreakUseCase @Inject constructor(
    private val repository: ReadingSessionRepository,
) {
    operator fun invoke(): Flow<StreakResult> =
        repository.observeDailyMinutes(daysBack = 365).map { days ->
            val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val activeDays = days.filter { it.minutes > 0 }.map { LocalDate.parse(it.day, fmt) }.toSet()

            var current = 0
            var day = LocalDate.now()
            while (activeDays.contains(day)) {
                current++
                day = day.minusDays(1)
            }

            var longest = 0
            var run = 0
            val sorted = activeDays.sorted()
            var prev: LocalDate? = null
            for (d in sorted) {
                run = if (prev == null || d == prev!!.plusDays(1)) run + 1 else 1
                if (run > longest) longest = run
                prev = d
            }

            StreakResult(current, longest)
        }
}
