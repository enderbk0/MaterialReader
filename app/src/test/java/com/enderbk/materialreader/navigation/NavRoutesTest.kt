package com.enderbk.materialreader.navigation

import com.enderbk.materialreader.data.DocumentEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavRoutesTest {

    @Test
    fun readerRouteByDocumentId() {
        assertEquals("reader?docId=abc123", Routes.readerForDocument("abc123"))
    }

    @Test
    fun topLevelRoutesAreDistinct() {
        assertEquals("library", Routes.LIBRARY)
        assertEquals("settings", Routes.SETTINGS)
        assertEquals("about", Routes.ABOUT)
        assertEquals("experimental", Routes.EXPERIMENTAL)
        assertEquals("welcome", Routes.WELCOME)
    }

    @Test
    fun readerPatternDeclaresBothArguments() {
        assertTrue(Routes.READER_PATTERN.contains("{docId}"))
        assertTrue(Routes.READER_PATTERN.contains("{uri}"))
        assertEquals(2, Routes.readerArguments.size)
        assertEquals(
            listOf(Routes.ARG_DOC_ID, Routes.ARG_URI),
            Routes.readerArguments.map { it.name }
        )
    }

    @Test
    fun documentIdsAreStableAndDistinct() {
        val uri = "content://com.example.provider/document/42"
        assertEquals(DocumentEntry.idForUri(uri), DocumentEntry.idForUri(uri))
        assertNotEquals(
            DocumentEntry.idForUri(uri),
            DocumentEntry.idForUri("content://com.example.provider/document/43")
        )
    }
}
