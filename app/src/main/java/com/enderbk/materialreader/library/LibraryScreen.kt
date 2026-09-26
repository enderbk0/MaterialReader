package com.enderbk.materialreader.library

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import com.enderbk.materialreader.ui.BleedTopBar
import com.enderbk.materialreader.ui.BottomBleedOverlay
import com.enderbk.materialreader.ui.TopBleedOverlay
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import com.enderbk.materialreader.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enderbk.materialreader.data.DocumentEntry
import com.enderbk.materialreader.data.DocumentStore
import com.enderbk.materialreader.data.SettingsStore
import com.enderbk.materialreader.data.SortOrder
import com.enderbk.materialreader.domain.progress
import com.enderbk.materialreader.ui.expressiveEffects
import com.enderbk.materialreader.ui.expressiveSpatial
import com.enderbk.materialreader.util.formatBytes
import com.enderbk.materialreader.util.formatLastOpened

@Composable
fun LibraryScreen(
    documents: DocumentStore,
    settings: SettingsStore,
    meta: DocumentMetaSource,
    onOpenReader: (String) -> Unit,
    floatingPill: Boolean,
    modifier: Modifier = Modifier
) {
    val vm: LibraryViewModel = viewModel(factory = libraryViewModelFactory(documents, settings, meta))
    val state by vm.uiState.collectAsState()
    var searchActive by remember { mutableStateOf(false) }
    var moveTarget by remember { mutableStateOf<DocumentEntry?>(null) }
    var relinkTarget by remember { mutableStateOf<DocumentEntry?>(null) }
    var fabMenuOpen by remember { mutableStateOf(false) }
    var newFolderOpen by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.onDocumentPicked(uri.toString())
    }
    val relinkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val target = relinkTarget
        relinkTarget = null
        if (uri != null && target != null) vm.onRelink(target, uri.toString())
    }

    LaunchedEffect(vm) {
        vm.openDocument.collect { id -> onOpenReader(id) }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .exclude(WindowInsets.navigationBars),
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
                onSortChange = vm::onSortChange
            )
        },
        floatingActionButton = {
            FabMenu(
                expanded = fabMenuOpen,
                onToggle = { fabMenuOpen = !fabMenuOpen },
                onOpenPdf = {
                    fabMenuOpen = false
                    picker.launch(arrayOf("application/pdf"))
                },
                onNewFolder = {
                    fabMenuOpen = false
                    newFolderOpen = true
                },
                floatingPill = floatingPill
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
            Column(
                modifier = Modifier.fillMaxSize()
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
                if (!searchActive && state.folders.isNotEmpty()) {
                    FolderChipsRow(
                        folders = state.folders,
                        selected = state.selectedFolder,
                        onSelect = vm::onSelectFolder,
                        modifier = Modifier.padding(bottom = 4.dp)
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
                                SectionHeader(stringResource(R.string.section_pinned))
                            }
                            items(state.pinned, key = { "p-${it.id}" }) { entry ->
                                DocumentRow(
                                    entry = entry,
                                    onOpen = { vm.onOpenEntry(entry) },
                                    onTogglePin = { vm.onTogglePin(entry) },
                                    onRemove = { vm.onRemove(entry) },
                                    onMoveToFolder = { moveTarget = entry },
                                    onRelink = {
                                        relinkTarget = entry
                                        relinkPicker.launch(arrayOf("application/pdf"))
                                    }
                                )
                            }
                        }
                        if (state.recent.isNotEmpty()) {
                            item(key = "header-recent") {
                                SectionHeader(
                            if (state.pinned.isEmpty()) {
                                stringResource(R.string.section_recent)
                            } else {
                                stringResource(R.string.section_all_documents)
                            }
                        )
                            }
                            items(state.recent, key = { "r-${it.id}" }) { entry ->
                                DocumentRow(
                                    entry = entry,
                                    onOpen = { vm.onOpenEntry(entry) },
                                    onTogglePin = { vm.onTogglePin(entry) },
                                    onRemove = { vm.onRemove(entry) },
                                    onMoveToFolder = { moveTarget = entry },
                                    onRelink = {
                                        relinkTarget = entry
                                        relinkPicker.launch(arrayOf("application/pdf"))
                                    }
                                )
                            }
                        }
                    }
                }
            }
            TopBleedOverlay()
            BottomBleedOverlay(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    if (newFolderOpen) {
        NewFolderDialog(
            onDismiss = { newFolderOpen = false },
            onConfirm = { name ->
                vm.onCreateFolder(name)
                newFolderOpen = false
            }
        )
    }
    moveTarget?.let { target ->
        MoveToFolderDialog(
            entry = target,
            folders = state.folders,
            onDismiss = { moveTarget = null },
            onConfirm = { folder ->
                vm.onMoveToFolder(target, folder)
                moveTarget = null
            },
            onDeleteFolder = vm::onDeleteFolder
        )
    }
    }
}

