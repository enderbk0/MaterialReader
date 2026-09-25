package com.enderbk.materialreader.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.enderbk.materialreader.ui.BleedTopBar
import com.enderbk.materialreader.ui.TopBleedOverlay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.LocalIndication
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enderbk.materialreader.data.SettingsStore

/**
 * Experimental settings: gated behind the About-screen easter egg. Features
 * here may change or disappear; the master pill at the top enables the whole
 * section (grey when off, dynamic primary when on).
 */
@Composable
fun ExperimentalScreen(
    settings: SettingsStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: SettingsViewModel = viewModel(factory = settingsViewModelFactory(settings))
    val state by vm.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            BleedTopBar(
                title = { Text("Experimental") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "master") {
                ExperimentalMasterPill(
                    on = state.experimentalEnabled,
                    onToggle = vm::setExperimentalEnabled
                )
            }
            item(key = "nav-header") {
                Text(
                    "Navigation",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp)
                )
            }
            item(key = "nav-group") {
                PreferenceGroup {
                    SwitchPreferenceRow(
                        title = "Floating navigation bar",
                        subtitle = "Pill-style bar instead of the bottom bar",
                        checked = state.floatingNavBar,
                        onCheckedChange = vm::setFloatingNavBar,
                        position = RowPosition.ALONE,
                        enabled = state.experimentalEnabled
                    )
                }
            }
            item(key = "note") {
                Text(
                    "Experimental features may change or disappear in updates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp)
                )
            }
        }
            TopBleedOverlay()
    }
    }
}

/**
 * Full-width master pill: grey ([surfaceContainerHighest][androidx.compose.material3.MaterialTheme.colorScheme])
 * when off, dynamic [primary][androidx.compose.material3.MaterialTheme.colorScheme] when on.
 */
@Composable
private fun ExperimentalMasterPill(
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val container = if (on) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = if (on) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(container)
            .toggleable(
                value = on,
                role = Role.Switch,
                interactionSource = interaction,
                indication = LocalIndication.current,
                onValueChange = onToggle
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Science, contentDescription = null, tint = content)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Experimental features",
                style = MaterialTheme.typography.titleMedium,
                color = if (on) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                if (on) "On" else "Off",
                style = MaterialTheme.typography.bodySmall,
                color = content
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            if (on) "ON" else "OFF",
            style = MaterialTheme.typography.labelLarge,
            color = content
        )
    }
}
