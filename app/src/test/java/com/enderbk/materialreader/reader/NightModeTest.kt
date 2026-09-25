package com.enderbk.materialreader.reader

import com.enderbk.materialreader.pdf.applyMatrix
import com.enderbk.materialreader.pdf.hueRotateMatrix
import com.enderbk.materialreader.pdf.nightModeMatrix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NightModeTest {

    private fun assertNear(expected: Float, actual: Float, tolerance: Float = 1.5f) {
        assertTrue(
            "expected ~$expected but was $actual",
            kotlin.math.abs(expected - actual) <= tolerance
        )
    }

    @Test
    fun hueRotateZeroIsIdentity() {
        val m = hueRotateMatrix(0.0)
        val out = applyMatrix(m, 200f, 100f, 50f)
        assertNear(200f, out[0], 0.01f)
        assertNear(100f, out[1], 0.01f)
        assertNear(50f, out[2], 0.01f)
    }

    @Test
    fun nightModeMapsWhiteToBlackAndBack() {
        val m = nightModeMatrix().values
        val black = applyMatrix(m, 255f, 255f, 255f)
        assertNear(0f, black[0])
        assertNear(0f, black[1])
        assertNear(0f, black[2])
        val white = applyMatrix(m, 0f, 0f, 0f)
        assertNear(255f, white[0])
        assertNear(255f, white[1])
        assertNear(255f, white[2])
    }

    @Test
    fun nightModeKeepsMidGrayMid() {
        val m = nightModeMatrix().values
        val out = applyMatrix(m, 128f, 128f, 128f)
        assertNear(127f, out[0], 2f)
        assertNear(127f, out[1], 2f)
        assertNear(127f, out[2], 2f)
    }

    @Test
    fun nightModePreservesRedHueWhileInverting() {
        val m = nightModeMatrix().values
        val out = applyMatrix(m, 255f, 0f, 0f)
        // Still reddish (R dominant, G and B equal) but bright on a dark page.
        assertTrue("R should dominate: ${out.toList()}", out[0] > 200f)
        assertNear(out[1], out[2], 2f)
        assertTrue("G should stay mid: ${out.toList()}", out[1] in 80f..200f)
    }

    @Test
    fun matrixValuesHaveExpectedShape() {
        val values = nightModeMatrix().values
        assertEquals(20, values.size)
        // Alpha row untouched.
        assertNear(0f, values[15], 0.001f)
        assertNear(0f, values[16], 0.001f)
        assertNear(0f, values[17], 0.001f)
        assertNear(1f, values[18], 0.001f)
        assertNear(0f, values[19], 0.001f)
    }
}
