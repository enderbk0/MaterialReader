package com.enderbk.materialreader.reader

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.enderbk.materialreader.R
import com.enderbk.materialreader.pdf.nightModeMatrix
import kotlinx.coroutines.delay

/**
 * One rendered PDF page. The bitmap comes from [PdfSession]'s bounded cache
 * (owned by the session — never recycled here); while it loads, a tonal
 * placeholder with the expected aspect ratio keeps scroll position stable.
 *
 * Double-tap toggles zoom; pinch is handled by the parent scroll container.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfPageItem(
    index: Int,
    pageCount: Int,
    width: Dp,
    renderWidthPx: Int,
    aspect: Float?,
    render: suspend (index: Int, widthPx: Int) -> Bitmap?,
    onDoubleTapZoom: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Free 2D pan when zoomed (photo-style). The parent list locks its scroll
     * while zoomed, so one finger moves the page in any direction — including
     * diagonally, which axis-locked nested scroll can never do.
     */
    pannable: Boolean = false,
    /** Max pan distance in each direction, in pixels. */
    maxPanPx: Offset = Offset.Zero,
    /**
     * Render-path flag (normal vs night). Part of the render effect key:
     * without it, toggling night mode mid-read would keep showing the stale
     * bitmap until something else changed the width. The previous bitmap
     * stays visible (no placeholder flash) until the new path renders.
     */
    night: Boolean = false
) {
    // Keyed by page only (NOT by width): during pinch the width changes every
    // frame, and resetting the bitmap each time made pages flicker through
    // placeholders so zoom looked broken. The previous bitmap stays visible
    // (stretched) until the settled width finishes rendering.
    var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }

    /**
     * Whether the bitmap on screen came from the night render path.
     * Toggling night mode must respond INSTANTLY even though analyzed night
     * renders take a while (document analysis runs once per page on IO):
     * while the shown bitmap and the requested mode disagree, a GPU invert
     * stands in — double inversion when turning off restores the original
     * look. The analyzed bitmap then swaps in and the filter lifts.
     */
    var showingNight by remember(index) { mutableStateOf(false) }
    var pan by remember(index) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(index, renderWidthPx, night) {
        // Settle debounce: coalesce rapid width changes from an active pinch
        // into a single render instead of queueing one per frame.
        delay(120)
        render(index, renderWidthPx)?.let {
            bitmap = it
            showingNight = night
        }
    }

    // Snap back to centered when zooming all the way out.
    LaunchedEffect(pannable) {
        if (!pannable) pan = Offset.Zero
    }

    val ratio = bitmap?.let { bmp ->
        if (bmp.height > 0) bmp.width.toFloat() / bmp.height.toFloat() else null
    } ?: aspect ?: (1f / 1.4142f) // A4 fallback until the real size is known.

    Box(
        modifier = modifier
            .requiredWidth(width)
            .pointerInput(pannable, maxPanPx) {
                // Free pan: single finger drags the zoomed page in any
                // direction. Two fingers are left for the parent's pinch.
                if (!pannable) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size > 1) continue
                        val change = pressed[0]
                        change.consume()
                        val delta = change.position - change.previousPosition
                        pan = Offset(
                            x = (pan.x + delta.x).coerceIn(-maxPanPx.x, maxPanPx.x),
                            y = (pan.y + delta.y).coerceIn(-maxPanPx.y, maxPanPx.y)
                        )
                    }
                }
            },
        contentAlignment = Alignment.TopCenter
    ) {
        // Plain page look: no card shadow or rounded frame, just the page.
        // A soft top glow while scrolling is drawn by the parent instead.
        Box(
            modifier = Modifier
                .requiredWidth(width)
                .aspectRatio(ratio)
                .graphicsLayer {
                    translationX = pan.x
                    translationY = pan.y
                }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Image,
                    onClickLabel = "Page ${index + 1} of $pageCount. Double tap to toggle zoom.",
                    onClick = {},
                    onDoubleClick = onDoubleTapZoom
                )
        ) {
            val bmp = bitmap
            if (bmp != null && !bmp.isRecycled) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = stringResource(
                        com.enderbk.materialreader.R.string.reader_page_description,
                        index + 1,
                        pageCount
                    ),
                    modifier = Modifier.fillMaxSize(),
                    colorFilter = if (needsInstantInvert(night, showingNight)) {
                        remember { instantNightFilter() }
                    } else {
                        null
                    }
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Whether a stand-in GPU invert is needed: exactly when the requested mode
 * disagrees with what is on screen (night requested but showing a normal
 * bitmap, or vice versa after turning night off). Pure rule, unit-tested.
 */
internal fun needsInstantInvert(night: Boolean, showingNight: Boolean): Boolean =
    night != showingNight

/** Full-page GPU invert used only as an instant stand-in (see above). */
internal fun instantNightFilter(): ColorFilter =
    ColorFilter.colorMatrix(ColorMatrix(nightModeMatrix().values))
