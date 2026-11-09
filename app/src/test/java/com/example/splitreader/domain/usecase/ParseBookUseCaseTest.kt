package com.example.splitreader.domain.usecase

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.domain.CrashReporter
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.ParseResult
import com.example.splitreader.domain.parser.BookParser
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

private class FakeCrashReporter : CrashReporter {
    val recorded = mutableListOf<Throwable>()
    override fun recordNonFatal(throwable: Throwable, message: String?) { recorded.add(throwable) }
}

private class ThrowingParser(private val toThrow: Exception) : BookParser {
    override val supportedExtensions = listOf("epub")
    override suspend fun parse(uri: Uri, context: Context): Book = throw toThrow
}

/**
 * Disk-full while writing a book's temp pictures surfaces as a raw `IOException("No space left on
 * device")` from the OS; it must be mapped to a clear user message here, the one place every
 * parser's exception already flows through, rather than shown verbatim.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ParseBookUseCaseTest {

    private fun useCase(parser: BookParser) =
        ParseBookUseCase(setOf(parser), ApplicationProvider.getApplicationContext(), FakeCrashReporter())

    private fun uriForEpub(): Uri {
        val f = File.createTempFile("book", ".epub")
        f.writeBytes(byteArrayOf(1, 2, 3))
        return Uri.fromFile(f)
    }

    @Test fun diskFullIOException_mapsToAClearMessage() = runTest {
        val results = useCase(ThrowingParser(IOException("No space left on device")))
            .invoke(uriForEpub()).toList()
        val error = results.last() as ParseResult.Error
        assertEquals("Not enough free space to open this book.", error.message)
    }

    @Test fun otherIOException_isNotMisreportedAsDiskFull() = runTest {
        val results = useCase(ThrowingParser(IOException("Connection reset")))
            .invoke(uriForEpub()).toList()
        val error = results.last() as ParseResult.Error
        assertEquals("Connection reset", error.message)
    }

    @Test fun nonIOExceptions_areUnaffected() = runTest {
        val results = useCase(ThrowingParser(IllegalStateException("No chapters found in fb2 file")))
            .invoke(uriForEpub()).toList()
        val error = results.last() as ParseResult.Error
        assertEquals("No chapters found in fb2 file", error.message)
    }
}
