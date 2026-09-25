package com.enderbk.materialreader.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
import com.enderbk.materialreader.pdf.TextHit
import com.enderbk.materialreader.pdf.userMessage
import com.enderbk.materialreader.ui.theme.ReaderDarkDefault
import com.enderbk.materialreader.ui.theme.ReaderDarkDim
import com.enderbk.materialreader.ui.theme.ReaderDarkPaper
import com.enderbk.materialreader.ui.theme.ReaderLightDefault
import com.enderbk.materialreader.ui.theme.ReaderLightDim
import com.enderbk.materialreader.ui.theme.ReaderLightSepia
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
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: ReaderViewModel = viewModel(
        key = "reader-${docId ?: rawUri}",
        factory = readerViewModelFactory(docId, rawUri, documents, settings, backend)
    )
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current

    val background = readerBackgroundColor(readerBackground, darkTheme)

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
                TopAppBar(
                    title = {
                        Text(
                            ui.entry?.displayName ?: "Reader",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to library")
                        }
                    },
                    actions = {
                        val ready = ui.status == ReaderStatus.Ready
                        IconButton(onClick = { vm.setSearchActive(true) }, enabled = ready) {
                            Icon(Icons.Filled.Search, contentDescription = "Search in this PDF")
                        }
                        IconButton(onClick = { vm.setTextSheet(true) }, enabled = ready) {
                            Icon(Icons.Filled.Description, contentDescription = "Page text, copy and links")
                        }
                        ReaderOverflowMenu(
                            ui = ui,
                            onZoomIn = { vm.onUserScale(ui.userScale * 1.25f) },
                            onZoomOut = { vm.onUserScale(ui.userScale / 1.25f) },
                            onZoomMode = vm::onZoomModeChange,
                            onLayout = vm::onLayoutChange,
                            onNightMode = vm::onNightModeChange,
                            onOpenSettings = onOpenSettings
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = background)
                )
            }
        },
        bottomBar = {
            if (ui.status == ReaderStatus.Ready && !ui.searchActive) {
                ReaderBottomBar(
                    ui = ui,
                    onPrev = vm::onPrevPage,
                    onNext = vm::onNextPage,
                    onZoomIn = { vm.onUserScale(ui.userScale * 1.25f) },
                    onZoomOut = { vm.onUserScale(ui.userScale / 1.25f) },
                    onJumpClick = { vm.setJumpDialog(true) }
                )
            }
        }
    ) { padding ->
        when (val status = ui.status) {
            ReaderStatus.Loading -> ReaderLoading(Modifier.fillMaxSize().padding(padding))
            is ReaderStatus.Error -> ReaderErrorPanel(
                message = status.failure.userMessage(ui.entry?.displayName ?: "This document"),
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
                            render = vm::renderPage,
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
    }
}

private fun readerBackgroundColor(background: ReaderBackground, darkTheme: Boolean): Color =
    when (background) {
        ReaderBackground.DEFAULT -> if (darkTheme) ReaderDarkDefault else ReaderLightDefault
        ReaderBackground.PAPER -> if (darkTheme) ReaderDarkPaper else ReaderLightSepia
        ReaderBackground.DIM -> if (darkTheme) ReaderDarkDim else ReaderLightDim
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
        Text("Opening document…", style = MaterialTheme.typography.bodyLarge)
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
        Text("Couldn't open this PDF", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back to library") }
            FilledTonalButton(onClick = onRetry) { Text("Try again") }
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
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search in document") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
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
                Icon(Icons.Filled.Close, contentDescription = "Close search")
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
                Text("Searching…")
            }
        }
        !ui.searchSearched -> {
            Box(modifier, contentAlignment = Alignment.Center) {
                Text(
                    "Type to search the document text.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ui.searchHits.isEmpty() -> {
            Box(modifier, contentAlignment = Alignment.Center) {
                Text(
                    "No matches for \"${ui.searchQuery}\".",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        else -> {
            LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "hits-header") {
                    Text(
                        "${ui.searchHits.size} match${if (ui.searchHits.size == 1) "" else "es"}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
                ui.searchHits.forEachIndexed { index, hit ->
                    item(key = "hit-$index-${hit.pageIndex}") {
                        ListItem(
                            headlineContent = {
                                Text("Page ${hit.pageIndex + 1}", style = MaterialTheme.typography.titleSmall)
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
                            Text("Go to page ${hit.pageIndex + 1}")
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
    onOpenSettings: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val ready = ui.status == ReaderStatus.Ready
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Reader options")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            DropdownMenuItem(
                text = { Text("Zoom in") },
                leadingIcon = { Icon(Icons.Filled.ZoomIn, contentDescription = null) },
                enabled = ready,
                onClick = { expanded = false; onZoomIn() }
            )
            DropdownMenuItem(
                text = { Text("Zoom out") },
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
                                ZoomMode.FIT_WIDTH -> "Fit width"
                                ZoomMode.FIT_PAGE -> "Fit page"
                                ZoomMode.ACTUAL_SIZE -> "Actual size"
                            }
                        )
                    },
                    trailingIcon = {
                        if (selected) {
                            Icon(Icons.Filled.Check, contentDescription = "Selected")
                        }
                    },
                    enabled = ready,
                    onClick = { expanded = false; onZoomMode(mode) }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(if (ui.layout == PageLayout.CONTINUOUS) "Single page view" else "Continuous scroll")
                },
                enabled = ready,
                onClick = {
                    expanded = false
                    onLayout(if (ui.layout == PageLayout.CONTINUOUS) PageLayout.SINGLE_PAGE else PageLayout.CONTINUOUS)
                }
            )
            DropdownMenuItem(
                text = { Text("Night mode") },
                trailingIcon = {
                    Switch(
                        checked = ui.nightMode,
                        onCheckedChange = {
                            expanded = false
                            onNightMode(it)
                        }
                    )
                },
                enabled = ready,
                onClick = {
                    expanded = false
                    onNightMode(!ui.nightMode)
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Settings") },
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page")
                }
            } else {
                // Continuous scroll: page turning is done by scrolling, so the
                // side slots hold always-visible zoom controls (pinch and
                // double-tap zoom keep working too).
                IconButton(onClick = onZoomOut, enabled = ui.userScale > 0.55f) {
                    Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out")
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onJumpClick) {
                Text(
                    formatPageIndicator(ui.currentPage + 1, ui.pageCount),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.weight(1f))
            if (ui.layout == PageLayout.SINGLE_PAGE) {
                IconButton(onClick = onNext, enabled = ui.currentPage < ui.pageCount - 1) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page")
                }
            } else {
                IconButton(onClick = onZoomIn, enabled = ui.userScale < 4.95f) {
                    Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in")
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
    val pinch = Modifier.pointerInput(Unit) {
        // Photo-style two-finger zoom, tracked manually: the stock transform
        // detector lets the scroll container steal two-finger moves, so pinch
        // silently did nothing. Here single-finger moves are never touched
        // (scroll keeps working) while two-finger moves are consumed and turn
        // into zoom — exactly like zooming a picture.
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var previousDistance = 0f
            var pinching = false
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size < 2) {
                    pinching = false
                    previousDistance = 0f
                    if (pressed.isEmpty()) break
                    continue
                }
                val distance = (pressed[0].position - pressed[1].position).getDistance()
                if (!pinching) {
                    pinching = true
                    previousDistance = distance
                    continue
                }
                if (previousDistance > 0f && distance > 0f && distance != previousDistance) {
                    pressed.forEach { it.consume() }
                    onUserScale((latestScale * (distance / previousDistance)).coerceIn(0.5f, 5f))
                }
                previousDistance = distance
            }
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
            onDoubleTapZoom = onDoubleTapZoom
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
        LazyColumn(
            state = listState,
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
                    background = background
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
    background: Color
) {
    val aspect = remember(index, ui.pageCount) { aspectFor(index) }
    val width = remember(ui.zoomMode, ui.userScale, viewportWidth, viewportHeight, aspect, index) {
        pageWidth(ui.zoomMode, ui.userScale, viewportWidth, viewportHeight, aspect) {
            pointsWidthFor(index)
        }
    }
    val renderPx = remember(width, densityScale) {
        ((width.value * densityScale + 79) / 80 * 80).toInt().coerceIn(240, 2560)
    }
    PdfPageItem(
        index = index,
        pageCount = ui.pageCount,
        width = width,
        renderWidthPx = renderPx,
        aspect = aspect,
        render = render,
        onDoubleTapZoom = onDoubleTapZoom,
        nightMode = ui.nightMode
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
    onDoubleTapZoom: () -> Unit
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
        ((width.value * density.density + 79) / 80 * 80).toInt().coerceIn(240, 2560)
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
            nightMode = ui.nightMode
        )
    }
}

private fun pageWidth(
    mode: ZoomMode,
    userScale: Float,
    viewportWidth: Dp,
    viewportHeight: Dp,
    aspect: Float?,
    pointsWidth: () -> Int?
): Dp {
    val base = when (mode) {
        ZoomMode.FIT_WIDTH -> viewportWidth
        ZoomMode.FIT_PAGE -> {
            val ratio = aspect ?: (1f / 1.4142f)
            val fitHeight = viewportHeight * ratio
            if (fitHeight < viewportWidth) fitHeight else viewportWidth
        }
        ZoomMode.ACTUAL_SIZE -> {
            // 1 PDF point = 1/72 inch; 1 dp = 1/160 inch → points × 160/72 dp.
            val pts = pointsWidth() ?: 612
            Dp(pts * 160f / 72f)
        }
    }
    return (base * userScale).coerceAtLeast(48.dp)
}

@Composable
private fun JumpToPageDialog(
    pageCount: Int,
    currentPage: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember(currentPage) { mutableStateOf((currentPage + 1).toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to page") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Page 1–$pageCount", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it.filter { c -> c.isDigit() }.take(6)
                        error = null
                    },
                    label = { Text("Page number") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val page = text.toIntOrNull()
                            if (page == null || page !in 1..pageCount) {
                                error = "Enter a number between 1 and $pageCount."
                            } else {
                                onConfirm(page - 1)
                            }
                        }
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val page = text.toIntOrNull()
                if (page == null || page !in 1..pageCount) {
                    error = "Enter a number between 1 and $pageCount."
                } else {
                    onConfirm(page - 1)
                }
            }) { Text("Go") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
            "Page $pageOneBased of $pageCount — text",
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
                    "No extractable text on this page. It may be a scanned image — the page still displays normally above.",
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
                        Text("Copy")
                    }
                    OutlinedButton(onClick = { shareText(context, text) }) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Share")
                    }
                }
            }
        }
        if (links.isNotEmpty()) {
            HorizontalDivider()
            Text("Links on this page", style = MaterialTheme.typography.titleSmall)
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
    Toast.makeText(context, "Text copied", Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share text"))
    }
}

private fun openLink(context: Context, uri: String) {
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val ok = runCatching { context.startActivity(intent); true }.getOrDefault(false)
    if (!ok) {
        Toast.makeText(context, "No app can open this link.", Toast.LENGTH_SHORT).show()
    }
}
