package com.enderbk.materialreader.pdf

import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfErrorsTest {

    @Test
    fun mapsKnownExceptions() {
        assertEquals(PdfOpenFailure.FileNotFound, mapOpenError(FileNotFoundException("gone")))
        assertEquals(PdfOpenFailure.PermissionLost, mapOpenError(SecurityException("denied")))
        assertEquals(
            PdfOpenFailure.PasswordProtected,
            mapOpenError(RuntimeException("document is encrypted"))
        )
        assertEquals(
            PdfOpenFailure.PasswordProtected,
            mapOpenError(RuntimeException("needs password"))
        )
        assertEquals(
            PdfOpenFailure.CorruptOrUnsupported,
            mapOpenError(RuntimeException("cannot parse header"))
        )
    }

    @Test
    fun passesThroughExplicitFailures() {
        val failure = PdfOpenFailure.PermissionLost
        assertEquals(failure, mapOpenError(PdfOpenException(failure)))
    }

    @Test
    fun messagesNameTheDocument() {
        val message = PdfOpenFailure.FileNotFound.userMessage("Report.pdf")
        assertTrue(message.contains("Report.pdf"))
    }

    @Test
    fun snippetCentersOnMatch() {
        val text = "lorem ipsum dolor sit amet consectetur adipiscing elit sed do"
        val at = text.indexOf("consectetur")
        val snippet = snippetAround(text, at, "consectetur".length)
        assertTrue(snippet.contains("consectetur"))
        // Short text needs no ellipsis.
        assertTrue(!snippet.startsWith("…") && !snippet.endsWith("…"))
    }

    @Test
    fun snippetTruncatesLongText() {
        val text = "a ".repeat(500) + "NEEDLE" + " b".repeat(500)
        val snippet = snippetAround(text, text.indexOf("NEEDLE"), 6)
        assertTrue(snippet.contains("NEEDLE"))
        assertTrue(snippet.startsWith("…") && snippet.endsWith("…"))
        assertTrue(snippet.length < text.length)
    }
}
