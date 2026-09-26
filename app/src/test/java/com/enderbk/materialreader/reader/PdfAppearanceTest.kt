package com.enderbk.materialreader.reader

import com.enderbk.materialreader.data.ReaderBackground
import com.enderbk.materialreader.ui.theme.ReaderDarkDefault
import com.enderbk.materialreader.ui.theme.ReaderDarkDim
import com.enderbk.materialreader.ui.theme.ReaderLightDefault
import com.enderbk.materialreader.ui.theme.ReaderLightDim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfAppearanceTest {

    @Test
    fun nightRenderPathIgnoresAppTheme() {
        // The core decoupling guarantee: flipping MaterialTheme never changes
        // how pages render when night mode is on.
        for (darkTheme in listOf(false, true)) {
            for (background in ReaderBackground.entries) {
                val appearance = resolvePdfAppearance(
                    nightMode = true,
                    background = background,
                    darkTheme = darkTheme
                )
                assertEquals(PdfRenderPath.NIGHT, appearance.path)
            }
        }
    }

    @Test
    fun nightBackgroundAlwaysDark() {
        assertEquals(
            ReaderDarkDefault,
            resolvePdfAppearance(true, ReaderBackground.DEFAULT, darkTheme = false).background
        )
        assertEquals(
            ReaderDarkDim,
            resolvePdfAppearance(true, ReaderBackground.DIM, darkTheme = false).background
        )
        assertEquals(
            ReaderDarkDim,
            resolvePdfAppearance(true, ReaderBackground.DIM, darkTheme = true).background
        )
    }

    @Test
    fun nightOffAlwaysRendersNormalPages() {
        // Explicit white-pages rule: with night mode off, pages render like
        // the source document (white) in every theme and background.
        for (darkTheme in listOf(false, true)) {
            for (background in ReaderBackground.entries) {
                val appearance = resolvePdfAppearance(
                    nightMode = false,
                    background = background,
                    darkTheme = darkTheme
                )
                assertEquals(PdfRenderPath.NORMAL, appearance.path)
            }
        }
    }

    @Test
    fun normalModeFollowsBackgroundAndTheme() {
        assertEquals(
            PdfAppearance(PdfRenderPath.NORMAL, ReaderLightDefault),
            resolvePdfAppearance(false, ReaderBackground.DEFAULT, darkTheme = false)
        )
        assertEquals(
            PdfAppearance(PdfRenderPath.NORMAL, ReaderDarkDefault),
            resolvePdfAppearance(false, ReaderBackground.DEFAULT, darkTheme = true)
        )
        assertEquals(
            PdfAppearance(PdfRenderPath.NORMAL, ReaderLightDim),
            resolvePdfAppearance(false, ReaderBackground.DIM, darkTheme = false)
        )
        assertEquals(
            PdfAppearance(PdfRenderPath.NORMAL, ReaderDarkDim),
            resolvePdfAppearance(false, ReaderBackground.DIM, darkTheme = true)
        )
    }

    @Test
    fun instantInvertOnlyWhileDisagreeing() {
        // Night requested but normal bitmap showing (analyzed render pending).
        assertTrue(needsInstantInvert(night = true, showingNight = false))
        // Just turned off while the night bitmap is still up: double-invert
        // restores the original look until the normal render lands.
        assertTrue(needsInstantInvert(night = false, showingNight = true))
        // Agreement in either direction: no stand-in filter.
        assertFalse(needsInstantInvert(night = true, showingNight = true))
        assertFalse(needsInstantInvert(night = false, showingNight = false))
    }
}
