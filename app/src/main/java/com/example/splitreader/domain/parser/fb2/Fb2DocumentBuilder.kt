package com.example.splitreader.domain.parser.fb2

import com.example.splitreader.domain.parser.util.BookTooLargeException
import com.example.splitreader.domain.parser.util.effectiveMaxDecompressed

/**
 * Pure FB2 event -> [Fb2Document] builder. Replaces Fb2Parser's ~20 boolean flags with an element-name
 * stack (context = "am I inside <epigraph>/<coverpage>/<binary>…") plus a section-frame stack (each leaf
 * <section> becomes a chapter). No Android dependencies (only java.io/java.util), so it is
 * unit-testable on the JVM.
 *
 * [maxBytes] bounds the total TEXT this builder will ever accumulate — summed across every
 * paragraph, title and the annotation, for the whole book, not per element. It is counted in BYTES
 * of heap, like the rest of the budget ([effectiveMaxDecompressed] is a fraction of the heap): text
 * is held as UTF-16, so each char counts 2 bytes. Android's XmlPullParser (KXmlParser) hands over a
 * whole text node as one [Fb2Event.Text] (it buffers the node itself), so one gigantic `<p>` is one
 * big event and many medium paragraphs are many events; without this running total the builders and
 * their final `toString()` copies would add up past a constrained heap. Throws [BookTooLargeException]
 * the moment the total is crossed, before appending the text that crossed it. (The parser's own
 * buffer for a single huge text node is outside this bound; the file size bounds that.)
 *
 * `<binary>` images (base64) do NOT count against [maxBytes]: an illustrated FB2 can carry many
 * megabytes of embedded images, and counting them against the text total refused books v25 opened.
 * This applies to EVERY `<binary>`, wanted or not (unreferenced, or referenced from outside any
 * section -- body-level, annotation, src-title-info coverpage): a `<binary>` end tag always clears
 * [insideBinary] of the per-binary cap, never adding its text to [totalBytes], even when its id is
 * never looked up. Each binary has its own cap, [maxBinaryBytes] (also a heap fraction, like
 * [maxBytes]'s default, but tighter -- see [com.example.splitreader.domain.parser.util.effectiveMaxBinaryBytes])
 * -- the guard for the same KXmlParser-hands-the-whole-node-over behaviour, now per image instead of
 * per book. Only a WANTED binary (the cover and/or referenced by an `<image>`) accumulates its
 * base64 at all; as soon as its `</binary>` end tag is seen, that text is decoded and handed to
 * [writeBinary] (id, decoded bytes, whether this call is for the cover role) so the Android side can
 * write it straight to a file. When one id is both the cover and an inline reference, [writeBinary]
 * is called once per role, so the cover and the inline image land in two different files; only
 * [writeBinary]'s returned key (a saved path, or null on failure) is kept here, in [coverPath] /
 * [Fb2Document.binaries] -- never the base64 or decoded bytes for the whole book at once.
 */
