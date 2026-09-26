package com.enderbk.materialreader.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.Image
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.enderbk.materialreader.ui.BleedTopBar
import com.enderbk.materialreader.ui.TopBleedOverlay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.enderbk.materialreader.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.enderbk.materialreader.BuildConfig
import com.enderbk.materialreader.data.AppSettings
import com.enderbk.materialreader.data.SettingsStore
import kotlinx.coroutines.launch

const val CONTRIBUTORS_URL = "$REPOSITORY_URL/graphs/contributors"
const val LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"

/**
 * About screen in the grouped settings style: app icon, name, and version on
 * top, then Project and dependency groups. Everything opens in the user's own
 * browser on explicit tap — the app itself stays offline.
 */
@Composable
fun AboutScreen(
    settings: SettingsStore,
    onBack: () -> Unit,
    onExperimentalClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appSettings by settings.settings.collectAsState(initial = AppSettings())
    var taps by remember { mutableIntStateOf(0) }

    fun onVersionTap() {
        if (appSettings.experimentalEnabled) {
            onExperimentalClick()
            return
        }
        taps++
        when (taps) {
            1 -> toast(context, context.getString(R.string.unlock_step_1))
            2 -> toast(context, context.getString(R.string.unlock_step_2))
            3 -> toast(context, context.getString(R.string.unlock_step_3))
            4 -> toast(context, context.getString(R.string.unlock_step_4))
            else -> {
                taps = 0
                toast(context, context.getString(R.string.unlock_done))
                scope.launch { settings.update { it.copy(experimentalEnabled = true) } }
                onExperimentalClick()
            }
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            BleedTopBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
            item(key = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_icon_art),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(24.dp))
                    )
                    Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable(
                            role = Role.Button,
                            onClick = ::onVersionTap
                        )
                    )
                    Text(
                        stringResource(R.string.about_tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Copyright (C) 2026 EnderBK",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item(key = "project-header") {
                Text(
                    stringResource(R.string.about_project),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                )
            }
            item(key = "creator-group") {
                PreferenceGroup {
                    NavigationPreferenceRow(
                        title = stringResource(R.string.about_creator),
                        subtitle = stringResource(R.string.about_creator_sub),
                        position = RowPosition.ALONE,
                        onClick = { openUrl(context, "https://github.com/enderbk0") },
                        external = true,
                        leading = {
                            Image(
                                painter = painterResource(id = R.drawable.avatar_enderbk),
                                contentDescription = stringResource(R.string.about_creator_sub),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                            )
                        }
                    )
                }
            }
            item(key = "project-group") {
                PreferenceGroup {
                    NavigationPreferenceRow(
                        title = stringResource(R.string.about_source),
                        subtitle = stringResource(R.string.about_source_sub),
                        position = RowPosition.TOP,
                        onClick = { openUrl(context, REPOSITORY_URL) },
                        external = true
                    )
                    NavigationPreferenceRow(
                        title = stringResource(R.string.about_contributors),
                        subtitle = stringResource(R.string.about_contributors_sub),
                        position = RowPosition.MIDDLE,
                        onClick = { openUrl(context, CONTRIBUTORS_URL) },
                        external = true
                    )
                    NavigationPreferenceRow(
                        title = stringResource(R.string.about_license),
                        subtitle = stringResource(R.string.about_license_sub),
                        position = RowPosition.BOTTOM,
                        onClick = { openUrl(context, LICENSE_URL) },
                        external = true
                    )
                }
            }
            item(key = "deps-header") {
                Text(
                    stringResource(R.string.about_deps),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp)
                )
            }
            item(key = "deps-group") {
                PreferenceGroup {
                    DependencyLicenses.forEachIndexed { index, dep ->
                        StaticPreferenceRow(
                            title = dep.name,
                            subtitle = stringResource(R.string.about_dep_version, dep.version, dep.license),
                            position = rowPositionFor(index, DependencyLicenses.size)
                        )
                    }
                }
            }
        }
            TopBleedOverlay()
    }
    }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
    val ok = runCatching { context.startActivity(intent); true }.getOrDefault(false)
    if (!ok) {
        Toast.makeText(context, context.getString(R.string.reader_no_app_for_link), Toast.LENGTH_SHORT).show()
    }
}
