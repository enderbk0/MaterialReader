package com.enderbk.materialreader.reader

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Night-mode page filter: full inversion (dark text becomes light, white
 * paper becomes black) followed by a 180° hue rotation that maps inverted
 * hues back onto themselves, so colored figures stay recognizable while
 * luminance stays inverted. Photos and diagrams are still recolored — an
 * honest, documented tradeoff of any matrix night mode.
 */
fun nightModeColorFilter(): ColorFilter = ColorFilter.colorMatrix(nightModeMatrix())

fun nightModeMatrix(): ColorMatrix =
    ColorMatrix(multiply4x5(hueRotateMatrix(180.0), invertMatrix()))

internal fun invertMatrix(): FloatArray = floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f
)

/** Standard luminance-preserving hue-rotation matrix (CSS/SVG formulation). */
internal fun hueRotateMatrix(degrees: Double): FloatArray {
    val rad = degrees * PI / 180.0
    val c = cos(rad).toFloat()
    val s = sin(rad).toFloat()
    val lumR = 0.213f
    val lumG = 0.715f
    val lumB = 0.072f
    return floatArrayOf(
        lumR + c * (1 - lumR) - s * lumR,
        lumG - c * lumG - s * lumG,
        lumB - c * lumB + s * (1 - lumB),
        0f, 0f,
        lumR - c * lumR + s * 0.143f,
        lumG + c * (1 - lumG) + s * 0.140f,
        lumB - c * lumB - s * 0.283f,
        0f, 0f,
        lumR - c * lumR - s * (1 - lumR),
        lumG - c * lumG + s * lumG,
        lumB + c * (1 - lumB) + s * lumB,
        0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
}

/** Row-major 4x5 affine matrix product: applies [b] first, then [a]. */
internal fun multiply4x5(a: FloatArray, b: FloatArray): FloatArray {
    require(a.size == 20 && b.size == 20)
    val out = FloatArray(20)
    for (row in 0..3) {
        for (col in 0..3) {
            var sum = 0f
            for (k in 0..3) sum += a[row * 5 + k] * b[k * 5 + col]
            out[row * 5 + col] = sum
        }
        var t = a[row * 5 + 4]
        for (k in 0..3) t += a[row * 5 + k] * b[k * 5 + 4]
        out[row * 5 + 4] = t
    }
    return out
}

/** Applies a row-major 4x5 matrix to an RGB triple (alpha untouched). Test helper. */
internal fun applyMatrix(m: FloatArray, r: Float, g: Float, b: Float): FloatArray {
    require(m.size == 20)
    return floatArrayOf(
        m[0] * r + m[1] * g + m[2] * b + m[4],
        m[5] * r + m[6] * g + m[7] * b + m[9],
        m[10] * r + m[11] * g + m[12] * b + m[14]
    )
}
