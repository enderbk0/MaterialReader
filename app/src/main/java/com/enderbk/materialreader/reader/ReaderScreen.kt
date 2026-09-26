package com.enderbk.materialreader.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.enderbk.materialreader.data.AppSettings
import com.enderbk.materialreader.data.ThemeMode
import com.enderbk.materialreader.settings.PreferenceGroup
import com.enderbk.materialreader.settings.RowPosition
import com.enderbk.materialreader.settings.SegmentedPreferenceRow
import com.enderbk.materialreader.settings.SwitchPreferenceRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import com.enderbk.materialreader.ui.expressiveEffects
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.enderbk.materialreader.ui.BleedTopBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import com.enderbk.materialreader.pdf.NightPageMode
import com.enderbk.materialreader.pdf.formatArgs
import com.enderbk.materialreader.pdf.messageRes
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import com.enderbk.materialreader.R
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enderbk.materialreader.data.DocumentStore
import com.enderbk.materialreader.data.PageLayout
import com.enderbk.materialreader.data.ReaderBackground
import com.enderbk.materialreader.data.SettingsStore
import com.enderbk.materialreader.data.ZoomMode
import com.enderbk.materialreader.pdf.PageLink
import com.enderbk.materialreader.pdf.ReaderBackend
import com.enderbk.materialreader.pdf.PdfOpenFailure
import com.enderbk.materialreader.pdf.TextHit
import com.enderbk.materialreader.util.formatPageIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    docId: String?,
    rawUri: String?,
    documents: DocumentStore,
    settings: SettingsStore,
    backend: ReaderBackend,
    keepScreenAwake: Boolean,
    readerBackground: ReaderBackground,
    darkTheme: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: ReaderViewModel = viewModel(
        key = "reader-${docId ?: rawUri}",
        factory = readerViewModelFactory(docId, rawUri, documents, settings, backend)
    )
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    var detailsOpen by remember { mutableStateOf(false) }
    // Diagnostic: which render path the current page actually took. Loaded
    // only while the dialog is open so it costs nothing otherwise.
    var detailsNightMode by remember { mutableStateOf<NightPageMode?>(null) }
    LaunchedEffect(detailsOpen, ui.nightMode, ui.currentPage) {
        detailsNightMode = if (detailsOpen && ui.nightMode) {
            vm.pageNightMode(ui.currentPage)
        } else {
            null
        }
    }
    var quickSettingsOpen by remember { mutableStateOf(false) }
    val quickVm: com.enderbk.materialreader.settings.SettingsViewModel = viewModel(
        key = "reader-quick-settings",
        factory = com.enderbk.materialreader.settings.settingsViewModelFactory(settings)
    )
    val quickSettings by quickVm.uiState.collectAsState()

    val appearance = resolvePdfAppearance(
        nightMode = ui.nightMode,
        background = readerBackground,
        darkTheme = darkTheme
    )
    val background = appearance.background

    DisposableEffect(keepScreenAwake, ui.status) {
        view.keepScreenOn = keepScreenAwake && ui.status == ReaderStatus.Ready
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        modifier = modifier,
        containerColor = background,
        topBar = {
            if (ui.searchActive) {
                ReaderSearchBar(
                    query = ui.searchQuery,
                    onQueryChange = vm::onSearchQueryChange,
                    onClose = { vm.setSearchActive(false) }
                )
            } else {
                BleedTopBar(
                    containerColor = background,
                    title = {
                        Text(
                            ui.entry?.displayName ?: stringResource(R.string.reader_fallback_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.desc_back_to_library))
                        }
                    },
                    actions = {
                        val ready = ui.status == ReaderStatus.Ready
                        IconButton(onClick = { vm.setSearchActive(true) }, enabled = ready) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.desc_search_in_pdf))
                        }
                        IconButton(onClick = { vm.setTextSheet(true) }, enabled = ready) {
                            Icon(Icons.Filled.Description, contentDescription = stringResource(R.string.desc_page_text))
                        }
                        ReaderOverflowMenu(
                            ui = ui,
                            onZoomIn = { vm.zoomBy(1.25f) },
                            onZoomOut = { vm.zoomBy(1f / 1.25f) },
                            onZoomMode = vm::onZoomModeChange,
                            onLayout = vm::onLayoutChange,
                            onNightMode = vm::onNightModeChange,
                            darkTheme = darkTheme,
                            onPrint = {
                                val entry = ui.entry
                                if (entry != null) printPdf(context, entry.uri, entry.displayName)
                            },
                            onShare = {
                                val entry = ui.entry
                                if (entry != null) sharePdf(context, entry.uri)
                            },
                            onDetails = { detailsOpen = true },
                            onOpenSettings = { quickSettingsOpen = true }
                        )
                    },
                )
            }
        },
        bottomBar = {
            if (ui.status == ReaderStatus.Ready && !ui.searchActive) {
                ReaderBottomBar(
                    ui = ui,
                    onPrev = vm::onPrevPage,
                    onNext = vm::onNextPage,
                    onZoomIn = { vm.zoomBy(1.25f) },
                    onZoomOut = { vm.zoomBy(1f / 1.25f) },
                    onJumpClick = { vm.setJumpDialog(true) }
                )
            }
        }
    ) { padding ->
        when (val status = ui.status) {
            ReaderStatus.Loading -> ReaderLoading(Modifier.fillMaxSize().padding(padding))
            is ReaderStatus.Error -> if (status.failure == PdfOpenFailure.FileNotFound && ui.entry != null) {
                val missingName = ui.entry!!.displayName
                AlertDialog(
                    onDismissRequest = onBack,
                    title = { Text(stringResource(R.string.reader_not_found_title)) },
                    text = { Text(stringResource(R.string.reader_not_found_body, missingName)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                vm.removeFromLibrary()
                                onBack()
                            }
                        ) { Text(stringResource(R.string.reader_not_found_yes)) }
                    },
                    dismissButton = {
                        TextButton(onClick = onBack) {
                            Text(stringResource(R.string.reader_not_found_no))
                        }
                    }
                )
            } else ReaderErrorPanel(
                message = stringResource(
                    status.failure.messageRes(),
                    *status.failure.formatArgs(
                        ui.entry?.displayName
                            ?: stringResource(R.string.reader_fallback_title)
                    )
                ),
                onRetry = vm::retry,
                onBack = onBack,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            ReaderStatus.Ready -> {
                if (ui.searchActive) {
                    SearchResults(
                        ui = ui,
                        onHitClick = vm::onSearchHitClick,
                        modifier = Modifier.fillMaxSize().padding(padding)
                    )
                } else {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize().padding(padding)
                    ) {
                        ReaderContent(
                            ui = ui,
                            viewportWidth = maxWidth,
                            viewportHeight = maxHeight,
                            background = background,
                            render = if (appearance.path == PdfRenderPath.NIGHT) {
                                vm::renderNightPage
                            } else {
                                vm::renderPage
                            },
                            aspectFor = vm::pageAspectPoints,
                            pointsWidthFor = { index -> vm.pageSizePoints(index)?.first },
                            onPageSettled = vm::onPageSettled,
                            onUserScale = vm::onUserScale,
                            onDoubleTapZoom = vm::onToggleDoubleTapZoom,
                            consumeScrollRequest = vm::consumeScrollRequest
                        )
                    }
                }
            }
        }

        if (detailsOpen && ui.entry != null) {
            val appearanceLabel = when {
                !ui.nightMode -> "Normal"
                detailsNightMode == null -> "Night (analyzing…)"
                detailsNightMode == NightPageMode.INVERT_ALL -> "Night · text invert"
                detailsNightMode == NightPageMode.INVERT_WITH_DIMMED_IMAGES -> "Night · photos preserved"
                else -> "Night · dimmed"
            }
            DocumentDetailsDialog(
                entry = ui.entry!!,
                currentPage = ui.currentPage,
                pageCount = ui.pageCount,
                appearance = appearanceLabel,
                appTheme = if (darkTheme) stringResource(R.string.reader_details_dark) else stringResource(R.string.reader_details_light),
                nightToggle = if (ui.nightMode) stringResource(R.string.reader_details_on) else stringResource(R.string.reader_details_off),
                onDismiss = { detailsOpen = false }
            )
        }

        if (ui.jumpDialogOpen) {
            JumpToPageDialog(
                pageCount = ui.pageCount,
                currentPage = ui.currentPage,
                onDismiss = { vm.setJumpDialog(false) },
                onConfirm = vm::onJumpRequest
            )
        }

        if (ui.textSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { vm.setTextSheet(false) },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                PageTextSheet(
                    pageOneBased = ui.currentPage + 1,
                    pageCount = ui.pageCount,
                    loading = ui.pageTextLoading,
                    text = ui.pageText,
                    links = ui.pageLinks
                )
            }
        }

        if (quickSettingsOpen) {
            ModalBottomSheet(
                onDismissRequest = { quickSettingsOpen = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                QuickSettingsSheet(
                    settings = quickSettings,
                    nightAvailable = darkTheme,
                    onThemeMode = quickVm::setThemeMode,
                    onNightMode = quickVm::setNightMode,
                    onKeepAwake = quickVm::setKeepAwake,
                    onReaderBackground = quickVm::setReaderBackground
                )
            }
        }
    }
}

