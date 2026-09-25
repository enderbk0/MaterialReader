package com.enderbk.materialreader.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enderbk.materialreader.data.DocumentEntry
import com.enderbk.materialreader.data.DocumentStore
import com.enderbk.materialreader.data.SettingsStore
import com.enderbk.materialreader.data.SortOrder
import com.enderbk.materialreader.domain.progress
import com.enderbk.materialreader.util.formatBytes
import com.enderbk.materialreader.util.formatLastOpened

@Composable
fun LibraryScreen(
    documents: DocumentStore,
    settings: SettingsStore,
    meta: DocumentMetaSource,
    onOpenReader: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: LibraryViewModel = viewModel(factory = libraryViewModelFactory(documents, settings, meta))
    val state by vm.uiState.collectAsState()
    var searchActive by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.onDocumentPicked(uri.toString())
    }

    LaunchedEffect(vm) {
        vm.openDocument.collect { id -> onOpenReader(id) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            LibraryTopBar(
                searchActive = searchActive,
                query = state.query,
                libraryEmpty = state.isEmpty,
                sortOrder = state.sortOrder,
                onOpenSearch = { searchActive = true },
                onCloseSearch = {
                    searchActive = false
                    vm.onQueryChange("")
                },
                onClearQuery = { vm.onQueryChange("") },
                onSortChange = vm::onSortChange,
                onOpenSettings = onOpenSettings
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(arrayOf("application/pdf")) },
                icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                text = { Text("Open PDF") }
            )
        }
    ) { padding ->
        if (state.isEmpty) {
            LibraryEmptyState(
                onOpenPdf = { picker.launch(arrayOf("application/pdf")) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (searchActive) {
                    LibrarySearchField(
                        query = state.query,
                        onQueryChange = vm::onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                if (state.visible.isEmpty()) {
                    NoSearchResults(
                        query = state.query,
                        onClear = { vm.onQueryChange("") },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                if (state.pinned.isNotEmpty()) {
                    item(key = "header-pinned") {
                        SectionHeader("Pinned")
                    }
                    items(state.pinned, key = { "p-${it.id}" }) { entry ->
                        DocumentRow(
                            entry = entry,
                            onOpen = { vm.onOpenEntry(entry) },
                            onTogglePin = { vm.onTogglePin(entry) },
                            onRemove = { vm.onRemove(entry) }
                        )
                    }
                }
                if (state.recent.isNotEmpty()) {
                    item(key = "header-recent") {
                        SectionHeader(if (state.pinned.isEmpty()) "Recent" else "All documents")
                    }
                    items(state.recent, key = { "r-${it.id}" }) { entry ->
                        DocumentRow(
                            entry = entry,
                            onOpen = { vm.onOpenEntry(entry) },
                            onTogglePin = { vm.onTogglePin(entry) },
                            onRemove = { vm.onRemove(entry) }
                        )
                    }
                }
                    }
                }
            }
        }
    }
}

// Scoped opt-in: with Material 3 1.4.0 the compiler flags this standard
// TopAppBar call site as experimental. No experimental components are used
// (TopAppBar, IconButton, DropdownMenu are all stable APIs); the opt-in is
// kept on this small private composable instead of the whole screen.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(
    searchActive: Boolean,
    query: String,
    libraryEmpty: Boolean,
    sortOrder: SortOrder,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onClearQuery: () -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onOpenSettings: () -> Unit
) {
    TopAppBar(
        title = { Text(if (searchActive) "Search PDFs" else "MaterialReader") },
        navigationIcon = {
            if (searchActive) {
                IconButton(onClick = onCloseSearch) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                }
            }
        },
        actions = {
            if (!searchActive) {
                IconButton(onClick = onOpenSearch, enabled = !libraryEmpty) {
                    Icon(Icons.Filled.Search, contentDescription = "Search PDFs")
                }
                SortMenu(current = sortOrder, onSelect = onSortChange)
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            } else if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        }
    )
}

@Composable
private fun SectionHeader(text: String) {    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
private fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.focusRequester(focusRequester),
        placeholder = { Text("Search by filename") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() })
    )
}

@Composable
private fun SortMenu(current: SortOrder, onSelect: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort documents")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (order) {
                                SortOrder.RECENT -> "Most recent"
                                SortOrder.NAME -> "Name"
                                SortOrder.SIZE -> "Size"
                            } + if (order == current) " ✓" else ""
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(order)
                    }
                )
            }
        }
    }
}

@Composable
private fun DocumentRow(
    entry: DocumentEntry,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onRemove: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val metaLine = buildString {
        entry.pageCount?.let { append("$it pages") }
        entry.sizeBytes?.let {
            if (isNotEmpty()) append(" • ")
            append(formatBytes(it))
        }
        if (entry.lastOpenedEpochMillis > 0) {
            if (isNotEmpty()) append(" • ")
            append(formatLastOpened(entry.lastOpenedEpochMillis))
        }
        entry.progress()?.let {
            if (it > 0f) append(" • ${(it * 100).toInt()}% read")
        }
    }
    ListItem(
        headlineContent = {
            Text(entry.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            if (metaLine.isNotEmpty()) {
                Text(metaLine, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },        leadingContent = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Options for ${entry.displayName}")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (entry.pinned) "Unpin" else "Pin") },
                        leadingIcon = {
                            Icon(
                                if (entry.pinned) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onTogglePin()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from library") },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        }
                    )
                }
            }
        },
        modifier = Modifier.clickable(
            onClick = onOpen,
            onClickLabel = "Open ${entry.displayName}"
        )
    )
}

@Composable
private fun LibraryEmptyState(onOpenPdf: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FilledTonalIconButton(onClick = onOpenPdf, modifier = Modifier.size(88.dp)) {
            Icon(
                Icons.Filled.PictureAsPdf,
                contentDescription = null,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("No PDFs yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Open a PDF to start reading. Your files stay on this device — nothing is uploaded, ever.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        androidx.compose.material3.Button(onClick = onOpenPdf) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Open PDF")
        }
    }
}

@Composable
private fun NoSearchResults(query: String, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text("No matches for \"$query\"", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClear) { Text("Clear search") }
    }
}
