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
