package com.example.splitreader.domain.model

sealed interface ParseResult {
    data object Idle : ParseResult
    data object Loading : ParseResult
    data class Success(val book: Book) : ParseResult
    data class Error(val message: String) : ParseResult
}
