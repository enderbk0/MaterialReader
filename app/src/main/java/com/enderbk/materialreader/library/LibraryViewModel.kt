package com.enderbk.materialreader.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import com.enderbk.materialreader.data.DocumentEntry
import com.enderbk.materialreader.data.DocumentStore
import com.enderbk.materialreader.data.SettingsStore
import com.enderbk.materialreader.data.SortOrder
import com.enderbk.materialreader.domain.distinctFolders
import com.enderbk.materialreader.domain.filteredByFolder
import com.enderbk.materialreader.domain.filteredByQuery
import com.enderbk.materialreader.domain.mergeFolders
import com.enderbk.materialreader.domain.sortedForLibrary
import com.enderbk.materialreader.util.DocumentMeta
import com.enderbk.materialreader.util.queryDocumentMeta
import com.enderbk.materialreader.util.takePersistableReadPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Local document-metadata access. System implementation uses only the
 * ContentResolver; the fake used in tests returns canned values.
 */
interface DocumentMetaSource {
    suspend fun describe(uriString: String): DocumentMeta
    fun persistPermission(uriString: String): Boolean
    fun releasePermission(uriString: String)
}

class SystemDocumentMetaSource(private val appContext: Context) : DocumentMetaSource {
    override suspend fun describe(uriString: String): DocumentMeta = withContext(Dispatchers.IO) {
        queryDocumentMeta(appContext, android.net.Uri.parse(uriString))
    }

    override fun persistPermission(uriString: String): Boolean =
        takePersistableReadPermission(appContext, android.net.Uri.parse(uriString))

    override fun releasePermission(uriString: String) {
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(
                android.net.Uri.parse(uriString),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }
}

data class LibraryUiState(
    val visible: List<DocumentEntry> = emptyList(),
    val pinned: List<DocumentEntry> = emptyList(),
    val recent: List<DocumentEntry> = emptyList(),
    val query: String = "",
    val sortOrder: SortOrder = SortOrder.RECENT,
    val totalCount: Int = 0,
    val isEmpty: Boolean = true,
    val folders: List<String> = emptyList(),
    /** Null = "All" collection. */
    val selectedFolder: String? = null
)

class LibraryViewModel(
    private val documents: DocumentStore,
    private val settings: SettingsStore,
    private val meta: DocumentMetaSource,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedFolder = MutableStateFlow<String?>(null)

    val searchQuery = query.asStateFlow()

    val uiState = combine(
        documents.documents,
        settings.settings,
        query,
        selectedFolder,
        documents.customFolders,
        ::combineLibraryState
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    /** Emits the library id to open in the reader. */
    private val _openDocument = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openDocument: SharedFlow<String> = _openDocument.asSharedFlow()

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onSelectFolder(folder: String?) {
        selectedFolder.value = folder
    }

    fun onCreateFolder(name: String) {
        viewModelScope.launch { documents.addCustomFolder(name) }
    }

    fun onDeleteFolder(folder: String) {
        viewModelScope.launch {
            documents.removeCustomFolder(folder)
            documents.documents.first().filter { it.folder == folder }.forEach {
                documents.upsert(it.copy(folder = null))
            }
            // A deleted filter must not keep pointing at a folder that is gone.
            if (selectedFolder.value == folder) selectedFolder.value = null
        }
    }

    /** Moves a document, creating the folder if it is new. Null removes it. */
    fun onMoveToFolder(entry: DocumentEntry, folder: String?) {
        val clean = folder?.trim()?.takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            documents.upsert(entry.copy(folder = clean))
            // Follow the document: never strand the user on a filter that no
            // longer contains it (e.g. moving out must land back on All).
            selectedFolder.value = clean
        }
    }

    fun onSortChange(order: SortOrder) {
        viewModelScope.launch {
            settings.update { it.copy(sortOrder = order) }
        }
    }

    fun onOpenEntry(entry: DocumentEntry) {
        viewModelScope.launch {
            documents.upsert(entry.copy(lastOpenedEpochMillis = System.currentTimeMillis()))
            _openDocument.emit(entry.id)
        }
    }

    fun onTogglePin(entry: DocumentEntry) {
        viewModelScope.launch { documents.setPinned(entry.id, !entry.pinned) }
    }

    /** Points an entry at a newly picked file (relink after it went missing). */
    fun onRelink(entry: DocumentEntry, newUriString: String) {
        viewModelScope.launch {
            withContext(io) { meta.persistPermission(newUriString) }
            val info = withContext(io) {
                runCatching { meta.describe(newUriString) }.getOrNull()
            }
            documents.upsert(
                entry.copy(
                    uri = newUriString,
                    displayName = info?.displayName ?: entry.displayName,
                    sizeBytes = info?.sizeBytes ?: entry.sizeBytes,
                    missing = false,
                    lastOpenedEpochMillis = System.currentTimeMillis()
                )
            )
            _openDocument.emit(entry.id)
        }
    }

    fun onRemove(entry: DocumentEntry) {
        viewModelScope.launch {
            documents.remove(entry.id)
            runCatching { meta.releasePermission(entry.uri) }
        }
    }

    /**
     * Adds a SAF-picked (or shared) document to the library, then opens it.
     * Takes the URI as a string so this ViewModel stays JVM-testable.
     */
    fun onDocumentPicked(uriString: String) {
        viewModelScope.launch {
            withContext(io) { meta.persistPermission(uriString) }
            val info = withContext(io) {
                runCatching { meta.describe(uriString) }.getOrNull()
            }
            val existing = withContext(io) {
                runCatching { documents.get(DocumentEntry.idForUri(uriString)) }.getOrNull()
            }
            val entry = (existing ?: DocumentEntry(
                id = DocumentEntry.idForUri(uriString),
                uri = uriString,
                displayName = info?.displayName ?: "Document.pdf",
                sizeBytes = info?.sizeBytes
            )).copy(
                displayName = if (existing == null) {
                    info?.displayName ?: existing?.displayName ?: "Document.pdf"
                } else {
                    existing.displayName
                },
                sizeBytes = info?.sizeBytes ?: existing?.sizeBytes,
                lastOpenedEpochMillis = System.currentTimeMillis()
            )
            documents.upsert(entry)
            _openDocument.emit(entry.id)
        }
    }
}

private fun combineLibraryState(
    docs: List<DocumentEntry>,
    appSettings: com.enderbk.materialreader.data.AppSettings,
    q: String,
    folder: String?,
    custom: Set<String>
): LibraryUiState {
    val visible = docs
        .filteredByFolder(folder)
        .filteredByQuery(q)
        .sortedForLibrary(appSettings.sortOrder)
    return LibraryUiState(
        visible = visible,
        pinned = visible.filter { it.pinned },
        recent = visible.filterNot { it.pinned },
        query = q,
        sortOrder = appSettings.sortOrder,
        totalCount = docs.size,
        isEmpty = docs.isEmpty(),
        folders = mergeFolders(docs.distinctFolders(), custom),
        selectedFolder = folder
    )
}

@Suppress("UNCHECKED_CAST")
fun libraryViewModelFactory(
    documents: DocumentStore,
    settings: SettingsStore,
    meta: DocumentMetaSource,
    io: CoroutineDispatcher = Dispatchers.IO
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LibraryViewModel(documents, settings, meta, io) as T
    }
}
