# Phase 1B — FB2 Parser Rewrite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `Fb2Parser`'s ~220-line, ~20-flag state machine with a pure, JVM-tested `Fb2DocumentBuilder` over a neutral `Fb2Event` model, closing text-loss bugs: drop the 5000-char paragraph cap (P4) and read verse/subtitle/cite (P5), while a thin `XmlPullParser` adapter keeps the Android bits (P10).

**Architecture:** New pure package `domain/parser/fb2/` (`Fb2Event`, `Fb2Document`, `Fb2DocumentBuilder`). `Fb2Parser` becomes a thin adapter: `XmlPullParser` → `Fb2Event` → builder → `Fb2Document` → Base64/File decode → `Book`. All structural logic is pure and unit-tested; no behavior change for valid books.

**Tech Stack:** Kotlin 2.0.21, JUnit4 (JVM, `app/src/test`), coroutines. Instrumented `ParserBeginningTest` is the real-file regression guard.

## Global Constraints

- New pure units in `domain/parser/fb2/` — **no `android.*` imports** in `Fb2Event`/`Fb2Document`/`Fb2DocumentBuilder`.
- minSdk 26, Kotlin 2.0.21. Tests JUnit4, hand-written, **no mock framework**, in `app/src/test`.
- Preserve ALL current behaviors: metadata (book-title, first-name+last-name, annotation), cover (`coverpage`→`image#id`→`binary`), section-frame stack (leaf `<section>` with direct prose → chapter; wrapper section emits nothing), ancestor-wrapper title prefix (`"<parent> · <title>"`), body-level preamble epigraph prepended to the first emitted chapter, section epigraph + `<text-author>` → epigraph paragraphs with correct `epigraphCount`, inline `<image href="#id">` anchored + deferred binary decode.
- Verse renders **line-by-line**: each `<v>` → its own paragraph. `<cite>` content → main (direct) paragraphs.
- Commit trailer: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- Run JVM tests with `./gradlew :app:testDebugUnitTest` (no device). Instrumented regression needs an emulator; run it at final review.

---

## File Structure

- `domain/parser/fb2/Fb2Event.kt` — **new** pure: `sealed interface Fb2Event { Start(name,href,id); Text(text); End(name) }`.
- `domain/parser/fb2/Fb2Document.kt` — **new** pure: `Fb2Document`, `Fb2ChapterData`.
- `domain/parser/fb2/Fb2DocumentBuilder.kt` — **new** pure: the state machine (element stack + section frames).
- `domain/parser/Fb2Parser.kt` — **rewrite** as thin adapter; keep Android decode (`saveFb2Cover`, inline images); delete the flag soup + private `SectionFrame`.
- `app/src/test/.../domain/parser/fb2/Fb2DocumentBuilderTest.kt` — **new** JVM tests.

---

## Task 1: Pure Fb2DocumentBuilder + JVM test suite (P4, P5, P10 core)

**Files:**
- Create: `app/src/main/java/com/example/splitreader/domain/parser/fb2/Fb2Event.kt`
- Create: `app/src/main/java/com/example/splitreader/domain/parser/fb2/Fb2Document.kt`
- Create: `app/src/main/java/com/example/splitreader/domain/parser/fb2/Fb2DocumentBuilder.kt`
- Create: `app/src/test/java/com/example/splitreader/domain/parser/fb2/Fb2DocumentBuilderTest.kt`

**Interfaces:**
- Produces: `Fb2Event` (Start/Text/End), `Fb2Document(title,author,annotation,chapters,coverBinaryId,binaries)`, `Fb2ChapterData(index,title,paragraphs,epigraphCount,imageRefs)`, and `class Fb2DocumentBuilder { fun accept(Fb2Event); fun finish(): Fb2Document }`. Consumed by `Fb2Parser` in Task 2.

- [ ] **Step 1: Write the failing test suite**

`app/src/test/java/com/example/splitreader/domain/parser/fb2/Fb2DocumentBuilderTest.kt`:
```kotlin
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
        listOf(start("body"), start("section"), start("title"), text(title), end("title")) +
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
}
```

- [ ] **Step 2: Run the test suite, verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*Fb2DocumentBuilderTest"`
Expected: FAIL — unresolved references (`Fb2Event`, `Fb2Document`, `Fb2DocumentBuilder`).

- [ ] **Step 3: Create the pure model**

`app/src/main/java/com/example/splitreader/domain/parser/fb2/Fb2Event.kt`:
```kotlin
package com.example.splitreader.domain.parser.fb2

/** A namespace-agnostic FB2 XML event. The adapter resolves [href] (any *href attr, '#' stripped)
 *  and [id] so the builder stays free of parser/attribute concerns. */
sealed interface Fb2Event {
    data class Start(val name: String, val href: String? = null, val id: String? = null) : Fb2Event
    data class Text(val text: String) : Fb2Event
    data class End(val name: String) : Fb2Event
}
```

