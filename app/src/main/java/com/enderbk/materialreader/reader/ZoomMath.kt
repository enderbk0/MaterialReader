package com.enderbk.materialreader.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.enderbk.materialreader.data.ZoomMode

/**
 * Pure zoom geometry (no Android framework beyond unit types — JVM-testable).
 *
 * 100% is the mode's base width: FIT_WIDTH tracks the viewport, FIT_PAGE fits
 * the viewport, ACTUAL_SIZE is physical size (1pt = 1/72in, 1dp = 1/160in).
 * Scales above 1.0 grow the *layout* (via `requiredWidth`, which — unlike
 * `width()` — is not coerced into the parent's max constraints), so 125%,
 * 150%, 200% are genuinely larger pages, not just a bigger number.
 */
internal fun pageWidth(
    mode: ZoomMode,
    userScale: Float,
    viewportWidth: Dp,
    viewportHeight: Dp,
    aspect: Float?,
    pointsWidth: () -> Int?
): Dp {
    val base = when (mode) {
        ZoomMode.FIT_WIDTH -> viewportWidth
        ZoomMode.FIT_PAGE -> {
            val ratio = aspect ?: (1f / 1.4142f)
            val fitHeight = viewportHeight * ratio
            if (fitHeight < viewportWidth) fitHeight else viewportWidth
        }
        ZoomMode.ACTUAL_SIZE -> {
            val pts = pointsWidth() ?: 612
            Dp(pts * 160f / 72f)
        }
    }
    return (base * userScale).coerceAtLeast(48.dp)
}

/**
 * Symmetric pan limits (px) for content of [contentW]×[contentH] inside a
 * [viewportW]×[viewportH] viewport. Zero while the content fits — panning only
 * exists past 100%.
 */
internal fun panBounds(
    contentW: Float,
    contentH: Float,
    viewportW: Float,
    viewportH: Float
): Offset = Offset(
    x = ((contentW - viewportW) / 2f).coerceAtLeast(0f),
    y = ((contentH - viewportH) / 2f).coerceAtLeast(0f)
)
