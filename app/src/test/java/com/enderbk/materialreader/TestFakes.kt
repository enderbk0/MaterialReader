package com.enderbk.materialreader

import android.graphics.Bitmap
import com.enderbk.materialreader.library.DocumentMetaSource
import com.enderbk.materialreader.pdf.PageLink
import com.enderbk.materialreader.pdf.PdfDocument
import com.enderbk.materialreader.pdf.PdfOpenException
import com.enderbk.materialreader.pdf.PdfOpenFailure
import com.enderbk.materialreader.pdf.ReaderBackend
import com.enderbk.materialreader.pdf.TextHit
import com.enderbk.materialreader.util.DocumentMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Swaps Dispatchers.Main for a test dispatcher in ViewModel tests. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

/** JVM fake: metadata source with canned values, records releases. */
class FakeDocumentMetaSource(
    var meta: DocumentMeta = DocumentMeta("Picked.pdf", 4242L)
) : DocumentMetaSource {
    val released = mutableListOf<String>()
    var persisted = mutableListOf<String>()

    override suspend fun describe(uriString: String): DocumentMeta = meta

    override fun persistPermission(uriString: String): Boolean {
        persisted += uriString
        return true
    }

    override fun releasePermission(uriString: String) {
        released += uriString
    }
}

/** JVM fake: no Bitmaps can exist here, so renderPage throws if ever called. */
class FakePdfDocument(
    override val pageCount: Int,
    private val pageWidthPoints: Int = 612,
    private val pageHeightPoints: Int = 792
) : PdfDocument {
    var closed = false
    var clearedCaches = 0

    override suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap =
        throw UnsupportedOperationException("No Bitmaps on the JVM")

    override suspend fun renderNightPage(index: Int, targetWidthPx: Int): Bitmap =
        throw UnsupportedOperationException("No Bitmaps on the JVM")

    override suspend fun pageMode(index: Int) =
        com.enderbk.materialreader.pdf.NightPageMode.INVERT_ALL

    override fun pageSizePoints(index: Int): Pair<Int, Int> =
        pageWidthPoints to pageHeightPoints

    override fun clearCache() {
        clearedCaches++
    }

    override fun close() {
        closed = true
    }
}

/** JVM fake for every platform call the reader ViewModel makes. */
class FakeReaderBackend(
    var pageCount: Int = 10,
    var canOpenResult: Boolean = true,
    var openFailure: PdfOpenFailure? = null,
    var searchResult: List<TextHit> = emptyList(),
    var pageTextResult: String = "",
    var pageLinksResult: List<PageLink> = emptyList(),
    var meta: DocumentMeta = DocumentMeta("Shared.pdf", 777L)
) : ReaderBackend {
    val openedSessions = mutableListOf<String>()
    var lastDocument: FakePdfDocument? = null

    override suspend fun describe(uriString: String): DocumentMeta = meta

    override fun persistPermission(uriString: String): Boolean = true

    override fun canOpen(uriString: String): Boolean = canOpenResult

    override suspend fun openSession(uriString: String): PdfDocument {
        openFailure?.let { throw PdfOpenException(it) }
        openedSessions += uriString
        return FakePdfDocument(pageCount).also { lastDocument = it }
    }

    override suspend fun renderPage(document: PdfDocument, index: Int, widthPx: Int): Bitmap =
        throw UnsupportedOperationException("No Bitmaps on the JVM")

    override suspend fun renderNightPage(document: PdfDocument, index: Int, widthPx: Int): Bitmap =
        throw UnsupportedOperationException("No Bitmaps on the JVM")

    var pageModeResult: com.enderbk.materialreader.pdf.NightPageMode =
        com.enderbk.materialreader.pdf.NightPageMode.INVERT_ALL

    override suspend fun pageMode(document: PdfDocument, index: Int) =
        pageModeResult

    override suspend fun search(uriString: String, query: String): List<TextHit> = searchResult

    override suspend fun pageText(uriString: String, page: Int): String = pageTextResult

    override suspend fun pageLinks(uriString: String, page: Int): List<PageLink> = pageLinksResult
}
