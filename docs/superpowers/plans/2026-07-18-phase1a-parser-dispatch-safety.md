# Phase 1A — Parser Dispatch & Safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make parser selection deterministic and correct, and make parsing resilient to malformed/hostile files (no data loss, no OOM), by extracting pure byte/dispatch logic into small JVM-testable units.

**Architecture:** Extraction-first. Create four pure units under `domain/parser/util/` plus a `MobiCodec`, cover them with fast JVM unit tests, then thin the Android-bound parsers to delegate to them. No behavior change for valid books — only robustness, deterministic dispatch, and collision-free filenames.

**Tech Stack:** Kotlin 2.0.21, JUnit4 (`app/src/test`, JVM), coroutines. Existing instrumented `ParserBeginningTest` is the end-to-end regression guard.

## Global Constraints

- Source package `com.example.splitreader`; parsers in `domain/parser/`; new pure units in `domain/parser/util/`.
- minSdk **26** — do NOT use `InputStream.readNBytes` (API 33). Use explicit read loops.
- Tests: JUnit4, **hand-written fakes, no mock framework**. Pure-logic tests go in `app/src/test/...` (JVM, no device). Only end-to-end parser tests are instrumented.
- `MAX_DECOMPRESSED = 300L * 1024 * 1024` (~300 MB) — DoS backstop, not a UX limit.
- `u32` used as a size/offset must be `Long` (`and 0xFFFFFFFFL`) to avoid sign overflow.
- Do NOT rewrite EPUB to stream (deferred by decision); a size guard is sufficient.
- Every commit ends with: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- Run JVM unit tests with `./gradlew :app:testDebugUnitTest` (no device). The instrumented regression (`ParserBeginningTest`) needs an emulator and is run at final review, not per task.

---

## File Structure

- `domain/parser/util/FileKeys.kt` — **new**: `stableId(key)` (P12).
- `domain/parser/util/StreamReading.kt` — **new**: `readUpTo`, `readAllBounded`, `BookTooLargeException`, `MAX_DECOMPRESSED` (P6, P8).
- `domain/parser/util/ParserSelector.kt` — **new**: `selectParser(...)` (P7).
- `domain/parser/util/ZipReading.kt` — **new**: `readZipEntries(input, maxTotal, accept)` (P8).
- `domain/parser/MobiCodec.kt` — **new**: `u16/u32/trailingDataSize/palmDocDecompress` (P9).
- `domain/parser/BookParser.kt` — **modify**: add `priority`.
- `domain/usecase/ParseBookUseCase.kt` — **modify**: use `readUpTo` + `selectParser`.
- `domain/parser/EpubParser.kt` — **modify**: `readZipEntries` guard + cover reuse + `stableId` (P8, P12).
- `domain/parser/MobiParser.kt` — **modify**: delegate to `MobiCodec`, file-size guard, `ensureActive`, `stableId`.
- `domain/parser/MobiFile.kt` — **modify**: reuse `MobiCodec` byte readers.
- `domain/parser/Fb2Parser.kt` — **modify**: `ensureActive`, `stableId`.
- Tests: `app/src/test/.../domain/parser/util/{FileKeysTest,StreamReadingTest,ParserSelectorTest,ZipReadingTest}.kt`, `app/src/test/.../domain/parser/MobiCodecTest.kt`.

---

## Task 1: FileKeys.stableId — collision-free resource ids (P12)

**Files:**
- Create: `app/src/main/java/com/example/splitreader/domain/parser/util/FileKeys.kt`
- Create: `app/src/test/java/com/example/splitreader/domain/parser/util/FileKeysTest.kt`
- Modify: `EpubParser.kt` (lines 70, 193), `MobiParser.kt` (line 181), `Fb2Parser.kt` (lines 242, 277)

