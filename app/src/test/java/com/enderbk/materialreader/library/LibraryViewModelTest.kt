package com.enderbk.materialreader.library

import com.enderbk.materialreader.FakeDocumentMetaSource
import com.enderbk.materialreader.MainDispatcherRule
import com.enderbk.materialreader.data.DocumentEntry
import com.enderbk.materialreader.data.InMemoryDocumentStore
import com.enderbk.materialreader.data.InMemorySettingsStore
import com.enderbk.materialreader.data.SortOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var docs: InMemoryDocumentStore
    private lateinit var settings: InMemorySettingsStore
    private lateinit var meta: FakeDocumentMetaSource
    private lateinit var vm: LibraryViewModel

    @Before
    fun setup() {
        docs = InMemoryDocumentStore()
        settings = InMemorySettingsStore()
        meta = FakeDocumentMetaSource()
        vm = LibraryViewModel(docs, settings, meta, mainRule.dispatcher)
    }

    @Test
    fun pickedDocumentIsAddedAndOpened() = runTest(mainRule.dispatcher) {
        val opened = mutableListOf<String>()
        val c1 = launch { vm.openDocument.collect { opened += it } }
        try {
            vm.onDocumentPicked("content://com.example/picked")
            advanceUntilIdle()

            val stored = docs.documents.first()
            assertEquals(1, stored.size)
            assertEquals("Picked.pdf", stored[0].displayName)
            assertEquals(4242L, stored[0].sizeBytes)
            assertEquals(listOf(stored[0].id), opened)
            assertEquals(listOf("content://com.example/picked"), meta.persisted)
        } finally {
            c1.cancel()
        }
    }

    @Test
    fun rePickingKeepsExistingMetadata() = runTest(mainRule.dispatcher) {
        val opened = mutableListOf<String>()
        val c1 = launch { vm.openDocument.collect { opened += it } }
        try {
            val uri = "content://com.example/same"
            val id = DocumentEntry.idForUri(uri)
            docs.upsert(
                DocumentEntry(id, uri, "Original.pdf", sizeBytes = 11, lastPage = 5, pageCount = 9)
            )
            meta.meta = com.enderbk.materialreader.util.DocumentMeta("Changed.pdf", 99)

            vm.onDocumentPicked(uri)
            advanceUntilIdle()

            val stored = docs.get(id)!!
            // The user's library name wins over a changed provider name.
            assertEquals("Original.pdf", stored.displayName)
            assertEquals(99L, stored.sizeBytes)
            assertEquals(5, stored.lastPage)
            assertEquals(listOf(id), opened)
        } finally {
            c1.cancel()
        }
    }

    @Test
    fun queryFiltersAndSortPersists() = runTest(mainRule.dispatcher) {
        docs.upsert(DocumentEntry("1", "u1", "Bravo.pdf"))
        docs.upsert(DocumentEntry("2", "u2", "Alpha.pdf"))
        val states = mutableListOf<LibraryUiState>()
        val c1 = launch { vm.uiState.collect { states.add(it) } }
        try {
            advanceUntilIdle()

            vm.onQueryChange("alpha")
            advanceUntilIdle()
            assertEquals(listOf("2"), states.last().visible.map { it.id })

            vm.onQueryChange("")
            vm.onSortChange(SortOrder.NAME)
            advanceUntilIdle()
            assertEquals(listOf("2", "1"), states.last().visible.map { it.id })
            assertEquals(SortOrder.NAME, settings.settings.first().sortOrder)
        } finally {
            c1.cancel()
        }
    }

    @Test
    fun togglePinAndRemove() = runTest(mainRule.dispatcher) {
        docs.upsert(DocumentEntry("1", "u1", "Doc.pdf"))
        advanceUntilIdle()

        vm.onTogglePin(docs.get("1")!!)
        advanceUntilIdle()
        assertTrue(docs.get("1")!!.pinned)

        vm.onRemove(docs.get("1")!!)
        advanceUntilIdle()
        assertTrue(docs.documents.first().isEmpty())
        assertEquals(listOf("u1"), meta.released)
    }

    @Test
    fun foldersMoveFilterAndVanish() = runTest(mainRule.dispatcher) {
        docs.upsert(DocumentEntry("1", "u1", "Doc.pdf"))
        docs.upsert(DocumentEntry("2", "u2", "Other.pdf"))
        val states = mutableListOf<LibraryUiState>()
        val c1 = launch { vm.uiState.collect { states.add(it) } }
        try {
            advanceUntilIdle()
            assertTrue(states.last().folders.isEmpty())

            vm.onMoveToFolder(docs.get("1")!!, "  Work  ")
            advanceUntilIdle()
            assertEquals("Work", docs.get("1")!!.folder)
            assertEquals(listOf("Work"), states.last().folders)

            vm.onSelectFolder("Work")
            advanceUntilIdle()
            assertEquals(listOf("1"), states.last().visible.map { it.id })

            vm.onMoveToFolder(docs.get("1")!!, null)
            advanceUntilIdle()
            assertEquals(null, docs.get("1")!!.folder)
            assertTrue(states.last().folders.isEmpty())
        } finally {
            c1.cancel()
        }
    }

    @Test
    fun customFoldersPersistFilterAndDelete() = runTest(mainRule.dispatcher) {
        docs.upsert(DocumentEntry("1", "u1", "Doc.pdf"))
        val states = mutableListOf<LibraryUiState>()
        val c1 = launch { vm.uiState.collect { states.add(it) } }
        try {
            vm.onCreateFolder("  Docs  ")
            advanceUntilIdle()
            assertEquals(listOf("Docs"), states.last().folders)

            vm.onMoveToFolder(docs.get("1")!!, "Docs")
            vm.onSelectFolder("Docs")
            advanceUntilIdle()
            assertEquals(listOf("1"), states.last().visible.map { it.id })

            vm.onDeleteFolder("Docs")
            advanceUntilIdle()
            assertTrue(states.last().folders.isEmpty())
            assertEquals(null, docs.get("1")!!.folder)
            assertEquals(listOf("1"), states.last().visible.map { it.id })
        } finally {
            c1.cancel()
        }
    }

    @Test
    fun relinkClearsMissingAndOpens() = runTest(mainRule.dispatcher) {
        docs.upsert(DocumentEntry("9", "content://gone", "Gone.pdf", missing = true))
        val opened = mutableListOf<String>()
        val c1 = launch { vm.openDocument.collect { opened += it } }
        try {
            vm.onRelink(docs.get("9")!!, "content://com.example/found")
            advanceUntilIdle()
            val got = docs.get("9")!!
            assertEquals("content://com.example/found", got.uri)
            assertEquals("Picked.pdf", got.displayName)
            assertEquals(false, got.missing)
            assertEquals(listOf("9"), opened)
        } finally {
            c1.cancel()
        }
    }

    @Test
    fun moveFollowsDocumentAcrossFilters() = runTest(mainRule.dispatcher) {
        docs.upsert(DocumentEntry("1", "u1", "Doc.pdf"))
        val states = mutableListOf<LibraryUiState>()
        val c1 = launch { vm.uiState.collect { states.add(it) } }
        try {
            advanceUntilIdle()

            vm.onMoveToFolder(docs.get("1")!!, "Work")
            advanceUntilIdle()
            assertEquals("Work", states.last().selectedFolder)
            assertEquals(listOf("1"), states.last().visible.map { it.id })

            // Moving out lands back on All instead of an empty filter.
            vm.onMoveToFolder(docs.get("1")!!, null)
            advanceUntilIdle()
            assertEquals(null, states.last().selectedFolder)
            assertEquals(listOf("1"), states.last().visible.map { it.id })
        } finally {
            c1.cancel()
        }
    }

    @Test
    fun openingEntryTouchesLastOpenedAndNavigates() = runTest(mainRule.dispatcher) {        docs.upsert(DocumentEntry("1", "u1", "Doc.pdf", lastOpenedEpochMillis = 0))
        val opened = mutableListOf<String>()
        val c1 = launch { vm.openDocument.collect { opened += it } }
        try {
            vm.onOpenEntry(docs.get("1")!!)
            advanceUntilIdle()

            assertTrue(docs.get("1")!!.lastOpenedEpochMillis > 0)
            assertEquals(listOf("1"), opened)
        } finally {
            c1.cancel()
        }
    }
}
