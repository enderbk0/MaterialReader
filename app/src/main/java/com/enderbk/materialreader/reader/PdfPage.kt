package com.enderbk.materialreader.reader

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    nightMode: Boolean = false
) {
    var bitmap by remember(index, renderWidthPx) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(index, renderWidthPx) {
        bitmap = null
        bitmap = render(index, renderWidthPx)
    }

    val ratio = bitmap?.let { bmp ->
        if (bmp.height > 0) bmp.width.toFloat() / bmp.height.toFloat() else null
    } ?: aspect ?: (1f / 1.4142f) // A4 fallback until the real size is known.

    Box(
        modifier = modifier.width(width),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            tonalElevation = 1.dp,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .width(width)
                .aspectRatio(ratio)
                .shadow(2.dp, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .combinedClickable(
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
                    contentDescription = "Page ${index + 1} of $pageCount",
                    modifier = Modifier.fillMaxSize(),
                    colorFilter = if (nightMode) {
                        remember { nightModeColorFilter() }
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
