package com.enderbk.materialreader.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentStoreTest {

    private fun entry(id: String, name: String = "$id.pdf") = DocumentEntry(
        id = id,
        uri = "content://example/$id",
        displayName = name
    )

    @Test
    fun upsertAddsAndReplacesById() = runTest {
        val store = InMemoryDocumentStore()
        store.upsert(entry("a", "First.pdf"))
        store.upsert(entry("b"))
        assertEquals(2, store.documents.first().size)

        store.upsert(entry("a", "Renamed.pdf"))
        val docs = store.documents.first()
        assertEquals(2, docs.size)
        assertEquals("Renamed.pdf", docs.first { it.id == "a" }.displayName)
    }

    @Test
    fun removeDropsEntry() = runTest {
        val store = InMemoryDocumentStore(listOf(entry("a"), entry("b")))
        store.remove("a")
        assertEquals(listOf("b"), store.documents.first().map { it.id })
    }

    @Test
    fun pinTogglesFlag() = runTest {
        val store = InMemoryDocumentStore(listOf(entry("a")))
        store.setPinned("a", true)
        assertTrue(store.get("a")!!.pinned)
        store.setPinned("a", false)
        assertFalse(store.get("a")!!.pinned)
    }

    @Test
    fun readingPositionPersistsPageAndCount() = runTest {
        val store = InMemoryDocumentStore(listOf(entry("a")))
        store.setReadingPosition("a", 7, 42)
        val got = store.get("a")!!
        assertEquals(7, got.lastPage)
        assertEquals(42, got.pageCount)
        assertTrue(got.lastOpenedEpochMillis > 0)
    }

    @Test
    fun readingPositionKeepsKnownPageCountWhenNull() = runTest {
        val store = InMemoryDocumentStore(
            listOf(entry("a").copy(pageCount = 42))
        )
        store.setReadingPosition("a", 3, null)
        assertEquals(42, store.get("a")!!.pageCount)
    }

    @Test
    fun getMissingReturnsNull() = runTest {
        assertNull(InMemoryDocumentStore().get("nope"))
    }
}

class SettingsStoreTest {

    @Test
    fun defaultsAreSane() = runTest {
        val settings = InMemorySettingsStore().settings.first()
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertTrue(settings.dynamicColor)
        assertEquals(ZoomMode.FIT_WIDTH, settings.defaultZoomMode)
        assertEquals(PageLayout.CONTINUOUS, settings.pageLayout)
        assertFalse(settings.keepScreenAwake)
        assertTrue(settings.rememberReadingPosition)
        assertEquals(ReaderBackground.DEFAULT, settings.readerBackground)
        assertFalse(settings.nightMode)
        assertFalse(settings.experimentalEnabled)
        assertFalse(settings.floatingNavBar)
        assertEquals(SortOrder.RECENT, settings.sortOrder)
    }

    @Test
    fun experimentalFlagsToggle() = runTest {
        val store = InMemorySettingsStore()
        store.update { it.copy(experimentalEnabled = true, floatingNavBar = true) }
        val settings = store.settings.first()
        assertTrue(settings.experimentalEnabled)
        assertTrue(settings.floatingNavBar)
    }

    fun updatesApplyInOrder() = runTest {
        val store = InMemorySettingsStore()
        store.update { it.copy(themeMode = ThemeMode.DARK) }
        store.update { it.copy(defaultZoomMode = ZoomMode.FIT_PAGE, keepScreenAwake = true) }
        val settings = store.settings.first()
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(ZoomMode.FIT_PAGE, settings.defaultZoomMode)
        assertTrue(settings.keepScreenAwake)
        // Untouched values keep defaults.
        assertTrue(settings.rememberReadingPosition)
    }
}
