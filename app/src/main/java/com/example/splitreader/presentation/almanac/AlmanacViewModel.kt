package com.example.splitreader.presentation.almanac

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitreader.data.local.BookMinutes
import com.example.splitreader.data.local.DailyMinutes
import com.example.splitreader.data.local.LangMinutes
import com.example.splitreader.domain.repository.ReadingSessionRepository
import com.example.splitreader.domain.repository.SavedWordRepository
import com.example.splitreader.domain.usecase.GetStreakUseCase
import com.example.splitreader.domain.usecase.StreakResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class TimeRange(val label: String, val daysBack: Int) {
    WEEK("Week", 7),
    MONTH("Month", 30),
    YEAR("Year", 365),
    ALL("All time", 3650),
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlmanacViewModel @Inject constructor(
    private val sessionRepository: ReadingSessionRepository,
    private val savedWordRepository: SavedWordRepository,
    private val getStreakUseCase: GetStreakUseCase,
) : ViewModel() {

    val selectedRange = MutableStateFlow(TimeRange.WEEK)

    val streak: StateFlow<StreakResult> = getStreakUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreakResult(0, 0))

    val dailyMinutes: StateFlow<List<DailyMinutes>> = selectedRange
        .flatMapLatest { range -> sessionRepository.observeDailyMinutes(range.daysBack) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rangeMinutes: StateFlow<Int> = selectedRange
        .flatMapLatest { range -> sessionRepository.observeDailyMinutes(range.daysBack) }
        .map { days -> days.sumOf { it.minutes } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val rangePages: StateFlow<Int> = selectedRange
        .flatMapLatest { range -> sessionRepository.observeDailyMinutes(range.daysBack) }
        .map { days -> days.sumOf { it.minutes * 2 } } // rough approx: 2 pages/min
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val rangeWords: StateFlow<Int> = selectedRange
        .flatMapLatest { range ->
            savedWordRepository.observeAll().map { words ->
                val since = System.currentTimeMillis() - range.daysBack * 24L * 60 * 60 * 1000
                words.count { it.savedAt >= since }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val timeByBook: StateFlow<List<BookMinutes>> = selectedRange
        .flatMapLatest { range -> sessionRepository.observeTimeByBook(range.daysBack) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val timeByLang: StateFlow<List<LangMinutes>> = selectedRange
        .flatMapLatest { range -> sessionRepository.observeTimeByLang(range.daysBack) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectRange(range: TimeRange) { selectedRange.value = range }
}
