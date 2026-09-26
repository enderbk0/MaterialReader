package com.enderbk.materialreader.pdf

/**
 * Pure night-mode region logic (no Android types — JVM unit-testable).
 *
 * Night mode inverts page *text* but never pictures: raster image placements
 * are extracted per page ([ImgRect], fractions of the rendered bitmap), the
 * page is inverted on the CPU, and the original image regions are composited
 * back over it. Fully scanned pages (images everywhere) fall back to a dim
 * that preserves hues instead of a meaningless full invert.
 */

/** Image rectangle as fractions of the rendered bitmap (y-down). */
data class ImgRect(val l: Float, val t: Float, val r: Float, val b: Float)

/** Image rectangle in PDF user space as fractions (y-up). */
data class YupRect(val l: Float, val b: Float, val r: Float, val t: Float)

enum class NightPageMode { INVERT_ALL, INVERT_WITH_DIMMED_IMAGES, DIM }

data class NightRegions(val rects: List<ImgRect>, val mode: NightPageMode)

/** Maps one y-up fraction point to rendered y-down fractions for /Rotate. */
fun mapToRendered(fx: Float, fyUp: Float, rotation: Int): Pair<Float, Float> {
    val r = ((rotation % 360) + 360) % 360
    return when (r) {
        90 -> fyUp to fx
        180 -> (1f - fx) to fyUp
        270 -> (1f - fyUp) to (1f - fx)
        else -> fx to (1f - fyUp)
    }
}

/** Maps a y-up fraction rect to a rendered y-down [ImgRect]. */
fun mapRectToRendered(rect: YupRect, rotation: Int): ImgRect {
    val corners = listOf(
        rect.l to rect.b,
        rect.r to rect.b,
        rect.l to rect.t,
        rect.r to rect.t
    ).map { (x, y) -> mapToRendered(x, y, rotation) }
    return ImgRect(
        l = corners.minOf { it.first }.coerceIn(0f, 1f),
        t = corners.minOf { it.second }.coerceIn(0f, 1f),
        r = corners.maxOf { it.first }.coerceIn(0f, 1f),
        b = corners.maxOf { it.second }.coerceIn(0f, 1f)
    )
}

private fun overlaps(a: ImgRect, b: ImgRect): Boolean =
    a.l < b.r && b.l < a.r && a.t < b.b && b.t < a.b

private fun union(a: ImgRect, b: ImgRect): ImgRect = ImgRect(
    l = minOf(a.l, b.l),
    t = minOf(a.t, b.t),
    r = maxOf(a.r, b.r),
    b = maxOf(a.b, b.b)
)

/** Unions overlapping rectangles (quadratic, but region counts are small). */
fun mergeRects(rects: List<ImgRect>): List<ImgRect> {
    val working = rects
        .filter { it.r > it.l && it.b > it.t }
        .take(128)
        .toMutableList()
    var changed = true
    while (changed) {
        changed = false
        outer@ for (i in working.indices) {
            for (j in i + 1 until working.size) {
                if (overlaps(working[i], working[j])) {
                    working[i] = union(working[i], working[j])
                    working.removeAt(j)
                    changed = true
                    break@outer
                }
            }
        }
    }
    return working
}

/** Fraction of the page covered by (merged) image regions. */
fun coverageOf(rects: List<ImgRect>): Float =
    rects.sumOf { ((it.r - it.l) * (it.b - it.t)).toDouble() }
        .toFloat()
        .coerceIn(0f, 1f)

/** Neutral dim used for scanned pages: darkens, preserves hues. */
fun dimMatrixValues(): FloatArray = floatArrayOf(
    0.55f, 0f, 0f, 0f, 0f,
    0f, 0.55f, 0f, 0f, 0f,
    0f, 0f, 0.55f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f
)

/**
 * Full-page invert with hue restore (text regions and no-image pages).
 * Kept in the pdf layer: the CPU night compositor consumes these values.
 */
fun nightModeMatrix(): androidx.compose.ui.graphics.ColorMatrix =
    androidx.compose.ui.graphics.ColorMatrix(multiply4x5(hueRotateMatrix(180.0), invertMatrix()))

internal fun invertMatrix(): FloatArray = floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f
)