@Composable
private fun ReaderLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.reader_opening), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ReaderErrorPanel(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.reader_error_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.desc_back_to_library)) }
            FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.reader_retry)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    BleedTopBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.reader_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.desc_clear_search))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() })
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.desc_close_search))
            }
        }
    )
}

@Composable
private fun SearchResults(
    ui: ReaderUiState,
    onHitClick: (TextHit) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        ui.searchRunning -> {
            Column(
                modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.reader_searching))
            }
        }
        !ui.searchSearched -> {
            Box(modifier, contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.reader_search_idle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ui.searchHits.isEmpty() -> {
            Box(modifier, contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.reader_search_no_matches, ui.searchQuery),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        else -> {
            LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "hits-header") {
                    Text(
                        if (ui.searchHits.size == 1) stringResource(R.string.reader_match_single) else stringResource(R.string.reader_matches, ui.searchHits.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
                ui.searchHits.forEachIndexed { index, hit ->
                    item(key = "hit-$index-${hit.pageIndex}") {
                        ListItem(
                            headlineContent = {
                                Text(stringResource(R.string.reader_hit_page, hit.pageIndex + 1), style = MaterialTheme.typography.titleSmall)
                            },
                            supportingContent = {
                                Text(hit.snippet, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        // Accessible tap target wired below the item text.
                        TextButton(
                            onClick = { onHitClick(hit) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(stringResource(R.string.reader_go_to_page, hit.pageIndex + 1))
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderOverflowMenu(
    ui: ReaderUiState,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomMode: (ZoomMode) -> Unit,
    onLayout: (PageLayout) -> Unit,
    onNightMode: (Boolean) -> Unit,
    darkTheme: Boolean,
    onPrint: () -> Unit,
    onShare: () -> Unit,
    onDetails: () -> Unit,
    onOpenSettings: () -> Unit
) {

    var expanded by remember { mutableStateOf(false) }
    val ready = ui.status == ReaderStatus.Ready
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.desc_reader_options))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_zoom_in)) },
                leadingIcon = { Icon(Icons.Filled.ZoomIn, contentDescription = null) },
                enabled = ready,
                onClick = { expanded = false; onZoomIn() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_zoom_out)) },
                leadingIcon = { Icon(Icons.Filled.ZoomOut, contentDescription = null) },
                enabled = ready,
                onClick = { expanded = false; onZoomOut() }
            )
            HorizontalDivider()
            ZoomMode.entries.forEach { mode ->
                val selected = ui.zoomMode == mode
                DropdownMenuItem(
                    text = {
                        Text(
                            when (mode) {
                                ZoomMode.FIT_WIDTH -> stringResource(R.string.reader_fit_width)
                                ZoomMode.FIT_PAGE -> stringResource(R.string.reader_fit_page)
                                ZoomMode.ACTUAL_SIZE -> stringResource(R.string.reader_actual_size)
                            }
                        )
                    },
                    trailingIcon = {
                        if (selected) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    enabled = ready,
                    onClick = { expanded = false; onZoomMode(mode) }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(if (ui.layout == PageLayout.CONTINUOUS) stringResource(R.string.reader_single_page) else stringResource(R.string.reader_continuous))
                },
                enabled = ready,
                onClick = {
                    expanded = false
                    onLayout(if (ui.layout == PageLayout.CONTINUOUS) PageLayout.SINGLE_PAGE else PageLayout.CONTINUOUS)
                }
            )
            // Single-fire toggle: the Switch is display-only (like the
            // settings rows) so one tap can never toggle twice and cancel
            // itself out. The whole row is the tap target.
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_night_mode)) },
                trailingIcon = {
                    Switch(
                        checked = ui.nightMode && darkTheme,
                        enabled = ready && darkTheme,
                        onCheckedChange = null
                    )
                },
                enabled = ready && darkTheme,
                onClick = {
                    expanded = false
                    onNightMode(!ui.nightMode)
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_print)) },
                leadingIcon = { Icon(Icons.Filled.Print, contentDescription = null) },
                enabled = ready,
                onClick = {
                    expanded = false
                    onPrint()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_share_document)) },
                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                enabled = ready,
                onClick = {
                    expanded = false
                    onShare()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_details)) },
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                enabled = ready,
                onClick = {
                    expanded = false
                    onDetails()
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_settings)) },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = { expanded = false; onOpenSettings() }
            )
        }
    }
}

