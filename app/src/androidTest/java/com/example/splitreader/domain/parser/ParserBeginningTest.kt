package com.example.splitreader.domain.parser

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.splitreader.domain.model.Book
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end check of concern #1/#2: parsing a real book file must NOT lose its immediate beginning.
 *
 * The reader auto-strips Project Gutenberg source/license boilerplate ([HtmlChapterExtractor]); this
 * test proves the actual opening of each book survives that pass (and the FB2/MOBI parsers' own
 * section/preamble logic). Real copyright-protected books from `qa_book/` are staged into test assets
 * under `qa/` by the `stageQaFixtures` Gradle task — absent locally, those cases skip. The public-domain
 * Gutenberg EPUB under `qa-pd/` is committed and always runs.
 *
 * Runs on a device/emulator because the parsers need the real Android runtime (XmlPullParser, Base64,
 * ContentResolver, ZipInputStream over a content stream).
 */
@RunWith(AndroidJUnit4::class)
class ParserBeginningTest {

    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Assets live in the *test* APK; parsing uses the app-under-test context (filesDir, covers). */
    private val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    private fun assetExists(path: String): Boolean {
        val dir = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        return runCatching { testContext.assets.list(dir)?.contains(name) == true }.getOrDefault(false)
    }

    private fun parseAsset(assetPath: String, parser: BookParser): Book {
        val bytes = testContext.assets.open(assetPath).use { it.readBytes() }
        val tmp = File(targetContext.cacheDir, "qa_" + assetPath.substringAfterLast('/'))
        tmp.writeBytes(bytes)
        return runBlocking { parser.parse(Uri.fromFile(tmp), targetContext) }
    }

    private fun openingText(book: Book, chapters: Int = 3): String =
        book.chapters.take(chapters).flatMap { it.paragraphs }.joinToString("\n")

    private fun assertBeginningPresent(book: Book, goldenOpening: String) {
        assertTrue("book has no chapters", book.chapters.isNotEmpty())
        assertTrue("first chapter has no paragraphs", book.chapters.first().paragraphs.isNotEmpty())
        assertTrue(
            "the book's opening text was lost — expected to find: \"$goldenOpening\"",
            openingText(book).contains(goldenOpening),
        )
    }

    // ── FB2 books from qa_book/ (skip if not staged locally) ──────────────────

    @Test
    fun fb2_academicLeader_keepsOpening() {
        assumeTrue("qa/academic_leader.fb2 not staged", assetExists("qa/academic_leader.fb2"))
        val book = parseAsset("qa/academic_leader.fb2", Fb2Parser())
        assertBeginningPresent(book, "Гнать пучок музыки обратно на базу")
    }

    @Test
    fun fb2_agrippa_keepsOpening() {
        assumeTrue("qa/agrippa.fb2 not staged", assetExists("qa/agrippa.fb2"))
        val book = parseAsset("qa/agrippa.fb2", Fb2Parser())
        assertBeginningPresent(book, "Они приехали из Уилинга")
    }

    @Test
    fun fb2_cthulhu_keepsOpening() {
        assumeTrue("qa/cthulhu.fb2 not staged", assetExists("qa/cthulhu.fb2"))
        val book = parseAsset("qa/cthulhu.fb2", Fb2Parser())
        assertBeginningPresent(book, "Я пишу в состоянии сильного душевного напряжения")
    }

    @Test
    fun fb2_johnnyMnemonic_keepsOpening() {
        assumeTrue("qa/johnny_mnemonic.fb2 not staged", assetExists("qa/johnny_mnemonic.fb2"))
        val book = parseAsset("qa/johnny_mnemonic.fb2", Fb2Parser())
        assertBeginningPresent(book, "Я сунул пушку в сумку")
    }

    @Test
    fun fb2_bekker_keepsNarrativeOpening() {
        assumeTrue("qa/bekker.fb2 not staged", assetExists("qa/bekker.fb2"))
        val book = parseAsset("qa/bekker.fb2", Fb2Parser())
        // Front matter (copyright/epigraph) precedes the prose, so search a few chapters in.
        assertTrue("book has no chapters", book.chapters.isNotEmpty())
        assertTrue(
            "the narrative opening was lost",
            book.chapters.take(5).flatMap { it.paragraphs }
                .joinToString("\n").contains("Первый Апокалипсис уничтожил"),
        )
    }

    // ── MOBI books from qa_book/ (structural — exact opening not extractable offline) ──

    @Test
    fun mobi_soulHunter_keepsRealBeginning() {
        assumeTrue("qa/soul_hunter.mobi not staged", assetExists("qa/soul_hunter.mobi"))
        assertMobiBeginningIntact("qa/soul_hunter.mobi")
    }

    @Test
    fun mobi_howToTrainDragon_keepsRealBeginning() {
        assumeTrue("qa/how_to_train_dragon.mobi not staged", assetExists("qa/how_to_train_dragon.mobi"))
        assertMobiBeginningIntact("qa/how_to_train_dragon.mobi")
    }

    private fun assertMobiBeginningIntact(assetPath: String) {
        val book = parseAsset(assetPath, MobiParser())
        assertTrue("book has no chapters", book.chapters.isNotEmpty())
        val firstChapterText = book.chapters.first().paragraphs.joinToString("\n")
        assertTrue(
            "first chapter is empty — beginning was trimmed away",
            firstChapterText.trim().length >= 20,
        )
        // The trim tool must not have left only a source/site mention at the start.
        val opening = openingText(book).lowercase()
        listOf("flibusta", "litres", "http://", "https://", "www.").forEach { marker ->
            assertFalse(
                "the book's opening is a source mention (\"$marker\"), real content was lost",
                book.chapters.first().paragraphs.firstOrNull()?.lowercase()?.contains(marker) == true,
            )
        }
        // Surface the actual opening in the test log for manual eyeballing.
        println("[MOBI $assetPath] opening: " + opening.take(160).replace('\n', ' '))
    }

    // ── Public-domain Gutenberg EPUB (committed, always runs) ─────────────────

    @Test
    fun epub_gutenbergAlice_stripsBoilerplateButKeepsOpening() {
        val book = parseAsset("qa-pd/gutenberg-11.epub", EpubParser())
        // The real story opening survives the Project Gutenberg boilerplate strip.
        assertTrue("book has no chapters", book.chapters.isNotEmpty())
        val allText = book.chapters.flatMap { it.paragraphs }.joinToString("\n")
        assertTrue(
            "the story opening was lost",
            allText.contains("Alice was beginning to get very tired"),
        )
        // The license / source boilerplate must be gone from the readable body.
        assertFalse("PG license leaked into the book body", allText.contains("for the use of anyone"))
        assertFalse(allText.contains("START OF THE PROJECT GUTENBERG"))
        assertFalse(allText.contains("END OF THE PROJECT GUTENBERG"))
    }
}
