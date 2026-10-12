package com.example.splitreader.presentation.reader

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.domain.usecase.TranslateTextUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Translates the reader's word/sentence selection. One request per tap and one per handle release
 * (never per drag step); results are applied only to the selection that asked for them.
 * Main-thread API (UI callbacks); one Job + a request number, no actor needed.
 */
class SelectionTranslator(
    private val scope: CoroutineScope,
    private val translateText: TranslateTextUseCase,
) {
    private val _state = MutableStateFlow<WordSelection?>(null)
    val state: StateFlow<WordSelection?> = _state.asStateFlow()

    private var job: Job? = null
    private var request = 0
    private var source = Language.ENGLISH
    private var target = Language.ENGLISH
    private var provider = TranslationProvider.MLKIT

    /**
     * Selects [word] and translates it at once. [source]/[target]/[provider] are remembered for the
     * next [dragEnd]. [type] is the caller's: a tapped selection is a WORD regardless of its text.
     */
    fun select(
        word: String,
        chapter: Int,
        paragraph: Int,
        start: Int,
        end: Int,
        source: Language,
        target: Language,
        provider: TranslationProvider,
        type: SelectionType = SelectionType.WORD,
    ) {
        this.source = source
        this.target = target
        this.provider = provider
        val sel = WordSelection(word, chapter, paragraph, start, end, type, SelectionTranslation.Translating())
        _state.value = sel
        launch(sel)
    }

    /** A handle moved: cancels any request and shows the new range as [SelectionTranslation.Pending]. */
    fun dragTo(text: String, start: Int, end: Int, type: SelectionType) {
        val cur = _state.value ?: return
        job?.cancel()
        request++
        _state.value = cur.copy(
            word = text,
            startChar = start,
            endChar = end,
            selectionType = type,
            translation = SelectionTranslation.Pending,
        )
    }

    /**
     * The handle was released: translates the range once. Only a [SelectionTranslation.Pending]
     * selection is translated — [dragTo] is the only way into Pending, so a release with no drag
     * since the last result requests nothing.
     */
    fun dragEnd() {
        val cur = _state.value ?: return
        if (cur.translation !is SelectionTranslation.Pending) return
        val sel = cur.copy(translation = SelectionTranslation.Translating())
        _state.value = sel
        launch(sel)
    }

    /** Cancels any request and drops the selection. */
    fun clear() {
        job?.cancel()
        request++
        _state.value = null
    }

    private fun launch(sel: WordSelection) {
        job?.cancel()
        val my = ++request
        val src = source
        val tgt = target
        val prov = provider
        job = scope.launch {
            var notice: TranslationState.FallbackNotice? = null
            var done = false
            translateText(listOf(sel.word), src, tgt, prov, 0, 0).collect { s ->
                when (s) {
                    // A finished download (1f) means the translation itself is now running: show
                    // "Translating…", not a "100%" that would sit there until the result arrives.
                    is TranslationState.DownloadingModel ->
                        apply(my, sel, SelectionTranslation.Translating(s.progress?.takeIf { it < 1f }))
                    is TranslationState.FallbackNotice -> notice = s
                    is TranslationState.Partial -> {
                        done = true
                        apply(my, sel, SelectionTranslation.Ready(s.text, notice))
                    }
                    is TranslationState.Error -> if (!done) apply(my, sel, SelectionTranslation.Failed(s.short))
                    else -> Unit
                }
            }
        }
    }

    /** Applies [t] only if this is still the current request for the same selection. */
    private fun apply(my: Int, sel: WordSelection, t: SelectionTranslation) {
        val cur = _state.value ?: return
        if (my != request) return
        if (cur.chapterIndex != sel.chapterIndex || cur.paragraphIndex != sel.paragraphIndex ||
            cur.startChar != sel.startChar || cur.endChar != sel.endChar
        ) return
        _state.value = cur.copy(translation = t)
    }
}
