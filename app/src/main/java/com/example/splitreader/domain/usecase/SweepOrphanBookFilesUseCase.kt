package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.repository.BookFiles
import com.example.splitreader.domain.repository.BookLibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Removes covers and pictures in app storage that belong to no book the library shows (see
 * [BookFiles.sweepOrphans]). A book leaves such files behind when it is deleted while a parse of it
 * is still running -- [DeleteBookUseCase] deletes its files, then the parse writes them anew -- or
 * when it was parsed and then not added (the free limit).
 *
 * A removed book (deleted, reading data kept) is not in the library: its files went when it was
 * removed, so whatever carries its id now is an orphan too.
 *
 * Run once per app start, off the main thread. A parse may already be running (a restored reader,
 * a picker result): it is safe because a book the library shows is never touched and a book being
 * imported has fresh files. The
 * orphans of the last run are an hour old by the next one or the one after, and a delete -- the
 * other candidate -- stays as fast as it is and is not where most of them come from. Best-effort:
 * a start that cannot read the library or the folders sweeps nothing, and the next start tries again.
 */
class SweepOrphanBookFilesUseCase @Inject constructor(
    private val library: BookLibraryRepository,
    private val files: BookFiles,
) {
    suspend operator fun invoke() {
        try {
            files.sweepOrphans(library.getAllBooks().first().map { it.uri })
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Orphans cost disk space only.
        }
    }
}
