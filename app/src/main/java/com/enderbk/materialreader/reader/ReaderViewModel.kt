package com.enderbk.materialreader.reader

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.enderbk.materialreader.data.AppSettings
import com.enderbk.materialreader.data.DocumentEntry
import com.enderbk.materialreader.data.DocumentStore
import com.enderbk.materialreader.data.PageLayout
import com.enderbk.materialreader.data.SettingsStore
import com.enderbk.materialreader.data.ZoomMode
import com.enderbk.materialreader.pdf.PageLink
import com.enderbk.materialreader.pdf.NightPageMode
import com.enderbk.materialreader.pdf.PdfOpenException
import com.enderbk.materialreader.pdf.PdfOpenFailure
import com.enderbk.materialreader.pdf.PdfDocument
import com.enderbk.materialreader.pdf.ReaderBackend
import com.enderbk.materialreader.pdf.TextHit
import com.enderbk.materialreader.pdf.mapOpenError
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

sealed interface ReaderStatus {
    data object Loading : ReaderStatus
    data object Ready : ReaderStatus
    data class Error(val failure: PdfOpenFailure) : ReaderStatus
}

data class ReaderUiState(
    val status: ReaderStatus = ReaderStatus.Loading,
    val entry: DocumentEntry? = null,
    val pageCount: Int = 0,
    /** 0-based current page. */
    val currentPage: Int = 0,
    /** User pinch/button zoom multiplier, 0.5..5. */
    val userScale: Float = 1f,
    val zoomMode: ZoomMode = ZoomMode.FIT_WIDTH,
    val layout: PageLayout = PageLayout.CONTINUOUS,
    val rememberPosition: Boolean = true,
    val nightMode: Boolean = false,
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val searchRunning: Boolean = false,
    val searchHits: List<TextHit> = emptyList(),
    val searchSearched: Boolean = false,
    /** One-shot page jump request consumed by the list. Null when none. */
    val scrollRequest: Int? = null,
    val textSheetOpen: Boolean = false,
    val pageTextLoading: Boolean = false,
    val pageText: String = "",
    val pageLinks: List<PageLink> = emptyList(),
    val jumpDialogOpen: Boolean = false
)

private const val SAVE_DEBOUNCE_MS = 600L
private const val SEARCH_DEBOUNCE_MS = 450L
private const val MIN_SCALE = 0.5f
private const val MAX_SCALE = 5f

