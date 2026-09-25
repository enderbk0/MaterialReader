package com.enderbk.materialreader.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Grouped preference rows in the style of ReFra's settings (Apache-2.0,
 * reimplemented here): rows sit in a [surfaceContainer][androidx.compose.material3.MaterialTheme.colorScheme]
 * group with position-aware corners — large outer radius, small inner radius —
 * that morph toward a pill while pressed. For on/off rows the *whole row* is
 * the toggle target (the [Switch] itself is display-only), which gives a
 * bigger touch target and cleaner TalkBack output.
 */

/** Position of a row inside a [PreferenceGroup]. */
enum class RowPosition { TOP, MIDDLE, BOTTOM, ALONE }

/** Row position for index [i] in a group of [size] rows. Pure function. */
fun rowPositionFor(i: Int, size: Int): RowPosition = when {
    size <= 1 -> RowPosition.ALONE
    i == 0 -> RowPosition.TOP
    i == size - 1 -> RowPosition.BOTTOM
    else -> RowPosition.MIDDLE
}

/**
 * Position-aware row shape: [outer] radius on group edges, [inner] radius
 * between rows. Pure function, unit-tested.
 */
fun rowShape(
    position: RowPosition,
    outer: Dp = 24.dp,
    inner: Dp = 8.dp
): RoundedCornerShape = when (position) {
    RowPosition.ALONE -> RoundedCornerShape(outer)
    RowPosition.TOP -> RoundedCornerShape(
        topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner
    )
    RowPosition.MIDDLE -> RoundedCornerShape(inner)
    RowPosition.BOTTOM -> RoundedCornerShape(
        topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer
    )
}

@Composable
fun PreferenceGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        content()
    }
}

@Composable
fun SwitchPreferenceRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    position: RowPosition,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val outer by animateDpAsState(
        targetValue = if (pressed) 48.dp else 24.dp,
        label = "preferenceRowCorner"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape(position, outer))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interaction,
                indication = LocalIndication.current,
                onValueChange = onCheckedChange
            )
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null
        )
    }
}

@Composable
fun <T> SegmentedPreferenceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    position: RowPosition,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape(position))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        SegmentedChoiceButtons(options = options, selected = selected, onSelect = onSelect)
    }
}

@Composable
private fun <T> SegmentedChoiceButtons(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size
                ),
                label = {
                    Text(
                        label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

/** Static title + subtitle row (no interaction). */
@Composable
fun StaticPreferenceRow(
    title: String,
    subtitle: String?,
    position: RowPosition,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape(position))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Whole-row navigation target with a trailing chevron (or external icon). */
@Composable
fun NavigationPreferenceRow(
    title: String,
    subtitle: String?,
    position: RowPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    external: Boolean = false,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape(position))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(
                role = Role.Button,
                onClickLabel = "Open $title",
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Box(modifier = Modifier.padding(end = 16.dp)) {
                leading()
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Icon(
            if (external) Icons.AutoMirrored.Filled.OpenInNew
            else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
