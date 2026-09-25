package com.enderbk.materialreader.reader

import androidx.compose.ui.unit.dp
import com.enderbk.materialreader.data.ZoomMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomMathTest {

    private val viewportW = 360.dp
    private val viewportH = 640.dp
    private val aspect = 0.7f // portrait page

    private fun fitWidth(scale: Float) =
        pageWidth(ZoomMode.FIT_WIDTH, scale, viewportW, viewportH, aspect) { 612 }

    @Test
    fun hundredPercentIsTheBaseFitScale() {
        assertEquals(360.dp, fitWidth(1f))
    }

    @Test
    fun aboveHundredPercentGrowsTheLayout() {
        // 125% / 150% / 200% must be genuinely larger page content.
        assertEquals(450.dp, fitWidth(1.25f))
        assertEquals(540.dp, fitWidth(1.5f))
        assertEquals(720.dp, fitWidth(2f))
    }

    @Test
    fun zoomingBackDownRestoresBase() {
        assertEquals(720.dp, fitWidth(2f))
        assertEquals(540.dp, fitWidth(1.5f))
        assertEquals(360.dp, fitWidth(1f))
        assertEquals(180.dp, fitWidth(0.5f))
    }

    @Test
    fun fitPageConstrainsByHeight() {
        // Wide viewport so height is the binding constraint: 640dp tall,
        // 0.7 aspect → ~448dp wide base, doubling at 200%.
        val wide = 1000.dp
        val base = pageWidth(ZoomMode.FIT_PAGE, 1f, wide, viewportH, aspect) { 612 }
        assertEquals(448f, base.value, 0.01f)
        val doubled = pageWidth(ZoomMode.FIT_PAGE, 2f, wide, viewportH, aspect) { 612 }
        assertEquals(base.value * 2f, doubled.value, 0.01f)
    }

    @Test
    fun actualSizeIsPhysical() {
        // 612pt at 160dp/inch → 1360dp regardless of viewport.
        assertEquals(
            1360.dp,
            pageWidth(ZoomMode.ACTUAL_SIZE, 1f, viewportW, viewportH, aspect) { 612 }
        )
    }

    @Test
    fun panBoundsGrowWithZoomAndVanishAtBase() {
        // At 100% (360x514 content in a 360x640 viewport): no panning.
        val contentH = 360f / aspect
        assertEquals(0f, panBounds(360f, contentH, 360f, 640f).x, 0.001f)
        assertEquals(0f, panBounds(360f, contentH, 360f, 640f).y, 0.001f)

        // At 200%: symmetric bounds in both axes.
        val grown = panBounds(720f, contentH * 2f, 360f, 640f)
        assertEquals(180f, grown.x, 0.001f)
        assertEquals((contentH * 2f - 640f) / 2f, grown.y, 0.001f)

        // Back down: zero again.
        assertEquals(0f, panBounds(360f, contentH, 360f, 640f).x, 0.001f)
    }
}