class ReaderViewModel(
    private val docId: String?,
    private val rawUri: String?,
    private val documents: DocumentStore,
    private val settings: SettingsStore,
    private val backend: ReaderBackend
) : ViewModel() {

    private val _ui = MutableStateFlow(ReaderUiState())
    val ui: StateFlow<ReaderUiState> = _ui.asStateFlow()

    private var document: PdfDocument? = null
    private var saveJob: Job? = null
    private var searchJob: Job? = null
    private var appSettings: AppSettings = AppSettings()

    init {
        viewModelScope.launch { bootstrap() }
        // Single source of truth: the settings store owns night mode, so the
        // quick sheet, the overflow menu, and the Settings screen all drive
        // the live reader through this flow. The echo of our own writes is
        // suppressed by distinctUntilChanged + the equality guard.
        viewModelScope.launch {
            settings.settings
                .map { it.nightMode }
                .distinctUntilChanged()
                .collect { enabled ->
                    _ui.update { state ->
                        if (state.nightMode == enabled) state
                        else state.copy(nightMode = enabled)
                    }
                }
        }
    }

    private suspend fun bootstrap() {
        appSettings = settings.settings.first()
        _ui.update {
            it.copy(
                zoomMode = appSettings.defaultZoomMode,
                layout = appSettings.pageLayout,
                rememberPosition = appSettings.rememberReadingPosition,
                nightMode = appSettings.nightMode
            )
        }
        // Resolve the library entry: by id, or create one from an external URI.
        var entry: DocumentEntry? = docId?.let {
            runCatching { documents.get(it) }.getOrNull()
        }
        if (entry == null) {
            val external = rawUri
            if (external != null) {
                runCatching { backend.persistPermission(external) }
                val meta = runCatching { backend.describe(external) }.getOrNull()
                val id = DocumentEntry.idForUri(external)
                entry = (runCatching { documents.get(id) }.getOrNull() ?: DocumentEntry(
                    id = id,
                    uri = external,
                    displayName = meta?.displayName ?: "Document.pdf",
                    sizeBytes = meta?.sizeBytes
                )).copy(lastOpenedEpochMillis = System.currentTimeMillis())
                documents.upsert(entry)
            }
        }
        if (entry == null) {
            _ui.update { it.copy(status = ReaderStatus.Error(PdfOpenFailure.FileNotFound)) }
            return
        }
        if (!backend.canOpen(entry.uri)) {
            // The file is gone (or its grant died): flag it missing so the
            // library can offer relink/remove instead of a dead error panel.
            val missing = entry.copy(missing = true)
            documents.upsert(missing)
            _ui.update {
                it.copy(
                    entry = missing,
                    status = ReaderStatus.Error(PdfOpenFailure.FileNotFound)
                )
            }
            return
        }
        try {
            val opened = backend.openSession(entry.uri)
            document = opened
            val count = opened.pageCount
            val start = if (appSettings.rememberReadingPosition) {
                entry.lastPage.coerceIn(0, (count - 1).coerceAtLeast(0))
            } else {
                0
            }
            documents.upsert(entry.copy(pageCount = count, lastOpenedEpochMillis = System.currentTimeMillis()))
            _ui.update {
                it.copy(
                    status = ReaderStatus.Ready,
                    entry = entry.copy(pageCount = count),
                    pageCount = count,
                    currentPage = start,
                    scrollRequest = start.takeIf { s -> s != 0 }
                )
            }
        } catch (e: PdfOpenException) {
            _ui.update { it.copy(entry = entry, status = ReaderStatus.Error(e.failure)) }
        } catch (e: Exception) {
            _ui.update { it.copy(entry = entry, status = ReaderStatus.Error(mapOpenError(e))) }
        }
    }

    /** Renders through the session; null when the document is not open. */
    suspend fun renderPage(index: Int, widthPx: Int): Bitmap? {
        val d = document ?: return null
        return runCatching { backend.renderPage(d, index, widthPx) }.getOrNull()
    }

    /** Night variant (pictures preserved); null when the document is not open. */
    suspend fun renderNightPage(index: Int, widthPx: Int): Bitmap? {
        val d = document ?: return null
        return runCatching { backend.renderNightPage(d, index, widthPx) }.getOrNull()
    }

    /** Classification used for [index], or null when the document is not open. */
    suspend fun pageNightMode(index: Int): NightPageMode? {
        val d = document ?: return null
        return runCatching { backend.pageMode(d, index) }.getOrNull()
    }

    fun pageAspectPoints(index: Int): Float? {
        val size = pageSizePoints(index) ?: return null
        return if (size.second > 0) size.first.toFloat() / size.second.toFloat() else null
    }

    /** Page size in PDF points (1/72 inch), or null when unavailable. */
    fun pageSizePoints(index: Int): Pair<Int, Int>? {
        val d = document ?: return null
        return runCatching { d.pageSizePoints(index) }.getOrNull()
    }

    fun consumeScrollRequest(): Int? {
        val target = _ui.value.scrollRequest ?: return null
        _ui.update { it.copy(scrollRequest = null) }
        return target
    }

    fun onPageSettled(page: Int) {
        val clamped = page.coerceIn(0, (_ui.value.pageCount - 1).coerceAtLeast(0))
        val changed = clamped != _ui.value.currentPage
        _ui.update { it.copy(currentPage = clamped) }
        if (!changed) return
        schedulePositionSave()
    }

    private fun schedulePositionSave() {
        val entry = _ui.value.entry ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            if (_ui.value.rememberPosition) {
                documents.setReadingPosition(entry.id, _ui.value.currentPage, _ui.value.pageCount)
            }
        }
    }

    fun onUserScale(scale: Float) {
        val clamped = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        // Throttle pinch streams: micro-changes would recompose the whole
        // reader per gesture event. Below 0.5% nothing visible changes.
        val current = _ui.value.userScale
        if (current != 0f && abs(clamped - current) / current < 0.005f) return
        _ui.update { it.copy(userScale = clamped) }
    }

    fun onToggleDoubleTapZoom() {
        val current = _ui.value.userScale
        val next = if (current > 1.25f) 1f else 2.5f
        _ui.update {
            it.copy(
                userScale = next,
                // Re-anchor on the current page so the view does not drift
                // while the layout grows around it.
                scrollRequest = it.currentPage
            )
        }
    }

    /**
     * Discrete zoom step (buttons, menu). Unlike pinch it re-anchors the
     * list on the current page so zooming never shifts position.
     */
    fun zoomBy(factor: Float) {
        val clamped = (_ui.value.userScale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        _ui.update { it.copy(userScale = clamped, scrollRequest = it.currentPage) }
    }

    fun onZoomModeChange(mode: ZoomMode) {
        document?.clearCache()
        _ui.update { it.copy(zoomMode = mode, userScale = 1f) }
        viewModelScope.launch { settings.update { s -> s.copy(defaultZoomMode = mode) } }
    }

    fun onLayoutChange(layout: PageLayout) {
        _ui.update { it.copy(layout = layout) }
        viewModelScope.launch { settings.update { s -> s.copy(pageLayout = layout) } }
    }

    fun onNightModeChange(enabled: Boolean) {
        _ui.update { it.copy(nightMode = enabled) }
        viewModelScope.launch { settings.update { s -> s.copy(nightMode = enabled) } }
    }

    fun onJumpRequest(page: Int) {
        val clamped = page.coerceIn(0, (_ui.value.pageCount - 1).coerceAtLeast(0))
        _ui.update { it.copy(jumpDialogOpen = false, currentPage = clamped, scrollRequest = clamped) }
        schedulePositionSave()
    }

    fun onNextPage() = onJumpRequest(_ui.value.currentPage + 1)

    fun onPrevPage() = onJumpRequest(_ui.value.currentPage - 1)

    fun setJumpDialog(open: Boolean) {
        _ui.update { it.copy(jumpDialogOpen = open) }
    }

    fun setSearchActive(active: Boolean) {
        searchJob?.cancel()
        _ui.update {
            it.copy(
                searchActive = active,
                searchQuery = if (active) it.searchQuery else "",
                searchHits = if (active) it.searchHits else emptyList(),
                searchSearched = if (active) it.searchSearched else false
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _ui.update { it.copy(searchQuery = query, searchRunning = true) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val uriString = _ui.value.entry?.uri
            if (query.isBlank() || uriString == null) {
                _ui.update { it.copy(searchRunning = false, searchHits = emptyList(), searchSearched = query.isNotBlank()) }
                return@launch
            }
            val hits = runCatching { backend.search(uriString, query) }.getOrDefault(emptyList())
            _ui.update { it.copy(searchRunning = false, searchHits = hits, searchSearched = true) }
        }
    }

    fun onSearchHitClick(hit: TextHit) {
        _ui.update { it.copy(scrollRequest = hit.pageIndex, searchActive = false) }
    }

    fun setTextSheet(open: Boolean) {
        _ui.update { it.copy(textSheetOpen = open) }
        if (open) reloadSheetContent()
    }

    private fun reloadSheetContent() {
        viewModelScope.launch {
            val entry = _ui.value.entry ?: return@launch
            val uriString = entry.uri
            val page = _ui.value.currentPage
            _ui.update { it.copy(pageTextLoading = true, pageText = "", pageLinks = emptyList()) }
            val text = runCatching { backend.pageText(uriString, page) }.getOrDefault("")
            val links = runCatching { backend.pageLinks(uriString, page) }.getOrDefault(emptyList())
            _ui.update { it.copy(pageTextLoading = false, pageText = text, pageLinks = links) }
        }
    }

    fun removeFromLibrary() {
        viewModelScope.launch {
            _ui.value.entry?.let { documents.remove(it.id) }
        }
    }

    fun retry() {
        _ui.update { it.copy(status = ReaderStatus.Loading) }
        viewModelScope.launch { bootstrap() }
    }

    override fun onCleared() {
        runCatching { document?.close() }
        document = null
        super.onCleared()
    }
}

@Suppress("UNCHECKED_CAST")
fun readerViewModelFactory(
    docId: String?,
    rawUri: String?,
    documents: DocumentStore,
    settings: SettingsStore,
    backend: ReaderBackend
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ReaderViewModel(docId, rawUri, documents, settings, backend) as T
    }
}