@Composable
private fun LibraryTopBar(
    searchActive: Boolean,
    query: String,
    libraryEmpty: Boolean,
    sortOrder: SortOrder,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onClearQuery: () -> Unit,
    onSortChange: (SortOrder) -> Unit
) {
    BleedTopBar(
        title = {
            Text(
                if (searchActive) {
                    stringResource(R.string.library_search_title)
                } else {
                    stringResource(R.string.library_title)
                }
            )
        },
        navigationIcon = {
            if (searchActive) {
                IconButton(onClick = onCloseSearch) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            }
        },
        actions = {
            if (!searchActive) {
                IconButton(onClick = onOpenSearch, enabled = !libraryEmpty) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = stringResource(R.string.desc_search_pdfs)
                    )
                }
                SortMenu(current = sortOrder, onSelect = onSortChange)
            } else if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.desc_clear_search)
                    )
                }
            }
        }
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
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
        placeholder = { Text(stringResource(R.string.library_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.desc_clear_search)
                    )
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
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(R.string.desc_sort)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (order) {
                                SortOrder.RECENT -> stringResource(R.string.sort_recent)
                                SortOrder.NAME -> stringResource(R.string.sort_name)
                                SortOrder.SIZE -> stringResource(R.string.sort_size)
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
    onRemove: () -> Unit,
    onMoveToFolder: () -> Unit,
    onRelink: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val metaLine = buildString {
        if (entry.missing) append(stringResource(R.string.library_missing_file))
        entry.pageCount?.let {
            if (isNotEmpty()) append(" • ")
            append("$it pages")
        }
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
            val container = if (entry.missing) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
            val content = if (entry.missing) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            }
            Surface(
                shape = CircleShape,
                color = container,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.PictureAsPdf,
                        contentDescription = null,
                        tint = content
                    )
                }
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(
                            R.string.desc_document_options,
                            entry.displayName
                        )
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (entry.pinned) {
                                    stringResource(R.string.action_unpin)
                                } else {
                                    stringResource(R.string.action_pin)
                                }
                            )
                        },
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
                    if (entry.missing) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_find_file_again)) },
                            leadingIcon = {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onRelink()
                            }
                        )
                    }
                    if (!entry.missing) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_move_to_folder)) },
                        leadingIcon = {
                            Icon(Icons.Filled.Folder, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onMoveToFolder()
                        }
                    )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_remove_from_library)) },
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
        Text(
            stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.library_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        androidx.compose.material3.Button(onClick = onOpenPdf) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.library_open_pdf))
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
        TextButton(onClick = onClear) { Text(stringResource(R.string.library_clear_search)) }
    }
}

@Composable
private fun FolderChipsRow(
    folders: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "folder-all") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.folder_all)) }
            )
        }
        items(folders, key = { "folder-$it" }) { folder ->
            FilterChip(
                selected = selected == folder,
                onClick = { onSelect(if (selected == folder) null else folder) },
                label = { Text(folder, maxLines = 1) }
            )
        }
    }
}

@Composable
private fun MoveToFolderDialog(
    entry: DocumentEntry,
    folders: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
    onDeleteFolder: (String) -> Unit
) {
    var selected by remember(entry) { mutableStateOf(entry.folder) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to_folder_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    entry.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                FolderRadioRow(
                    label = stringResource(R.string.move_to_folder_none),
                    selected = selected == null && newName.isBlank(),
                    onClick = {
                        selected = null
                        newName = ""
                    }
                )
                folders.forEach { folder ->
                    FolderRadioRow(
                        label = folder,
                        selected = selected == folder && newName.isBlank(),
                        onClick = {
                            selected = folder
                            newName = ""
                        },
                        onDelete = { onDeleteFolder(folder) }
                    )
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(48) },
                    label = { Text(stringResource(R.string.move_to_folder_new_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Text(
                    stringResource(R.string.move_to_folder_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(if (newName.isNotBlank()) newName else selected) }) {
                Text(stringResource(R.string.move_to_folder_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun FolderRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.desc_delete_folder, label))
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

/**
 * Expandable "+" menu (stable-API equivalent of the expressive FAB menu):
 * New folder + Open PDF, fully rounded, with expressive enter/exit.
 */
@Composable
private fun FabMenu(
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenPdf: () -> Unit,
    onNewFolder: () -> Unit,
    floatingPill: Boolean,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = expressiveSpatial(),
        label = "fabRotation"
    )
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = if (floatingPill) 96.dp else 0.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(expressiveEffects()) + scaleIn(expressiveSpatial(), initialScale = 0.8f),
            exit = fadeOut(expressiveEffects()) + scaleOut(expressiveSpatial(), targetScale = 0.8f)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FabActionRow(
                    label = stringResource(R.string.fab_new_folder),
                    onClick = onNewFolder,
                    icon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) }
                )
                FabActionRow(
                    label = stringResource(R.string.library_open_pdf),
                    onClick = onOpenPdf,
                    icon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) }
                )
            }
        }
        FloatingActionButton(onClick = onToggle) {
            Icon(
                Icons.Filled.Add,
                contentDescription = if (expanded) "Close menu" else "Add: open PDF or new folder",
                modifier = Modifier.graphicsLayer { rotationZ = rotation }
            )
        }
    }
}

@Composable
private fun FabActionRow(
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.inverseSurface,
            tonalElevation = 6.dp,
            onClick = onClick
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        SmallFloatingActionButton(onClick = onClick) {
            icon()
        }
    }
}

@Composable
private fun NewFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_folder_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(48) },
                    label = { Text(stringResource(R.string.new_folder_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.new_folder_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onConfirm(name.trim())
                    Toast.makeText(
                        context,
                        context.getString(R.string.new_folder_created, name.trim()),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            ) { Text(stringResource(R.string.new_folder_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
