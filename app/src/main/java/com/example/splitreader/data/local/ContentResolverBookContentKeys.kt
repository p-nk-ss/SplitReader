package com.example.splitreader.data.local

import android.content.Context
import com.example.splitreader.domain.repository.BookContentKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** SKELETON */
@Singleton
class ContentResolverBookContentKeys @Inject constructor(
    @ApplicationContext private val context: Context,
) : BookContentKeys {
    override suspend fun keyOf(uri: String): String? = null
}
