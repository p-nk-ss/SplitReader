package com.example.splitreader.presentation.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitreader.domain.model.UpdateState
import com.example.splitreader.domain.repository.AppUpdates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The update banner (Home) and the About row (Settings) over the [AppUpdates] port. */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val appUpdates: AppUpdates,
) : ViewModel() {
    val state: StateFlow<UpdateState> = appUpdates.state

    fun update() {
        viewModelScope.launch { appUpdates.startUpdate() }
    }

    fun restart() = appUpdates.completeUpdate()
    fun notNow() = appUpdates.snooze()
}
