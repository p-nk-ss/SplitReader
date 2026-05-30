package com.example.splitreader.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitreader.data.local.ReadingProgressManager
import com.example.splitreader.presentation.theme.ReaderThemeKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Surfaces the persisted reader-theme selection at app scope so the entire app
 * (nav rail, Library, Almanac, Words) follows the same light/dark palette as the
 * reading pane. The in-reader theme picker remains the single source of truth.
 */
@HiltViewModel
class AppThemeViewModel @Inject constructor(
    progressManager: ReadingProgressManager,
) : ViewModel() {

    val themeKey: StateFlow<ReaderThemeKey> = progressManager.readerThemeName
        .map(::themeKeyFromName)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = themeKeyFromName(progressManager.getReaderThemeName()),
        )

    private companion object {
        fun themeKeyFromName(name: String): ReaderThemeKey = when (name) {
            "DEFAULT" -> ReaderThemeKey.PAPER // migrate old persisted value
            else -> ReaderThemeKey.entries.find { it.name == name } ?: ReaderThemeKey.PAPER
        }
    }
}
