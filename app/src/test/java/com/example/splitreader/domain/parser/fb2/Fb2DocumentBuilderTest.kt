package com.example.splitreader.domain.parser.fb2

import org.junit.Assert.assertEquals
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
        val doc = build(
            start("body"),
            start("section"), start("title"), text("Ch"), end("title"),
            start("p"), text("Before"), end("p"),
            start("image", href = "img1"), end("image"),
            start("p"), text("After"), end("p"),
            end("section"),
            end("body"),
            start("binary", id = "img1"), text("BASE64DATA"), end("binary"),
        )
        val ch = doc.chapters[0]
        // anchor is after the first body paragraph (index 1), no epigraph/preamble shift
        assertEquals(listOf(1 to "img1"), ch.imageRefs)
        assertEquals("BASE64DATA", doc.binaries["img1"])
    }

    @Test fun cover_binaryIdCapturedAndStored() {
        val doc = build(
            start("coverpage"), start("image", href = "cover.jpg"), end("image"), end("coverpage"),
            start("body"), start("section"), start("title"), text("Ch"), end("title"),
            start("p"), text("Prose"), end("p"), end("section"), end("body"),
            start("binary", id = "cover.jpg"), text("COVER64"), end("binary"),
        )
        assertEquals("cover.jpg", doc.coverBinaryId)
        assertEquals("COVER64", doc.binaries["cover.jpg"])
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
}
