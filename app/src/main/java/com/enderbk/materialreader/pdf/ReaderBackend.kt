package com.enderbk.materialreader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.enderbk.materialreader.util.DocumentMeta
import com.enderbk.materialreader.util.canOpenUri
import com.enderbk.materialreader.util.queryDocumentMeta
import com.enderbk.materialreader.util.takePersistableReadPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * All platform I/O the reader needs, behind one interface so ViewModels stay
 * unit-testable with fakes. The system implementation performs only local
 * ContentResolver / filesystem work — never network.
 */
/**
 * URIs cross this boundary as plain strings so ViewModels remain pure-JVM
 * testable; only the system implementation touches android.net.Uri.
 */
interface ReaderBackend {
    suspend fun describe(uriString: String): DocumentMeta
    fun persistPermission(uriString: String): Boolean
    fun canOpen(uriString: String): Boolean
    suspend fun openSession(uriString: String): PdfDocument
    suspend fun renderPage(document: PdfDocument, index: Int, widthPx: Int): Bitmap
    suspend fun search(uriString: String, query: String): List<TextHit>
    suspend fun pageText(uriString: String, page: Int): String
    suspend fun pageLinks(uriString: String, page: Int): List<PageLink>
}

class SystemReaderBackend(private val appContext: Context) : ReaderBackend {
    override suspend fun describe(uriString: String): DocumentMeta = withContext(Dispatchers.IO) {
        queryDocumentMeta(appContext, Uri.parse(uriString))
    }

    override fun persistPermission(uriString: String): Boolean =
        takePersistableReadPermission(appContext, Uri.parse(uriString))

    override fun canOpen(uriString: String): Boolean =
        runCatching { canOpenUri(appContext, Uri.parse(uriString)) }.getOrDefault(false)

    override suspend fun openSession(uriString: String): PdfDocument =
        AndroidPdfDocument(PdfSession.open(appContext, Uri.parse(uriString)))

    override suspend fun renderPage(document: PdfDocument, index: Int, widthPx: Int): Bitmap =
        document.renderPage(index, widthPx)

    override suspend fun search(uriString: String, query: String): List<TextHit> =
        searchPdfText(appContext, Uri.parse(uriString), query)

    override suspend fun pageText(uriString: String, page: Int): String =
        extractPageText(appContext, Uri.parse(uriString), page)

    override suspend fun pageLinks(uriString: String, page: Int): List<PageLink> =
        extractPageLinks(appContext, Uri.parse(uriString), page)
}
