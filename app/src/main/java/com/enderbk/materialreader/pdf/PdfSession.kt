package com.enderbk.materialreader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
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
    private val fileDescriptor: ParcelFileDescriptor,
    private val appContext: Context,
    private val documentUri: Uri
) {
    val pageCount: Int get() = renderer.pageCount

    /**
     * Lazily opened PdfBox handle used ONLY for per-page image-region
     * analysis (night mode). The PdfRenderer session above stays the single
     * rendering path; this handle never renders. Opened once, closed with
     * the session — never per gesture frame.
     */
    private var textDoc: PDDocument? = null
    private val textLock = Any()
    private val nightRegions = mutableMapOf<Int, NightRegions>()

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
     *
     * Zooming in multiplies pixel cost quadratically (a 3x page is ~37 MB at
     * full width), so the width is capped for crispness-without-OOM and a
     * failed allocation retries once at half width instead of dropping the
     * frame — the layout still grows either way, so zoom always responds.
     */
    suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap = withContext(Dispatchers.IO) {
        require(index in 0 until pageCount) { "Page $index out of range (0..${pageCount - 1})" }
        val width = targetWidthPx.coerceIn(MIN_RENDER_WIDTH_PX, MAX_RENDER_WIDTH_PX)
        val key = "$index@$width"
        synchronized(cacheLock) {
            cache.get(key)?.takeIf { !it.isRecycled }?.let { return@withContext it }
        }
        val bitmap = try {
            renderLocked(index, width)
        } catch (e: OutOfMemoryError) {
            synchronized(cacheLock) { cache.evictAll() }
            renderLocked(index, (width / 2).coerceAtLeast(MIN_RENDER_WIDTH_PX))
        }
        synchronized(cacheLock) { cache.put(key, bitmap) }
        bitmap
    }

    private fun renderLocked(index: Int, width: Int): Bitmap {
        return synchronized(renderer) {
            renderer.openPage(index).use { page ->
                val scale = width.toFloat() / page.width.toFloat()
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                // Transparent PDFs are far more common than expected, and
                // PdfRenderer composites page content OVER the bitmap without
                // clearing it: a fresh bitmap is transparent black, so such
                // pages would show the (dark) reader background through as
                // their "paper". Every mainstream reader flattens onto white;
                // the framework docs put initialization on the caller.
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }

    /** Drops cached bitmaps without closing the document (e.g. on zoom change). */
    fun clearCache() {
        synchronized(cacheLock) { cache.evictAll() }
    }

    /**
     * Night-mode bitmap: text regions inverted, raster pictures composited
     * back un-inverted, scanned pages dimmed. Regions are analyzed once per
     * page and cached; bitmaps join the shared LRU. Never called per gesture
     * frame — Compose requests settled (index, width) pairs only.
     */
    suspend fun renderNightPage(index: Int, targetWidthPx: Int): Bitmap =
        withContext(Dispatchers.IO) {
            require(index in 0 until pageCount) { "Page $index out of range" }
            val width = targetWidthPx.coerceIn(MIN_RENDER_WIDTH_PX, MAX_RENDER_WIDTH_PX)
            val key = "night@$index@$width"
            synchronized(cacheLock) {
                cache.get(key)?.takeIf { !it.isRecycled }?.let { return@withContext it }
            }
            val base = renderPage(index, width)
            val regions = pageNightRegions(index)
            // A PDF that is already dark (dark background by design, dark
            // scan) must not be inverted bright: dim it instead.
            val mode = adjustForBackground(regions.mode, backgroundLuminance(base))
            val out = when (mode) {
                NightPageMode.INVERT_ALL -> applyMatrix(base, nightModeMatrix().values)
                NightPageMode.DIM -> applyMatrix(base, dimMatrixValues())
                // Invert everything (text goes white), then paint the picture
                // regions back as dimmed originals: hues survive, nothing glares.
                NightPageMode.INVERT_WITH_DIMMED_IMAGES -> {
                    val inverted = applyMatrix(base, nightModeMatrix().values)
                    if (regions.rects.isEmpty()) {
                        inverted
                    } else {
                        val dimmed = applyMatrix(base, dimMatrixValues())
                        compositeRegions(inverted, dimmed, regions.rects)
                    }
                }
            }
            synchronized(cacheLock) { cache.put(key, out) }
            out
        }

    /** Image regions for [index], analyzed once and cached for the session. */
    suspend fun pageNightRegions(index: Int): NightRegions = withContext(Dispatchers.IO) {
        require(index in 0 until pageCount) { "Page $index out of range" }
        synchronized(textLock) {
            nightRegions.getOrPut(index) {
                val doc = textDoc ?: openTextDoc()
                if (doc == null) {
                    NightRegions(emptyList(), NightPageMode.INVERT_ALL)
                } else {
                    runCatching { pageImageRegionsBlocking(doc, index) }
                        .getOrDefault(NightRegions(emptyList(), NightPageMode.INVERT_ALL))
                }
            }
        }
    }

    private fun openTextDoc(): PDDocument? {        val doc = runCatching {
            appContext.contentResolver.openInputStream(documentUri)?.use { input ->
                PDDocument.load(input, MemoryUsageSetting.setupMixed(32L * 1024L * 1024L))
            }
        }.getOrNull() ?: return null
        textDoc = doc
        return doc
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { fileDescriptor.close() }
        synchronized(textLock) {
            runCatching { textDoc?.close() }
            textDoc = null
            nightRegions.clear()
        }
        runCatching { clearCache() }
    }

    companion object {
        /**
         * Border-band luminance of a rendered page: samples the margins
         * (where page background lives, not content) with a stride that caps
         * the work at a few thousand pixels. Transparent pixels are skipped
         * by [luminanceOf] — they show the reader background, not the PDF.
         */
        fun backgroundLuminance(src: Bitmap): Float? {
            val w = src.width
            val h = src.height
            if (w <= 0 || h <= 0) return null
            val bandX = (w * 0.08f).toInt().coerceAtLeast(1)
            val bandY = (h * 0.06f).toInt().coerceAtLeast(1)
            val stride = kotlin.math.sqrt((w * h) / 3000.0).toInt().coerceAtLeast(1)
            val pixels = ArrayList<Int>(4096)
            var y = 0
            while (y < h) {
                val edgeRow = y < bandY || y >= h - bandY
                var x = 0
                while (x < w) {
                    val edgeCol = x < bandX || x >= w - bandX
                    if (edgeRow || edgeCol) {
                        pixels.add(src.getPixel(x, y))
                    }
                    x += stride
                }
                y += stride
            }
            if (pixels.isEmpty()) return null
            return luminanceOf(pixels.toIntArray())
        }

        /**
         * Draws [src] through a color matrix into a new bitmap. Used for
         * whole-page night transforms (invert / dim).
         */
        fun applyMatrix(src: Bitmap, values: FloatArray): Bitmap {
            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix(values))
            }
            Canvas(out).drawBitmap(src, 0f, 0f, paint)
            return out
        }

        /**
         * Overlays [src] regions onto [dst] (both same dimensions): used to
         * paint dimmed picture regions back over an inverted page.
         */        fun compositeRegions(dst: Bitmap, src: Bitmap, rects: List<ImgRect>): Bitmap {
            if (rects.isEmpty()) return dst
            val canvas = Canvas(dst)
            val paint = Paint().apply { isFilterBitmap = true }
            val w = dst.width.toFloat()
            val h = dst.height.toFloat()
            for (region in rects) {
                val l = (region.l * w).coerceIn(0f, w)
                val t = (region.t * h).coerceIn(0f, h)
                val r = (region.r * w).coerceIn(0f, w)
                val b = (region.b * h).coerceIn(0f, h)
                if (r > l + 1f && b > t + 1f) {
                    val bounds = RectF(l, t, r, b)
                    canvas.drawBitmap(
                        src,
                        Rect(l.toInt(), t.toInt(), r.toInt(), b.toInt()),
                        bounds,
                        paint
                    )
                }
            }
            return dst
        }
        const val MIN_RENDER_WIDTH_PX = 240

        /** Upper bound so deep zoom stays crisp without risking huge allocations. */
        const val MAX_RENDER_WIDTH_PX = 2048

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
                PdfSession(PdfRenderer(pfd), pfd, context.applicationContext, uri)
            } catch (e: Exception) {
                runCatching { pfd.close() }
                throw PdfOpenException(mapOpenError(e), e)
            }
        }
    }
}