class Fb2DocumentBuilder(
    private val maxBytes: Long = effectiveMaxDecompressed(),
    private val maxBinaryBytes: Long = effectiveMaxDecompressed(),
    private val writeBinary: (id: String, bytes: ByteArray, isCover: Boolean) -> String? = { _, _, _ -> null },
) {

    private var title = "Unknown Title"
    private var firstName = ""
    private var lastName = ""
    private val annotation = StringBuilder()

    private val chapters = mutableListOf<Fb2ChapterData>()
    private var chapterIndex = 0

    private val elementStack = ArrayDeque<String>()
    private val sectionStack = ArrayDeque<SectionFrame>()
    private var text = StringBuilder()

    private val preambleParagraphs = mutableListOf<String>()
    private var preambleFlushed = false

    private var coverBinaryId: String? = null
    private var coverPath: String? = null
    private val referencedImageIds = mutableSetOf<String>()
    private val binaryText = mutableMapOf<String, StringBuilder>()  // accumulates one binary's base64 at a time
    private val savedBinaries = mutableMapOf<String, String>()       // inline-image id -> writeBinary's returned key
    private var insideBinary = false   // true for EVERY <binary>, wanted or not -- its per-binary cap always applies
    private var currentBinaryId: String? = null  // non-null only for a WANTED binary (cover and/or referenced)
    private var currentBinaryBytes = 0L

    private var totalBytes = 0L

    fun accept(event: Fb2Event) {
        when (event) {
            is Fb2Event.Start -> onStart(event)
            is Fb2Event.Text -> onText(event.text)
            is Fb2Event.End -> onEnd(event.name)
        }
    }

    private fun onStart(e: Fb2Event.Start) {
        val name = e.name
        // A <p>/<v> inside a <title> is transparent — its text feeds the title, so don't reset here.
        if (name in TEXT_LEAVES && !elementStack.contains("title")) text = StringBuilder()
        when (name) {
            "image" -> {
                val id = e.href
                if (id.isNullOrEmpty()) {
                    // no-op
                } else if (elementStack.contains("coverpage")) {
                    if (coverBinaryId == null) coverBinaryId = id
                } else {
                    top()?.let { frame ->
                        frame.imageRefs.add(frame.directParagraphs.size to id)
                        referencedImageIds.add(id)
                    }
                }
            }
            "binary" -> {
                insideBinary = true
                currentBinaryBytes = 0L
                val id = e.id
                currentBinaryId = if (id != null && (id == coverBinaryId || id in referencedImageIds)) {
                    binaryText.getOrPut(id) { StringBuilder() }
                    id
                } else {
                    null
                }
            }
            "section" -> if (elementStack.contains("body")) sectionStack.addLast(SectionFrame(chapterIndex))
        }
        elementStack.addLast(name)
    }

    private fun onText(t: String) {
        if (insideBinary) {
            // Binary (base64 image) text has its own, separate cap -- it never counts against the
            // book's text budget, whether this binary is wanted (cover/referenced) or not: an
            // unreferenced binary's base64 must still bypass totalBytes, or a large unused image
            // refuses the whole book. This is also the guard for KXmlParser handing a whole
            // <binary> text node over as one event: the check runs right after it lands in memory.
            currentBinaryBytes += t.length * 2L // UTF-16 in memory
            if (currentBinaryBytes > maxBinaryBytes) throw BookTooLargeException("Book is too large to open safely.")
            currentBinaryId?.let { binaryText[it]?.append(t) } // only a wanted binary accumulates its base64
            return
        }
        totalBytes += t.length * 2L // UTF-16 in memory
        if (totalBytes > maxBytes) throw BookTooLargeException("Book is too large to open safely.")
        when {
            elementStack.contains("annotation") -> annotation.append(t).append(' ')
            elementStack.any { it in TEXT_LEAVES } -> text.append(t)
        }
    }

    private fun onEnd(name: String) {
        if (elementStack.lastOrNull() == name) elementStack.removeLast()
        when (name) {
            "book-title" -> title = flush()
            "first-name" -> firstName = flush()
            "last-name" -> lastName = flush()
            "text-author" -> {
                val v = flush()
                if (v.isNotBlank() && elementStack.contains("epigraph")) top()?.epigraphParagraphs?.add(v)
            }
            "title" -> {
                val v = flush()
                if (elementStack.lastOrNull() == "section") {
                    top()?.let { it.title = v.ifBlank { it.title } }
                } else if (v.isNotBlank() && top() != null) {
                    addParagraph(v)   // poem/other title -> paragraph (don't lose it)
                }
            }
            "p", "v", "subtitle" -> {
                // Inside a <title> these are transparent: their text already fed the title buffer.
                if (!elementStack.contains("title")) {
                    val v = flush()
                    if (v.isNotBlank()) addParagraph(v)
                }
            }
            "binary" -> finishBinary()
            "section" -> emitSection()
        }
    }

    /**
     * Decodes the just-finished `<binary>`'s accumulated base64 and hands the bytes to
     * [writeBinary] immediately -- so only one binary's bytes are ever in memory at a time, not
     * every referenced image for the whole book. When the same id is both the cover AND referenced
     * inline, [writeBinary] is called twice (once per role): the cover gets its own file
     * ([coverPath]), the inline image its own ([savedBinaries]), so a chapter's image never ends up
     * pointing at the cover file (or vice versa) -- restores the pre-refactor behaviour.
     */
    private fun finishBinary() {
        insideBinary = false
        val id = currentBinaryId ?: return
        currentBinaryId = null
        val base64 = binaryText.remove(id)?.toString().orEmpty()
        val bytes = decodeBase64(base64) ?: return
        if (id == coverBinaryId) writeBinary(id, bytes, true)?.let { coverPath = it }
        if (id in referencedImageIds) writeBinary(id, bytes, false)?.let { savedBinaries[id] = it }
    }

    private fun decodeBase64(raw: String): ByteArray? {
        val cleaned = raw.replace(WHITESPACE, "")
        if (cleaned.isEmpty()) return null
        return try { java.util.Base64.getDecoder().decode(cleaned) } catch (_: Exception) { null }
    }

    private fun flush(): String = text.toString().trim()

    /** Routes a paragraph to preamble / section epigraph / section body by current context. */
    private fun addParagraph(value: String) {
        when {
            // Preamble epigraph: FB2 places <epigraph> only in <body>/<section>, so "no open section" => body-level.
            elementStack.contains("epigraph") && sectionStack.isEmpty() -> preambleParagraphs.add(value)
            elementStack.contains("epigraph") -> top()?.epigraphParagraphs?.add(value)
            else -> top()?.directParagraphs?.add(value)
        }
    }

    private fun emitSection() {
        val frame = sectionStack.removeLastOrNull() ?: return
        if (frame.directParagraphs.isEmpty()) return   // wrapper section: children already emitted
        var preShift = 0
        if (!preambleFlushed && preambleParagraphs.isNotEmpty()) {
            preShift = preambleParagraphs.size
            frame.directParagraphs.addAll(0, preambleParagraphs)
            preambleParagraphs.clear()
            preambleFlushed = true
        }
        val allParagraphs = frame.epigraphParagraphs + frame.directParagraphs
        if (allParagraphs.isEmpty()) return
        val prefix = sectionStack
            .filter { it.title != "Chapter ${it.index + 1}" }
            .joinToString("") { "${it.title} · " }
        val epiShift = frame.epigraphParagraphs.size
        chapters.add(
            Fb2ChapterData(
                index = chapterIndex,
                title = prefix + frame.title,
                paragraphs = allParagraphs,
                epigraphCount = frame.epigraphParagraphs.size,
                imageRefs = frame.imageRefs.map { (a, id) -> (epiShift + preShift + a) to id },
            ),
        )
        chapterIndex++
    }

    fun finish(): Fb2Document {
        val author = "$firstName $lastName".trim().ifBlank { "Unknown Author" }
        return Fb2Document(
            title = title,
            author = author,
            annotation = annotation.toString(),
            chapters = chapters.toList(),
            coverBinaryId = coverBinaryId,
            coverPath = coverPath,
            binaries = savedBinaries.toMap(),
        )
    }

    private fun top() = sectionStack.lastOrNull()

    private class SectionFrame(val index: Int) {
        var title: String = "Chapter ${index + 1}"
        val directParagraphs = mutableListOf<String>()
        val epigraphParagraphs = mutableListOf<String>()
        val imageRefs = mutableListOf<Pair<Int, String>>()
    }

    private companion object {
        val TEXT_LEAVES = setOf(
            "p", "v", "subtitle", "title", "text-author", "book-title", "first-name", "last-name",
        )
        val WHITESPACE = Regex("\\s")
    }
}
