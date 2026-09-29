package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.ReadingPosition

/**
 * Pre-2026-09 progress, stored as a chapter-local LazyColumn item index (docs/adr/0001). Read once
 * per book by MigrateLegacyReadingPositionsUseCase, then gone.
 * TODO(cleanup): delete with that use case once versionCode >= 8 has shipped.
 */
interface LegacyReadingPositionStore {
    fun isReadingPositionMigrated(bookUri: String): Boolean
    /** The pre-migration (chapter, chapter-local item index, pixel offset), or null if none was saved. */
    fun legacyProgress(bookUri: String): LegacyProgress?
    /**
     * In one SharedPreferences edit: write [position] (if non-null), set the migrated flag, and drop
     * every legacy scroll key of [bookUri]. Must complete durably (a synchronous commit, not a
     * fire-and-forget apply) before returning: MigrateLegacyReadingPositionsUseCase's never-convert-twice
     * guarantee depends on this write surviving a process death right after it returns, before the
     * bookmark rewrite runs. Callers must invoke this off the main thread.
     */
    fun completeReadingPositionMigration(bookUri: String, position: ReadingPosition?)
}

data class LegacyProgress(val chapter: Int, val itemIndex: Int, val offset: Int)
