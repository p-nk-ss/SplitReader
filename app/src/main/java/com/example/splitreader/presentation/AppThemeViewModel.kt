package com.example.splitreader.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitreader.domain.model.OrientationLock
import com.example.splitreader.domain.repository.ReadingPreferences
import com.example.splitreader.domain.model.ReaderThemeKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Surfaces the persisted reader-theme selection at app scope so the entire app
 * (nav rail, Library, Almanac, Words) follows the same light/dark palette as the
 * reading pane. [ReadingPreferences.appearance] is the single source of truth; both the
 * in-reader theme picker and Settings write it.
 *
 * Both values are projections of [ReadingPreferences.appearance]: a typography change (font size,
 * spacing…) never re-emits the theme or the orientation. StateFlow conflation already guarantees
 * that; `distinctUntilChanged` states the intent and drops the no-op upstream values early.
 */
@HiltViewModel
class AppThemeViewModel @Inject constructor(
    progressManager: ReadingPreferences,
) : ViewModel() {

    val themeKey: StateFlow<ReaderThemeKey> = progressManager.appearance
        .map { it.theme }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = progressManager.appearance.value.theme,
        )

    /** App-wide screen-orientation policy, applied by [MainActivity] to requestedOrientation. */
    val orientationLock: StateFlow<OrientationLock> = progressManager.appearance
        .map { it.orientationLock }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = progressManager.appearance.value.orientationLock,
        )
}
