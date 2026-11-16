package com.example.splitreader.data.local

import android.content.Context
import android.net.Uri
import com.example.splitreader.domain.repository.BookContentKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** [BookContentKeys] over the content resolver: the SHA-256 of the file, streamed on the IO dispatcher. */
@Singleton
class ContentResolverBookContentKeys @Inject constructor(
    @ApplicationContext private val context: Context,
) : BookContentKeys {
    override suspend fun keyOf(uri: String): String? = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    ensureActive()
                    val read = stream.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            } ?: return@withContext null
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // A file that cannot be read is a book without a key, never a failed open.
        }
    }
}
