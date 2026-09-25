package com.enderbk.materialreader.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

data class DocumentMeta(val displayName: String, val sizeBytes: Long?)

/** Reads display name + size through the ContentResolver. Never copies content. */
fun queryDocumentMeta(context: Context, uri: Uri): DocumentMeta {
    var name: String? = null
    var size: Long? = null
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) name = cursor.getString(nameIndex)
                if (sizeIndex != -1) {
                    val s = cursor.getLong(sizeIndex)
                    if (s >= 0) size = s
                }
            }
        }
    }
    val fallback = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "Document.pdf"
    return DocumentMeta(displayName = name ?: fallback, sizeBytes = size)
}

/**
 * Persists SAF read access so the document stays in the library across
 * restarts. Returns false when the provider does not allow it (e.g. some
 * shared/send intents) — the document can still be read for this session.
 */
fun takePersistableReadPermission(context: Context, uri: Uri): Boolean {
    return runCatching {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        true
    }.getOrDefault(false)
}

/** Best-effort check that a URI still resolves (permission kept, file present). */
fun canOpenUri(context: Context, uri: Uri): Boolean {
    return runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.close()
        true
    }.getOrDefault(false)
}
