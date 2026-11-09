package com.example.splitreader.domain.parser.util

import java.io.File

/**
 * Deletes [prefix]-named subdirectories of [cacheDir] older than [maxAgeMillis] -- scratch folders
 * a parse that died mid-import (process death, e.g. mid-EPUB-extraction) never got to remove in its
 * own `finally`. Only directories (not files) whose name starts with [prefix] are candidates; [keep]
 * (the calling parse's own, about-to-be-used folder) is never touched, however old it looks, so a
 * sweep can never race a concurrent parse. Best-effort: a directory that can't be listed or deleted
 * is silently left alone.
 */
fun sweepStaleSpillDirs(
    cacheDir: File,
    prefix: String,
    maxAgeMillis: Long,
    keep: File? = null,
    now: Long = System.currentTimeMillis(),
) {
    val children = try { cacheDir.listFiles() } catch (_: Exception) { null } ?: return
    for (dir in children) {
        if (dir == keep) continue
        if (!dir.isDirectory || !dir.name.startsWith(prefix)) continue
        if (now - dir.lastModified() > maxAgeMillis) {
            try { dir.deleteRecursively() } catch (_: Exception) { /* best-effort */ }
        }
    }
}