`app/src/main/java/com/example/splitreader/domain/parser/fb2/Fb2Document.kt`:
```kotlin
package com.example.splitreader.domain.parser.fb2

/** Pure result of parsing an FB2 event stream; the Android adapter turns this into a Book. */
data class Fb2Document(
    val title: String,
    val author: String,
    val annotation: String,
    val chapters: List<Fb2ChapterData>,
    val coverBinaryId: String?,
    val binaries: Map<String, String>,   // binary id -> raw base64 text (cover + referenced images)
)

data class Fb2ChapterData(
    val index: Int,
    val title: String,
    val paragraphs: List<String>,        // epigraph paragraphs first, then body
    val epigraphCount: Int,
    val imageRefs: List<Pair<Int, String>>,  // (final anchor into paragraphs, binary id)
)
```

- [ ] **Step 4: Implement the builder**

`app/src/main/java/com/example/splitreader/domain/parser/fb2/Fb2DocumentBuilder.kt`:
```kotlin
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
```

- [ ] **Step 5: Run the test suite, verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*Fb2DocumentBuilderTest"`
Expected: PASS (11 tests). If a test fails, fix the builder (not the test) — the tests encode the required behavior. Common gotchas: the element stack must be pushed AFTER start-handling and popped BEFORE end-handling (so `elementStack.lastOrNull()` is the parent in `onEnd`); `addParagraph` context checks read the stack after the leaf was popped.

- [ ] **Step 6: Confirm no Android imports leaked into the pure package**

Run: `grep -rn "import android" app/src/main/java/com/example/splitreader/domain/parser/fb2/ || echo "clean: no android imports"`
Expected: `clean: no android imports`.

- [ ] **Step 7: Compile and run the full unit suite**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; all unit tests pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/splitreader/domain/parser/fb2/ \
  app/src/test/java/com/example/splitreader/domain/parser/fb2/
git commit -m "feat(parser): pure Fb2DocumentBuilder — verse/subtitle/cite, no 5000 cap (P4,P5)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Rewrite Fb2Parser as a thin adapter (P10)

**Files:**
- Rewrite: `app/src/main/java/com/example/splitreader/domain/parser/Fb2Parser.kt`

**Interfaces:**
- Consumes: `Fb2DocumentBuilder`, `Fb2Event`, `Fb2Document`/`Fb2ChapterData` (Task 1); existing `stableId`, `ImageStore`, `SynopsisExtractor`, `Chapter`, `ChapterImage`, `Book`.

- [ ] **Step 1: Rewrite the parser**

