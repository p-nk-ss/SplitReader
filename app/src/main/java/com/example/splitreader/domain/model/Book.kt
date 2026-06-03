package com.example.splitreader.domain.model

data class Book(
    val title: String,
    val author: String,
    val chapters: List<Chapter>,
    val filePath: String,
    val coverPath: String? = null,
    val synopsis: String? = null,
)
