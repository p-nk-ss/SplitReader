package com.example.splitreader.domain.parser.fb2

/**
 * Pure FB2 event -> [Fb2Document] builder. Replaces Fb2Parser's ~20 boolean flags with an element-name
 * stack (context = "am I inside <epigraph>/<coverpage>/<binary>…") plus a section-frame stack (each leaf
 * <section> becomes a chapter). No Android dependencies, so it is unit-testable on the JVM.
 */
class Fb2DocumentBuilder {

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
    private val referencedImageIds = mutableSetOf<String>()
    private val binaries = mutableMapOf<String, StringBuilder>()
    private var currentBinaryId: String? = null

    fun accept(event: Fb2Event) {
        when (event) {
            is Fb2Event.Start -> onStart(event)
            is Fb2Event.Text -> onText(event.text)
            is Fb2Event.End -> onEnd(event.name)
        }
    }

    private fun onStart(e: Fb2Event.Start) {
        val name = e.name
        if (name in TEXT_LEAVES) text = StringBuilder()   // start a fresh text buffer for this leaf
        when (name) {
            "image" -> {
                val id = e.href
                if (id.isNullOrEmpty()) {
                    // no-op
                } else if (elementStack.contains("coverpage")) {
                    if (coverBinaryId == null) coverBinaryId = id
                } else if (sectionStack.isNotEmpty()) {
                    top()!!.imageRefs.add(top()!!.directParagraphs.size to id)
                    referencedImageIds.add(id)
                }
            }
            "binary" -> {
                val id = e.id
                if (id != null && (id == coverBinaryId || id in referencedImageIds)) {
                    currentBinaryId = id
                    binaries.getOrPut(id) { StringBuilder() }
                }
            }
            "section" -> if (elementStack.contains("body")) sectionStack.addLast(SectionFrame(chapterIndex))
        }
        elementStack.addLast(name)
    }

    private fun onText(t: String) {
        when {
            currentBinaryId != null -> binaries[currentBinaryId]?.append(t)
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
                val v = flush()
                if (v.isNotBlank()) addParagraph(v)
            }
            "binary" -> currentBinaryId = null
            "section" -> emitSection()
        }
    }

    private fun flush(): String = text.toString().trim()

    /** Routes a paragraph to preamble / section epigraph / section body by current context. */
    private fun addParagraph(value: String) {
        when {
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
            binaries = binaries.mapValues { it.value.toString() },
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
    }
}
