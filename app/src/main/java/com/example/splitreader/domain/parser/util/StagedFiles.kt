package com.example.splitreader.domain.parser.util

import com.example.splitreader.domain.CrashReporter
import com.example.splitreader.domain.model.Book
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * The cover and pictures a parse writes, held back until the whole parse has succeeded.
 *
 * Books are re-parsed on every open, and a parse's final paths (`filesDir/covers/<stableId>.<ext>`,
 * `filesDir/images/<bookHash>_<name>.<ext>`) are deterministic per book URI -- the SAME files an
 * owned book's cover and chapter images are. So each file is written to a temp file next to its
 * target ([stage]); [commit] moves them all over their targets once the parse has succeeded, and
 * [rollback] deletes them when it fails or is cancelled, leaving the owned files untouched.
 */
class StagedFiles(
    /** Name of the temp file a picture is staged in, next to its final [File]; unique per parse. */
    private val tempNameFor: (target: File) -> String = DEFAULT_TEMP_NAME,
) {
    private class Staged(val temp: File, val target: File)

    private val staged = mutableListOf<Staged>()

    private var lost = 0
    private var firstLoss: Throwable? = null

    /**
     * A cover or picture that could not be saved. The book still opens, without it -- which is why
     * nobody would ever hear of it: [reportLost] says so once per parse.
     */
    fun noteLost(cause: Throwable) {
        lost++
        if (firstLoss == null) firstLoss = cause
    }

    /**
     * Records the pictures lost so far as ONE non-fatal ("3 pictures of an EPUB book could not be
     * saved", with the first cause), not one per picture: a full disk loses every picture of the book.
     * [book] is the format with its article ("an EPUB"). Nothing lost, nothing recorded.
     */
    fun reportLost(reporter: CrashReporter, book: String) {
        val cause = firstLoss ?: return
        val count = lost
        lost = 0
        firstLoss = null
        try {
            reporter.recordNonFatal(cause, "$count picture${if (count == 1) "" else "s"} of $book book could not be saved")
        } catch (_: Exception) { /* reporting must never fail a parse that succeeded */ }
    }

    /**
     * Writes [target]'s new content to a temp file next to it via [write] and stages it. Returns
     * true when [target]'s path may be shown by the book: the new content is staged, or the write
     * failed (e.g. disk nearly full) but the owned file from an earlier parse is still there, intact
     * -- a re-parse that cannot rewrite a picture must not take it away for the session.
     *
     * A failed write is never staged (it can never be moved over the owned file): its temp is
     * deleted and the loss is noted either way. False only when there is no file to show at all.
     */
    fun stage(target: File, write: (temp: File) -> Unit): Boolean {
        var temp: File? = null
        return try {
            temp = File(target.parentFile, tempNameFor(target))
            write(temp)
            staged.add(Staged(temp, target))
            true
        } catch (e: Exception) {
            try { temp?.delete() } catch (_: Exception) { /* best-effort */ }
            noteLost(e)
            isFile(target)
        }
    }

    /**
     * Moves every staged file over its target (atomic rename, replacing the old file), in staging order.
     *
     * One file that cannot be moved never fails the parse (it would fail on every open of the book):
     * its temp is deleted, the loss is noted ([reportLost]) and the rest are still moved. Returns the
     * final paths that hold NO file afterwards -- a book must not reference those. A target that was
     * already there when its move failed is still the owned, valid picture and is not returned.
     */
    fun commit(): Set<String> {
        val failed = mutableListOf<File>()
        for (s in staged) {
            try {
                try {
                    Files.move(s.temp.toPath(), s.target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(s.temp.toPath(), s.target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: Exception) {
                try { s.temp.delete() } catch (_: Exception) { /* best-effort */ }
                noteLost(e)
                failed += s.target
            }
        }
        staged.clear()
        // Looked at only now: the same target may have been staged twice, and a later move may have filled it.
        return failed.filterNot { isFile(it) }.mapTo(LinkedHashSet()) { it.absolutePath }
    }

    /**
     * [commit], for the parsers: [book] was built with the FINAL paths before anything was moved, so
     * the cover and the pictures whose files did not make it are taken out of it here. Text, chapters
     * and paragraph indices are never changed.
     */
    fun commit(book: Book): Book {
        val missing = commit()
        if (missing.isEmpty()) return book
        return book.copy(
            coverPath = book.coverPath?.takeUnless { it in missing },
            chapters = book.chapters.map { ch ->
                if (ch.images.none { it.path in missing }) ch else ch.copy(images = ch.images.filterNot { it.path in missing })
            },
        )
    }

    private fun isFile(f: File): Boolean = try { f.isFile } catch (_: Exception) { false }

    /** Best-effort delete of this parse's temp files (never a target). */
    fun rollback() {
        for (s in staged) {
            try { s.temp.delete() } catch (_: Exception) { /* best-effort */ }
        }
        staged.clear()
    }

    companion object {
        /** Marks a staged file's temp name: `<final name>.part-<UUID>`. */
        private const val TEMP_MARKER = ".part-"
        const val STALE_TEMP_MILLIS = 60 * 60 * 1000L

        val DEFAULT_TEMP_NAME: (File) -> String = { target -> "${target.name}$TEMP_MARKER${UUID.randomUUID()}" }

        /**
         * Matches only a real temp name's suffix: `.part-<UUID>` (the exact shape [DEFAULT_TEMP_NAME]
         * produces), anchored at the end of the name. A final picture file whose name happens to
         * contain the literal substring ".part-" (e.g. an fb2 binary id "pic.part-1") must NOT match.
         */
        private val TEMP_NAME_PATTERN = Regex("\\.part-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        /**
         * Deletes `*.part-<UUID>` temp files in `filesDir/covers` and `filesDir/images` older than
         * [STALE_TEMP_MILLIS] -- left by a parse the process died in. A running parse's temps are
         * fresh, so they are never touched. Plain files only; a symbolic link is left alone. Best-effort.
         */
        fun sweepStale(filesDir: File, now: Long = System.currentTimeMillis()) {
            for (dirName in listOf("covers", "images")) {
                val children = try { File(filesDir, dirName).listFiles() } catch (_: Exception) { null } ?: continue
                for (f in children) {
                    if (!TEMP_NAME_PATTERN.containsMatchIn(f.name)) continue
                    try {
                        if (Files.isSymbolicLink(f.toPath()) || !f.isFile) continue
                        if (now - f.lastModified() > STALE_TEMP_MILLIS) f.delete()
                    } catch (_: Exception) { /* best-effort */ }
                }
            }
        }
    }
}
