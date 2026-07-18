# Phase 1C — MOBI Chapter Splitting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop classic-MOBI books without `<mbp:pagebreak>` from collapsing into one giant chapter (P11), by adding a pure `MobiChapterSplitter` that falls back to heading-based splitting when no page breaks exist.

**Architecture:** New pure `MobiChapterSplitter.split(html): List<String>` (string/regex, JVM-tested): primary split on page breaks (`mbp:pagebreak` + inline `page-break-before`); if that yields ≥2 fragments, trust them; otherwise split before each heading of the shallowest level present. `MobiParser.buildChapters` swaps one line to call it; `HtmlChapterExtractor` is unchanged.

**Tech Stack:** Kotlin 2.0.21, JUnit4 (JVM, `app/src/test`). Instrumented `ParserBeginningTest` (MOBI cases) is the real-file regression guard.

## Global Constraints

- New pure unit `MobiChapterSplitter` in `domain/parser/` — **no `android.*` imports**; string/regex only.
- Conservative fallback: heading-split ONLY when page-break split yields < 2 fragments. Heading level = shallowest present (h1, else h2, else h3).
- Do not change `HtmlChapterExtractor` or the `buildChapters` loop body beyond the single `fragments` line.
- Tests JUnit4, hand-written, no mock framework, `app/src/test`.
- Commit trailer: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- JVM tests: `./gradlew :app:testDebugUnitTest` (no device). Instrumented regression needs an emulator (Task 2).

---

## File Structure

- `domain/parser/MobiChapterSplitter.kt` — **new** pure splitter.
- `app/src/test/.../domain/parser/MobiChapterSplitterTest.kt` — **new** JVM tests.
- `domain/parser/MobiParser.kt` — **modify**: `buildChapters` one line; remove the `PAGEBREAK` companion constant.

---

## Task 1: Pure MobiChapterSplitter + JVM tests (P11 core)

**Files:**
- Create: `app/src/main/java/com/example/splitreader/domain/parser/MobiChapterSplitter.kt`
- Create: `app/src/test/java/com/example/splitreader/domain/parser/MobiChapterSplitterTest.kt`

**Interfaces:**
- Produces: `object MobiChapterSplitter { fun split(html: String): List<String> }` — consumed by `MobiParser.buildChapters` in Task 2.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/example/splitreader/domain/parser/MobiChapterSplitterTest.kt`:
```kotlin
package com.example.splitreader.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobiChapterSplitterTest {

    @Test fun pageBreaks_splitIntoFragments() {
        val html = "<p>a</p><mbp:pagebreak/><p>b</p><mbp:pagebreak/><p>c</p>"
        assertEquals(3, MobiChapterSplitter.split(html).size)
    }

    @Test fun noBreaks_splitsOnHeadings() {
        val html = "<h2>Ch1</h2><p>a</p><h2>Ch2</h2><p>b</p><h2>Ch3</h2><p>c</p>"
        val frags = MobiChapterSplitter.split(html)
        assertEquals(3, frags.size)
        assertTrue(frags.all { it.startsWith("<h2") })
    }

    @Test fun noBreaksNoHeadings_singleFragment() {
        val html = "<p>a</p><p>b</p><p>c</p>"
        assertEquals(1, MobiChapterSplitter.split(html).size)
    }

    @Test fun pageBreakBefore_splits() {
        val html = "<p>a</p><div style=\"page-break-before:always\">b</div>"
        assertEquals(2, MobiChapterSplitter.split(html).size)
    }

    @Test fun shallowestHeadingLevelWins() {
        val html = "<h1>Part 1</h1><h2>Sub</h2><p>a</p><h1>Part 2</h1><h2>Sub</h2><p>b</p>"
        val frags = MobiChapterSplitter.split(html)
        assertEquals(2, frags.size) // split on h1 only, not h2
        assertTrue(frags.all { it.startsWith("<h1") })
    }

    @Test fun pageBreaks_takePrecedenceOverHeadings() {
        val html = "<h2>A</h2><p>a</p><mbp:pagebreak/><h2>B</h2><p>b</p>"
        // 2 page-break fragments -> trust breaks, do NOT additionally split on headings
        assertEquals(2, MobiChapterSplitter.split(html).size)
    }

    @Test fun preHeadingContentKeptAsFirstFragment() {
        val html = "<p>front matter</p><h2>Ch1</h2><p>a</p>"
        val frags = MobiChapterSplitter.split(html)
        assertEquals(2, frags.size)
        assertTrue(frags[0].contains("front matter"))
        assertTrue(frags[1].startsWith("<h2"))
    }
}
```

- [ ] **Step 2: Run the tests, verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*MobiChapterSplitterTest"`
Expected: FAIL — unresolved reference `MobiChapterSplitter`.

- [ ] **Step 3: Implement the splitter**

