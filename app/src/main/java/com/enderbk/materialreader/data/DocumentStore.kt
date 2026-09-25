package com.enderbk.materialreader.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Persistence for the document library. Implementations must keep everything
 * on-device; there is intentionally no sync, account, or network path here.
 */
interface DocumentStore {
    val documents: Flow<List<DocumentEntry>>
    suspend fun upsert(entry: DocumentEntry)
    suspend fun remove(id: String)
    suspend fun setPinned(id: String, pinned: Boolean)
    suspend fun setReadingPosition(id: String, page: Int, pageCount: Int?)
    suspend fun get(id: String): DocumentEntry?
}

/** In-memory implementation used by tests and Compose previews. */
class InMemoryDocumentStore(initial: List<DocumentEntry> = emptyList()) : DocumentStore {
    private val state = MutableStateFlow(initial)

    override val documents: Flow<List<DocumentEntry>> = state.asStateFlow()

    override suspend fun upsert(entry: DocumentEntry) {
        state.update { list ->
            val index = list.indexOfFirst { it.id == entry.id }
            if (index == -1) list + entry else list.toMutableList().also { it[index] = entry }
        }
    }

    override suspend fun remove(id: String) {
        state.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun setPinned(id: String, pinned: Boolean) {
        state.update { list -> list.map { if (it.id == id) it.copy(pinned = pinned) else it } }
    }

    override suspend fun setReadingPosition(id: String, page: Int, pageCount: Int?) {
        state.update { list ->
            list.map {
                if (it.id == id) {
                    it.copy(
                        lastPage = page,
                        pageCount = pageCount ?: it.pageCount,
                        lastOpenedEpochMillis = System.currentTimeMillis()
                    )
                } else {
                    it
                }
            }
        }
    }

    override suspend fun get(id: String): DocumentEntry? = state.value.firstOrNull { it.id == id }
}
