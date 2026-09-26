package com.enderbk.materialreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard rectangular top bar. (Kept as the single place carrying the
 * Material 3 opt-in the compiler requires for this call site.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleedTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.surface
) {
    TopAppBar(
        modifier = modifier,
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor)
    )
}

/**
 * Soft color bleed for content scrolling underneath a top bar: a vertical
 * gradient starting at the exact bar color and fading to fully transparent.
 * Must OVERLAY the content (drawn last in a [Box]) — fading into an identical
 * background would be invisible. Never consumes touch.
 */
@Composable
fun TopBleedOverlay(
    containerColor: Color = MaterialTheme.colorScheme.surface,
    height: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    listOf(containerColor, Color.Transparent)
                )
            )
    )
}

/**
 * Mirror of [TopBleedOverlay] for the bottom edge: fades from transparent
 * into the navigation bar color, drawn over the content end and sitting
 * below the in-app navigation bar. Never consumes touch.
 */
@Composable
fun BottomBleedOverlay(
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    height: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, containerColor)
                )
            )
    )
}
