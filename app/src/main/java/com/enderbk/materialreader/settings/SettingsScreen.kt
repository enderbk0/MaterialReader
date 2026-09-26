package com.enderbk.materialreader.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.enderbk.materialreader.ui.BleedTopBar
import com.enderbk.materialreader.ui.BottomBleedOverlay
import com.enderbk.materialreader.ui.TopBleedOverlay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enderbk.materialreader.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enderbk.materialreader.BuildConfig
import com.enderbk.materialreader.data.PageLayout
import com.enderbk.materialreader.data.ReaderBackground
import com.enderbk.materialreader.data.SettingsStore
import com.enderbk.materialreader.data.ThemeMode
import com.enderbk.materialreader.data.ZoomMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: SettingsStore,
    onBack: () -> Unit,
    onAboutClick: () -> Unit,
    showBack: Boolean,
    darkThemeActive: Boolean,
    modifier: Modifier = Modifier
) {
    val vm: SettingsViewModel = viewModel(factory = settingsViewModelFactory(settings))
    val state by vm.uiState.collectAsState()
    var languageDialogOpen by remember { mutableStateOf(false) }
    val languageScope = rememberCoroutineScope()
    var pendingRestartLanguage by remember { mutableStateOf<String?>(null) }
    val activity = LocalContext.current as android.app.Activity

    Scaffold(
        modifier = modifier,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .exclude(WindowInsets.navigationBars),
        topBar = {
            BleedTopBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = if (showBack) {
                    {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                } else {
                    {}
                }
            )
        }
    ) { padding ->
        // When the floating pill replaces the bottom bar it overlays content:
        // keep extra clearance so the last rows never hide underneath it.
        val floatingPill = state.experimentalEnabled && state.floatingNavBar
        Box(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, top = 8.dp, end = 16.dp,
                bottom = if (floatingPill) 104.dp else 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item { SectionHeader(stringResource(R.string.settings_appearance)) }
            item {
                PreferenceGroup {
                    SegmentedPreferenceRow(
                        title = stringResource(R.string.settings_theme),
                        options = listOf(
                            ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                            ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                            ThemeMode.DARK to stringResource(R.string.settings_theme_dark)
                        ),
                        selected = state.themeMode,
                        onSelect = vm::setThemeMode,
                        position = RowPosition.TOP
                    )
                    SwitchPreferenceRow(
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            stringResource(R.string.settings_dynamic_on)
                        } else {
                            stringResource(R.string.settings_dynamic_needs_12)
                        },
                        checked = state.dynamicColor,
                        onCheckedChange = vm::setDynamicColor,
                        position = RowPosition.MIDDLE,
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    )
                    SegmentedPreferenceRow(
                        title = stringResource(R.string.settings_background),
                        options = listOf(
                            ReaderBackground.DEFAULT to stringResource(R.string.settings_background_default),
                            ReaderBackground.DIM to stringResource(R.string.settings_background_dim)
                        ),
                        selected = state.readerBackground,
                        onSelect = vm::setReaderBackground,
                        position = RowPosition.MIDDLE
                    )
                    SwitchPreferenceRow(
                        title = stringResource(R.string.settings_rounded_font),
                        subtitle = stringResource(R.string.settings_rounded_font_sub),
                        checked = state.roundedFont,
                        onCheckedChange = vm::setRoundedFont,
                        position = RowPosition.MIDDLE
                    )
                    NavigationPreferenceRow(
                        title = stringResource(R.string.settings_language),
                        subtitle = appLanguageName(state.appLanguage),
                        position = RowPosition.BOTTOM,
                        onClick = { languageDialogOpen = true }
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.settings_reading)) }
            item {
                PreferenceGroup {
                    SegmentedPreferenceRow(
                        title = stringResource(R.string.settings_zoom),
                        options = listOf(
                            ZoomMode.FIT_WIDTH to stringResource(R.string.settings_zoom_width),
                            ZoomMode.FIT_PAGE to stringResource(R.string.settings_zoom_page),
                            ZoomMode.ACTUAL_SIZE to stringResource(R.string.settings_zoom_actual)
                        ),
                        selected = state.defaultZoomMode,
                        onSelect = vm::setZoomMode,
                        position = RowPosition.TOP
                    )
                    SegmentedPreferenceRow(
                        title = stringResource(R.string.settings_layout),
                        options = listOf(
                            PageLayout.CONTINUOUS to stringResource(R.string.settings_layout_continuous),
                            PageLayout.SINGLE_PAGE to stringResource(R.string.settings_layout_single)
                        ),
                        selected = state.pageLayout,
                        onSelect = vm::setPageLayout,
                        position = RowPosition.MIDDLE
                    )
                    SwitchPreferenceRow(
                        title = stringResource(R.string.settings_night),
                        subtitle = if (darkThemeActive) {
                            stringResource(R.string.settings_night_sub)
                        } else {
                            stringResource(R.string.settings_night_unavailable)
                        },
                        checked = state.nightMode && darkThemeActive,
                        onCheckedChange = vm::setNightMode,
                        position = RowPosition.MIDDLE,
                        enabled = darkThemeActive
                    )
                    SwitchPreferenceRow(
                        title = stringResource(R.string.settings_keep_awake),
                        subtitle = stringResource(R.string.settings_keep_awake_sub),
                        checked = state.keepScreenAwake,
                        onCheckedChange = vm::setKeepAwake,
                        position = RowPosition.MIDDLE
                    )
                    SwitchPreferenceRow(
                        title = stringResource(R.string.settings_remember),
                        subtitle = stringResource(R.string.settings_remember_sub),
                        checked = state.rememberReadingPosition,
                        onCheckedChange = vm::setRememberPosition,
                        position = RowPosition.BOTTOM
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.settings_privacy)) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrivacyLine(stringResource(R.string.settings_privacy_1))
                        PrivacyLine(stringResource(R.string.settings_privacy_2))
                        PrivacyLine(stringResource(R.string.settings_privacy_3))
                        PrivacyLine(stringResource(R.string.settings_privacy_4))
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_about)) }
            item {
                PreferenceGroup {
                    NavigationPreferenceRow(
                        title = stringResource(R.string.settings_about_row),
                        subtitle = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                        position = RowPosition.ALONE,
                        onClick = onAboutClick
                    )
                }
            }
        }
            TopBleedOverlay()
            BottomBleedOverlay(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
    pendingRestartLanguage?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingRestartLanguage = null },
            title = { Text(stringResource(R.string.settings_language_restart_title)) },
            text = { Text(stringResource(R.string.settings_language_restart_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        com.enderbk.materialreader.util.applyAppLanguage(activity, pending)
                        pendingRestartLanguage = null
                        com.enderbk.materialreader.util.restartApp(activity)
                    }
                ) { Text(stringResource(R.string.settings_language_restart_now)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestartLanguage = null }) {
                    Text(stringResource(R.string.settings_language_restart_later))
                }
            }
        )
    }
    if (languageDialogOpen) {
        LanguageDialog(
            current = state.appLanguage,
            onDismiss = { languageDialogOpen = false },
            onSelect = { tag ->
                // Persist first; the restart dialog applies afterwards so the
                // relaunched app deterministically reads the new value back.
                languageScope.launch {
                    vm.setAppLanguageSync(tag)
                    pendingRestartLanguage = tag
                }
            }
        )
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

/** Native language names (never translated). Empty tag = follow system. */
private val AppLanguageChoices = listOf("en" to "English", "vi" to "Tiếng Việt")

@Composable
private fun appLanguageName(tag: String): String =
    if (tag.isBlank()) {
        stringResource(R.string.settings_language_system)
    } else {
        // Unknown/stale tags (e.g. removed languages) fall back to system.
        AppLanguageChoices.firstOrNull { it.first == tag }?.second
            ?: stringResource(R.string.settings_language_system)
    }

@Composable
private fun LanguageDialog(
    current: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = {
                            onSelect("")
                            onDismiss()
                        }, role = Role.Button)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = current.isBlank(), onClick = {
                        onSelect("")
                        onDismiss()
                    })
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.settings_language_system),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                AppLanguageChoices.forEach { (tag, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = {
                                onSelect(tag)
                                onDismiss()
                            }, role = Role.Button)
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == tag, onClick = {
                            onSelect(tag)
                            onDismiss()
                        })
                        Spacer(Modifier.width(4.dp))
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}
