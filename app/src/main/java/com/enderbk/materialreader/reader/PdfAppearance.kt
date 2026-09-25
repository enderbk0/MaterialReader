package com.enderbk.materialreader.reader

import androidx.compose.ui.graphics.Color
import com.enderbk.materialreader.data.ReaderBackground
import com.enderbk.materialreader.ui.theme.ReaderDarkDefault
import com.enderbk.materialreader.ui.theme.ReaderDarkDim
import com.enderbk.materialreader.ui.theme.ReaderLightDefault
import com.enderbk.materialreader.ui.theme.ReaderLightDim

/**
 * Explicit PDF appearance model.
 *
 * Two independent concepts, never implicitly coupled:
 *
 * 1. Application theme (Light / Dark / System → [darkTheme]): tints the
 *    *surrounding* Compose UI only.
 * 2. PDF page appearance ([PdfRenderPath]): [nightMode] alone selects the
 *    render path. Flipping the Material theme never changes how pages are
 *    rendered — only the surrounding background, and night mode pins that
 *    background dark in every theme.
 *
 * Pure function of (nightMode, background, darkTheme) — JVM unit-tested for
 * every combination, including Dark+Night ≡ Light+Night rendering.
 */
enum class PdfRenderPath { NORMAL, NIGHT }

data class PdfAppearance(
    val path: PdfRenderPath,
    val background: Color
)

fun resolvePdfAppearance(
    nightMode: Boolean,
    background: ReaderBackground,
    darkTheme: Boolean
): PdfAppearance {
    // Night OFF + any theme (including dark) → NORMAL path: pages render
    // white, exactly like the source document.
    if (nightMode) {
        return PdfAppearance(
            path = PdfRenderPath.NIGHT,
            background = when (background) {
                ReaderBackground.DEFAULT -> ReaderDarkDefault
                ReaderBackground.DIM -> ReaderDarkDim
            }
        )
    }
    return PdfAppearance(
        path = PdfRenderPath.NORMAL,
        background = when (background) {
            ReaderBackground.DEFAULT -> if (darkTheme) ReaderDarkDefault else ReaderLightDefault
            ReaderBackground.DIM -> if (darkTheme) ReaderDarkDim else ReaderLightDim
        }
    )
}
