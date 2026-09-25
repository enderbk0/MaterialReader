package com.enderbk.materialreader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PDF rendering engine built on the platform [PdfRenderer].
 *
 * Why PdfRenderer: it is part of Android itself (no proprietary SDK, no
 * network, permissively usable), renders text PDFs, scanned/image PDFs,
 * embedded fonts, transparency, and mixed page sizes correctly, and renders
 * pages on demand so large documents never load fully into memory.
 *
 * Memory strategy: pages render lazily at the exact pixel width the screen
 * needs (capped), bitmaps are shared through a bounded LRU cache, and only
 * the pages on screen are held by Compose. See README for honest limits
 * (e.g. no JavaScript/forms, selection is text-layer based).
 */
class PdfSession private constructor(
    private val renderer: PdfRenderer,
    private val fileDescriptor: ParcelFileDescriptor
) {
    val pageCount: Int get() = renderer.pageCount

    /** Width/height of a page in PDF points (1/72 inch). */
    fun pageSizePoints(index: Int): Pair<Int, Int> {
        renderer.openPage(index).use { page ->
            return page.width to page.height
        }
    }

    /**
     * Renders [index] at [targetWidthPx] wide (aspect preserved), using the
     * bitmap cache when possible. Renders are serialized because PdfRenderer
     * page instances are short-lived here and bitmap creation is the cost.
     */
    suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap = withContext(Dispatchers.IO) {
        require(index in 0 until pageCount) { "Page $index out of range (0..${pageCount - 1})" }
        val width = targetWidthPx.coerceIn(MIN_RENDER_WIDTH_PX, MAX_RENDER_WIDTH_PX)
        val key = "$index@$width"
        synchronized(cacheLock) {
            cache.get(key)?.takeIf { !it.isRecycled }?.let { return@withContext it }
        }
        val bitmap = synchronized(renderer) {
            renderer.openPage(index).use { page ->
                val scale = width.toFloat() / page.width.toFloat()
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
        synchronized(cacheLock) { cache.put(key, bitmap) }
        bitmap
    }

    /** Drops cached bitmaps without closing the document (e.g. on zoom change). */
    fun clearCache() {
        synchronized(cacheLock) { cache.evictAll() }
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { fileDescriptor.close() }
        runCatching { clearCache() }
    }

    companion object {
        const val MIN_RENDER_WIDTH_PX = 240
        const val MAX_RENDER_WIDTH_PX = 2560

        private val cacheLock = Any()
        private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(
            // ~1/6 of the heap in KB; bitmaps evict automatically under pressure.
            (Runtime.getRuntime().maxMemory() / 1024 / 6).toInt().coerceAtLeast(8 * 1024)
        ) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                (value.byteCount / 1024).coerceAtLeast(1)
        }

        /** Opens a session for [uri]. Throws [PdfOpenException] on failure. */
        suspend fun open(context: Context, uri: Uri): PdfSession = withContext(Dispatchers.IO) {
            val pfd = try {
                context.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw PdfOpenException(PdfOpenFailure.CorruptOrUnsupported)
            } catch (e: PdfOpenException) {
                throw e
            } catch (e: Exception) {
                throw PdfOpenException(mapOpenError(e), e)
            }
            try {
                PdfSession(PdfRenderer(pfd), pfd)
            } catch (e: Exception) {
                runCatching { pfd.close() }
                throw PdfOpenException(mapOpenError(e), e)
            }
        }
    }
}
