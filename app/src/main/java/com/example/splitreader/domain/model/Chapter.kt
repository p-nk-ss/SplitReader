package com.example.splitreader.domain.model

data class Chapter(
    val index: Int,
    val title: String,
    val paragraphs: List<String>,
    val epigraphCount: Int = 0,
)