`app/src/main/java/com/example/splitreader/domain/parser/MobiChapterSplitter.kt`:
```kotlin
package com.example.splitreader.domain.parser

/**
 * Splits decompressed classic-MOBI HTML into per-chapter fragments. The primary signal is a page
 * break (`<mbp:pagebreak>` or an inline `page-break-before`); when a book has none — which would
 * otherwise collapse the whole book into one giant chapter — it falls back to splitting before each
 * heading of the shallowest level present. Pure and string-based; the split may cut through nesting,
 * but HtmlChapterExtractor (Jsoup) tolerates that and extracts text either way.
 */
object MobiChapterSplitter {

    private val PAGE_BREAK = Regex(
        "<\\s*mbp:pagebreak\\b[^>]*>|<[^>]*\\bpage-break-before\\b[^>]*>",
        RegexOption.IGNORE_CASE,
    )
    private val HEADING_LEVELS = listOf("h1", "h2", "h3")

    fun split(html: String): List<String> {
        val byBreaks = PAGE_BREAK.split(html).map { it.trim() }.filter { it.isNotEmpty() }
        if (byBreaks.size >= 2) return byBreaks

        val level = HEADING_LEVELS.firstOrNull {
            Regex("<\\s*$it[\\s/>]", RegexOption.IGNORE_CASE).containsMatchIn(html)
        } ?: return listOf(html)

        val fragments = html.split(Regex("(?=<\\s*$level[\\s/>])", RegexOption.IGNORE_CASE))
            .map { it.trim() }.filter { it.isNotEmpty() }
        return fragments.ifEmpty { listOf(html) }
    }
}
```

- [ ] **Step 4: Run the tests, verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*MobiChapterSplitterTest"`
Expected: PASS (7 tests). If a test fails, fix the splitter, not the test.

- [ ] **Step 5: Confirm no Android imports**

Run: `grep -n "import android" app/src/main/java/com/example/splitreader/domain/parser/MobiChapterSplitter.kt || echo "clean: no android imports"`
Expected: `clean: no android imports`.

- [ ] **Step 6: Compile and run the full unit suite**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; all unit tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/splitreader/domain/parser/MobiChapterSplitter.kt \
  app/src/test/java/com/example/splitreader/domain/parser/MobiChapterSplitterTest.kt
git commit -m "feat(parser): MobiChapterSplitter — heading fallback when no page breaks (P11)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Wire the splitter into MobiParser (P11)

**Files:**
- Modify: `app/src/main/java/com/example/splitreader/domain/parser/MobiParser.kt`

**Interfaces:**
- Consumes: `MobiChapterSplitter.split` (Task 1).

- [ ] **Step 1: Replace the fragment-splitting line in `buildChapters`**

In `MobiParser.kt`, in `buildChapters`, replace:
```kotlin
        val fragments = PAGEBREAK.split(html).map { it.trim() }.filter { it.isNotEmpty() }
            .ifEmpty { listOf(html) }
```
with:
```kotlin
        val fragments = MobiChapterSplitter.split(html)
```
Leave the rest of the `buildChapters` loop unchanged.

- [ ] **Step 2: Remove the now-unused PAGEBREAK constant**

In `MobiParser.kt`'s `companion object`, delete the line:
```kotlin
        private val PAGEBREAK = Regex("<\\s*mbp:pagebreak[^>]*>", RegexOption.IGNORE_CASE)
```
Keep `CP1252`. Verify no other reference to `PAGEBREAK` remains in the file (compile will catch it).

- [ ] **Step 3: Compile and run the full unit suite**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; all unit tests pass.

- [ ] **Step 4: Instrumented regression — real MOBI books still parse**

An emulator must be running. Run:
`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.splitreader.domain.parser.ParserBeginningTest`
Expected: `BUILD SUCCESSFUL`. The MOBI cases (`mobi_soulHunter_keepsRealBeginning`, `mobi_howToTrainDragon_keepsRealBeginning`) must pass if `qa_book/` is staged locally (they `assumeTrue`-skip otherwise) — their "beginning intact / not a source mention" assertions must still hold with the new splitter. The always-on EPUB case must pass. If a test-APK install fails on emulator `/data` space, run `adb uninstall com.example.splitreader` then retry.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/splitreader/domain/parser/MobiParser.kt
git commit -m "fix(parser): use MobiChapterSplitter so heading-only MOBI books get chapters (P11)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Definition of Done (maps to spec §5)

1. A MOBI HTML blob with headings but no `<mbp:pagebreak>` splits into multiple fragments (`noBreaks_splitsOnHeadings`). *(Task 1)*
2. A blob with ≥2 page-break fragments is split by breaks, not additionally by headings (`pageBreaks_takePrecedenceOverHeadings`). *(Task 1)*
3. A blob with neither breaks nor headings yields exactly one fragment (`noBreaksNoHeadings_singleFragment`). *(Task 1)*
4. `MobiParser` holds no inline splitting logic; it lives in the pure `MobiChapterSplitter` (no `android.*`). *(Tasks 1, 2)*
5. `./gradlew :app:testDebugUnitTest` green; instrumented `ParserBeginningTest` (MOBI + EPUB) passes on an emulator. *(Task 2)*
