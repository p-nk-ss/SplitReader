package com.example.splitreader.domain.parser

import android.content.Context
import android.net.Uri
import com.example.splitreader.domain.model.Book

interface BookParser {
    suspend fun parse(uri: Uri, context: Context): Book
}