@Composable
private fun ReaderBottomBar(
    ui: ReaderUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onJumpClick: () -> Unit
) {
    Column {
        val progress = if (ui.pageCount > 1) {
            ui.currentPage.toFloat() / (ui.pageCount - 1).toFloat()
        } else {
            0f
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
        BottomAppBar {
            if (ui.layout == PageLayout.SINGLE_PAGE) {
                IconButton(onClick = onPrev, enabled = ui.currentPage > 0) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_previous_page))
                }
            } else {
                // Continuous scroll: page turning is done by scrolling, so the
                // side slots hold always-visible zoom controls (pinch and
                // double-tap zoom keep working too).
                IconButton(onClick = onZoomOut, enabled = ui.userScale > 0.55f) {
                    Icon(Icons.Filled.ZoomOut, contentDescription = stringResource(R.string.reader_zoom_out))
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onJumpClick) {
                Text(
                    formatPageIndicator(ui.currentPage + 1, ui.pageCount) +
                        if (ui.userScale != 1f) " • ${(ui.userScale * 100).toInt()}%" else "",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.weight(1f))
            if (ui.layout == PageLayout.SINGLE_PAGE) {
                IconButton(onClick = onNext, enabled = ui.currentPage < ui.pageCount - 1) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.reader_next_page))
                }
            } else {
                IconButton(onClick = onZoomIn, enabled = ui.userScale < 4.95f) {
                    Icon(Icons.Filled.ZoomIn, contentDescription = stringResource(R.string.reader_zoom_in))
                }
            }
        }
    }
}

