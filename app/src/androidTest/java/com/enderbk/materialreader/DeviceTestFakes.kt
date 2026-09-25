package com.enderbk.materialreader

import android.graphics.Bitmap
import com.enderbk.materialreader.library.DocumentMetaSource
import com.enderbk.materialreader.pdf.PageLink
import com.enderbk.materialreader.pdf.PdfDocument
import com.enderbk.materialreader.pdf.ReaderBackend
import com.enderbk.materialreader.pdf.TextHit
import com.enderbk.materialreader.util.DocumentMeta

/** Device-test doubles. String-based boundaries keep them trivial. */
class FakeMetaSource(
    private val meta: DocumentMeta = DocumentMeta("Picked.pdf", 100L)
) : DocumentMetaSource {
    override suspend fun describe(uriString: String): DocumentMeta = meta
    override fun persistPermission(uriString: String): Boolean = true
    override fun releasePermission(uriString: String) = Unit
}

class FakeBackend : ReaderBackend {
    override suspend fun describe(uriString: String): DocumentMeta =
        DocumentMeta("Shared.pdf", 100L)

    override fun persistPermission(uriString: String): Boolean = true

    override fun canOpen(uriString: String): Boolean = true

    override suspend fun openSession(uriString: String): PdfDocument =
        throw UnsupportedOperationException("Not needed for these UI states")

    override suspend fun renderPage(document: PdfDocument, index: Int, widthPx: Int): Bitmap =
        throw UnsupportedOperationException("Not needed for these UI states")

    override suspend fun renderNightPage(document: PdfDocument, index: Int, widthPx: Int): Bitmap =
        throw UnsupportedOperationException("Not needed for these UI states")

    override suspend fun search(uriString: String, query: String): List<TextHit> = emptyList()

    override suspend fun pageText(uriString: String, page: Int): String = ""

    override suspend fun pageLinks(uriString: String, page: Int): List<PageLink> = emptyList()
}
