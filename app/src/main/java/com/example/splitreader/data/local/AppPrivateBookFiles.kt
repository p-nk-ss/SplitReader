package com.example.splitreader.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.splitreader.domain.parser.util.StagedFiles
import com.example.splitreader.domain.parser.util.stableId
import com.example.splitreader.domain.repository.BookFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BookFiles] over the app's `filesDir` and the persisted URI grants.
 *
 * File names are the parsers' and downloaders' own: a cover is `covers/<stableId(uri)>.<ext>`, a
 * picture `images/<stableId(uri)>_<name>.<ext>`, each possibly with a `.part-<UUID>` temp next to
 * it (StagedFiles; a temp younger than the sweep age is left to its parse or the sweep); a catalog book is `catalog/<source>/<id>.epub` and a Drive book
 * `drive/<fileId>[.<ext>]`, and for those two the book's uri IS the file.
 */
@Singleton
class AppPrivateBookFiles @Inject constructor(
    @ApplicationContext private val context: Context,
) : BookFiles {

    override suspend fun deleteFor(bookUri: String): Unit = withContext(Dispatchers.IO) {
        // stableId is 16 hex chars, so "<id>." / "<id>_" can never match another book's files.
        val id = stableId(bookUri)
        deleteChildren("covers") { it.startsWith("$id.") }
        deleteChildren("images") { it.startsWith("${id}_") }

        val uri = try { Uri.parse(bookUri) } catch (_: Exception) { null } ?: return@withContext
        when (uri.scheme) {
            "file" -> uri.path?.let { deleteDownload(File(it)) }
            "content" -> releaseReadGrant(uri)
        }
    }

    private fun deleteChildren(dirName: String, matches: (name: String) -> Boolean) {
        val children = try { File(context.filesDir, dirName).listFiles() } catch (_: Exception) { null } ?: return
        for (f in children) {
            try {
                // A fresh `.part-<UUID>` may belong to a parse of this book that is still running
                // (opened, then deleted elsewhere); the parse removes it itself, or the sweep does later.
                if (matches(f.name) && f.isFile && !StagedFiles.isLiveTemp(f)) f.delete()
            } catch (_: Exception) { /* best-effort */ }
        }
    }

    /**
     * Deletes [file] only when it really is a file inside one of the app's own download folders
     * (resolved path, so `catalog/../x` does not count). A `file://` book anywhere else is the
     * reader's own file and stays.
     */
    private fun deleteDownload(file: File) {
        try {
            val target = file.canonicalFile
            val owned = DOWNLOAD_DIRS.any { dir ->
                target.path.startsWith(File(context.filesDir, dir).canonicalPath + File.separator)
            }
            if (owned && target.isFile) target.delete()
        } catch (_: Exception) { /* best-effort */ }
    }

    /** The grant HomeViewModel.openBook took for a picked file; without the book it is only a leak
     *  (the system caps persisted grants per app). The file itself is the reader's and stays. */
    private fun releaseReadGrant(uri: Uri) {
        try {
            val resolver = context.contentResolver
            if (resolver.persistedUriPermissions.any { it.uri == uri }) {
                resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) { /* no grant, or the provider is gone */ }
    }

    private companion object {
        val DOWNLOAD_DIRS = listOf("catalog", "drive")
    }
}