/** Standard luminance-preserving hue-rotation matrix (CSS/SVG formulation). */
internal fun hueRotateMatrix(degrees: Double): FloatArray {
    val rad = degrees * kotlin.math.PI / 180.0
    val c = kotlin.math.cos(rad).toFloat()
    val s = kotlin.math.sin(rad).toFloat()
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

/** One image with its classification signals resolved. */
data class AnalyzedImage(
    val rect: ImgRect,
    val fullBleed: Boolean
)

private const val BLEED_MARGIN = 0.02f

/**
 * Curves/shading ops that mark genuine vector artwork. Plain stroked rules
 * and underlines do NOT count (they live in [RawPageAnalysis.filledFraction]
 * territory instead) — otherwise every table would lose true-black pages.
 */
private const val ARTWORK_OPS = 12

/** Filled-rect area fraction that marks chart bars/backgrounds as artwork. */
private const val ARTWORK_FILL_FRACTION = 0.15f

/**
 * Multi-signal page classification. The governing principle: dark text must
 * become light text. Only artwork whose hues would be destroyed by inversion
 * (curves, gradients, heavy fills) takes the DIM path; everything else —
 * including full-page scans — goes through invert, with picture regions
 * composited back as dimmed (hue-preserving) originals:
 *
 * A. text only ......................... INVERT_ALL (true black pages)
 * B. text + embedded photos ............ INVERT_WITH_DIMMED_IMAGES
 * C. full-page scan (± OCR layer) ...... INVERT_WITH_DIMMED_IMAGES
 *                                         (≈ dimmed original — dark + legible)
 * D. image-heavy ....................... INVERT_WITH_DIMMED_IMAGES
 * E. vector-art heavy .................. DIM (chart hues must survive)
 * F. full-bleed poster WITH headline ... DIM (headline stays legible)
 *
 * Vector paths cannot be masked out like raster regions, so (E) dims the
 * whole page instead of inverting it — documented limitation.
 */
fun classifyDocumentPage(
    raw: RawPageAnalysis,
    rotation: Int
): NightRegions {
    // A full-bleed image has the page's aspect by construction, so bleed
    // alone (plus headline text) decides — no separate aspect comparison.
    val analyzed = raw.images.map { image ->
        val rect = mapRectToRendered(image.yup, rotation)
        val fullBleed = rect.l < BLEED_MARGIN && rect.t < BLEED_MARGIN &&
            (1f - rect.r) < BLEED_MARGIN && (1f - rect.b) < BLEED_MARGIN
        AnalyzedImage(rect = rect, fullBleed = fullBleed)
    }
    val merged = mergeRects(analyzed.map { it.rect })

    // E: genuine vector artwork — dim keeps chart hues, invert would lie.
    if (raw.artOps >= ARTWORK_OPS || raw.filledFraction > ARTWORK_FILL_FRACTION) {
        return NightRegions(merged, NightPageMode.DIM)
    }
    // A: no raster content at all.
    if (analyzed.isEmpty()) {
        return NightRegions(emptyList(), NightPageMode.INVERT_ALL)
    }
    // F: full-bleed poster carrying a headline — dim keeps it legible, while
    // painting the photo back over inverted text would erase the headline.
    val dominant = analyzed.maxByOrNull {
        (it.rect.r - it.rect.l) * (it.rect.b - it.rect.t)
    }
    if (dominant != null && dominant.fullBleed && raw.firstTextOp != null) {
        return NightRegions(merged, NightPageMode.DIM)
    }
    // B/C/D: invert everything, then paint pictures back dimmed (not bright).
    return NightRegions(merged, NightPageMode.INVERT_WITH_DIMMED_IMAGES)
}

/**
 * Mean luminance (0..1) of opaque pixels, skipping transparent ones (a
 * transparent margin is not page content — it shows the reader background
 * through). Returns null when too few opaque pixels exist to judge.
 * Pure function over ARGB ints — JVM unit-testable.
 */
fun luminanceOf(pixels: IntArray): Float? {
    var sum = 0.0
    var count = 0
    for (pixel in pixels) {
        if ((pixel ushr 24) < 128) continue
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        sum += (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
        count++
    }
    if (count < 100) return null
    return (sum / count).toFloat()
}

/** Below this border luminance the page already counts as dark. */
const val DARK_BACKGROUND_LUMINANCE = 0.4f

/**
 * A PDF that is already dark must not be inverted (that would blast it
 * bright): text and photo pages alike take the dim path instead, which only
 * darkens further. Null (unknown) keeps the signal decision untouched.
 */
fun adjustForBackground(mode: NightPageMode, backgroundLuminance: Float?): NightPageMode {
    if (backgroundLuminance == null) return mode
    if (backgroundLuminance >= DARK_BACKGROUND_LUMINANCE) return mode
    return when (mode) {
        NightPageMode.INVERT_ALL -> NightPageMode.DIM
        NightPageMode.INVERT_WITH_DIMMED_IMAGES -> NightPageMode.DIM
        NightPageMode.DIM -> NightPageMode.DIM
    }
}
