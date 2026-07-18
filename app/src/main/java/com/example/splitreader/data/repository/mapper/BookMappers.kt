package com.example.splitreader.data.repository.mapper

import com.example.splitreader.data.local.BookEntity
import com.example.splitreader.domain.model.LibraryBook

fun BookEntity.toDomain(): LibraryBook = LibraryBook(
    uri = uri,
    title = title,
    author = author,
    coverPath = coverPath,
    lastOpenedAt = lastOpenedAt,
    chapterCount = chapterCount,
    synopsis = synopsis,
)
