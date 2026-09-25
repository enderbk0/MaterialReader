package com.enderbk.materialreader.pdf

import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

private val TextOps = setOf("Tj", "TJ", "'", "\"")

/** Curves and shading: genuine vector artwork (charts, gradients, drawings). */
private val ArtOps = setOf("c", "v", "y", "sh")

/** Operators that fill the current path (consume accumulated rect area). */
private val FillOps = setOf("f", "F", "f*", "B", "B*", "b", "b*")

/** Operators that end a path without filling (stroke-only, clip, discard). */
private val NonFillPathOps = setOf("S", "s", "n", "W", "W*")

/** One raster image placement observed while walking a page. */
data class RawImage(
    /** y-up fractions relative to the crop box. */
    val yup: YupRect
)

/** Everything the classifier needs about one page. */
data class RawPageAnalysis(
    val images: List<RawImage>,
    val textOps: Int,
    /** Curve/shading op count — real artwork, not table rules. */
    val artOps: Int,
    /** Fraction of the page area covered by FILLED rects (bars, backgrounds). */
    val filledFraction: Float,
    /** Stream order of the first text op, or null when the page has no text. */
    val firstTextOp: Long?
)

/**
 * Walks a page's content stream (recursing into Form XObjects) recording
 * raster placements plus text/artwork activity.
 *
 * Crucial distinction: rectangular rules, table grids, and underlines are
 * structural (stroked or tiny fills) and must NOT mark a page as
 * vector-artwork — otherwise ordinary tables would lose true-black night
 * pages. Only curves/shading and *substantial filled areas* count as art.
 *
 * Every PdfBox call is guarded: malformed content degrades to fewer signals,
 * never a crash.
 */
private class SignalEngine : PDFStreamEngine() {
    val images = mutableListOf<RawImage>()
    var textOps = 0
    var artOps = 0
    var filledAreaPts = 0f
    var firstTextOp: Long? = null
    private var opIndex = 0L
    private var pendingRectArea = 0f
    private var boxX = 0f
    private var boxY = 0f
    private var boxW = 1f
    private var boxH = 1f

    fun analyze(page: PDPage): RawPageAnalysis {
        images.clear()
        textOps = 0
        artOps = 0
        filledAreaPts = 0f
        firstTextOp = null
        opIndex = 0L
        pendingRectArea = 0f
        val crop = runCatching { page.cropBox ?: page.mediaBox }.getOrNull()
        if (crop != null) {
            boxX = crop.lowerLeftX
            boxY = crop.lowerLeftY
            boxW = crop.width.coerceAtLeast(1f)
            boxH = crop.height.coerceAtLeast(1f)
        }
        runCatching { processPage(page) }
        val pageArea = boxW * boxH
        return RawPageAnalysis(
            images = images.toList(),
            textOps = textOps,
            artOps = artOps,
            filledFraction = (filledAreaPts / pageArea).coerceIn(0f, 1f),
            firstTextOp = firstTextOp
        )
    }

    override fun processOperator(operator: Operator, operands: List<COSBase>) {
        val index = opIndex++
        when (operator.name) {
            in TextOps -> {
                textOps++
                if (firstTextOp == null) firstTextOp = index
            }
            in ArtOps -> artOps++
            "re" -> {
                // Rectangle path construction: accumulate, settled on paint.
                val w = (operands.getOrNull(2) as? COSNumber)?.floatValue() ?: 0f
                val h = (operands.getOrNull(3) as? COSNumber)?.floatValue() ?: 0f
                pendingRectArea += abs(w * h)
            }
            in FillOps -> {
                filledAreaPts += pendingRectArea
                pendingRectArea = 0f
            }
            in NonFillPathOps -> pendingRectArea = 0f
            "Do" -> {
                pendingRectArea = 0f
                val name = operands.firstOrNull() as? COSName
                recordImage(name)
            }
        }
        runCatching { super.processOperator(operator, operands) }
    }

    private fun recordImage(name: COSName?) {
        if (name == null) return
        val xobject = runCatching { resources.getXObject(name) }.getOrNull()
        if (xobject !is PDImageXObject) return
        val matrix = runCatching { graphicsState.currentTransformationMatrix }.getOrNull()
            ?: return
        // An image fills the unit square mapped through the CTM.
        val p0 = runCatching { matrix.transformPoint(0f, 0f) }.getOrNull() ?: return
        val p1 = runCatching { matrix.transformPoint(1f, 1f) }.getOrNull() ?: return
        val l = ((minOf(p0.x, p1.x) - boxX) / boxW).coerceIn(0f, 1f)
        val r = ((maxOf(p0.x, p1.x) - boxX) / boxW).coerceIn(0f, 1f)
        val b = ((minOf(p0.y, p1.y) - boxY) / boxH).coerceIn(0f, 1f)
        val t = ((maxOf(p0.y, p1.y) - boxY) / boxH).coerceIn(0f, 1f)
        if (r > l && t > b) images += RawImage(YupRect(l, b, r, t))
        if (images.size > 256) return
    }
}

/**
 * Night regions for one page. Runs on Dispatchers.IO.
 */
suspend fun pageImageRegions(doc: PDDocument, index: Int): NightRegions =
    withContext(Dispatchers.IO) {
        pageImageRegionsBlocking(doc, index)
    }

/** Blocking core for use inside session locks (caller owns threading). */
fun pageImageRegionsBlocking(doc: PDDocument, index: Int): NightRegions {
    val page = runCatching { doc.getPage(index) }.getOrNull()
        ?: return NightRegions(emptyList(), NightPageMode.INVERT_ALL)
    val raw = SignalEngine().analyze(page)
    val rotation = runCatching { page.rotation }.getOrDefault(0)
    return classifyDocumentPage(raw, rotation)
}
