package com.enderbk.materialreader.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.enderbk.materialreader.data.DocumentStore
import com.enderbk.materialreader.data.SettingsStore
import com.enderbk.materialreader.data.ThemeMode
import com.enderbk.materialreader.library.DocumentMetaSource
import com.enderbk.materialreader.library.LibraryScreen
import com.enderbk.materialreader.pdf.ReaderBackend
import com.enderbk.materialreader.reader.ReaderScreen
import com.enderbk.materialreader.settings.AboutScreen
import com.enderbk.materialreader.settings.SettingsScreen
import com.enderbk.materialreader.ui.theme.MaterialReaderTheme

/**
 * App shell: theme, adaptive navigation (bottom bar on phones, rail on
 * tablets/foldables), and the nav graph. The reader is a full-screen
 * destination without the navigation suite.
 */
@Composable
fun MaterialReaderApp(
    documents: DocumentStore,
    settings: SettingsStore,
    meta: DocumentMetaSource,
    backend: ReaderBackend,
    externalUri: String?,
    onConsumeExternalUri: () -> Unit
) {
    val appSettings by settings.settings.collectAsState(initial = null)
    val themeMode = appSettings?.themeMode ?: ThemeMode.SYSTEM
    val dynamicColor = appSettings?.dynamicColor ?: true

    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialReaderTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
        val navController = rememberNavController()

        LaunchedEffect(externalUri) {
            if (externalUri != null) {
                navController.navigate(Routes.readerForUri(externalUri)) {
                    launchSingleTop = true
                }
                onConsumeExternalUri()
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 840.dp
            val backStack by navController.currentBackStackEntryAsState()
            val currentRoute = backStack?.destination?.route
            val showSuite = currentRoute == Routes.LIBRARY || currentRoute == Routes.SETTINGS

            Row(Modifier.fillMaxSize()) {
                if (showSuite && useRail) {
                    NavigationRailSuite(
                        currentRoute = currentRoute,
                        onLibrary = {
                            navController.navigate(Routes.LIBRARY) { launchSingleTop = true }
                        },
                        onSettings = {
                            navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                        }
                    )
                }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    // Each tab (library/reader/settings) is itself a Scaffold that
                    // applies the status-bar and navigation-bar insets to its own
                    // bars. If the outer Scaffold also inset its content by those
                    // bars, every top bar would be pushed down twice and a gap
                    // would sit above the bottom bar — so both are excluded here
                    // (cutout insets are still respected).
                    contentWindowInsets = ScaffoldDefaults.contentWindowInsets
                        .exclude(WindowInsets.statusBars)
                        .exclude(WindowInsets.navigationBars),
                    bottomBar = {
                        if (showSuite && !useRail) {
                            BottomSuite(
                                currentRoute = currentRoute,
                                onLibrary = {
                                    navController.navigate(Routes.LIBRARY) { launchSingleTop = true }
                                },
                                onSettings = {
                                    navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                                }
                            )
                        }
                    }
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = Routes.LIBRARY,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        composable(Routes.LIBRARY) {
                            LibraryScreen(
                                documents = documents,
                                settings = settings,
                                meta = meta,
                                onOpenReader = { id ->
                                    navController.navigate(Routes.readerForDocument(id))
                                },
                                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                            )
                        }
                        composable(
                            route = Routes.READER_PATTERN,
                            arguments = Routes.readerArguments
                        ) { entry ->
                            val docId = entry.arguments?.getString(Routes.ARG_DOC_ID)
                            val uri = entry.arguments?.getString(Routes.ARG_URI)
                            ReaderScreen(
                                docId = docId,
                                rawUri = uri,
                                documents = documents,
                                settings = settings,
                                backend = backend,
                                keepScreenAwake = appSettings?.keepScreenAwake ?: false,
                                readerBackground = appSettings?.readerBackground
                                    ?: com.enderbk.materialreader.data.ReaderBackground.DEFAULT,
                                darkTheme = darkTheme,
                                onBack = { navController.popBackStack() },
                                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                            )
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                settings = settings,
                                onBack = { navController.popBackStack() },
                                onAboutClick = { navController.navigate(Routes.ABOUT) }
                            )
                        }
                        composable(Routes.ABOUT) {
                            AboutScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomSuite(
    currentRoute: String?,
    onLibrary: () -> Unit,
    onSettings: () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Routes.LIBRARY,
            onClick = onLibrary,
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("Library") }
        )
        NavigationBarItem(
            selected = currentRoute == Routes.SETTINGS,
            onClick = onSettings,
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text("Settings") }
        )
    }
}

@Composable
private fun NavigationRailSuite(
    currentRoute: String?,
    onLibrary: () -> Unit,
    onSettings: () -> Unit
) {
    NavigationRail {
        NavigationRailItem(
            selected = currentRoute == Routes.LIBRARY,
            onClick = onLibrary,
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("Library") }
        )
        NavigationRailItem(
            selected = currentRoute == Routes.SETTINGS,
            onClick = onSettings,
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text("Settings") }
        )
    }
}
