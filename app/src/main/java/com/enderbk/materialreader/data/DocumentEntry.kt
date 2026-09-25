package com.enderbk.materialreader.data

import kotlinx.serialization.Serializable

/**
 * One PDF known to the library. Only metadata is stored on-device:
 * the content URI (with a persistable SAF grant), display name, size,
 * page count, reading position, and pinned flag. File contents are never
 * copied, uploaded, or transmitted anywhere.
 */
@Serializable
data class DocumentEntry(
    /** Stable id derived from the URI string. */
    val id: String,
    /** The content:// URI string granted via the Storage Access Framework. */
    val uri: String,
    val displayName: String,
    val sizeBytes: Long? = null,
    val pageCount: Int? = null,
    val lastPage: Int = 0,
    val lastOpenedEpochMillis: Long = 0L,
    val pinned: Boolean = false,
    /** User folder name, or null for the ungrouped "All" collection. */
    val folder: String? = null,
    /** True when the file could not be opened (moved/deleted); offers relink. */
    val missing: Boolean = false
) {
    companion object {
        fun idForUri(uri: String): String = uri.hashCode().toUInt().toString(16)
    }
}
