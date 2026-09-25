package com.enderbk.materialreader.pdf

import android.graphics.Bitmap
import java.io.Closeable

/**
 * An open PDF document. The production implementation wraps the platform
 * PdfRenderer ([PdfSession]); tests substitute a fake. Bitmaps returned by
 * [renderPage] are owned by the document's cache and must not be recycled by
 * callers.
 */
interface PdfDocument : Closeable {
    val pageCount: Int
    suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap
    /** Night bitmap: text inverted, pictures preserved (CPU composite, cached). */
    suspend fun renderNightPage(index: Int, targetWidthPx: Int): Bitmap
    fun pageSizePoints(index: Int): Pair<Int, Int>
    fun clearCache()
}

/** Production [PdfDocument] backed by [PdfSession]. */
class AndroidPdfDocument(private val session: PdfSession) : PdfDocument {
    override val pageCount: Int get() = session.pageCount

    override suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap =
        session.renderPage(index, targetWidthPx)

    override suspend fun renderNightPage(index: Int, targetWidthPx: Int): Bitmap =
        session.renderNightPage(index, targetWidthPx)

    override fun pageSizePoints(index: Int): Pair<Int, Int> =
        session.pageSizePoints(index)

    override fun clearCache() = session.clearCache()

    override fun close() = session.close()
}
