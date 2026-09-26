package com.enderbk.materialreader.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.enderbk.materialreader.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Floating pill navigation (experimental): a fully-rounded bar with the two
 * top-level destinations, in dynamic scheme colors. Only the selected tab
 * shows its label; labels expand/collapse with expressive motion. Built with
 * stable APIs to mirror the expressive floating toolbar without taking an
 * alpha dependency.
 */
@Composable
fun FloatingNavBar(
    currentRoute: String?,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingDestination(
                selected = currentRoute == Routes.LIBRARY,
                onClick = onLibrary,
                icon = Icons.Filled.History,
                label = stringResource(R.string.nav_library)
            )
            FloatingDestination(
                selected = currentRoute == Routes.SETTINGS,
                onClick = onSettings,
                icon = Icons.Filled.Settings,
                label = stringResource(R.string.nav_settings)
            )
        }
    }
}

@Composable
private fun RowScope.FloatingDestination(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String
) {
    // Color crossfades alongside the label instead of cutting instantly.
    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "pillBackground"
    )
    val foreground = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    // Specs hoisted out of the animation: same instance every recomposition,
    // eased tweens with no spring overshoot so the small pill settles cleanly
    // even while the new screen composes underneath the tab switch.
    val labelEnter = remember {
        fadeIn(tween(250)) +
            expandHorizontally(animationSpec = tween(400, easing = FastOutSlowInEasing))
    }
    val labelExit = remember {
        fadeOut(tween(150)) +
            shrinkHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing))
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClickLabel = if (label == stringResource(R.string.nav_library)) stringResource(R.string.nav_open_library) else stringResource(R.string.nav_open_settings), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = foreground)
        AnimatedVisibility(
            visible = selected,
            enter = labelEnter,
            exit = labelExit
        ) {
            Text(
                label,
                color = foreground,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
