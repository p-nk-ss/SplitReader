package com.example.splitreader.domain.parser.fb2

import com.example.splitreader.domain.parser.util.BookTooLargeException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Fb2DocumentBuilderTest {

    private fun start(name: String, href: String? = null, id: String? = null) = Fb2Event.Start(name, href, id)
    private fun text(t: String) = Fb2Event.Text(t)
    private fun end(name: String) = Fb2Event.End(name)

    private fun build(vararg events: Fb2Event): Fb2Document {
        val b = Fb2DocumentBuilder()
        events.forEach { b.accept(it) }
        return b.finish()
    }

    /** Wraps a single leaf-section body: <body><section><title>T</title> ...content... </section></body> */
    private fun section(title: String, vararg content: Fb2Event): List<Fb2Event> =
        listOf(start("body"), start("section"), start("title"), start("p"), text(title), end("p"), end("title")) +
            content.toList() + listOf(end("section"), end("body"))

    @Test fun p4_longParagraphIsKept() {
        val long = "x".repeat(6000)
        val doc = build(*section("Ch", start("p"), text(long), end("p")).toTypedArray())
        assertEquals(1, doc.chapters.size)
        assertTrue(doc.chapters[0].paragraphs.any { it.length == 6000 })
    }

    @Test fun p5_verseLinesBecomeSeparateParagraphs() {
        val doc = build(*section("Poem",
            start("poem"), start("stanza"),
            start("v"), text("Line one"), end("v"),
            start("v"), text("Line two"), end("v"),
            end("stanza"), end("poem"),
        ).toTypedArray())
        assertEquals(listOf("Line one", "Line two"), doc.chapters[0].paragraphs)
    }

    @Test fun p5_subtitleBecomesParagraph() {
        val doc = build(*section("Ch",
            start("subtitle"), text("A subtitle"), end("subtitle"),
            start("p"), text("Body"), end("p"),
        ).toTypedArray())
        assertEquals(listOf("A subtitle", "Body"), doc.chapters[0].paragraphs)
    }

    @Test fun p5_citeContentBecomesMainParagraphs() {
        val doc = build(*section("Ch",
            start("cite"), start("p"), text("Quoted line"), end("p"), end("cite"),
            start("p"), text("Body"), end("p"),
        ).toTypedArray())
        assertEquals(listOf("Quoted line", "Body"), doc.chapters[0].paragraphs)
        assertEquals(0, doc.chapters[0].epigraphCount)
    }

    @Test fun nestedSections_leafBecomesChapterWithAncestorPrefix() {
        val doc = build(
            start("body"),
            start("section"), start("title"), text("Part One"), end("title"),
            start("section"), start("title"), text("Ch 1"), end("title"),
            start("p"), text("Prose"), end("p"),
            end("section"),
            end("section"),
            end("body"),
        )
        assertEquals(1, doc.chapters.size)
        assertEquals("Part One · Ch 1", doc.chapters[0].title)
        assertEquals(listOf("Prose"), doc.chapters[0].paragraphs)
    }

    @Test fun preambleEpigraph_prependedToFirstChapter() {
        val doc = build(
            start("body"),
            start("epigraph"), start("p"), text("An epigraph"), end("p"), end("epigraph"),
            start("section"), start("title"), text("Ch 1"), end("title"),
            start("p"), text("Prose"), end("p"),
            end("section"),
            end("body"),
        )
        assertEquals(listOf("An epigraph", "Prose"), doc.chapters[0].paragraphs)
    }

    @Test fun sectionEpigraph_goesToEpigraphParagraphsFirst() {
        val doc = build(*section("Ch",
            start("epigraph"),
            start("p"), text("Epi line"), end("p"),
            start("text-author"), text("Author"), end("text-author"),
            end("epigraph"),
            start("p"), text("Body"), end("p"),
        ).toTypedArray())
        val ch = doc.chapters[0]
        assertEquals(listOf("Epi line", "Author", "Body"), ch.paragraphs)
        assertEquals(2, ch.epigraphCount)
    }

    @Test fun inlineImage_anchorAndBinaryCaptured() {
        // The builder decodes and hands off each <binary>'s bytes as soon as it is parsed (so the
        // raw base64 is never retained for the whole book); "aGVsbG8=" decodes to "hello".
        val written = mutableListOf<Triple<String, ByteArray, Boolean>>()
        val b = Fb2DocumentBuilder(writeBinary = { id, bytes, isCover -> written.add(Triple(id, bytes, isCover)); "saved:$id" })
        listOf(
            start("body"),
            start("section"), start("title"), text("Ch"), end("title"),
            start("p"), text("Before"), end("p"),
            start("image", href = "img1"), end("image"),
            start("p"), text("After"), end("p"),
            end("section"),
            end("body"),
            start("binary", id = "img1"), text("aGVsbG8="), end("binary"),
        ).forEach(b::accept)
        val doc = b.finish()
        val ch = doc.chapters[0]
        // anchor is after the first body paragraph (index 1), no epigraph/preamble shift
        assertEquals(listOf(1 to "img1"), ch.imageRefs)
        assertEquals("saved:img1", doc.binaries["img1"])
        assertEquals(1, written.size)
        assertEquals("img1", written[0].first)
        assertArrayEquals("hello".toByteArray(), written[0].second)
        assertFalse(written[0].third) // not the cover
    }

    @Test fun cover_binaryIdCapturedAndStored() {
        // "Y292ZXI=" decodes to "cover".
        val written = mutableListOf<Triple<String, ByteArray, Boolean>>()
        val b = Fb2DocumentBuilder(writeBinary = { id, bytes, isCover -> written.add(Triple(id, bytes, isCover)); "cover:$id" })
        listOf(
            start("coverpage"), start("image", href = "cover.jpg"), end("image"), end("coverpage"),
            start("body"), start("section"), start("title"), text("Ch"), end("title"),
            start("p"), text("Prose"), end("p"), end("section"), end("body"),
            start("binary", id = "cover.jpg"), text("Y292ZXI="), end("binary"),
        ).forEach(b::accept)
        val doc = b.finish()
        assertEquals("cover.jpg", doc.coverBinaryId)
        assertEquals("cover:cover.jpg", doc.binaries["cover.jpg"])
        assertEquals(1, written.size)
        assertTrue(written[0].third) // is the cover
        assertArrayEquals("cover".toByteArray(), written[0].second)
    }

    @Test fun metadata_titleAuthorAnnotation() {
        val doc = build(
            start("description"),
            start("title-info"),
            start("book-title"), text("My Book"), end("book-title"),
            start("author"),
            start("first-name"), text("Jane"), end("first-name"),
            start("last-name"), text("Doe"), end("last-name"),
            end("author"),
            start("annotation"), start("p"), text("A summary."), end("p"), end("annotation"),
            end("title-info"),
            end("description"),
            *section("Ch", start("p"), text("Prose"), end("p")).toTypedArray(),
        )
        assertEquals("My Book", doc.title)
        assertEquals("Jane Doe", doc.author)
        assertTrue(doc.annotation.contains("A summary."))
    }

    @Test fun emptyInput_producesNoChapters() {
        val doc = build(start("body"), end("body"))
        assertTrue(doc.chapters.isEmpty())
    }

    @Test fun wrappedTitle_singleP_setsTitleWithoutDuplicateParagraph() {
        val doc = build(
            start("body"), start("section"),
            start("title"), start("p"), text("Chapter 1"), end("p"), end("title"),
            start("p"), text("Prose"), end("p"),
            end("section"), end("body"),
        )
        assertEquals("Chapter 1", doc.chapters[0].title)
        assertEquals(listOf("Prose"), doc.chapters[0].paragraphs) // title NOT duplicated into body
    }

    @Test fun epigraphTitleDoesNotClobberSectionTitle() {
        val doc = build(
            start("body"), start("section"),
            start("title"), start("p"), text("Real Title"), end("p"), end("title"),
            start("epigraph"),
            start("title"), start("p"), text("Epigraph Heading"), end("p"), end("title"),
            start("p"), text("Epi line"), end("p"),
            end("epigraph"),
            start("p"), text("Body"), end("p"),
            end("section"), end("body"),
        )
        assertEquals("Real Title", doc.chapters[0].title)         // section title NOT clobbered
        assertTrue(doc.chapters[0].paragraphs.contains("Epigraph Heading")) // epigraph title kept as epigraph para
        assertEquals("Body", doc.chapters[0].paragraphs.last())
    }

    @Test fun wrappedTitle_multiP_capturedNotEmittedAsParagraphs() {
        val doc = build(
            start("body"), start("section"),
            start("title"),
            start("p"), text("Book One"), end("p"),
            start("p"), text("The Journey"), end("p"),
            end("title"),
            start("p"), text("Prose"), end("p"),
            end("section"), end("body"),
        )
        assertEquals(listOf("Prose"), doc.chapters[0].paragraphs) // no spurious title paragraphs
        assertTrue(doc.chapters[0].title.contains("Book One"))
        assertTrue(doc.chapters[0].title.contains("The Journey"))
    }

    // ── Very large books (OOM guard) ──────────────────────────────────────────────────────────

    @Test fun singleHugeParagraph_overBudget_throwsWithoutBuildingTheWholeString() {
        // Whether a parser hands one gigantic <p> over whole (KXmlParser buffers a whole text node)
        // or in pieces, the running total across the whole book catches it -- shown here with many
        // small Text events, so no single one looks large on its own.
        val b = Fb2DocumentBuilder(maxBytes = 1000)
        val events = listOf(start("body"), start("section"), start("title"), start("p"), text("Ch"), end("p"), end("title"), start("p")) +
            List(50) { text("x".repeat(100)) } + listOf(end("p"), end("section"), end("body"))
        assertThrows(BookTooLargeException::class.java) {
            events.forEach { b.accept(it) }
        }
    }

    @Test fun manyModestParagraphs_togetherOverBudget_throws() {
        // Each paragraph alone is tiny; summed across the whole book they exceed the budget -- the
        // budget is per book, not per paragraph, so many medium paragraphs can't add up to an OOM.
        val b = Fb2DocumentBuilder(maxBytes = 1000)
        val paragraphs = (1..50).flatMap { listOf(start("p"), text("y".repeat(50)), end("p")) }
        val events = listOf(start("body"), start("section"), start("title"), start("p"), text("Ch"), end("p"), end("title")) +
            paragraphs + listOf(end("section"), end("body"))
        assertThrows(BookTooLargeException::class.java) {
            events.forEach { b.accept(it) }
        }
    }

    @Test fun budgetIsCountedInBytes_aCharIsTwoBytesInMemory() {
        // The budget is a heap fraction in bytes (effectiveMaxDecompressed); text is held as UTF-16,
        // two bytes per char, so 600 chars (1200 bytes) exceed a 1000-byte budget.
        val b = Fb2DocumentBuilder(1000)
        val events = listOf(start("body"), start("section"), start("p"), text("z".repeat(600)), end("p"), end("section"), end("body"))
        assertThrows(BookTooLargeException::class.java) {
            events.forEach { b.accept(it) }
        }
    }

    @Test fun normalSizedBook_wellUnderBudget_stillParses() {
        // An explicit, generous budget is exercised directly (rather than the default-constructed
        // `build()` helper) to prove normal content is unaffected by the new accounting.
        val b = Fb2DocumentBuilder(maxBytes = 1_000_000)
        section("Ch", start("p"), text("Ordinary prose."), end("p")).forEach { b.accept(it) }
        val result = b.finish()
        assertEquals(listOf("Ordinary prose."), result.chapters[0].paragraphs)
    }

    // ── <binary> images bounded separately from the text budget ──────────────────────────────

    @Test fun imagesTotalingMoreThanTheTextBudget_eachUnderThePerBinaryLimit_open() {
        // Two images, each comfortably under the per-binary cap. Their base64 text (8 bytes *2 =
        // 16 bytes each, 32 total) would bust a 10-byte text budget if it counted there -- it must
        // not: binaries have their own, separate cap.
        val b = Fb2DocumentBuilder(maxBytes = 10, maxBinaryBytes = 1_000_000, writeBinary = { id, _, _ -> "saved:$id" })
        listOf(
            start("body"),
            start("section"), start("title"), text("Ch"), end("title"),
            start("p"), text("Hi"), end("p"),
            start("image", href = "img1"), end("image"),
            start("image", href = "img2"), end("image"),
            end("section"), end("body"),
            start("binary", id = "img1"), text("aGVsbG8="), end("binary"),
            start("binary", id = "img2"), text("aGVsbG8="), end("binary"),
        ).forEach(b::accept)
        val doc = b.finish()
        assertEquals(1, doc.chapters.size)
        assertEquals(mapOf("img1" to "saved:img1", "img2" to "saved:img2"), doc.binaries)
    }

    @Test fun aSingleBinaryOverThePerBinaryLimit_throws() {
        // "aGVsbG8=" is 8 chars = 16 bytes in memory, over a 10-byte per-binary cap -- this is the
        // guard for KXmlParser handing the whole binary text node over as one in-memory event.
        val b = Fb2DocumentBuilder(maxBytes = 1_000_000, maxBinaryBytes = 10)
        val events = listOf(
            start("body"), start("section"), start("title"), text("Ch"), end("title"),
            start("image", href = "img1"), end("image"),
            end("section"), end("body"),
            start("binary", id = "img1"), text("aGVsbG8="), end("binary"),
        )
        assertThrows(BookTooLargeException::class.java) {
            events.forEach { b.accept(it) }
        }
    }

    @Test fun textOverItsOwnBudget_stillThrows_evenWithAnImagePresent() {
        // A generous per-binary cap does not loosen the text budget -- they are independent.
        val b = Fb2DocumentBuilder(maxBytes = 10, maxBinaryBytes = 1_000_000)
        val events = listOf(
            start("body"), start("section"), start("title"), text("Ch"), end("title"),
            start("p"), text("This paragraph text is definitely longer than ten bytes"), end("p"),
            start("image", href = "img1"), end("image"),
            end("section"), end("body"),
            start("binary", id = "img1"), text("aGVsbG8="), end("binary"),
        )
        assertThrows(BookTooLargeException::class.java) {
            events.forEach { b.accept(it) }
        }
    }
}