**Interfaces:**
- Produces: `com.example.splitreader.domain.parser.util.stableId(key: String): String` — deterministic hex id, consumed by all three parsers for cover/image filenames.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/example/splitreader/domain/parser/util/FileKeysTest.kt`:
```kotlin
package com.example.splitreader.domain.parser.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileKeysTest {
    @Test fun deterministic_sameInput_sameId() {
        assertEquals(stableId("content://book/1"), stableId("content://book/1"))
    }

    @Test fun differentInputs_differentIds() {
        assertNotEquals(stableId("content://book/1"), stableId("content://book/2"))
    }

    @Test fun hexAndStableLength() {
        val id = stableId("file:///data/user/0/app/files/catalog/42.epub")
        assertEquals(16, id.length)
        assertTrue("id must be lowercase hex", id.all { it in "0123456789abcdef" })
    }

    @Test fun noCollisionsAcrossRealisticKeys() {
        val keys = (1..1000).map { "content://com.android.providers/document/$it" }
        assertEquals(keys.size, keys.map { stableId(it) }.toSet().size)
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*FileKeysTest"`
Expected: FAIL — unresolved reference `stableId`.

- [ ] **Step 3: Implement**

`app/src/main/java/com/example/splitreader/domain/parser/util/FileKeys.kt`:
```kotlin
package com.example.splitreader.domain.parser.util

import java.security.MessageDigest

/**
 * Stable, collision-resistant id for a resource key (e.g. a book uri), used to name cover/image
 * files on disk. Replaces String.hashCode(), whose 32-bit collisions let different books overwrite
 * each other's cover. Returns the first 16 hex chars (64 bits) of the SHA-256 of [key].
 */
fun stableId(key: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
    return buildString(16) { for (i in 0 until 8) append("%02x".format(digest[i])) }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*FileKeysTest"`
Expected: PASS.

- [ ] **Step 5: Wire stableId into the three parsers**

Add `import com.example.splitreader.domain.parser.util.stableId` to each file, then replace the hashCode-based ids:

`EpubParser.kt:70` — replace
```kotlin
        val bookHash = uri.toString().hashCode().toLong().and(0x7FFFFFFFL)
```
with
```kotlin
        val bookHash = stableId(uri.toString())
```
`EpubParser.kt:193` — replace
```kotlin
            val hash = uri.toString().hashCode().toLong().and(0x7FFFFFFFL)
```
with
```kotlin
            val hash = stableId(uri.toString())
```
(The surrounding `File(coversDir, "$hash.$ext")` / `"${bookHash}_..."` string interpolation is unchanged — `hash`/`bookHash` are now Strings.)

`MobiParser.kt:181` — replace
```kotlin
            val hash = filePath.hashCode().toLong().and(0x7FFFFFFFL)
```
with
```kotlin
            val hash = stableId(filePath)
```
`Fb2Parser.kt:242` — replace
```kotlin
            val bookHash = filePath.hashCode().toLong().and(0x7FFFFFFFL)
```
with
```kotlin
            val bookHash = stableId(filePath)
```
`Fb2Parser.kt:277` — replace
```kotlin
            val hash = filePath.hashCode().toLong().and(0x7FFFFFFFL)
```
with
```kotlin
            val hash = stableId(filePath)
```

- [ ] **Step 6: Compile and run unit tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; all unit tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/splitreader/domain/parser/util/FileKeys.kt \
  app/src/test/java/com/example/splitreader/domain/parser/util/FileKeysTest.kt \
  app/src/main/java/com/example/splitreader/domain/parser/EpubParser.kt \
  app/src/main/java/com/example/splitreader/domain/parser/MobiParser.kt \
  app/src/main/java/com/example/splitreader/domain/parser/Fb2Parser.kt
git commit -m "fix(parser): content-addressed cover/image filenames (P12)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: StreamReading — reliable header read + bounded read (P6, P8)

**Files:**
- Create: `app/src/main/java/com/example/splitreader/domain/parser/util/StreamReading.kt`
- Create: `app/src/test/java/com/example/splitreader/domain/parser/util/StreamReadingTest.kt`
- Modify: `domain/usecase/ParseBookUseCase.kt` (readHeaderBytes, lines 66-71)

**Interfaces:**
- Produces: `readUpTo(input: InputStream, n: Int): ByteArray`; `readAllBounded(input: InputStream, maxBytes: Long): ByteArray`; `class BookTooLargeException`; `const val MAX_DECOMPRESSED: Long`. Consumed here by `ParseBookUseCase`; `readAllBounded`/`MAX_DECOMPRESSED` also by Task 5.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/example/splitreader/domain/parser/util/StreamReadingTest.kt`:
```kotlin
package com.example.splitreader.domain.parser.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/** An InputStream that hands out at most one byte per read() call, to expose short reads. */
private class DripStream(bytes: ByteArray) : InputStream() {
    private val src = ByteArrayInputStream(bytes)
    override fun read(): Int = src.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int =
        if (len == 0) 0 else src.read(b, off, 1) // never fills more than 1 byte
}

class StreamReadingTest {
    @Test fun readUpTo_fillsDespiteShortReads() {
        val data = ByteArray(256) { it.toByte() }
        val got = readUpTo(DripStream(data), 256)
        assertArrayEquals(data, got)
    }

    @Test fun readUpTo_truncatesAtEof() {
        val data = ByteArray(10) { it.toByte() }
        val got = readUpTo(DripStream(data), 256)
        assertEquals(10, got.size)
        assertArrayEquals(data, got)
    }

    @Test fun readAllBounded_returnsBytesWithinLimit() {
        val data = ByteArray(1000) { it.toByte() }
        assertArrayEquals(data, readAllBounded(ByteArrayInputStream(data), 2000))
    }

    @Test fun readAllBounded_throwsWhenExceeded() {
        val data = ByteArray(5000)
        assertThrows(BookTooLargeException::class.java) {
            readAllBounded(ByteArrayInputStream(data), 1024)
        }
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*StreamReadingTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement**

`app/src/main/java/com/example/splitreader/domain/parser/util/StreamReading.kt`:
```kotlin
package com.example.splitreader.domain.parser.util

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** DoS backstop for decompressed/whole-file reads. Generous — legitimate illustrated books pass. */
const val MAX_DECOMPRESSED: Long = 300L * 1024 * 1024

/** Thrown when a book's content exceeds [MAX_DECOMPRESSED]; surfaced to the user as a parse error. */
class BookTooLargeException(message: String) : Exception(message)

/**
 * Reads up to [n] bytes, looping until [n] is reached or EOF. InputStream.read(byte[]) may return
 * fewer bytes than requested even when more are available, so a single read is unreliable for
 * magic-byte detection. Returns exactly what was read (<= n).
 */
fun readUpTo(input: InputStream, n: Int): ByteArray {
    val buf = ByteArray(n)
    var off = 0
    while (off < n) {
        val r = input.read(buf, off, n - off)
        if (r < 0) break
        off += r
    }
    return if (off == n) buf else buf.copyOf(off)
}

/** Reads the whole stream, throwing [BookTooLargeException] as soon as [maxBytes] is exceeded. */
fun readAllBounded(input: InputStream, maxBytes: Long): ByteArray {
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(8 * 1024)
    var total = 0L
    while (true) {
        val r = input.read(chunk)
        if (r < 0) break
        total += r
        if (total > maxBytes) throw BookTooLargeException("Book is too large to open safely.")
        out.write(chunk, 0, r)
    }
    return out.toByteArray()
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*StreamReadingTest"`
Expected: PASS.

- [ ] **Step 5: Wire readUpTo into the header read**

In `ParseBookUseCase.kt`, add `import com.example.splitreader.domain.parser.util.readUpTo`, and replace `readHeaderBytes` (lines 66-71):
```kotlin
    /** Reads the first [byteCount] bytes (or fewer) of the file for magic-byte format detection. */
    private fun readHeaderBytes(uri: Uri, byteCount: Int): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { stream ->
            readUpTo(stream, byteCount)
        } ?: ByteArray(0)
```

- [ ] **Step 6: Compile and run unit tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; all pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/splitreader/domain/parser/util/StreamReading.kt \
  app/src/test/java/com/example/splitreader/domain/parser/util/StreamReadingTest.kt \
  app/src/main/java/com/example/splitreader/domain/usecase/ParseBookUseCase.kt
git commit -m "fix(parser): fill-read file header for reliable magic detection (P6)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: ParserSelector + priority — deterministic dispatch (P7)

**Files:**
- Create: `app/src/main/java/com/example/splitreader/domain/parser/util/ParserSelector.kt`
- Create: `app/src/test/java/com/example/splitreader/domain/parser/util/ParserSelectorTest.kt`
- Modify: `BookParser.kt` (add `priority`), `EpubParser.kt` / `MobiParser.kt` / `Fb2Parser.kt` (set `priority`), `ParseBookUseCase.kt` (use selector, lines 37-43)

**Interfaces:**
- Consumes: `BookParser.canParse`, and a new `BookParser.priority: Int`.
- Produces: `selectParser(parsers: Collection<BookParser>, fileName: String, mimeType: String, header: ByteArray): BookParser?` — deterministic winner (highest priority; className tie-break).

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/example/splitreader/domain/parser/util/ParserSelectorTest.kt`:
```kotlin
package com.example.splitreader.domain.parser.util

import android.content.Context
import android.net.Uri
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.parser.BookParser
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

private class FakeParser(
    override val supportedExtensions: List<String>,
    override val priority: Int,
    private val matches: Boolean,
) : BookParser {
    override fun canParse(fileName: String, mimeType: String, header: ByteArray) = matches
    override suspend fun parse(uri: Uri, context: Context): Book = throw NotImplementedError()
}

class ParserSelectorTest {
    private val h = ByteArray(0)

    @Test fun picksHighestPriorityAmongMatches() {
        val low = FakeParser(listOf("xml"), priority = 5, matches = true)
        val high = FakeParser(listOf("epub"), priority = 10, matches = true)
        assertSame(high, selectParser(listOf(low, high), "b.epub", "", h))
    }

    @Test fun ignoresNonMatching() {
        val no = FakeParser(listOf("epub"), priority = 100, matches = false)
        val yes = FakeParser(listOf("fb2"), priority = 5, matches = true)
        assertSame(yes, selectParser(listOf(no, yes), "b.fb2", "", h))
    }

    @Test fun noMatchReturnsNull() {
        val no = FakeParser(listOf("epub"), priority = 10, matches = false)
        assertNull(selectParser(listOf(no), "b.txt", "", h))
    }

    @Test fun equalPriorityTieBreakIsDeterministic() {
        val a = FakeParser(listOf("a"), priority = 5, matches = true)
        val b = FakeParser(listOf("b"), priority = 5, matches = true)
        val first = selectParser(listOf(a, b), "x", "", h)
        val second = selectParser(listOf(b, a), "x", "", h)
        assertSame(first, second) // order-independent
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*ParserSelectorTest"`
Expected: FAIL — unresolved `priority` / `selectParser`.

- [ ] **Step 3: Add `priority` to the interface**

In `BookParser.kt`, add inside the interface (after `supportedExtensions`):
```kotlin
    /** Selection priority when multiple parsers match; higher wins. Magic-byte parsers set this high. */
    val priority: Int get() = 0
```

- [ ] **Step 4: Implement the selector**

`app/src/main/java/com/example/splitreader/domain/parser/util/ParserSelector.kt`:
```kotlin
package com.example.splitreader.domain.parser.util

import com.example.splitreader.domain.parser.BookParser

/**
 * Picks the highest-[BookParser.priority] parser whose [BookParser.canParse] matches. The registry is
 * a Hilt Set with no guaranteed iteration order, and canParse predicates overlap (e.g. FB2 claims
 * generic text/xml), so priority — with a stable class-name tie-break — makes the choice deterministic.
 */
fun selectParser(
    parsers: Collection<BookParser>,
    fileName: String,
    mimeType: String,
    header: ByteArray,
): BookParser? =
    parsers
        .filter { it.canParse(fileName, mimeType, header) }
        .maxWithOrNull(compareBy({ it.priority }, { it::class.java.name }))
```

- [ ] **Step 5: Run the test, verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*ParserSelectorTest"`
Expected: PASS.

- [ ] **Step 6: Set priorities and wire the selector**

In `EpubParser.kt` and `MobiParser.kt`, add `override val priority = 10` (magic-byte formats). In `Fb2Parser.kt`, add `override val priority = 5` (matches broad `text/xml`). Place each next to `supportedExtensions`.

In `ParseBookUseCase.kt`, add `import com.example.splitreader.domain.parser.util.selectParser`, and replace the selection block (lines 37-43):
```kotlin
            val parser = selectParser(parsers, fileName, mimeType, header)
                ?: throw IllegalArgumentException(
                    "Unsupported format: $fileName\nSupported: " +
                        parsers.flatMap { it.supportedExtensions }
                            .distinct()
                            .joinToString(", ") { ".$it" }
                )
```

- [ ] **Step 7: Compile and run unit tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; all pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/splitreader/domain/parser/util/ParserSelector.kt \
  app/src/test/java/com/example/splitreader/domain/parser/util/ParserSelectorTest.kt \
  app/src/main/java/com/example/splitreader/domain/parser/BookParser.kt \
  app/src/main/java/com/example/splitreader/domain/parser/EpubParser.kt \
  app/src/main/java/com/example/splitreader/domain/parser/MobiParser.kt \
  app/src/main/java/com/example/splitreader/domain/parser/Fb2Parser.kt \
  app/src/main/java/com/example/splitreader/domain/usecase/ParseBookUseCase.kt
git commit -m "fix(parser): deterministic parser selection by priority (P7)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: MobiCodec — pure PalmDOC/byte logic with sign + distance-0 fixes (P9)

**Files:**
- Create: `app/src/main/java/com/example/splitreader/domain/parser/MobiCodec.kt`
- Create: `app/src/test/java/com/example/splitreader/domain/parser/MobiCodecTest.kt`
- Modify: `MobiParser.kt` (delegate byte ops; `ensureActive`), `MobiFile.kt` (reuse readers)

**Interfaces:**
- Produces: `object MobiCodec` with `u16(b,off): Int`, `u32(b,off): Long`, `trailingDataSize(data,size,flags): Int`, `palmDocDecompress(input,length): ByteArray`. Consumed by `MobiParser` and `MobiFile`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/example/splitreader/domain/parser/MobiCodecTest.kt`:
```kotlin
package com.example.splitreader.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobiCodecTest {
    @Test fun u32_highBitSet_isNonNegativeLong() {
        val b = byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x01)
        assertEquals(0x80000001L, MobiCodec.u32(b, 0))
        assertTrue(MobiCodec.u32(b, 0) > 0)
    }

    @Test fun u16_reads_bigEndian() {
        assertEquals(0x0102, MobiCodec.u16(byteArrayOf(0x01, 0x02), 0))
    }

    @Test fun palmDoc_literalRun_roundTrips() {
        // 0x03 => copy next 3 literal bytes 'A','B','C'
        val input = byteArrayOf(0x03, 'A'.code.toByte(), 'B'.code.toByte(), 'C'.code.toByte())
        assertEquals("ABC", String(MobiCodec.palmDocDecompress(input, input.size), Charsets.US_ASCII))
    }

    @Test fun palmDoc_distanceZero_doesNotCorruptOrHang() {
        // A back-reference pair (0x80..0xBF) encoding distance 0 must be skipped, not copied.
        val input = byteArrayOf(0x41, 0x80.toByte(), 0x00) // 'A' then a distance-0 pair
        val out = MobiCodec.palmDocDecompress(input, input.size)
        assertEquals("A", String(out, Charsets.US_ASCII)) // no garbage appended, terminates
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*MobiCodecTest"`
Expected: FAIL — unresolved `MobiCodec`.

- [ ] **Step 3: Implement MobiCodec (move + fix)**

`app/src/main/java/com/example/splitreader/domain/parser/MobiCodec.kt` — this moves the four byte helpers out of `MobiParser`, changes `u32` to return `Long`, and adds the `distance == 0` guard:
```kotlin
package com.example.splitreader.domain.parser

/**
 * Pure byte-level primitives for classic MOBI / PalmDOC, extracted from MobiParser so they can be
 * unit-tested on the JVM. [u32] returns Long to avoid the sign overflow that a 32-bit header field
 * with the high bit set would cause when used as a size/offset.
 */
object MobiCodec {

    fun u16(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    fun u32(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or
            ((b[off + 1].toLong() and 0xFF) shl 16) or
            ((b[off + 2].toLong() and 0xFF) shl 8) or
            (b[off + 3].toLong() and 0xFF)

    /**
     * Size of the trailing data entries appended to a text record, stripped before decompression.
     * Ported from the MOBI spec / kindleunpack `getSizeOfTrailingDataEntries`.
     */
    fun trailingDataSize(data: ByteArray, size: Int, flags: Int): Int {
        fun entrySize(end: Int): Int {
            var bitpos = 0
            var result = 0
            var pos = end
            if (pos <= 0) return 0
            while (true) {
                val v = data[pos - 1].toInt() and 0xFF
                result = result or ((v and 0x7F) shl bitpos)
                bitpos += 7
                pos -= 1
                if (v and 0x80 != 0 || bitpos >= 28 || pos == 0) return result
            }
        }
        var num = 0
        var testFlags = flags shr 1
        while (testFlags != 0) {
            if (testFlags and 1 == 1) num += entrySize(size - num)
            testFlags = testFlags shr 1
        }
        if (flags and 1 == 1 && size - num - 1 >= 0) {
            num += (data[size - num - 1].toInt() and 0x3) + 1
        }
        return num
    }

    /** PalmDOC (LZ77 variant) decompression. A back-reference with distance 0 is skipped (guard). */
    fun palmDocDecompress(input: ByteArray, length: Int): ByteArray {
        var out = ByteArray(maxOf(64, length * 8))
        var outLen = 0
        fun put(b: Int) {
            if (outLen >= out.size) out = out.copyOf(out.size * 2)
            out[outLen++] = b.toByte()
        }
        var i = 0
        while (i < length) {
            val c = input[i].toInt() and 0xFF
            i++
            when {
                c == 0x00 -> put(0)
                c in 0x01..0x08 -> {
                    var j = 0
                    while (j < c && i < length) { put(input[i].toInt() and 0xFF); i++; j++ }
                }
                c in 0x09..0x7F -> put(c)
                c in 0x80..0xBF -> {
                    if (i < length) {
                        val c2 = input[i].toInt() and 0xFF
                        i++
                        val pair = ((c shl 8) or c2) and 0x3FFF
                        val distance = pair shr 3
                        val copyLen = (pair and 0x07) + 3
                        var src = outLen - distance
                        if (distance > 0 && src >= 0) {
                            var k = 0
                            while (k < copyLen) { put(out[src].toInt() and 0xFF); src++; k++ }
                        }
                    }
                }
                else -> { put(' '.code); put(c xor 0x80) } // 0xC0..0xFF: space + (c & 0x7F)
            }
        }
        return out.copyOf(outLen)
    }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*MobiCodecTest"`
Expected: PASS.

- [ ] **Step 5: Refactor MobiParser to delegate to MobiCodec**

In `MobiParser.kt`:
- Delete the private `u16`, `u32`, `trailingDataSize`, `palmDocDecompress` methods (current lines 211-287).
- Replace every `u16(x, y)` call with `MobiCodec.u16(x, y)`, `palmDocDecompress(a, b)` with `MobiCodec.palmDocDecompress(a, b)`, `trailingDataSize(a, b, c)` with `MobiCodec.trailingDataSize(a, b, c)`.
- For `u32` calls (now `Long`): the header fields are read as Long, compared as Long, and converted to Int only where used as an array index / loop bound. Update the header-decode block so each `u32(...)` becomes `MobiCodec.u32(...)` and its use is `.toInt()` where an Int is required. Concretely:
  - `val mobiHeaderLength = if (hasMobiHeader) u32(rec0, 20) else 0` → `val mobiHeaderLength = if (hasMobiHeader) MobiCodec.u32(rec0, 20).toInt() else 0`
  - `val fileVersion = if (hasMobiHeader && rec0.size >= 40) u32(rec0, 36) else 6` → `... MobiCodec.u32(rec0, 36).toInt() else 6`
  - `val textEncoding = if (hasMobiHeader) u32(rec0, 28) else 1252` → `... MobiCodec.u32(rec0, 28).toInt() else 1252`
  - `val firstImageIndex = if (hasMobiHeader && mobiHeaderLength >= 0x70) u32(rec0, 108) else -1` → `... MobiCodec.u32(rec0, 108).toInt() else -1`
  - EXTH flag test `(u32(rec0, 0x80) and 0x40) != 0` → `(MobiCodec.u32(rec0, 0x80) and 0x40L) != 0L`
  - In `parseExth`: `val type = u32(rec0, p)` → `val type = MobiCodec.u32(rec0, p).toInt()`; `val len = u32(rec0, p + 4)` → `val len = MobiCodec.u32(rec0, p + 4).toInt()`; `val count = u32(rec0, start + 8)` → `val count = MobiCodec.u32(rec0, start + 8).toInt()`.
  - In `readFullName`: `val off = u32(rec0, 0x54)` → `val off = MobiCodec.u32(rec0, 0x54).toInt()`; `val len = u32(rec0, 0x58)` → `val len = MobiCodec.u32(rec0, 0x58).toInt()`.
- Add cooperative cancellation to the text-record decompression loop. Add import `import kotlinx.coroutines.ensureActive` and `import kotlin.coroutines.coroutineContext`, and at the top of the `for (i in 1..lastTextRecord)` body insert:
```kotlin
            coroutineContext.ensureActive()
```
(`parse` is a `suspend fun`, so `coroutineContext` is available.)

- [ ] **Step 6: Refactor MobiFile to reuse MobiCodec readers**

In `MobiFile.kt`, delete its private `u16`/`u32` methods and use `MobiCodec`:
- `val recordCount: Int = if (bytes.size >= 78) u16(76) else 0` → `... MobiCodec.u16(bytes, 76) else 0`
- `IntArray(recordCount) { i -> u32(78 + i * 8) }` → `IntArray(recordCount) { i -> MobiCodec.u32(bytes, 78 + i * 8).toInt() }`

- [ ] **Step 7: Compile and run unit tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; all pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/splitreader/domain/parser/MobiCodec.kt \
  app/src/test/java/com/example/splitreader/domain/parser/MobiCodecTest.kt \
  app/src/main/java/com/example/splitreader/domain/parser/MobiParser.kt \
  app/src/main/java/com/example/splitreader/domain/parser/MobiFile.kt
git commit -m "fix(parser): extract MobiCodec; Long u32, distance-0 guard, cancellable decode (P9)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Size backstop — bounded zip read + MOBI file guard + FB2 cancellation (P8, P9)

**Files:**
- Create: `app/src/main/java/com/example/splitreader/domain/parser/util/ZipReading.kt`
- Create: `app/src/test/java/com/example/splitreader/domain/parser/util/ZipReadingTest.kt`
- Modify: `EpubParser.kt` (use `readZipEntries`; cover from imageMap), `MobiParser.kt` (`readAllBounded` for the file), `Fb2Parser.kt` (`ensureActive`)

**Interfaces:**
- Consumes: `MAX_DECOMPRESSED`, `BookTooLargeException` (Task 2).
- Produces: `readZipEntries(input: InputStream, maxTotal: Long, accept: (String) -> Boolean): Map<String, ByteArray>` — decompresses matching entries, throwing `BookTooLargeException` once cumulative bytes exceed `maxTotal`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/example/splitreader/domain/parser/util/ZipReadingTest.kt`:
```kotlin
package com.example.splitreader.domain.parser.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
    val bos = ByteArrayOutputStream()
    ZipOutputStream(bos).use { zip ->
        for ((name, data) in entries) {
            zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry()
        }
    }
    return bos.toByteArray()
}

class ZipReadingTest {
    @Test fun readsOnlyAcceptedEntries() {
        val zip = zipOf("a.xhtml" to "hi".toByteArray(), "b.bin" to byteArrayOf(1, 2))
        val out = readZipEntries(ByteArrayInputStream(zip), 1_000) { it.endsWith(".xhtml") }
        assertEquals(setOf("a.xhtml"), out.keys)
        assertArrayEquals("hi".toByteArray(), out["a.xhtml"])
        assertFalse(out.containsKey("b.bin"))
    }

    @Test fun throwsWhenTotalExceedsLimit() {
        // Highly compressible 2 MB entry (zip-bomb-like); limit is 1 KB.
        val big = ByteArray(2 * 1024 * 1024) // all zeros -> tiny compressed, huge inflated
        val zip = zipOf("big.html" to big)
        assertThrows(BookTooLargeException::class.java) {
            readZipEntries(ByteArrayInputStream(zip), 1024) { true }
        }
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*ZipReadingTest"`
Expected: FAIL — unresolved `readZipEntries`.

- [ ] **Step 3: Implement**

`app/src/main/java/com/example/splitreader/domain/parser/util/ZipReading.kt`:
```kotlin
package com.example.splitreader.domain.parser.util

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Reads every non-directory zip entry whose name satisfies [accept] into a name -> bytes map,
 * accumulating the total decompressed size and throwing [BookTooLargeException] the moment it
 * exceeds [maxTotal]. This bounds a maliciously compressed (zip-bomb) EPUB before it exhausts memory.
 */
fun readZipEntries(
    input: InputStream,
    maxTotal: Long,
    accept: (String) -> Boolean,
): Map<String, ByteArray> {
    val result = LinkedHashMap<String, ByteArray>()
    var total = 0L
    val chunk = ByteArray(8 * 1024)
    ZipInputStream(input).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && accept(entry.name)) {
                val out = ByteArrayOutputStream()
                while (true) {
                    val r = zip.read(chunk)
                    if (r < 0) break
                    total += r
                    if (total > maxTotal) throw BookTooLargeException("Book is too large to open safely.")
                    out.write(chunk, 0, r)
                }
                result[entry.name] = out.toByteArray()
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    return result
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*ZipReadingTest"`
Expected: PASS.

- [ ] **Step 5: Wire the bounded read + cover reuse into EpubParser**

In `EpubParser.kt`, add imports:
```kotlin
import com.example.splitreader.domain.parser.util.MAX_DECOMPRESSED
import com.example.splitreader.domain.parser.util.readZipEntries
```
Replace the manual zip-reading block (current lines 40-54) with a single bounded read that fills both maps:
```kotlin
        val allEntries = context.contentResolver.openInputStream(uri)?.use { input ->
            readZipEntries(input, MAX_DECOMPRESSED) { name ->
                val ext = name.substringAfterLast('.').lowercase()
                ext in textExtensions || ext in imageExtensions
            }
        } ?: throw IllegalStateException("Cannot open file")
        allEntries.forEach { (name, bytes) ->
            when (name.substringAfterLast('.').lowercase()) {
                in textExtensions -> entryMap[name] = bytes
                in imageExtensions -> imageMap[name] = bytes
            }
        }
```
Then replace `extractCoverFromZip` usage (line 107-110) so the cover comes from the already-loaded `imageMap` instead of re-opening the zip:
```kotlin
        val coverPath = opf.coverHref?.let { href ->
            val fullCoverPath = if (opfDir.isEmpty()) href else "$opfDir$href"
            val coverBytes = resolveImageBytes(href, opfDir, imageMap)
                ?: imageMap[fullCoverPath]
            coverBytes?.let { saveCover(context, it, uri.toString(), fullCoverPath) }
        }
```
Add a small helper (replacing `extractCoverFromZip`, keep `resolveImageBytes`/`normalizePath`/`decodeSrc`):
```kotlin
    /** Writes cover [bytes] to filesDir/covers/<stableId>.<ext> and returns its absolute path. */
    private fun saveCover(context: Context, bytes: ByteArray, uriKey: String, coverEntryPath: String): String? =
        try {
            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val ext = coverEntryPath.substringAfterLast('.', "jpg").lowercase()
                .takeIf { it in setOf("jpg", "jpeg", "png", "webp") } ?: "jpg"
            val coverFile = File(coversDir, "${stableId(uriKey)}.$ext")
            coverFile.writeBytes(bytes)
            coverFile.absolutePath
        } catch (_: Exception) {
            null
        }
```
Delete the now-unused `extractCoverFromZip` method (lines 187-215) and the `ZipInputStream`/`java.util.zip.ZipInputStream` import if no longer referenced.

- [ ] **Step 6: Wire the file-size backstop into MobiParser**

In `MobiParser.kt`, add imports:
```kotlin
import com.example.splitreader.domain.parser.util.MAX_DECOMPRESSED
import com.example.splitreader.domain.parser.util.readAllBounded
```
Replace the whole-file read (current lines 35-36):
```kotlin
        val bytes = context.contentResolver.openInputStream(uri)?.use { readAllBounded(it, MAX_DECOMPRESSED) }
            ?: throw IllegalStateException("Cannot open file")
```

- [ ] **Step 7: Add cooperative cancellation to the FB2 pull loop**

In `Fb2Parser.kt`, add imports `import kotlinx.coroutines.ensureActive` and `import kotlin.coroutines.coroutineContext`. Make `parseInternal` a `suspend fun` (its only caller `parse` is already `suspend`), and inside the main `while (eventType != XmlPullParser.END_DOCUMENT)` loop, at the top of the body, insert:
```kotlin
            coroutineContext.ensureActive()
```
Update the call site in `parse` (line 34): `parseInternal(inputStream, uri.toString(), context)` stays the same (already inside a suspend function).

- [ ] **Step 8: Compile, run unit tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; all pass.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/splitreader/domain/parser/util/ZipReading.kt \
  app/src/test/java/com/example/splitreader/domain/parser/util/ZipReadingTest.kt \
  app/src/main/java/com/example/splitreader/domain/parser/EpubParser.kt \
  app/src/main/java/com/example/splitreader/domain/parser/MobiParser.kt \
  app/src/main/java/com/example/splitreader/domain/parser/Fb2Parser.kt
git commit -m "fix(parser): decompressed-size backstop + EPUB cover reuse + FB2 cancellation (P8)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Definition of Done (maps to spec §7)

1. `readUpTo` fills the header despite short reads — MOBI magic detection reliable. *(Task 2)*
2. Overlapping `canParse` resolved deterministically by `priority`. *(Task 3)*
3. A file whose decompressed size exceeds `MAX_DECOMPRESSED` yields `ParseResult.Error` (via `BookTooLargeException`), not OOM. *(Tasks 2, 5)*
4. `u32` offsets with the high bit set stay non-negative; PalmDOC `distance == 0` doesn't corrupt. *(Task 4)*
5. Covers/images are content-addressed (`stableId`); no cross-book overwrite. *(Task 1)*
6. All new JVM unit tests green: `./gradlew :app:testDebugUnitTest`. *(all tasks)*
7. **Regression:** instrumented `ParserBeginningTest` still passes (valid-book parsing unchanged) — run at final review on an emulator:
   `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.splitreader.domain.parser.ParserBeginningTest`
