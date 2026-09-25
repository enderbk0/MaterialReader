package com.enderbk.materialreader.reader

import com.enderbk.materialreader.FakeReaderBackend
import com.enderbk.materialreader.MainDispatcherRule
import com.enderbk.materialreader.data.DocumentEntry
import com.enderbk.materialreader.data.InMemoryDocumentStore
import com.enderbk.materialreader.data.InMemorySettingsStore
import com.enderbk.materialreader.data.PageLayout
import com.enderbk.materialreader.data.ZoomMode
import com.enderbk.materialreader.pdf.PageLink
import com.enderbk.materialreader.pdf.PdfOpenFailure
import com.enderbk.materialreader.pdf.TextHit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var docs: InMemoryDocumentStore
    private lateinit var settings: InMemorySettingsStore
    private lateinit var backend: FakeReaderBackend

    private val entry = DocumentEntry(
        id = "doc1",
        uri = "content://com.example/doc1",
        displayName = "Doc.pdf",
        lastPage = 4,
        pageCount = 10
    )

    @Before
    fun setup() {
        docs = InMemoryDocumentStore(listOf(entry))
        settings = InMemorySettingsStore()
        backend = FakeReaderBackend(pageCount = 10)
    }

    private fun vm(
        docId: String? = "doc1",
        rawUri: String? = null,
        vmSettings: InMemorySettingsStore = settings
    ) = ReaderViewModel(docId, rawUri, docs, vmSettings, backend)

    @Test
    fun bootstrapOpensAtSavedPosition() = runTest(mainRule.dispatcher) {
        val viewModel = vm()
        val states = mutableListOf<ReaderUiState>()
        val collect = launch { viewModel.ui.collect { states.add(it) } }
        advanceUntilIdle()

        val last = states.last()
        assertEquals(ReaderStatus.Ready, last.status)
        assertEquals(10, last.pageCount)
        assertEquals(4, last.currentPage)
        assertEquals(listOf("content://com.example/doc1"), backend.openedSessions)
        collect.cancel()
    }

    @Test
    fun missingEntryReportsFileNotFound() = runTest(mainRule.dispatcher) {
        val viewModel = vm(docId = "absent")
        val states = mutableListOf<ReaderUiState>()
        val collect = launch { viewModel.ui.collect { states.add(it) } }
        advanceUntilIdle()

        assertEquals(ReaderStatus.Error(PdfOpenFailure.FileNotFound), states.last().status)
        collect.cancel()
    }

    @Test
    fun externalUriCreatesLibraryEntry() = runTest(mainRule.dispatcher) {
        val viewModel = vm(docId = null, rawUri = "content://com.example/shared")
        val states = mutableListOf<ReaderUiState>()
        val collect = launch { viewModel.ui.collect { states.add(it) } }
        advanceUntilIdle()

        val last = states.last()
        assertEquals(ReaderStatus.Ready, last.status)
        assertEquals("Shared.pdf", last.entry!!.displayName)
        collect.cancel()
    }

    @Test
    fun openFailureSurfacesError() = runTest(mainRule.dispatcher) {
        backend.openFailure = PdfOpenFailure.PasswordProtected
        val viewModel = vm()
        val states = mutableListOf<ReaderUiState>()
        val collect = launch { viewModel.ui.collect { states.add(it) } }
        advanceUntilIdle()

        assertEquals(
            ReaderStatus.Error(PdfOpenFailure.PasswordProtected),
            states.last().status
        )
        collect.cancel()
    }

    @Test
    fun lostPermissionSurfacesError() = runTest(mainRule.dispatcher) {
        backend.canOpenResult = false
        val viewModel = vm()
        val states = mutableListOf<ReaderUiState>()
        val collect = launch { viewModel.ui.collect { states.add(it) } }
        advanceUntilIdle()

        assertEquals(
            ReaderStatus.Error(PdfOpenFailure.PermissionLost),
            states.last().status
        )
        collect.cancel()
    }

    @Test
    fun pageChangesPersistDebounced() = runTest(mainRule.dispatcher) {
        val viewModel = vm()
        val states = mutableListOf<ReaderUiState>()
        val collect = launch { viewModel.ui.collect { states.add(it) } }
        advanceUntilIdle()

        viewModel.onPageSettled(8)
        runCurrent()
        assertEquals(8, states.last().currentPage)
        // Debounce window: the position is not saved before time passes.
        assertEquals(4, docs.get("doc1")!!.lastPage)
        advanceTimeBy(700)
        advanceUntilIdle()
        assertEquals(8, docs.get("doc1")!!.lastPage)
        collect.cancel()
    }

    @Test
    fun rememberPositionOffStartsAtZeroAndSkipsSave() = runTest(mainRule.dispatcher) {
        val noRemember = InMemorySettingsStore()
        noRemember.update { it.copy(rememberReadingPosition = false) }
        val viewModel = vm(vmSettings = noRemember)
        val states = mutableListOf<ReaderUiState>()
        val collect = launch { viewModel.ui.collect { states.add(it) } }
        advanceUntilIdle()

        assertEquals(0, states.last().currentPage)
        viewModel.onPageSettled(3)
        advanceTimeBy(1000)
        advanceUntilIdle()
        assertEquals(4, docs.get("doc1")!!.lastPage)
        collect.cancel()
    }

    @Test
    fun zoomAndLayoutUpdateStateAndSettings() = runTest(mainRule.dispatcher) {
        val viewModel = vm()
        advanceUntilIdle()

        viewModel.onUserScale(9f)
        assertEquals(5f, viewModel.ui.value.userScale, 0.001f)
        viewModel.onUserScale(0.1f)
        assertEquals(0.5f, viewModel.ui.value.userScale, 0.001f)

        viewModel.onZoomModeChange(ZoomMode.FIT_PAGE)
        advanceUntilIdle()
        assertEquals(ZoomMode.FIT_PAGE, viewModel.ui.value.zoomMode)
        assertEquals(ZoomMode.FIT_PAGE, settings.settings.first().defaultZoomMode)

        viewModel.onLayoutChange(PageLayout.SINGLE_PAGE)
        advanceUntilIdle()
        assertEquals(PageLayout.SINGLE_PAGE, viewModel.ui.value.layout)

        viewModel.onJumpRequest(99)
        assertEquals(9, viewModel.ui.value.currentPage)
        viewModel.onNextPage()
        assertEquals(9, viewModel.ui.value.currentPage) // clamped at end
        viewModel.onPrevPage()
        assertEquals(8, viewModel.ui.value.currentPage)
    }

    @Test
    fun nightModeTogglePersists() = runTest(mainRule.dispatcher) {
        val viewModel = vm()
        advanceUntilIdle()
        assertFalse(viewModel.ui.value.nightMode)

        viewModel.onNightModeChange(true)
        advanceUntilIdle()
        assertTrue(viewModel.ui.value.nightMode)
        assertTrue(settings.settings.first().nightMode)

        viewModel.onNightModeChange(false)
        advanceUntilIdle()
        assertFalse(viewModel.ui.value.nightMode)
    }

    @Test
    fun searchDebouncesAndJumpsToHit() = runTest(mainRule.dispatcher) {
        backend.searchResult = listOf(TextHit(6, "…found…"), TextHit(7, "…found again…"))
        val viewModel = vm()
        advanceUntilIdle()

        viewModel.setSearchActive(true)
        viewModel.onSearchQueryChange("found")
        assertTrue(viewModel.ui.value.searchRunning)
        advanceTimeBy(500)
        advanceUntilIdle()
        assertFalse(viewModel.ui.value.searchRunning)
        assertEquals(2, viewModel.ui.value.searchHits.size)

        viewModel.onSearchHitClick(TextHit(6, "…found…"))
        assertFalse(viewModel.ui.value.searchActive)
        // The list consumes the request and settles on the page (simulated here).
        assertEquals(6, viewModel.consumeScrollRequest())
        viewModel.onPageSettled(6)
        assertEquals(6, viewModel.ui.value.currentPage)
    }

    @Test
    fun textSheetLoadsTextAndLinks() = runTest(mainRule.dispatcher) {
        backend.pageTextResult = "Hello PDF"
        backend.pageLinksResult = listOf(PageLink(4, "https://example.com", "Example"))
        val viewModel = vm()
        advanceUntilIdle()

        viewModel.setTextSheet(true)
        advanceUntilIdle()
        assertTrue(viewModel.ui.value.textSheetOpen)
        assertEquals("Hello PDF", viewModel.ui.value.pageText)
        assertEquals(1, viewModel.ui.value.pageLinks.size)
    }
}