@Composable
private fun ReaderContent(
    ui: ReaderUiState,
    viewportWidth: Dp,
    viewportHeight: Dp,
    background: Color,
    render: suspend (index: Int, widthPx: Int) -> Bitmap?,
    aspectFor: (Int) -> Float?,
    pointsWidthFor: (Int) -> Int?,
    onPageSettled: (Int) -> Unit,
    onUserScale: (Float) -> Unit,
    onDoubleTapZoom: () -> Unit,
    consumeScrollRequest: () -> Int?
) {
    val density = LocalDensity.current
    val latestScale by rememberUpdatedState(ui.userScale)
    // Finger anchor (LazyColumn coordinates, px): while pinching, scale
    // changes are compensated around this point so content stays under the
    // fingers instead of drifting. Null when no pinch is active.
    var pinchAnchorY by remember { mutableStateOf<Float?>(null) }
    val pinch = Modifier.pointerInput(Unit) {
        // Photo-style two-finger zoom, tracked manually: the stock transform
        // detector lets the scroll container steal two-finger moves, so pinch
        // silently did nothing. Single-finger moves are never touched (scroll
        // keeps working) while two-finger moves are consumed and turn into
        // zoom — exactly like zooming a picture.
        //
        // Zoom factor ACCUMULATES per gesture against the scale at gesture
        // start: per-frame deltas below the VM's throttle would otherwise be
        // dropped with their motion lost forever, freezing slow pinches.
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var previousDistance = 0f
            var pinching = false
            var base = latestScale
            var cumulative = 1f
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size < 2) {
                    pinching = false
                    previousDistance = 0f
                    pinchAnchorY = null
                    if (pressed.isEmpty()) break
                    continue
                }
                val distance = (pressed[0].position - pressed[1].position).getDistance()
                pinchAnchorY = (pressed[0].position.y + pressed[1].position.y) / 2f
                if (!pinching) {
                    // Arm immediately AND consume: otherwise this first frame
                    // leaks to the scroll container and the list starts moving
                    // under the fingers before zoom takes over.
                    pinching = true
                    previousDistance = distance
                    base = latestScale
                    cumulative = 1f
                    pressed.forEach { it.consume() }
                    continue
                }
                if (previousDistance > 0f && distance > 0f && distance != previousDistance) {
                    pressed.forEach { it.consume() }
                    cumulative *= distance / previousDistance
                    onUserScale((base * cumulative).coerceIn(0.5f, 5f))
                }
                previousDistance = distance
            }
            pinchAnchorY = null
        }
    }

    if (ui.layout == PageLayout.SINGLE_PAGE) {
        SinglePageContent(
            ui = ui,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            pinch = pinch,
            render = render,
            aspectFor = aspectFor,
            pointsWidthFor = pointsWidthFor,
            onDoubleTapZoom = onDoubleTapZoom,
        )
    } else {
        val listState = rememberLazyListState()
        val firstVisible = listState.firstVisibleItemIndex
        LaunchedEffect(firstVisible, ui.pageCount) {
            if (ui.pageCount > 0) onPageSettled(firstVisible)
        }
        LaunchedEffect(ui.scrollRequest) {
            val target = ui.scrollRequest
            if (target != null) {
                listState.scrollToItem(target)
                consumeScrollRequest()
            }
        }
        // Finger-anchored pinch: when the scale changes mid-pinch, shift the
        // scroll offset so the content point under the fingers stays put.
        // (Single-page mode is center-anchored instead; there is no list.)
        var lastScale by remember { mutableFloatStateOf(ui.userScale) }
        LaunchedEffect(ui.userScale) {
            val previous = lastScale
            lastScale = ui.userScale
            val anchor = pinchAnchorY
            if (anchor != null && previous > 0f && ui.userScale != previous) {
                val ratio = ui.userScale / previous
                val offset = listState.firstVisibleItemScrollOffset
                val corrected = ((offset + anchor) * ratio - anchor).toInt().coerceAtLeast(0)
                listState.scrollToItem(listState.firstVisibleItemIndex, corrected)
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
        // While zoomed the list locks: one finger pans the zoomed page
        // freely in 2D (photo-style) instead of fighting the scroll.
            LazyColumn(
                state = listState,
                userScrollEnabled = ui.userScale <= 1.02f,
                modifier = Modifier.fillMaxSize().then(pinch),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(count = ui.pageCount, key = { it }) { index ->
                    ContinuousPage(
                        index = index,
                        ui = ui,
                        viewportWidth = viewportWidth,
                        viewportHeight = viewportHeight,
                        densityScale = density.density,
                        render = render,
                        aspectFor = aspectFor,
                        pointsWidthFor = pointsWidthFor,
                        onDoubleTapZoom = onDoubleTapZoom,
                        background = background,
                    )
                }
            }
            // Soft top glow instead of a hard rectangle: fades in while the list
            // moves, fades out when it settles. Purely visual, never consumes touch.
            val glowAlpha by animateFloatAsState(
                targetValue = if (listState.isScrollInProgress) 1f else 0f,
                animationSpec = expressiveEffects(),
                label = "scrollGlow"
            )
            if (glowAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .graphicsLayer { alpha = glowAlpha }
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun ContinuousPage(
    index: Int,
    ui: ReaderUiState,
    viewportWidth: Dp,
    viewportHeight: Dp,
    densityScale: Float,
    render: suspend (index: Int, widthPx: Int) -> Bitmap?,
    aspectFor: (Int) -> Float?,
    pointsWidthFor: (Int) -> Int?,
    onDoubleTapZoom: () -> Unit,
    background: Color,
) {
    val aspect = remember(index, ui.pageCount) { aspectFor(index) }
    val width = remember(ui.zoomMode, ui.userScale, viewportWidth, viewportHeight, aspect, index) {
        pageWidth(ui.zoomMode, ui.userScale, viewportWidth, viewportHeight, aspect) {
            pointsWidthFor(index)
        }
    }
    val renderPx = remember(width, densityScale) {
        ((width.value * densityScale + 159) / 160 * 160).toInt().coerceIn(240, 2048)
    }
    val maxPanPx = remember(width, aspect, viewportWidth, viewportHeight, densityScale) {
        val widthPx = width.value * densityScale
        val heightPx = widthPx / (aspect ?: (1f / 1.4142f))
        panBounds(
            contentW = widthPx,
            contentH = heightPx,
            viewportW = viewportWidth.value * densityScale,
            viewportH = viewportHeight.value * densityScale
        )
    }
    PdfPageItem(
        index = index,
        pageCount = ui.pageCount,
        width = width,
        renderWidthPx = renderPx,
        aspect = aspect,
        render = render,
        onDoubleTapZoom = onDoubleTapZoom,
        pannable = ui.userScale > 1.02f,
        maxPanPx = maxPanPx,
        night = ui.nightMode
    )
}

@Composable
private fun SinglePageContent(
    ui: ReaderUiState,
    viewportWidth: Dp,
    viewportHeight: Dp,
    pinch: Modifier,
    render: suspend (index: Int, widthPx: Int) -> Bitmap?,
    aspectFor: (Int) -> Float?,
    pointsWidthFor: (Int) -> Int?,
    onDoubleTapZoom: () -> Unit,
) {
    val density = LocalDensity.current
    val index = ui.currentPage
    val aspect = remember(index, ui.pageCount) { aspectFor(index) }
    val width = remember(ui.zoomMode, ui.userScale, viewportWidth, viewportHeight, aspect, index) {
        pageWidth(ui.zoomMode, ui.userScale, viewportWidth, viewportHeight, aspect) {
            pointsWidthFor(index)
        }
    }
    val renderPx = remember(width) {
        ((width.value * density.density + 159) / 160 * 160).toInt().coerceIn(240, 2048)
    }
    val maxPanPx = remember(width, aspect, viewportWidth, viewportHeight) {
        val widthPx = width.value * density.density
        val heightPx = widthPx / (aspect ?: (1f / 1.4142f))
        panBounds(
            contentW = widthPx,
            contentH = heightPx,
            viewportW = viewportWidth.value * density.density,
            viewportH = viewportHeight.value * density.density
        )
    }
    Box(
        modifier = Modifier.fillMaxSize().then(pinch),
        contentAlignment = Alignment.Center
    ) {
        PdfPageItem(
            index = index,
            pageCount = ui.pageCount,
            width = width,
            renderWidthPx = renderPx,
            aspect = aspect,
            render = render,
            onDoubleTapZoom = onDoubleTapZoom,
            pannable = ui.userScale > 1.02f,
            maxPanPx = maxPanPx,
            night = ui.nightMode
        )
    }
}

@Composable
private fun JumpToPageDialog(
    pageCount: Int,
    currentPage: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember(currentPage) { mutableStateOf((currentPage + 1).toString()) }
    var invalid by remember(currentPage) { mutableStateOf(false) }

    fun submit() {
        val page = text.toIntOrNull()
        if (page == null || page !in 1..pageCount) {
            invalid = true
        } else {
            onConfirm(page - 1)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_goto_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.reader_goto_range, pageCount),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it.filter { c -> c.isDigit() }.take(6)
                        invalid = false
                    },
                    label = { Text(stringResource(R.string.reader_goto_label)) },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) {
                        { Text(stringResource(R.string.reader_goto_invalid, pageCount)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { submit() }
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit) { Text(stringResource(R.string.reader_goto_go)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun PageTextSheet(
    pageOneBased: Int,
    pageCount: Int,
    loading: Boolean,
    text: String,
    links: List<PageLink>
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.reader_page_text_title, pageOneBased, pageCount),
            style = MaterialTheme.typography.titleMedium
        )
        when {
            loading -> {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            text.isBlank() -> {
                Text(
                    stringResource(R.string.reader_page_text_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                SelectionContainer {
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { copyText(context, text) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.reader_copy))
                    }
                    OutlinedButton(onClick = { shareText(context, text) }) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.reader_share))
                    }
                }
            }
        }
        if (links.isNotEmpty()) {
            HorizontalDivider()
            Text(stringResource(R.string.reader_links_title), style = MaterialTheme.typography.titleSmall)
            links.forEach { link ->
                TextButton(onClick = { openLink(context, link.uri) }) {
                    Icon(Icons.Filled.Link, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        link.label ?: link.uri,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("PDF text", text))
    Toast.makeText(context, context.getString(R.string.reader_copied), Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.reader_share_text_title)))
    }
}

private fun openLink(context: Context, uri: String) {
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val ok = runCatching { context.startActivity(intent); true }.getOrDefault(false)
    if (!ok) {
        Toast.makeText(context, context.getString(R.string.reader_no_app_for_link), Toast.LENGTH_SHORT).show()
    }
}

/** Sends the PDF bytes straight to the system print service. Fully offline. */
private fun printPdf(context: Context, uriString: String, jobName: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
    runCatching {
        printManager.print(jobName, PdfPrintAdapter(context.applicationContext, uriString, jobName), null)
    }
}

private class PdfPrintAdapter(
    private val appContext: Context,
    private val uriString: String,
    private val jobName: String
) : android.print.PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: android.print.PrintAttributes?,
        newAttributes: android.print.PrintAttributes?,
        cancellationSignal: android.os.CancellationSignal?,
        callback: android.print.PrintDocumentAdapter.LayoutResultCallback?,
        extras: android.os.Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = android.print.PrintDocumentInfo.Builder(jobName)
            .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<android.print.PageRange>,
        destination: android.os.ParcelFileDescriptor,
        cancellationSignal: android.os.CancellationSignal?,
        callback: android.print.PrintDocumentAdapter.WriteResultCallback?
    ) {
        try {
            appContext.contentResolver.openInputStream(android.net.Uri.parse(uriString))?.use { input ->
                java.io.FileOutputStream(destination.fileDescriptor).use { output ->
                    input.copyTo(output)
                }
            }
            callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.message)
        }
    }
}

/** Shares the PDF file itself with another app (read permission granted). */
private fun sharePdf(context: Context, uriString: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(uriString))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.reader_share_pdf_title)))
    }
}

