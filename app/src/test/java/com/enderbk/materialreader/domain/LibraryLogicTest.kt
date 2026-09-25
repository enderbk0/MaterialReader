package com.enderbk.materialreader.domain

import com.enderbk.materialreader.data.DocumentEntry
import com.enderbk.materialreader.data.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryLogicTest {

    private fun entry(
        id: String,
        name: String,
        pinned: Boolean = false,
        opened: Long = 0L,
        size: Long? = null,
        lastPage: Int = 0,
        pages: Int? = null
    ) = DocumentEntry(
        id = id,
        uri = "content://example/$id",
        displayName = name,
        sizeBytes = size,
        pageCount = pages,
        lastPage = lastPage,
        lastOpenedEpochMillis = opened,
        pinned = pinned
    )

    @Test
    fun pinnedAlwaysComeFirstRegardlessOfOrder() {
        val docs = listOf(
            entry("a", "b.pdf", opened = 300),
            entry("b", "a.pdf", pinned = true, opened = 100)
        )
        val byRecent = docs.sortedForLibrary(SortOrder.RECENT)
        assertEquals(listOf("b", "a"), byRecent.map { it.id })
        val byName = docs.sortedForLibrary(SortOrder.NAME)
        assertEquals(listOf("b", "a"), byName.map { it.id })
    }

    @Test
    fun sortsByRecentNameAndSize() {
        val docs = listOf(
            entry("a", "b.pdf", opened = 100, size = 50),
            entry("b", "a.pdf", opened = 300, size = 10),
            entry("c", "c.pdf", opened = 200, size = 99)
        )
        assertEquals(listOf("b", "c", "a"), docs.sortedForLibrary(SortOrder.RECENT).map { it.id })
        assertEquals(listOf("b", "a", "c"), docs.sortedForLibrary(SortOrder.NAME).map { it.id })
        assertEquals(listOf("c", "a", "b"), docs.sortedForLibrary(SortOrder.SIZE).map { it.id })
    }

    @Test
    fun filtersByQueryCaseInsensitively() {
        val docs = listOf(entry("a", "Annual Report.pdf"), entry("b", "notes.pdf"))
        assertEquals(listOf("a"), docs.filteredByQuery("report").map { it.id })
        assertEquals(listOf("b"), docs.filteredByQuery("NOTES").map { it.id })
        assertEquals(2, docs.filteredByQuery("  ").size)
        assertTrue(docs.filteredByQuery("missing").isEmpty())
    }

    @Test
    fun progressIsNullWithoutPageCount() {
        assertNull(entry("a", "x.pdf").progress())
        assertNull(entry("a", "x.pdf", pages = 1, lastPage = 0).progress())
    }

    @Test
    fun progressSpansZeroToOne() {
        assertEquals(0f, entry("a", "x.pdf", pages = 11, lastPage = 0).progress())
        assertEquals(0.5f, entry("a", "x.pdf", pages = 11, lastPage = 5).progress()!!, 0.001f)
        assertEquals(1f, entry("a", "x.pdf", pages = 11, lastPage = 10).progress())
        // Out-of-range saved positions are clamped, never crash.
        assertEquals(1f, entry("a", "x.pdf", pages = 11, lastPage = 99).progress())
    }

    @Test
    fun restorablePositionOnlyPastFirstPage() {
        assertFalse(entry("a", "x.pdf", lastPage = 0).hasRestorablePosition())
        assertTrue(entry("a", "x.pdf", lastPage = 4).hasRestorablePosition())
    }
}
