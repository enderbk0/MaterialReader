package com.enderbk.materialreader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NightRegionsTest {

    @Test
    fun rotationMappingKeepsCornersOnCorners() {
        // Unrotated: y flips for the bitmap.
        assertEquals(0.2f to 0.7f, mapToRendered(0.2f, 0.3f, 0))
        // 90 CW: old bottom-left lands top-left.
        assertEquals(0f to 0f, mapToRendered(0f, 0f, 90))
        // 180: old bottom-left lands top-right.
        assertEquals(1f to 0f, mapToRendered(0f, 0f, 180))
        // 270 CW: old bottom-left lands bottom-right.
        assertEquals(1f to 1f, mapToRendered(0f, 0f, 270))
        // Unknown rotations degrade to unrotated instead of garbage.
        assertEquals(0.2f to 0.7f, mapToRendered(0.2f, 0.3f, 45))
    }

    @Test
    fun rectMappingPreservesArea() {
        val mapped = mapRectToRendered(YupRect(0.1f, 0.2f, 0.5f, 0.8f), 0)
        assertEquals(0.1f, mapped.l, 0.001f)
        assertEquals(0.2f, mapped.t, 0.001f)
        assertEquals(0.5f, mapped.r, 0.001f)
        assertEquals(0.8f, mapped.b, 0.001f)
        val rotated = mapRectToRendered(YupRect(0.1f, 0.2f, 0.5f, 0.8f), 90)
        val area = { r: ImgRect -> (r.r - r.l) * (r.b - r.t) }
        assertEquals(area(mapped), area(rotated), 0.001f)
    }

    @Test
    fun mergeUnionsOverlapsAndDropsDegenerate() {
        val merged = mergeRects(
            listOf(
                ImgRect(0f, 0f, 0.5f, 0.5f),
                ImgRect(0.4f, 0.4f, 0.9f, 0.9f),
                ImgRect(0.1f, 0.1f, 0.1f, 0.9f), // zero width: dropped
                ImgRect(0.92f, 0.92f, 1f, 1f) // disjoint corner: kept
            )
        )
        assertEquals(2, merged.size)
        val big = merged.first { it.r > 0.5f && it.b > 0.5f }
        assertEquals(0f, big.l, 0.001f)
        assertEquals(0.9f, big.r, 0.001f)
    }

    @Test
    fun coverageSumsMergedArea() {
        assertEquals(0.25f, coverageOf(listOf(ImgRect(0f, 0f, 0.5f, 0.5f))), 0.001f)
        assertEquals(
            0.5f,
            coverageOf(
                mergeRects(
                    listOf(
                        ImgRect(0f, 0f, 0.5f, 0.5f),
                        ImgRect(0.5f, 0.5f, 1f, 1f)
                    )
                )
            ),
            0.001f
        )
    }

    @Test
    fun dimMatrixDarkensButKeepsHueOrder() {
        val m = dimMatrixValues()
        assertEquals(20, m.size)
        val white = applyMatrix(m, 255f, 255f, 255f)
        assertTrue(white[0] in 100f..180f)
        assertEquals(white[0], white[1], 1f)
        assertEquals(white[1], white[2], 1f)
        val black = applyMatrix(m, 0f, 0f, 0f)
        assertEquals(0f, black[0], 0.001f)
        // Red stays redder than green/blue.
        val red = applyMatrix(m, 200f, 50f, 50f)
        assertTrue(red[0] > red[1] && red[1] == red[2])
    }

    // ---- Classification (A–F) ----

    private fun raw(
        images: List<RawImage> = emptyList(),
        textOps: Int = 0,
        artOps: Int = 0,
        filledFraction: Float = 0f,
        firstTextOp: Long? = if (textOps > 0) 0L else null
    ) = RawPageAnalysis(images, textOps, artOps, filledFraction, firstTextOp)

    /** y-down fractions helper: photo() takes display-space bounds. */
    private fun photo(l: Float, t: Float, r: Float, b: Float) =
        RawImage(YupRect(l, 1f - b, r, 1f - t))

    @Test
    fun classATextOnlyInverts() {
        val out = classifyDocumentPage(raw(textOps = 12), 0)
        assertEquals(NightPageMode.INVERT_ALL, out.mode)
        assertTrue(out.rects.isEmpty())
    }

    @Test
    fun tableRulesDoNotTriggerVectorPath() {
        // 40 stroked table cells + text: structural, not artwork.
        val out = classifyDocumentPage(raw(textOps = 30, artOps = 0, filledFraction = 0f), 0)
        assertEquals(NightPageMode.INVERT_ALL, out.mode)
    }

    @Test
    fun classBEmbeddedPhotoInvertsWithDimmedBack() {
        val out = classifyDocumentPage(raw(
                images = listOf(photo(0.3f, 0.3f, 0.6f, 0.6f)),
                textOps = 8
            ),
            0
        )
        assertEquals(NightPageMode.INVERT_WITH_DIMMED_IMAGES, out.mode)
        assertEquals(1, out.rects.size)
    }

    @Test
    fun classBLargePhotoWithMargins() {
        val out = classifyDocumentPage(raw(
                images = listOf(RawImage(YupRect(0.02f, 0.02f, 0.98f, 0.6f))),
                textOps = 4
            ),
            0
        )
        assertEquals(NightPageMode.INVERT_WITH_DIMMED_IMAGES, out.mode)
    }

    @Test
    fun classCFullPageScanGoesThroughInvertDimBack() {
        // One giant image IS the page: invert + dim-back ≈ dark original.
        val out = classifyDocumentPage(raw(images = listOf(photo(0f, 0f, 1f, 1f))),
            0
        )
        assertEquals(NightPageMode.INVERT_WITH_DIMMED_IMAGES, out.mode)
        assertEquals(1, out.rects.size)
    }

    @Test
    fun classCScanWithOcrLayer() {
        // A full-bleed scan carrying an OCR text layer is signal-identical to
        // a poster with a headline (full image + text ops): both take DIM,
        // which keeps either legible instead of painting over the text.
        val out = classifyDocumentPage(raw(
                images = listOf(photo(0f, 0f, 1f, 1f)),
                textOps = 200
            ),
            0
        )
        assertEquals(NightPageMode.DIM, out.mode)
    }

    @Test
    fun classCRotatedScan() {
        val out = classifyDocumentPage(raw(images = listOf(photo(0f, 0f, 1f, 1f))),
            90
        )
        assertEquals(NightPageMode.INVERT_WITH_DIMMED_IMAGES, out.mode)
    }

    @Test
    fun nearFullPageImage() {
        val out = classifyDocumentPage(raw(images = listOf(photo(0.01f, 0.01f, 0.99f, 0.99f))),
            0
        )
        // 95%+ coverage without headline text: invert + dim-back.
        assertEquals(NightPageMode.INVERT_WITH_DIMMED_IMAGES, out.mode)
    }

    @Test
    fun classFPosterWithHeadlineDims() {
        // Full-bleed image + real headline: dim keeps it legible, while
        // painting the photo back would erase the inverted headline.
        val out = classifyDocumentPage(raw(
                images = listOf(photo(0f, 0f, 1f, 1f)),
                textOps = 6
            ),
            0
        )
        assertEquals(NightPageMode.DIM, out.mode)
    }

    @Test
    fun classDMultipleOverlappingImages() {
        val out = classifyDocumentPage(raw(
                images = listOf(
                    photo(0.05f, 0.05f, 0.45f, 0.4f),
                    photo(0.4f, 0.35f, 0.8f, 0.7f),
                    photo(0.1f, 0.7f, 0.3f, 0.9f)
                ),
                textOps = 6
            ),
            0
        )
        assertEquals(NightPageMode.INVERT_WITH_DIMMED_IMAGES, out.mode)
        assertTrue(out.rects.size in 1..3)
    }

    @Test
    fun classEVectorCurvesDim() {
        val out = classifyDocumentPage(raw(artOps = 40), 0)
        assertEquals(NightPageMode.DIM, out.mode)
    }

    @Test
    fun classEFilledChartBarsDim() {
        // Bar chart: filled rects covering a third of the page.
        val out = classifyDocumentPage(raw(textOps = 4, filledFraction = 0.3f),
            0
        )
        assertEquals(NightPageMode.DIM, out.mode)
    }

    @Test
    fun vectorPlusTextDims() {
        val out = classifyDocumentPage(raw(textOps = 10, artOps = 120),
            0
        )
        assertEquals(NightPageMode.DIM, out.mode)
    }

    @Test
    fun luminanceSkipsTransparentAndAverages() {
        // Opaque white.
        assertEquals(
            1f,
            luminanceOf(
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt())
                    .let { it + it + IntArray(200) { 0xFFFFFFFF.toInt() } }
            ) ?: -1f,
            0.01f
        )
        // Opaque black.
        assertEquals(
            0f,
            luminanceOf(IntArray(200) { 0xFF000000.toInt() }) ?: -1f,
            0.01f
        )
        // Fully transparent margins do not count as dark content.
        assertEquals(null, luminanceOf(IntArray(5000) { 0x00000000 }))
        // Mixed: white page with a black band still reads bright overall.
        val mixed = IntArray(200) { i -> if (i < 20) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        assertTrue((luminanceOf(mixed) ?: 0f) > 0.85f)
    }

    @Test
    fun darkBackgroundPagesDimInsteadOfInvert() {
        assertEquals(
            NightPageMode.DIM,
            adjustForBackground(NightPageMode.INVERT_ALL, 0.15f)
        )
        assertEquals(
            NightPageMode.DIM,
            adjustForBackground(NightPageMode.INVERT_WITH_DIMMED_IMAGES, 0.2f)
        )
        // Light pages and unknown backgrounds keep the signal decision.
        assertEquals(
            NightPageMode.INVERT_ALL,
            adjustForBackground(NightPageMode.INVERT_ALL, 0.9f)
        )
        assertEquals(
            NightPageMode.INVERT_WITH_DIMMED_IMAGES,
            adjustForBackground(NightPageMode.INVERT_WITH_DIMMED_IMAGES, null)
        )
        // Boundary: 0.4 counts as dark.
        assertEquals(
            NightPageMode.DIM,
            adjustForBackground(NightPageMode.INVERT_ALL, 0.39f)
        )
        assertEquals(
            NightPageMode.INVERT_ALL,
            adjustForBackground(NightPageMode.INVERT_ALL, 0.41f)
        )
    }
}