@Composable
private fun DocumentDetailsDialog(
    entry: com.enderbk.materialreader.data.DocumentEntry,
    currentPage: Int,
    pageCount: Int,
    appearance: String,
    appTheme: String,
    nightToggle: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_details)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                DetailRow(stringResource(R.string.reader_details_name), entry.displayName)
                DetailRow(stringResource(R.string.reader_details_size),
                    com.enderbk.materialreader.util.formatBytes(entry.sizeBytes)
                )
                if (pageCount > 0) {
                    DetailRow(stringResource(R.string.reader_details_pages), pageCount.toString())
                    DetailRow(stringResource(R.string.reader_details_position),
                        formatPageIndicator(currentPage + 1, pageCount)
                    )
                }
                DetailRow(stringResource(R.string.reader_details_folder), entry.folder ?: stringResource(R.string.folder_all))
                DetailRow(stringResource(R.string.reader_details_appearance), appearance)
                DetailRow(stringResource(R.string.reader_details_theme), appTheme)
                DetailRow(stringResource(R.string.reader_details_night), nightToggle)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** Minimal in-reader settings: theme, night mode, keep-awake. No navigation. */
@Composable
private fun QuickSettingsSheet(
    settings: AppSettings,
    nightAvailable: Boolean,
    onThemeMode: (ThemeMode) -> Unit,
    onNightMode: (Boolean) -> Unit,
    onKeepAwake: (Boolean) -> Unit,
    onReaderBackground: (ReaderBackground) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            stringResource(R.string.reader_quick_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        PreferenceGroup {
            SegmentedPreferenceRow(
                title = stringResource(R.string.settings_theme),
                options = listOf(
                    ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                    ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                    ThemeMode.DARK to stringResource(R.string.settings_theme_dark)
                ),
                selected = settings.themeMode,
                onSelect = onThemeMode,
                position = RowPosition.TOP
            )
            SwitchPreferenceRow(
                title = stringResource(R.string.reader_night_mode),
                subtitle = if (nightAvailable) {
                    stringResource(R.string.settings_night_sub)
                } else {
                    stringResource(R.string.settings_night_unavailable)
                },
                checked = settings.nightMode && nightAvailable,
                onCheckedChange = onNightMode,
                position = RowPosition.MIDDLE,
                enabled = nightAvailable
            )
            SwitchPreferenceRow(
                title = stringResource(R.string.settings_keep_awake),
                subtitle = stringResource(R.string.settings_keep_awake_sub),
                checked = settings.keepScreenAwake,
                onCheckedChange = onKeepAwake,
                position = RowPosition.MIDDLE
            )
            SegmentedPreferenceRow(
                title = stringResource(R.string.settings_background),
                options = listOf(
                    ReaderBackground.DEFAULT to stringResource(R.string.settings_background_default),
                    ReaderBackground.DIM to stringResource(R.string.settings_background_dim)
                ),
                selected = settings.readerBackground,
                onSelect = onReaderBackground,
                position = RowPosition.BOTTOM
            )
        }
    }
}
