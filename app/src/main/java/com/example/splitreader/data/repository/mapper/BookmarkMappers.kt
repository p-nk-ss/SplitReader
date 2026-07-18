package com.example.splitreader.data.repository.mapper

import com.example.splitreader.data.local.BookmarkEntity
import com.example.splitreader.domain.model.Bookmark

fun BookmarkEntity.toDomain(): Bookmark =
    Bookmark(id, bookUri, chapterIndex, paragraphIndex, label, createdAt)
