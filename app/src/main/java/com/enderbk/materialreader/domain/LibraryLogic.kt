package com.enderbk.materialreader.domain

import com.enderbk.materialreader.data.DocumentEntry
import com.enderbk.materialreader.data.SortOrder

/** Pure library logic — no Android dependencies, fully unit-testable. */

/** Sorts documents: pinned items always come first, then the chosen order. */
fun List<DocumentEntry>.sortedForLibrary(order: SortOrder): List<DocumentEntry> {
    val pinnedFirst = sortedByDescending { it.pinned }
    return when (order) {
        SortOrder.RECENT -> pinnedFirst.sortedWith(
            compareByDescending<DocumentEntry> { it.pinned }
                .thenByDescending { it.lastOpenedEpochMillis }
        )
        SortOrder.NAME -> pinnedFirst.sortedWith(
            compareByDescending<DocumentEntry> { it.pinned }
                .thenBy { it.displayName.lowercase() }
        )
        SortOrder.SIZE -> pinnedFirst.sortedWith(
            compareByDescending<DocumentEntry> { it.pinned }
                .thenByDescending { it.sizeBytes ?: -1L }
        )
    }
}

/** Case-insensitive filename filter. Blank query returns everything. */
fun List<DocumentEntry>.filteredByQuery(query: String): List<DocumentEntry> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { it.displayName.contains(q, ignoreCase = true) }
}

/** Reading progress in 0..1, or null when the page count is unknown. */
fun DocumentEntry.progress(): Float? {
    val total = pageCount
    if (total == null || total <= 1) return null
    return (lastPage.coerceIn(0, total - 1).toFloat() / (total - 1).toFloat())
}

/** True when there is a saved position worth restoring (not the first page). */
fun DocumentEntry.hasRestorablePosition(): Boolean = lastPage > 0