Replace the entire body of `app/src/main/java/com/example/splitreader/domain/parser/Fb2Parser.kt` with the thin adapter below. It keeps `canParse`, `priority`, cooperative cancellation, and the Base64/File decode; it deletes `parseInternal`'s flag soup and the private `SectionFrame`.
```kotlin
package com.example.splitreader.domain.parser

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.ChapterImage
import com.example.splitreader.domain.parser.fb2.Fb2Document
import com.example.splitreader.domain.parser.fb2.Fb2DocumentBuilder
import com.example.splitreader.domain.parser.fb2.Fb2Event
import com.example.splitreader.domain.parser.util.stableId
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

class Fb2Parser @Inject constructor() : BookParser {

    override val supportedExtensions = listOf("fb2", "fb2.xml")
    override val priority = 5

    override fun canParse(fileName: String, mimeType: String, header: ByteArray): Boolean =
        fileName.endsWith(".fb2", ignoreCase = true) ||
            fileName.endsWith(".fb2.xml", ignoreCase = true) ||
            mimeType.contains("fb2", ignoreCase = true) ||
            mimeType == "text/xml" ||
            mimeType == "application/xml" ||
            String(header, Charsets.ISO_8859_1).contains("<FictionBook", ignoreCase = true)

    override suspend fun parse(uri: Uri, context: Context): Book {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open file stream")
        val doc = inputStream.use { readDocument(it) }
        if (doc.chapters.isEmpty()) throw IllegalStateException("No chapters found in fb2 file")
        return assembleBook(doc, uri.toString(), context)
    }

    /** Drives XmlPullParser, translating each event into an [Fb2Event] for the pure builder. */
    private suspend fun readDocument(input: InputStream): Fb2Document {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            .newPullParser().apply { setInput(input, null) }
        val builder = Fb2DocumentBuilder()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            coroutineContext.ensureActive()
            when (eventType) {
                XmlPullParser.START_TAG -> builder.accept(
                    Fb2Event.Start(parser.name.lowercase(), hrefOf(parser), parser.getAttributeValue(null, "id")?.trim()),
                )
                XmlPullParser.TEXT -> builder.accept(Fb2Event.Text(parser.text ?: ""))
                XmlPullParser.END_TAG -> builder.accept(Fb2Event.End(parser.name?.lowercase() ?: ""))
            }
            eventType = parser.next()
        }
        return builder.finish()
    }

    /** First attribute whose name ends with "href" (handles href, l:href, xlink:href), '#' stripped. */
    private fun hrefOf(parser: XmlPullParser): String? =
        (0 until parser.attributeCount)
            .firstOrNull { parser.getAttributeName(it).endsWith("href") }
            ?.let { parser.getAttributeValue(it) }
            ?.removePrefix("#")?.trim()

    private fun assembleBook(doc: Fb2Document, filePath: String, context: Context): Book {
        val bookHash = stableId(filePath)
        val chapters = doc.chapters.map { ch ->
            val images = ch.imageRefs.mapNotNull { (anchor, id) ->
                val bytes = decodeBinary(doc.binaries[id]) ?: return@mapNotNull null
                ImageStore.save(context, bytes, "${bookHash}_$id")
                    ?.let { ChapterImage(anchor.coerceIn(0, ch.paragraphs.size), it) }
            }
            Chapter(
                index = ch.index,
                title = ch.title,
                paragraphs = ch.paragraphs,
                epigraphCount = ch.epigraphCount,
                images = images,
            )
        }
        val coverPath = doc.coverBinaryId?.let { doc.binaries[it] }
            ?.let { saveCover(it, filePath, context) }
        val synopsis = SynopsisExtractor.build(doc.annotation, chapters.flatMap { it.paragraphs })
        Log.d("FB2", "Parsed: title=${doc.title}, author=${doc.author}, chapters=${chapters.size}")
        return Book(
            title = doc.title,
            author = doc.author,
            chapters = chapters,
            filePath = filePath,
            coverPath = coverPath,
            synopsis = synopsis,
        )
    }

    private fun decodeBinary(raw: String?): ByteArray? {
        val cleaned = raw?.replace("\\s".toRegex(), "")
        if (cleaned.isNullOrEmpty()) return null
        return try { Base64.decode(cleaned, Base64.DEFAULT) } catch (_: Exception) { null }
    }

    private fun saveCover(rawBase64: String, filePath: String, context: Context): String? {
        val bytes = decodeBinary(rawBase64) ?: return null
        return try {
            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val coverFile = File(coversDir, "${stableId(filePath)}.jpg")
            coverFile.writeBytes(bytes)
            coverFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
```

- [ ] **Step 2: Compile and run the full unit suite**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; all unit tests pass (no `Fb2Parser`-specific unit tests, but the module must compile and the builder tests still pass).

- [ ] **Step 3: Instrumented regression — real FB2 files parse unchanged**

An emulator must be running. Run:
`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.splitreader.domain.parser.ParserBeginningTest`
Expected: `BUILD SUCCESSFUL`. The FB2 cases (`fb2_academicLeader_keepsOpening`, `fb2_agrippa…`, `fb2_cthulhu…`, `fb2_johnnyMnemonic…`, `fb2_bekker…`) must pass if `qa_book/` is staged locally (they `assumeTrue`-skip otherwise); the always-on EPUB case must still pass. If a test-APK install fails on emulator `/data` space, run `adb uninstall com.example.splitreader` then retry.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/splitreader/domain/parser/Fb2Parser.kt
git commit -m "refactor(parser): thin Fb2Parser adapter over Fb2DocumentBuilder (P10)

Replaces the ~220-line, ~20-flag parseInternal with an XmlPullParser->Fb2Event
adapter plus Base64/File decode; all structural logic now lives in the pure,
unit-tested builder.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Definition of Done (maps to spec §6)

1. A `<p>` longer than 5000 chars survives (`Fb2DocumentBuilderTest.p4_longParagraphIsKept`). *(Task 1)*
2. `<v>`/`<subtitle>`/`<cite>` produce paragraphs; verse is line-by-line (P5 tests). *(Task 1)*
3. Regression builder tests green: nested sections + ancestor prefix, preamble epigraph, section epigraph + `epigraphCount`, inline image anchors + binary capture, cover id, metadata. *(Task 1)*
4. `Fb2Parser` holds no flag-soup state machine; all structural logic is in `Fb2DocumentBuilder` (pure, no `android.*` imports). *(Tasks 1, 2)*
5. `./gradlew :app:testDebugUnitTest` green; instrumented `ParserBeginningTest` FB2 + EPUB cases pass on an emulator. *(Task 2)*
