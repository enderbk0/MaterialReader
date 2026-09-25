package com.enderbk.materialreader.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enderbk.materialreader.BuildConfig
import com.enderbk.materialreader.data.PageLayout
import com.enderbk.materialreader.data.ReaderBackground
import com.enderbk.materialreader.data.SettingsStore
import com.enderbk.materialreader.data.ThemeMode
import com.enderbk.materialreader.data.ZoomMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsStore,
    onBack: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: SettingsViewModel = viewModel(factory = settingsViewModelFactory(settings))
    val state by vm.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item { SectionHeader("Appearance") }
            item {
                PreferenceGroup {
                    SegmentedPreferenceRow(
                        title = "Theme",
                        options = listOf(
                            ThemeMode.SYSTEM to "System",
                            ThemeMode.LIGHT to "Light",
                            ThemeMode.DARK to "Dark"
                        ),
                        selected = state.themeMode,
                        onSelect = vm::setThemeMode,
                        position = RowPosition.TOP
                    )
                    SwitchPreferenceRow(
                        title = "Dynamic color",
                        subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            "Match your wallpaper on Android 12+"
                        } else {
                            "Requires Android 12 or newer"
                        },
                        checked = state.dynamicColor,
                        onCheckedChange = vm::setDynamicColor,
                        position = RowPosition.MIDDLE,
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    )
                    SegmentedPreferenceRow(
                        title = "Reader background",
                        options = listOf(
                            ReaderBackground.DEFAULT to "Default",
                            ReaderBackground.PAPER to "Paper",
                            ReaderBackground.DIM to "Dim"
                        ),
                        selected = state.readerBackground,
                        onSelect = vm::setReaderBackground,
                        position = RowPosition.BOTTOM
                    )
                }
            }

            item { SectionHeader("Reading") }
            item {
                PreferenceGroup {
                    SegmentedPreferenceRow(
                        title = "Default zoom",
                        options = listOf(
                            ZoomMode.FIT_WIDTH to "Width",
                            ZoomMode.FIT_PAGE to "Page",
                            ZoomMode.ACTUAL_SIZE to "Actual"
                        ),
                        selected = state.defaultZoomMode,
                        onSelect = vm::setZoomMode,
                        position = RowPosition.TOP
                    )
                    SegmentedPreferenceRow(
                        title = "Page layout",
                        options = listOf(
                            PageLayout.CONTINUOUS to "Continuous",
                            PageLayout.SINGLE_PAGE to "Single"
                        ),
                        selected = state.pageLayout,
                        onSelect = vm::setPageLayout,
                        position = RowPosition.MIDDLE
                    )
                    SwitchPreferenceRow(
                        title = "Night mode",
                        subtitle = "Invert page colors for reading in the dark (colored figures keep their hues)",
                        checked = state.nightMode,
                        onCheckedChange = vm::setNightMode,
                        position = RowPosition.MIDDLE
                    )
                    SwitchPreferenceRow(
                        title = "Keep screen awake while reading",
                        subtitle = "Prevent the display from sleeping in the reader",
                        checked = state.keepScreenAwake,
                        onCheckedChange = vm::setKeepAwake,
                        position = RowPosition.MIDDLE
                    )
                    SwitchPreferenceRow(
                        title = "Remember reading position",
                        subtitle = "Reopen documents where you left off (stored only on this device)",
                        checked = state.rememberReadingPosition,
                        onCheckedChange = vm::setRememberPosition,
                        position = RowPosition.BOTTOM
                    )
                }
            }

            item { SectionHeader("Privacy") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrivacyLine("Your PDFs stay on this device.")
                        PrivacyLine("No accounts, no cloud, no sync.")
                        PrivacyLine("No analytics, ads, or tracking.")
                        PrivacyLine("No internet permission — the app cannot send anything anywhere.")
                    }
                }
            }

            item { SectionHeader("About") }
            item {
                PreferenceGroup {
                    NavigationPreferenceRow(
                        title = "About MaterialReader",
                        subtitle = "Version ${BuildConfig.VERSION_NAME}",
                        position = RowPosition.ALONE,
                        onClick = onAboutClick
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp)
    )
}

@Composable
private fun PrivacyLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
