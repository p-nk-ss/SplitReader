package com.example.splitreader.domain.usecase

import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.ReadingPosition
import com.example.splitreader.domain.repository.BookmarkRepository
import com.example.splitreader.domain.repository.LegacyReadingPositionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One-off, per book, on first open: converts progress and bookmarks saved as chapter-local
 * LazyColumn item indices into Reading positions (docs/adr/0001).
 *
 * Order matters. Prefs (position + migrated flag) are written first, bookmarks second. If the
 * process dies between them, bookmarks stay in legacy coordinates (the old, off-by-one behaviour).
 * They are never converted twice, which would shift them again.
 *
 * Conversion uses the *current* `showIllustrations`, not whatever was in effect when the
 * position/bookmark was saved, so a user who toggled illustrations after their last save gets
 * positions off by the number of illustrations above them (accepted, see docs/adr/0001).
 *
 * A @Singleton whose whole run, flag check included, is serialised by one Mutex: a recreated
 * ReaderScreen re-runs loadBook on the same ViewModel while the first run is still going (on
 * Dispatchers.IO), and two runs that both pass the flag check would convert bookmarks twice.
 *
 * TODO(cleanup): delete with LegacyReadingPositionStore once versionCode >= 8 has shipped.
 */
@Singleton
class MigrateLegacyReadingPositionsUseCase @Inject constructor(
    private val legacy: LegacyReadingPositionStore,
    private val bookmarks: BookmarkRepository,
) {
    private val mutex = Mutex()

    suspend operator fun invoke(book: Book, showIllustrations: Boolean) = mutex.withLock { migrate(book, showIllustrations) }

    private suspend fun migrate(book: Book, showIllustrations: Boolean) {
        val uri = book.filePath
        if (legacy.isReadingPositionMigrated(uri)) return

        val position = legacy.legacyProgress(uri)?.let { lp ->
            book.chapters.getOrNull(lp.chapter)
                ?.let { legacyItemToPosition(it, lp.chapter, lp.itemIndex, lp.offset, showIllustrations) }
        }
        legacy.completeReadingPositionMigration(uri, position)

        val old = bookmarks.listForBook(uri)
        if (old.isEmpty()) return
        val converted = old
            .map { bm ->
                val chapter = book.chapters.getOrNull(bm.chapterIndex) ?: return@map bm
                bm.copy(
                    paragraphIndex = legacyItemToPosition(chapter, bm.chapterIndex, bm.paragraphIndex, 0, showIllustrations).paragraph,
                )
            }
            .groupBy { it.chapterIndex to it.paragraphIndex }
            .values.map { same -> same.minBy { it.createdAt } }
            .sortedWith(compareBy({ it.chapterIndex }, { it.paragraphIndex }))
        if (converted == old) return // ids are kept by copy(), so equal means nothing moved
        bookmarks.replaceForBook(uri, converted)
    }

    companion object {
        /**
         * FROZEN copy of the pre-2026-09 per-chapter item layout: masthead, then for each paragraph
         * the illustrations anchored to it followed by the paragraph, then trailing illustrations.
         * Do not replace with BookItemIndex. The live layout may change, but saved data was
         * written against this one.
         */
        internal fun legacyItemToPosition(
            chapter: Chapter,
            chapterIndex: Int,
            itemIndex: Int,
            offset: Int,
            showIllustrations: Boolean,
        ): ReadingPosition {
            if (chapter.paragraphs.isEmpty()) return ReadingPosition(chapterIndex, 0, 0)
            val images = if (showIllustrations) chapter.images else emptyList()
            val layout: List<Int?> = buildList {
                add(null) // masthead
                chapter.paragraphs.indices.forEach { idx ->
                    images.forEach { if (it.anchorParagraph == idx) add(null) }
                    add(idx)
                }
                images.forEach { if (it.anchorParagraph >= chapter.paragraphs.size) add(null) }
            }
            val i = itemIndex.coerceIn(0, layout.lastIndex)
            layout[i]?.let { return ReadingPosition(chapterIndex, it, offset) }
            val below = (i..layout.lastIndex).firstNotNullOfOrNull { layout[it] }
            return ReadingPosition(chapterIndex, below ?: chapter.paragraphs.lastIndex, 0)
        }
    }
}
