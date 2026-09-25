package com.enderbk.materialreader.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
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
import com.enderbk.materialreader.settings.ExperimentalScreen
import com.enderbk.materialreader.ui.expressiveEffects
import com.enderbk.materialreader.ui.expressiveSpatial
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
            // The floating pill replaces the standard suite wherever it shows.
            val floatingOn = (appSettings?.experimentalEnabled == true) &&
                (appSettings?.floatingNavBar == true)
            // Settings is a tab when reached from the library: no back arrow.
            // Reached from the reader/about, it keeps one.
            val showSettingsBack = navController.previousBackStackEntry
                ?.destination?.route?.let { it != Routes.LIBRARY && it != Routes.SETTINGS }
                ?: false

            Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                if (showSuite && useRail && !floatingOn) {
                    NavigationRailSuite(
                        currentRoute = currentRoute,
                        onLibrary = {
                            navController.navigateTab(Routes.LIBRARY)
                        },
                        onSettings = {
                            navController.navigateTab(Routes.SETTINGS)
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
                        if (showSuite && !useRail && !floatingOn) {
                            BottomSuite(
                                currentRoute = currentRoute,
                                onLibrary = {
                                    navController.navigateTab(Routes.LIBRARY)
                                },
                                onSettings = {
                                    navController.navigateTab(Routes.SETTINGS)
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
                                floatingPill = floatingOn
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
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                settings = settings,
                                onBack = { navController.popBackStack() },
                                onAboutClick = { navController.navigate(Routes.ABOUT) },
                                showBack = showSettingsBack
                            )
                        }
                        composable(Routes.ABOUT) {
                            AboutScreen(
                                settings = settings,
                                onBack = { navController.popBackStack() },
                                onExperimentalClick = { navController.navigate(Routes.EXPERIMENTAL) }
                            )
                        }
                        composable(Routes.EXPERIMENTAL) {
                            ExperimentalScreen(
                                settings = settings,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
                }
                AnimatedVisibility(
                    visible = showSuite && floatingOn,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(expressiveEffects()) +
                        scaleIn(expressiveSpatial(), initialScale = 0.85f),
                    exit = fadeOut(expressiveEffects()) +
                        scaleOut(expressiveSpatial(), targetScale = 0.85f)
                ) {
                    FloatingNavBar(
                        currentRoute = currentRoute,
                        onLibrary = {
                            navController.navigateTab(Routes.LIBRARY)
                        },
                        onSettings = {
                            navController.navigateTab(Routes.SETTINGS)
                        },
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = 12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Tab navigation that preloads once and retains: saveState/restoreState keep
 * each tab's ViewModel, scroll position, and composed content alive across
 * tab switches, so returning to Settings/Library is instant instead of a
 * cold rebuild every visit. Pushed screens (reader/about/experimental) keep
 * plain back-stack behavior.
 */
private fun androidx.navigation.NavHostController.navigateTab(route: String) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(Routes.LIBRARY) {
            saveState = true
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
            icon = { Icon(Icons.Filled.History, contentDescription = null) },
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
            icon = { Icon(Icons.Filled.History, contentDescription = null) },
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
