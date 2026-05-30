package com.example.splitreader.presentation.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitreader.data.local.SavedWordEntity
import com.example.splitreader.data.local.TextToSpeechManager
import com.example.splitreader.domain.repository.SavedWordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LangFilter {
    data object All : LangFilter
    data class Lang(val code: String) : LangFilter
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VocabulaireViewModel @Inject constructor(
    private val repository: SavedWordRepository,
    private val textToSpeechManager: TextToSpeechManager,
) : ViewModel() {

    val langFilter = MutableStateFlow<LangFilter>(LangFilter.All)
    val query = MutableStateFlow("")

    val words: StateFlow<List<SavedWordEntity>> = combine(langFilter, query) { filter, q ->
        filter to q
    }.flatMapLatest { (filter, q) ->
        when {
            q.isNotBlank() -> repository.search(q)
            filter is LangFilter.Lang -> repository.observeByLang(filter.code)
            else -> repository.observeAll()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedWord = MutableStateFlow<SavedWordEntity?>(null)

    fun select(word: SavedWordEntity) { selectedWord.value = word }
    fun setFilter(filter: LangFilter) { langFilter.value = filter }
    fun setQuery(q: String) { query.value = q }

    fun updateNote(word: SavedWordEntity, note: String) {
        viewModelScope.launch {
            val updated = word.copy(note = note)
            repository.update(updated)
            // Keep the detail pane (rendered from selectedWord) in sync with the saved note
            if (selectedWord.value?.id == word.id) selectedWord.value = updated
        }
    }

    fun delete(word: SavedWordEntity) {
        viewModelScope.launch {
            repository.delete(word)
            if (selectedWord.value?.id == word.id) selectedWord.value = null
        }
    }

    fun speak(text: String, langCode: String) = textToSpeechManager.speak(text, langCode)
}
